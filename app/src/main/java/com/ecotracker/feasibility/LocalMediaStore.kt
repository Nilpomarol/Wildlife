package com.wildlife.feasibility

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import org.json.JSONObject

data class StoredMediaSummary(
    val fileCount: Int,
    val totalBytes: Long,
    val capacityBytes: Long,
    val pinnedFiles: Int = 0,
    val corruptFilesRemoved: Int = 0,
)

data class MediaDownloadResult(val localUri: String?, val fromCache: Boolean, val failureCode: String? = null)

interface MediaRepository {
    fun bestLocalVariant(asset: PublishedMediaAsset, variant: String): String?
    fun downloadNow(generationId: String, asset: PublishedMediaAsset, variant: String, pinned: Boolean = false): MediaDownloadResult
    fun inventory(): StoredMediaSummary
    fun evict(bytesNeeded: Long = 0L): Long
}

/** Generation-aware, checksum-validated private media storage backed by transactional metadata. */
class LocalMediaStore internal constructor(
    context: Context,
    private val capacityBytes: Long = DEFAULT_CAPACITY_BYTES,
    private val connectionFactory: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
    private val clock: () -> Long = System::currentTimeMillis,
) : MediaRepository {
    private val appContext = context.applicationContext
    private val root = File(appContext.filesDir, DIRECTORY).apply { mkdirs() }
    private val index = LocalMediaIndexes.application(appContext, root)

    override fun inventory(): StoredMediaSummary {
        val totals = index.totals()
        return StoredMediaSummary(totals.fileCount, totals.totalBytes, capacityBytes, totals.pinnedFiles)
    }

    fun summary(): StoredMediaSummary = inventory()

    override fun bestLocalVariant(asset: PublishedMediaAsset, variant: String): String? = keyLock(asset.assetId, variant) {
        val expected = asset.variant(variant) ?: return@keyLock null
        bestLocalVariantUnlocked(asset, variant, expected)
    }

    override fun downloadNow(
        generationId: String,
        asset: PublishedMediaAsset,
        variant: String,
        pinned: Boolean,
    ): MediaDownloadResult = keyLock(asset.assetId, variant) {
        val expected = asset.variant(variant) ?: return@keyLock MediaDownloadResult(null, false, "missing_variant")
        bestLocalVariantUnlocked(asset, variant, expected)?.let { return@keyLock MediaDownloadResult(it, true) }
        if (expected.expectedBytes != null && expected.expectedBytes > capacityBytes) {
            return@keyLock MediaDownloadResult(null, false, "asset_too_large")
        }
        val url = runCatching { URL(expected.directUrl) }.getOrNull()
            ?: return@keyLock MediaDownloadResult(null, false, "invalid_url")
        if (url.protocol != "https") return@keyLock MediaDownloadResult(null, false, "invalid_url")

        val reservationId = UUID.randomUUID().toString()
        val required = expected.expectedBytes ?: minOf(MAX_SINGLE_ASSET_BYTES, capacityBytes)
        val reserved = STORAGE_COORDINATOR.write {
            index.clearExpiredReservations(clock() - RESERVATION_LEASE_MS)
            evictLocked(required)
            if (index.storedBytes() + index.reservedBytes() + required > capacityBytes) false
            else index.reserve(reservationId, required, clock()).let { true }
        }
        if (!reserved) return@keyLock MediaDownloadResult(null, false, "storage_full")

        val fileName = "${sha256("${asset.assetId}:$variant")}.${extension(expected.mimeType)}"
        val target = File(root, fileName)
        val temporary = File(root, ".$fileName.$reservationId.part")
        try {
            val connection = connectionFactory(url).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", WildlifeNetworkIdentity.REFERENCE_MEDIA_USER_AGENT)
            }
            try {
                if (connection.responseCode !in 200..299) return@keyLock MediaDownloadResult(null, false, "http_${connection.responseCode}")
                if (!connection.contentType.orEmpty().substringBefore(';').equals(expected.mimeType, ignoreCase = true)) {
                    return@keyLock MediaDownloadResult(null, false, "mime_mismatch")
                }
                val maximum = minOf(MAX_SINGLE_ASSET_BYTES, capacityBytes)
                connection.inputStream.use { input ->
                    FileOutputStream(temporary).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var total = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > maximum) return@keyLock MediaDownloadResult(null, false, "asset_too_large")
                            output.write(buffer, 0, count)
                        }
                        output.fd.sync()
                    }
                }
                if (!validate(temporary, expected)) return@keyLock MediaDownloadResult(null, false, "validation_failed")
                val committed = STORAGE_COORDINATOR.write {
                    if (target.exists() && !target.delete()) return@write false
                    if (!temporary.renameTo(target)) return@write false
                    val now = clock()
                    index.commitReservation(
                        reservationId,
                        MediaIndexEntry(
                            asset.assetId, variant, fileName, target.length(), expected.contentSha256,
                            expected.mimeType, generationId, pinned, now, now, target.lastModified(),
                        ),
                    )
                    true
                }
                if (!committed) return@keyLock MediaDownloadResult(null, false, "write_failed")
                return@keyLock MediaDownloadResult(Uri.fromFile(target).toString(), false)
            } finally {
                connection.disconnect()
            }
        } catch (_: Exception) {
            return@keyLock MediaDownloadResult(null, false, "network_failure")
        } finally {
            temporary.delete()
            index.releaseReservation(reservationId)
        }
    }

    /** Removes unpinned least-recently-used files. */
    override fun evict(bytesNeeded: Long): Long = STORAGE_COORDINATOR.write { evictLocked(bytesNeeded) }

    fun reconcileGeneration(activeGenerationId: String, validAssetIds: Set<String>): Int = STORAGE_COORDINATOR.write {
        root.listFiles().orEmpty().filter { it.name.endsWith(".part") }.forEach(File::delete)
        val entries = index.entries()
        var removed = 0
        entries.filter {
            it.assetId !in validAssetIds && !it.assetId.isRuntimeRecoveryAsset()
        }.forEach { entry ->
            ownedFile(entry.fileName)?.delete()
            index.delete(entry.assetId, entry.variant)
            removed++
        }
        index.moveToGeneration(activeGenerationId)
        cleanupOrphans(index.entries())
        evictLocked(0)
        removed
    }

    fun pin(assetId: String, variant: String, pinned: Boolean): Boolean = index.pin(assetId, variant, pinned)

    fun clear() = STORAGE_COORDINATOR.write {
        index.clear()
        root.listFiles().orEmpty().forEach(File::deleteRecursively)
        root.mkdirs()
    }

    fun isStored(localUri: String?): Boolean {
        val path = localUri?.let(Uri::parse)?.takeIf { it.scheme == "file" }?.path ?: return false
        val file = runCatching { File(path).canonicalFile }.getOrNull() ?: return false
        val ownedRoot = runCatching { root.canonicalFile }.getOrNull() ?: return false
        return file.isFile && file.length() > 0 && file.toPath().startsWith(ownedRoot.toPath())
    }

    /** Compatibility path for off-catalogue remote cache; published content uses [downloadNow]. */
    fun storePhoto(remoteUrl: String, taxonId: Long): String? = legacyAsset(
        remoteUrl,
        "legacy-photo:$taxonId:${sha256(remoteUrl).take(16)}",
    )
    fun storeSilhouette(asset: SilhouetteAsset): SilhouetteAsset = asset.copy(localUri = legacyAsset(asset.url, "legacy-silhouette:${asset.matchRank}:${asset.taxonName}"))

    private fun legacyAsset(remoteUrl: String, key: String): String? {
        val variant = PublishedMediaVariant("detail", remoteUrl, mimeFromUrl(remoteUrl), 0, 0, null, null)
        val asset = PublishedMediaAsset(
            key, "legacy", "remote", key, remoteUrl, "Legacy attributed source", "legacy", null,
            "species", "Legacy remote asset", listOf(variant),
        )
        return downloadNow("legacy", asset, "detail").localUri
    }

    private fun bestLocalVariantUnlocked(asset: PublishedMediaAsset, variant: String, expected: PublishedMediaVariant): String? =
        STORAGE_COORDINATOR.read {
            val entry = index.entry(asset.assetId, variant) ?: return@read null
            val file = ownedFile(entry.fileName) ?: return@read null
            if (!validateCached(file, expected, entry)) {
                index.delete(entry.assetId, entry.variant)
                file.delete()
                return@read null
            }
            val now = clock()
            if (now - entry.lastAccessedAt >= ACCESS_TOUCH_INTERVAL_MS) index.touch(entry.assetId, entry.variant, now)
            Uri.fromFile(file).toString()
        }

    private fun evictLocked(bytesNeeded: Long): Long {
        var freed = 0L
        val target = (index.storedBytes() + index.reservedBytes() + bytesNeeded - capacityBytes).coerceAtLeast(0L)
        if (target == 0L) return 0
        index.evictionCandidates().forEach { entry ->
            if (freed >= target) return@forEach
            val file = ownedFile(entry.fileName)
            val bytes = file?.length() ?: entry.bytes
            if (file == null || !file.exists() || file.delete()) {
                index.delete(entry.assetId, entry.variant)
                freed += bytes
            }
        }
        return freed
    }

    private fun cleanupOrphans(entries: List<MediaIndexEntry>) {
        val names = entries.mapTo(mutableSetOf()) { it.fileName } + setOf(LEGACY_INDEX, "$LEGACY_INDEX.backup")
        root.listFiles().orEmpty()
            .filter { it.isFile && it.name !in names && !it.name.endsWith(".part") }
            .forEach(File::delete)
    }

    private fun validate(file: File, expected: PublishedMediaVariant): Boolean {
        if (!file.isFile || file.length() <= 0 || file.length() > MAX_SINGLE_ASSET_BYTES) return false
        if (expected.expectedBytes != null && file.length() != expected.expectedBytes) return false
        if (expected.contentSha256 != null && sha256(file) != expected.contentSha256) return false
        val signature = file.inputStream().use { input -> ByteArray(12).also { input.read(it) } }
        return matchesMime(signature, expected.mimeType)
    }

    private fun validateCached(file: File, expected: PublishedMediaVariant, entry: MediaIndexEntry): Boolean {
        val metadataMatches = file.isFile &&
            file.length() == entry.bytes &&
            entry.mimeType.equals(expected.mimeType, ignoreCase = true) &&
            (expected.expectedBytes == null || entry.bytes == expected.expectedBytes) &&
            (expected.contentSha256 == null || entry.sha256 == expected.contentSha256)
        if (metadataMatches && entry.verifiedModifiedAt > 0 && file.lastModified() == entry.verifiedModifiedAt) {
            val signature = file.inputStream().use { input -> ByteArray(12).also { input.read(it) } }
            return matchesMime(signature, expected.mimeType)
        }
        if (!validate(file, expected)) return false
        index.verify(
            entry.assetId,
            entry.variant,
            file.length(),
            expected.contentSha256,
            expected.mimeType,
            file.lastModified(),
        )
        return true
    }

    private fun ownedFile(name: String): File? = runCatching { File(root, name).canonicalFile }.getOrNull()
        ?.takeIf { it.parentFile == root.canonicalFile }

    internal fun sha256(value: String) = sha256(value.toByteArray())
    private fun sha256(file: File) = file.inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        digest.digest().toHex()
    }
    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    private inline fun <T> keyLock(assetId: String, variant: String, block: () -> T): T {
        val key = "$assetId\u0000$variant"
        val lock = KEY_LOCKS[(key.hashCode() and Int.MAX_VALUE) % KEY_LOCKS.size]
        return synchronized(lock, block)
    }

    private companion object {
        val STORAGE_COORDINATOR = ReentrantReadWriteLock()
        val KEY_LOCKS = Array(64) { Any() }
        const val DIRECTORY = "reference_media_v2"
        const val LEGACY_INDEX = "media-index.json"
        const val DEFAULT_CAPACITY_BYTES = 192L * 1024 * 1024
        const val MAX_SINGLE_ASSET_BYTES = 16L * 1024 * 1024
        const val ACCESS_TOUCH_INTERVAL_MS = 60L * 60L * 1_000L
        const val RESERVATION_LEASE_MS = 2L * 60L * 1_000L
    }
}

