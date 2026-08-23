package com.wildlife.feasibility

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipFile

data class InstalledRegionalCatalogue(
    val regionKey: String,
    val displayName: String,
    val version: String,
)

data class InstalledRegionalTaxon(
    val taxonId: Long,
    val commonName: String,
    val scientificName: String,
    val rarity: EncounterRarity,
    /** Linnaean class from the frozen taxonomy, e.g. "Mammalia", "Aves". Blank if unknown. */
    val taxonClass: String,
)

data class InstalledRegionalAchievement(val label: String, val taxonIds: Set<Long>)

/** Metadata for the immutable regional-content bundle currently installed on the device. */
data class RegionalContentPackInventory(
    val sourceDigest: String,
    val fileBytes: Long,
    val installedAtMs: Long,
    val catalogues: List<InstalledRegionalCatalogue>,
) {
    val isInstalled: Boolean
        get() = fileBytes > 0
}

data class RegionalContentPackInstallResult(
    val installed: Boolean,
    val sourceDigest: String? = null,
    val errorCode: String? = null,
)

/**
 * Reads the generated regional content pack. The app may atomically install a locally supplied,
 * checksum-validated pack, but this store never downloads content or contacts a service itself.
 */
class RegionalCatalogueAssetStore(private val context: Context) {
    /**
     * The base bundle can be safely re-created from the APK. This method removes only that copied
     * content and its metadata; observations, assignments and progression live in other stores.
     */
    fun removeInstalledBasePack(): Boolean {
        val destination = destinationFile()
        val staged = stagedFile()
        val backup = backupFile()
        val removed = listOf(destination, staged, backup).all { file -> !file.exists() || file.delete() }
        preferences().edit().clear().commit()
        return removed
    }

    /**
     * Installs a deterministic `wildlife-content-pack.zip` produced by the catalogue generator.
     * Existing observations and the XP ledger are not part of this pack and are never touched.
     */
    fun installContentPack(archive: File): RegionalContentPackInstallResult {
        if (!archive.isFile || archive.length() !in 1..MAX_ARCHIVE_BYTES) {
            return RegionalContentPackInstallResult(installed = false, errorCode = "invalid_archive")
        }
        val staged = stagedFile()
        return try {
            ZipFile(archive).use { zip ->
                val manifest = manifest(zip) ?: return RegionalContentPackInstallResult(false, errorCode = "invalid_manifest")
                val database = zip.getEntry(PACK_DATABASE) ?: return RegionalContentPackInstallResult(false, errorCode = "missing_database")
                if (database.size !in 1..MAX_DATABASE_BYTES) {
                    return RegionalContentPackInstallResult(false, errorCode = "invalid_database")
                }
                staged.delete()
                zip.getInputStream(database).use { input -> staged.outputStream().use(input::copyTo) }
                if (sha256(staged) != manifest.databaseSha256) {
                    staged.delete()
                    return RegionalContentPackInstallResult(false, errorCode = "checksum_mismatch")
                }
                validateDatabase(staged, manifest.sourceDigest)
                replaceInstalledPack(staged, manifest.sourceDigest, ORIGIN_EXTERNAL)
                RegionalContentPackInstallResult(installed = true, sourceDigest = manifest.sourceDigest)
            }
        } catch (_: Exception) {
            staged.delete()
            RegionalContentPackInstallResult(installed = false, errorCode = "invalid_pack")
        }
    }

    fun inventory(): RegionalContentPackInventory {
        val destination = installedDatabase()
        return RegionalContentPackInventory(
            sourceDigest = installedDigest() ?: baseSourceDigest(),
            fileBytes = destination.length(),
            installedAtMs = preferences().getLong(KEY_INSTALLED_AT_MS, 0L),
            catalogues = catalogues(),
        )
    }