internal object LocalMediaIndexes {
    @Volatile private var instance: LocalMediaIndex? = null

    fun application(context: Context, root: File): LocalMediaIndex = instance ?: synchronized(this) {
        instance ?: LocalMediaIndex(context.applicationContext, root).also { instance = it }
    }

    fun resetForTests() = synchronized(this) {
        instance?.close()
        instance = null
    }
}

internal data class MediaIndexTotals(val fileCount: Int, val totalBytes: Long, val pinnedFiles: Int)

internal data class MediaIndexEntry(
    val assetId: String,
    val variant: String,
    val fileName: String,
    val bytes: Long,
    val sha256: String?,
    val mimeType: String,
    val generationId: String,
    val pinned: Boolean,
    val downloadedAt: Long,
    val lastAccessedAt: Long,
    val verifiedModifiedAt: Long,
) {
    fun values() = ContentValues().apply {
        put("asset_id", assetId)
        put("variant", variant)
        put("file_name", fileName)
        put("bytes", bytes)
        put("sha256", sha256)
        put("mime_type", mimeType)
        put("generation_id", generationId)
        put("pinned", if (pinned) 1 else 0)
        put("downloaded_at_ms", downloadedAt)
        put("last_accessed_at_ms", lastAccessedAt)
        put("verified_modified_at_ms", verifiedModifiedAt)
    }

    companion object {
        fun fromCursor(cursor: Cursor) = MediaIndexEntry(
            cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getLong(3),
            cursor.getString(4), cursor.getString(5), cursor.getString(6), cursor.getInt(7) != 0,
            cursor.getLong(8), cursor.getLong(9), cursor.getLong(10),
        )

        fun fromLegacy(value: JSONObject) = MediaIndexEntry(
            value.getString("asset_id"), value.getString("variant"), value.getString("file_name"), value.getLong("bytes"),
            value.optString("sha256").takeIf { it.isNotBlank() && it != "null" }, value.getString("mime_type"),
            value.getString("generation_id"), value.getBoolean("pinned"), value.getLong("downloaded_at"), value.getLong("last_accessed_at"),
            0,
        )
    }
}