    fun catalogues(): List<InstalledRegionalCatalogue> = database().use { database ->
        database.rawQuery(
            "SELECT DISTINCT region_key, catalogue_version FROM regional_taxon ORDER BY region_key",
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val key = cursor.getString(0)
                    add(InstalledRegionalCatalogue(key, DISPLAY_NAMES.getValue(key), cursor.getString(1)))
                }
            }
        }
    }

    fun taxa(regionKey: String): List<InstalledRegionalTaxon> = database().use { database ->
        database.rawQuery(
            """SELECT rt.taxon_id, t.scientific_name, t.common_names_json, rt.encounter_rarity, t.taxonomy_json
                FROM regional_taxon rt JOIN taxon t ON t.taxon_id = rt.taxon_id
                WHERE rt.region_key = ? ORDER BY rt.sort_order""".trimIndent(),
            arrayOf(regionKey),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val names = org.json.JSONObject(cursor.getString(2))
                    val taxonClass = runCatching {
                        org.json.JSONObject(cursor.getString(4)).optString("class", "")
                    }.getOrDefault("")
                    add(
                        InstalledRegionalTaxon(
                            taxonId = cursor.getLong(0),
                            commonName = names.optString("en", cursor.getString(1)),
                            scientificName = cursor.getString(1),
                            rarity = EncounterRarity.valueOf(cursor.getString(3).uppercase()),
                            taxonClass = taxonClass,
                        ),
                    )
                }
            }
        }
    }

    fun achievements(regionKey: String): List<InstalledRegionalAchievement> = database().use { database ->
        database.rawQuery(
            "SELECT achievement_type, taxon_ids_json FROM regional_achievement WHERE region_key = ? ORDER BY achievement_key",
            arrayOf(regionKey),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val ids = org.json.JSONArray(cursor.getString(1)).let { array ->
                        buildSet { for (index in 0 until array.length()) add(array.getLong(index)) }
                    }
                    add(InstalledRegionalAchievement(cursor.getString(0), ids))
                }
            }
        }
    }

    private fun database(): SQLiteDatabase = SQLiteDatabase.openDatabase(
        installedDatabase().path,
        null,
        SQLiteDatabase.OPEN_READONLY,
    )

    private fun installedDatabase(): File {
        val destination = destinationFile()
        if (destination.exists() && installedDigest() != null) {
            return destination
        }
        installBasePack(destination, baseSourceDigest())
        return destination
    }

    /** Stages the asset before replacing the prior readable bundle to survive interrupted updates. */
    private fun installBasePack(destination: File, digest: String) {
        destination.parentFile?.mkdirs()
        val staged = stagedFile()
        staged.delete()
        try {
            context.assets.open(ASSET).use { input -> staged.outputStream().use(input::copyTo) }
            check(staged.length() > 0L) { "Regional content bundle is empty." }
            replaceInstalledPack(staged, digest, ORIGIN_BUNDLED)
        } catch (error: Exception) {
            staged.delete()
            throw error
        }
    }

    private fun replaceInstalledPack(staged: File, digest: String, origin: String) {
        val destination = destinationFile()
        val backup = backupFile()
        backup.delete()
        if (destination.exists()) check(destination.renameTo(backup)) {
            "Could not stage the previous regional content bundle."
        }
        try {
            check(staged.renameTo(destination)) { "Could not install the regional content bundle." }
            backup.delete()
            preferences().edit()
                .putString(KEY_DIGEST, digest)
                .putString(KEY_ORIGIN, origin)
                .putLong(KEY_INSTALLED_AT_MS, System.currentTimeMillis())
                .apply()
        } catch (error: Exception) {
            staged.delete()
            if (!destination.exists() && backup.exists()) backup.renameTo(destination)
            throw error
        }
    }

    private fun manifest(zip: ZipFile): RegionalContentPackManifest? {
        val entry = zip.getEntry(MANIFEST) ?: return null
        if (entry.size !in 1..MAX_MANIFEST_BYTES) return null
        val json = zip.getInputStream(entry).bufferedReader().use { it.readText() }
        return RegionalContentPackManifest.fromJson(json)
    }

    private fun validateDatabase(databaseFile: File, expectedDigest: String) {
        SQLiteDatabase.openDatabase(databaseFile.path, null, SQLiteDatabase.OPEN_READONLY).use { database ->
            database.rawQuery("SELECT value FROM catalogue_metadata WHERE key = 'source_digest'", null).use { cursor ->
                check(cursor.moveToFirst() && cursor.getString(0) == expectedDigest) {
                    "Pack database digest does not match manifest."
                }
            }
            database.rawQuery("SELECT 1 FROM regional_taxon LIMIT 1", null).close()
            database.rawQuery("SELECT 1 FROM regional_achievement LIMIT 1", null).close()
        }
    }

    private fun installedDigest(): String? = preferences().getString(KEY_DIGEST, null)

    private fun baseSourceDigest(): String = context.assets.open(REPORT).bufferedReader().use { reader ->
        org.json.JSONObject(reader.readText()).getString("source_digest")
    }

    private fun sha256(file: File): String = file.inputStream().use(::sha256)

    private fun sha256(input: InputStream): String = MessageDigest.getInstance("SHA-256").let { digest ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun destinationFile() = File(context.noBackupFilesDir, DATABASE)

    private fun stagedFile() = File(context.noBackupFilesDir, "$DATABASE.staged")

    private fun backupFile() = File(context.noBackupFilesDir, "$DATABASE.backup")

    private fun preferences() = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    private companion object {
        const val ASSET = "catalogues/catalogue.sqlite"
        const val REPORT = "catalogues/catalogue-report.json"
        const val DATABASE = "regional-catalogue.sqlite"
        const val PACK_DATABASE = "catalogue.sqlite"
        const val MANIFEST = "manifest.json"
        const val PREFERENCES = "regional_catalogue_content"
        const val KEY_DIGEST = "source_digest"
        const val KEY_ORIGIN = "content_origin"
        const val KEY_INSTALLED_AT_MS = "installed_at_ms"
        const val ORIGIN_BUNDLED = "bundled"
        const val ORIGIN_EXTERNAL = "external"
        const val MAX_ARCHIVE_BYTES = 50L * 1024 * 1024
        const val MAX_DATABASE_BYTES = 45L * 1024 * 1024
        const val MAX_MANIFEST_BYTES = 16L * 1024
        val DISPLAY_NAMES = mapOf(
            "mediterranean_europe" to "Mediterranean Europe",
            "east_africa" to "East Africa",
            "caribbean" to "Caribbean",
        )
    }
}

internal data class RegionalContentPackManifest(val sourceDigest: String, val databaseSha256: String) {
    companion object {
        fun fromJson(json: String): RegionalContentPackManifest? = runCatching {
            require(SCHEMA_VERSION.containsMatchIn(json))
            val digest = SOURCE_DIGEST.find(json)?.groupValues?.get(1)
                ?: error("Missing source digest")
            val checksum = DATABASE_SHA256.find(json)?.groupValues?.get(1)
                ?: error("Missing database checksum")
            require(digest.matches(Regex("[a-f0-9]{64}")))
            require(checksum.matches(Regex("[a-f0-9]{64}")))
            RegionalContentPackManifest(digest, checksum)
        }.getOrNull()

        private val SCHEMA_VERSION = Regex("\\\"schema_version\\\"\\s*:\\s*1(?:\\s*[,}])")
        private val SOURCE_DIGEST = Regex("\\\"source_digest\\\"\\s*:\\s*\\\"([a-f0-9]{64})\\\"")
        private val DATABASE_SHA256 = Regex("\\\"catalogue\\.sqlite\\\"\\s*:\\s*\\\"([a-f0-9]{64})\\\"")
    }
}