internal class LocalMediaIndex(context: Context, private val mediaRoot: File) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE,
    null,
    VERSION,
) {
    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE media_entry (
                asset_id TEXT NOT NULL,
                variant TEXT NOT NULL,
                file_name TEXT NOT NULL UNIQUE,
                bytes INTEGER NOT NULL CHECK(bytes > 0),
                sha256 TEXT,
                mime_type TEXT NOT NULL,
                generation_id TEXT NOT NULL,
                pinned INTEGER NOT NULL CHECK(pinned IN (0,1)),
                downloaded_at_ms INTEGER NOT NULL,
                last_accessed_at_ms INTEGER NOT NULL,
                verified_modified_at_ms INTEGER NOT NULL,
                PRIMARY KEY(asset_id, variant)
            )""".trimIndent(),
        )
        db.execSQL("CREATE INDEX media_entry_lru ON media_entry(pinned, last_accessed_at_ms)")
        db.execSQL("CREATE INDEX media_entry_generation ON media_entry(generation_id)")
        db.execSQL(
            """CREATE TABLE media_reservation (
                reservation_id TEXT PRIMARY KEY,
                bytes INTEGER NOT NULL CHECK(bytes > 0),
                created_at_ms INTEGER NOT NULL
            )""".trimIndent(),
        )
        createMetadataTable(db)
        if (migrateLegacyIndex(db)) markMigrationComplete(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) db.execSQL("ALTER TABLE media_entry ADD COLUMN verified_modified_at_ms INTEGER NOT NULL DEFAULT 0")
        if (oldVersion < 3) createMetadataTable(db)
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        if (!migrationComplete(db)) {
            db.transaction {
                if (migrateLegacyIndex(this)) markMigrationComplete(this)
            }
        }
        if (migrationComplete(db)) {
            // The import transaction has committed before legacy metadata is removed.
            File(mediaRoot, LEGACY_INDEX).delete()
            File(mediaRoot, "$LEGACY_INDEX.backup").delete()
        }
    }

    fun totals(): MediaIndexTotals = readableDatabase.rawQuery(
        "SELECT COUNT(*),COALESCE(SUM(bytes),0),COALESCE(SUM(pinned),0) FROM media_entry",
        emptyArray(),
    ).use { cursor ->
        cursor.moveToFirst()
        MediaIndexTotals(cursor.getInt(0), cursor.getLong(1), cursor.getInt(2))
    }

    fun storedBytes(): Long = readableDatabase.rawQuery("SELECT COALESCE(SUM(bytes),0) FROM media_entry", emptyArray()).use {
        it.moveToFirst(); it.getLong(0)
    }

    fun reservedBytes(): Long = readableDatabase.rawQuery("SELECT COALESCE(SUM(bytes),0) FROM media_reservation", emptyArray()).use {
        it.moveToFirst(); it.getLong(0)
    }

    fun entry(assetId: String, variant: String): MediaIndexEntry? = readableDatabase.rawQuery(
        "$ENTRY_SELECT WHERE asset_id=? AND variant=?",
        arrayOf(assetId, variant),
    ).use { if (it.moveToFirst()) MediaIndexEntry.fromCursor(it) else null }

    fun entries(): List<MediaIndexEntry> = readableDatabase.rawQuery(ENTRY_SELECT, emptyArray()).use { cursor ->
        buildList { while (cursor.moveToNext()) add(MediaIndexEntry.fromCursor(cursor)) }
    }

    fun evictionCandidates(): List<MediaIndexEntry> = readableDatabase.rawQuery(
        "$ENTRY_SELECT WHERE pinned=0 ORDER BY last_accessed_at_ms, downloaded_at_ms",
        emptyArray(),
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(MediaIndexEntry.fromCursor(cursor)) } }

    fun commitReservation(reservationId: String, entry: MediaIndexEntry) = writableDatabase.transaction {
        insertWithOnConflict("media_entry", null, entry.values(), SQLiteDatabase.CONFLICT_REPLACE)
        delete("media_reservation", "reservation_id=?", arrayOf(reservationId))
    }

    fun delete(assetId: String, variant: String) {
        writableDatabase.delete("media_entry", "asset_id=? AND variant=?", arrayOf(assetId, variant))
    }

    fun touch(assetId: String, variant: String, now: Long) {
        writableDatabase.update("media_entry", ContentValues().apply { put("last_accessed_at_ms", now) }, "asset_id=? AND variant=?", arrayOf(assetId, variant))
    }

    fun verify(assetId: String, variant: String, bytes: Long, sha256: String?, mimeType: String, modifiedAt: Long) {
        writableDatabase.update(
            "media_entry",
            ContentValues().apply {
                put("bytes", bytes)
                put("sha256", sha256)
                put("mime_type", mimeType)
                put("verified_modified_at_ms", modifiedAt)
            },
            "asset_id=? AND variant=?",
            arrayOf(assetId, variant),
        )
    }

    fun pin(assetId: String, variant: String, pinned: Boolean): Boolean = writableDatabase.update(
        "media_entry",
        ContentValues().apply { put("pinned", if (pinned) 1 else 0) },
        "asset_id=? AND variant=?",
        arrayOf(assetId, variant),
    ) > 0

    fun moveToGeneration(generationId: String) {
        writableDatabase.update("media_entry", ContentValues().apply { put("generation_id", generationId) }, "generation_id!=?", arrayOf(generationId))
    }

    fun reserve(id: String, bytes: Long, now: Long) {
        writableDatabase.insertOrThrow("media_reservation", null, ContentValues().apply {
            put("reservation_id", id); put("bytes", bytes); put("created_at_ms", now)
        })
    }

    fun releaseReservation(id: String) {
        writableDatabase.delete("media_reservation", "reservation_id=?", arrayOf(id))
    }

    fun clearExpiredReservations(cutoff: Long) {
        writableDatabase.delete("media_reservation", "created_at_ms<?", arrayOf(cutoff.toString()))
    }

    fun clear() = writableDatabase.transaction {
        delete("media_entry", null, null)
        delete("media_reservation", null, null)
    }

    private fun migrateLegacyIndex(db: SQLiteDatabase): Boolean {
        val primary = File(mediaRoot, LEGACY_INDEX)
        val backup = File(mediaRoot, "$LEGACY_INDEX.backup")
        val sources = listOf(primary, backup).filter(File::isFile)
        if (sources.isEmpty()) return true
        sources.forEach { source ->
            val array = runCatching { JSONObject(source.readText()).getJSONArray("entries") }.getOrNull()
                ?: return@forEach
            for (index in 0 until array.length()) {
                val entry = runCatching { MediaIndexEntry.fromLegacy(array.getJSONObject(index)) }.getOrNull()
                    ?: continue
                val file = runCatching { File(mediaRoot, entry.fileName).canonicalFile }.getOrNull()
                if (file?.parentFile == mediaRoot.canonicalFile && file.isFile && file.length() > 0) {
                    db.insertWithOnConflict("media_entry", null, entry.copy(bytes = file.length()).values(), SQLiteDatabase.CONFLICT_REPLACE)
                }
            }
            return true
        }
        return false
    }

    private fun createMetadataTable(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS media_index_meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
    }

    private fun markMigrationComplete(db: SQLiteDatabase) {
        db.insertWithOnConflict(
            "media_index_meta",
            null,
            ContentValues().apply { put("key", MIGRATION_KEY); put("value", "1") },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    private fun migrationComplete(db: SQLiteDatabase): Boolean = db.rawQuery(
        "SELECT 1 FROM media_index_meta WHERE key=? AND value='1' LIMIT 1",
        arrayOf(MIGRATION_KEY),
    ).use { it.moveToFirst() }

    private companion object {
        const val DATABASE = "reference-media-index.db"
        const val VERSION = 3
        const val LEGACY_INDEX = "media-index.json"
        const val MIGRATION_KEY = "legacy_json_imported"
        const val ENTRY_SELECT = "SELECT asset_id,variant,file_name,bytes,sha256,mime_type,generation_id,pinned,downloaded_at_ms,last_accessed_at_ms,verified_modified_at_ms FROM media_entry"
    }
}

internal fun permittedDownloadBytes(storedBytes: Long, assetLimitBytes: Long, totalCapacityBytes: Long): Long = minOf(assetLimitBytes, (totalCapacityBytes - storedBytes).coerceAtLeast(0L))
internal fun validStoredAsset(length: Long, assetLimitBytes: Long) = length in 1..assetLimitBytes
private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
private fun extension(mime: String) = when (mime.lowercase()) { "image/png" -> "png"; "image/webp" -> "webp"; else -> "jpg" }
private fun mimeFromUrl(url: String) = when { url.substringBefore('?').endsWith(".png", true) -> "image/png"; url.substringBefore('?').endsWith(".webp", true) -> "image/webp"; else -> "image/jpeg" }

private fun String.isRuntimeRecoveryAsset() =
    startsWith("legacy-photo:") || startsWith("legacy-silhouette:")
private fun matchesMime(bytes: ByteArray, mime: String) = when (mime.lowercase()) {
    "image/png" -> bytes.size >= 8 && bytes.take(8) == listOf<Byte>(-119, 80, 78, 71, 13, 10, 26, 10)
    "image/jpeg" -> bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()
    "image/webp" -> String(bytes.take(4).toByteArray()) == "RIFF" && String(bytes.drop(8).take(4).toByteArray()) == "WEBP"
    else -> false
}

private inline fun <T> SQLiteDatabase.transaction(block: SQLiteDatabase.() -> T): T {
    beginTransaction()
    return try {
        val result = block()
        setTransactionSuccessful()
        result
    } finally {
        endTransaction()
    }
}
