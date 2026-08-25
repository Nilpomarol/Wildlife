package com.wildlife.feasibility

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.io.InputStream
import java.io.Closeable
import java.security.MessageDigest
import java.time.Instant
import java.util.zip.ZipFile
import org.json.JSONObject

data class InstalledRegionalCatalogue(val regionKey: String, val displayName: String, val version: String)
data class InstalledRegionalTaxon(
    val taxonId: Long,
    val commonName: String,
    val scientificName: String,
    val rarity: EncounterRarity,
    val taxonClass: String,
)
data class InstalledRegionalAchievement(val label: String, val taxonIds: Set<Long>)

data class RegionalContentPackInventory(
    val sourceDigest: String,
    val fileBytes: Long,
    val installedAtMs: Long,
    val catalogues: List<InstalledRegionalCatalogue>,
    val generationId: String = "",
    val generationSequence: Long = 0,
    val schemaVersion: Int = 0,
    val releaseStatus: String = "",
    val origin: String = "",
) { val isInstalled: Boolean get() = fileBytes > 0 }

data class RegionalContentPackInstallResult(
    val installed: Boolean,
    val sourceDigest: String? = null,
    val errorCode: String? = null,
)

internal data class PublishedContentStoreDiagnostics(
    val activeDatabaseOpenCount: Int,
    val fullValidationCount: Int,
    val regionalProjectionQueryCount: Int,
)

internal data class RegionalContentSwapFiles(
    val active: File,
    val staged: File,
    val backup: File,
)

internal data class RegionalContentPackManifest(
    val generationId: String,
    val generationSequence: Long,
    val contentSchemaVersion: Int,
    val sourceDigest: String,
    val releaseStatus: String,
    val generatedAt: String,
    val minimumAppVersion: Int,
    val databaseSha256: String,
    val reportSha256: String,
) {
    companion object {
        fun fromJson(json: String): RegionalContentPackManifest? = runCatching {
            val value = JSONObject(json)
            require(value.getInt("schema_version") == PACK_SCHEMA_VERSION)
            val files = value.getJSONObject("files")
            RegionalContentPackManifest(
                value.getString("generation_id").also { require(it.isNotBlank()) },
                value.getLong("generation_sequence").also { require(it > 0) },
                value.getInt("content_schema_version"),
                value.getString("source_digest").requireSha256(),
                value.getString("release_status"),
                value.getString("generated_at").also(::parseInstant),
                value.getInt("minimum_app_version"),
                files.getString(PACK_DATABASE).requireSha256(),
                files.getString(PACK_REPORT).requireSha256(),
            )
        }.getOrNull()
    }
}

internal object PublishedContentInstallPolicy {
    fun rejectionCode(
        candidate: PublishedContentGeneration,
        current: PublishedContentGeneration?,
        appVersionCode: Int,
        allowDraftContent: Boolean,
    ): String? {
        if (candidate.schemaVersion != CONTENT_SCHEMA_VERSION) return "incompatible_schema"
        if (candidate.minimumAppVersion > appVersionCode) return "incompatible_app"
        if (candidate.releaseStatus !in setOf("draft", "release")) return "invalid_release_status"
        if (candidate.releaseStatus == "draft" && !allowDraftContent) return "draft_not_allowed"
        if (current == null) return null
        if (candidate.generationSequence == current.generationSequence) {
            return if (candidate == current) "already_installed" else "generation_conflict"
        }
        if (candidate.generationSequence < current.generationSequence) return "downgrade"
        if (candidate.generationId == current.generationId) return "generation_conflict"
        return null
    }
}

/** Verified, read-only gateway for the immutable generated content database. */
class RegionalCatalogueAssetStore(
    context: Context,
    private val allowDraftContent: Boolean = BuildConfig.DEBUG,
    private val appVersionCode: Int = BuildConfig.VERSION_CODE,
) : PublishedContentRepository, Closeable {
    private val appContext = context.applicationContext
    private var activeDatabase: SQLiteDatabase? = null
    private var activeGeneration: PublishedContentGeneration? = null
    private var catalogueCache: List<InstalledRegionalCatalogue>? = null
    private var activeDatabaseOpenCount = 0
    private var fullValidationCount = 0
    private var regionalProjectionQueryCount = 0
    private val bundledDescriptor: BundledDescriptor by lazy(::readBundledDescriptor)
    private val taxaContentCache = object : LinkedHashMap<RegionLocaleKey, Map<Long, PublishedTaxonContent>>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<RegionLocaleKey, Map<Long, PublishedTaxonContent>>?) = size > MAX_REGION_CACHE_ENTRIES
    }
    private val mediaByRegionCache = object : LinkedHashMap<String, Map<Long, List<PublishedMediaAsset>>>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Map<Long, List<PublishedMediaAsset>>>?) = size > MAX_REGION_CACHE_ENTRIES
    }

    override fun generation(): PublishedContentGeneration = synchronized(CONTENT_LOCK) {
        openActiveDatabaseLocked()
        checkNotNull(activeGeneration)
    }

    fun inventory(): RegionalContentPackInventory = synchronized(CONTENT_LOCK) {
        val database = openActiveDatabaseLocked()
        val file = destinationFile()
        val generation = checkNotNull(activeGeneration)
        RegionalContentPackInventory(
            generation.sourceDigest, file.length(), preferences().getLong(KEY_INSTALLED_AT_MS, 0L),
            catalogueCache ?: readCatalogues(database).also { catalogueCache = it },
            generation.generationId, generation.generationSequence, generation.schemaVersion, generation.releaseStatus,
            preferences().getString(KEY_ORIGIN, "") ?: "",
        )
    }

    override fun catalogues(): List<InstalledRegionalCatalogue> = synchronized(CONTENT_LOCK) {
        catalogueCache ?: readCatalogues(openActiveDatabaseLocked()).also { catalogueCache = it }
    }

    private fun readCatalogues(database: SQLiteDatabase): List<InstalledRegionalCatalogue> =
        database.rawQuery(
            """SELECT cv.region_key, r.names_json, cv.catalogue_version
                FROM catalogue_version cv JOIN region r ON r.region_key = cv.region_key
                ORDER BY r.display_order""".trimIndent(), null,
        ).use { cursor -> buildList {
            while (cursor.moveToNext()) {
                val names = JSONObject(cursor.getString(1))
                add(InstalledRegionalCatalogue(cursor.getString(0), names.optString("en", cursor.getString(0)), cursor.getString(2)))
            }
        } }

    override fun taxa(regionKey: String): List<InstalledRegionalTaxon> = withDatabase { database ->
        database.rawQuery(
            """SELECT rt.taxon_id, t.scientific_name,
                       COALESCE((SELECT tn.common_name FROM taxon_name tn WHERE tn.taxon_id=t.taxon_id AND tn.locale='en'),t.scientific_name),
                       rt.encounter_rarity, t.taxonomy_json
                FROM regional_taxon rt JOIN taxon t ON t.taxon_id = rt.taxon_id
                WHERE rt.region_key = ? ORDER BY rt.sort_order""".trimIndent(), arrayOf(regionKey),
        ).use { cursor -> buildList {
            while (cursor.moveToNext()) {
                add(InstalledRegionalTaxon(
                    cursor.getLong(0), cursor.getString(2), cursor.getString(1),
                    EncounterRarity.valueOf(cursor.getString(3).uppercase()),
                    JSONObject(cursor.getString(4)).optString("class", ""),
                ))
            }
        } }
    }

    override fun achievements(regionKey: String): List<InstalledRegionalAchievement> = withDatabase { database ->
        database.rawQuery(
            """SELECT ra.achievement_type,rat.taxon_id
                FROM regional_achievement ra
                LEFT JOIN regional_achievement_taxon rat
                  ON rat.region_key=ra.region_key AND rat.catalogue_version=ra.catalogue_version AND rat.achievement_key=ra.achievement_key
                WHERE ra.region_key=? ORDER BY ra.achievement_key,rat.sort_order""".trimIndent(),
            arrayOf(regionKey),
        ).use { cursor ->
            val members = linkedMapOf<String, MutableSet<Long>>()
            while (cursor.moveToNext()) {
                val ids = members.getOrPut(cursor.getString(0), ::linkedSetOf)
                if (!cursor.isNull(1)) ids += cursor.getLong(1)
            }
            members.map { (type, ids) -> InstalledRegionalAchievement(type, ids) }
        }
    }

    override fun publishedTaxonCount(): Int = withDatabase { database ->
        database.rawQuery("SELECT COUNT(DISTINCT taxon_id) FROM regional_taxon", null).use { cursor ->
            check(cursor.moveToFirst()); cursor.getInt(0)
        }
    }

    override fun mediaAssetIds(): Set<String> = withDatabase { database ->
        database.rawQuery("SELECT asset_id FROM media_asset", null).use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
    }

    override fun mediaAssets(regionKey: String): List<PublishedMediaAsset> =
        mediaByTaxon(regionKey).values.flatten().distinctBy { it.assetId }

    override fun mediaByTaxon(regionKey: String): Map<Long, List<PublishedMediaAsset>> = synchronized(CONTENT_LOCK) {
        mediaByRegionCache[regionKey] ?: readMediaByTaxon(openActiveDatabaseLocked(), regionKey)
            .also { mediaByRegionCache[regionKey] = it }
    }

    override fun mediaAsset(assetId: String): PublishedMediaAsset? = withDatabase { database ->
        readMediaAsset(database, assetId)
    }

    override fun taxon(taxonId: Long, locale: String): PublishedTaxonContent? =
        withDatabase { database -> readTaxon(database, taxonId, locale) }

    override fun taxaContent(regionKey: String, locale: String): Map<Long, PublishedTaxonContent> = synchronized(CONTENT_LOCK) {
        val key = RegionLocaleKey(regionKey, locale)
        taxaContentCache[key] ?: readTaxaContent(openActiveDatabaseLocked(), regionKey, locale).also { content ->
            taxaContentCache[key] = content
            mediaByRegionCache[regionKey] = content.mapNotNull { (taxonId, taxon) ->
                taxon.media.takeIf { it.isNotEmpty() }?.let { taxonId to it }
            }.toMap()
        }
    }

    /** Four bounded regional queries replace the previous per-taxon query cascade. */
    private fun readTaxaContent(
        database: SQLiteDatabase,
        regionKey: String,
        locale: String,
    ): Map<Long, PublishedTaxonContent> {
        regionalProjectionQueryCount += REGIONAL_PROJECTION_QUERY_COUNT
        val builders = linkedMapOf<Long, PublishedTaxonBuilder>()
        database.rawQuery(
            """SELECT t.taxon_id,t.scientific_name,t.rank,t.taxonomy_json,
                       COALESCE(
                         (SELECT tn.common_name FROM taxon_name tn WHERE tn.taxon_id=t.taxon_id AND tn.locale=?),
                         (SELECT tn.common_name FROM taxon_name tn WHERE tn.taxon_id=t.taxon_id AND tn.locale='en'),
                         t.scientific_name)
                FROM regional_taxon rt JOIN taxon t ON t.taxon_id=rt.taxon_id
                WHERE rt.region_key=? ORDER BY rt.sort_order""".trimIndent(),
            arrayOf(locale, regionKey),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val taxonId = cursor.getLong(0)
                val taxonomy = JSONObject(cursor.getString(3))
                builders[taxonId] = PublishedTaxonBuilder(
                    taxonId = taxonId,
                    scientificName = cursor.getString(1),
                    commonName = cursor.getString(4),
                    rank = cursor.getString(2),
                    taxonClass = taxonomy.optString("class", ""),
                    familyName = taxonomy.optString("family").takeIf(String::isNotBlank),
                )
            }
        }
        if (builders.isEmpty()) return emptyMap()

        database.rawQuery(
            """SELECT td.taxon_id,td.locale,td.summary,td.source_url,td.attribution,td.licence_code,td.licence_url
                FROM regional_taxon rt JOIN taxon_description td ON td.taxon_id=rt.taxon_id
                WHERE rt.region_key=?
                ORDER BY rt.sort_order,CASE WHEN td.locale=? THEN 0 WHEN td.locale='en' THEN 1 ELSE 2 END""".trimIndent(),
            arrayOf(regionKey, locale),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val builder = builders[cursor.getLong(0)] ?: continue
                if (builder.description == null) builder.description = PublishedTaxonDescription(
                    cursor.getString(1), cursor.getString(2), cursor.getString(3),
                    cursor.getString(4), cursor.getString(5), cursor.getString(6),
                )
            }
        }
        database.rawQuery(
            """SELECT tc.taxon_id,tc.status,tc.authority,tc.source_url,tc.retrieved_at
                FROM regional_taxon rt JOIN taxon_conservation tc ON tc.taxon_id=rt.taxon_id
                WHERE rt.region_key=? ORDER BY rt.sort_order""".trimIndent(),
            arrayOf(regionKey),
        ).use { cursor ->
            while (cursor.moveToNext()) builders[cursor.getLong(0)]?.conservation =
                PublishedTaxonConservation(cursor.getString(1), cursor.getString(2), cursor.getString(3), cursor.getString(4))
        }
        val media = readMediaByTaxon(database, regionKey)
        builders.forEach { (taxonId, builder) -> builder.media = media[taxonId].orEmpty() }
        return builders.mapValuesTo(linkedMapOf()) { (_, builder) -> builder.build() }
    }

    /** One flattened asset/variant query; an asset is materialised once after all variants arrive. */
    private fun readMediaByTaxon(database: SQLiteDatabase, regionKey: String): Map<Long, List<PublishedMediaAsset>> {
        val assetsByTaxon = linkedMapOf<Long, LinkedHashMap<String, PublishedMediaBuilder>>()
        database.rawQuery(
            """SELECT ma.taxon_id,ma.asset_id,ma.media_type,ma.provider,ma.provider_asset_id,
                        ma.source_url,ma.creator,ma.licence_code,ma.licence_url,ma.match_rank,ma.matched_taxon_name,
                        mv.variant,mv.direct_url,mv.mime_type,mv.width,mv.height,mv.expected_bytes,mv.content_sha256
                FROM regional_taxon rt JOIN media_asset ma ON ma.taxon_id=rt.taxon_id
                LEFT JOIN media_variant mv ON mv.asset_id=ma.asset_id
                WHERE rt.region_key=?
                ORDER BY rt.sort_order,ma.media_type,ma.asset_id,mv.variant""".trimIndent(),
            arrayOf(regionKey),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val taxonAssets = assetsByTaxon.getOrPut(cursor.getLong(0), ::linkedMapOf)
                val asset = taxonAssets.getOrPut(cursor.getString(1)) { cursor.toMediaBuilder(1) }
                if (!cursor.isNull(11)) asset.variants += cursor.toMediaVariant(11)
            }
        }
        return assetsByTaxon.mapValuesTo(linkedMapOf()) { (_, assets) ->
            assets.values.map(PublishedMediaBuilder::build)
        }
    }

    private fun readMediaAsset(database: SQLiteDatabase, assetId: String): PublishedMediaAsset? {
        var builder: PublishedMediaBuilder? = null
        database.rawQuery(
            """SELECT ma.asset_id,ma.media_type,ma.provider,ma.provider_asset_id,ma.source_url,
                        ma.creator,ma.licence_code,ma.licence_url,ma.match_rank,ma.matched_taxon_name,
                        mv.variant,mv.direct_url,mv.mime_type,mv.width,mv.height,mv.expected_bytes,mv.content_sha256
                FROM media_asset ma LEFT JOIN media_variant mv ON mv.asset_id=ma.asset_id
                WHERE ma.asset_id=? ORDER BY mv.variant""".trimIndent(),
            arrayOf(assetId),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val asset = builder ?: cursor.toMediaBuilder(0).also { builder = it }
                if (!cursor.isNull(10)) asset.variants += cursor.toMediaVariant(10)
            }
        }
        return builder?.build()
    }

    private fun readTaxon(database: SQLiteDatabase, taxonId: Long, locale: String): PublishedTaxonContent? {
        val identity = database.rawQuery(
            """SELECT t.scientific_name,t.rank,t.taxonomy_json,
                       COALESCE(
                         (SELECT tn.common_name FROM taxon_name tn WHERE tn.taxon_id=t.taxon_id AND tn.locale=?),
                         (SELECT tn.common_name FROM taxon_name tn WHERE tn.taxon_id=t.taxon_id AND tn.locale='en'),
                         t.scientific_name)
                FROM taxon t WHERE t.taxon_id=?""".trimIndent(),
            arrayOf(locale, taxonId.toString()),
        ).use { cursor -> if (!cursor.moveToFirst()) null else arrayOf(cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getString(3)) }
            ?: return null
        val description = database.rawQuery(
            """SELECT locale, summary, source_url, attribution, licence_code, licence_url
                FROM taxon_description WHERE taxon_id = ? ORDER BY CASE WHEN locale = ? THEN 0 WHEN locale = 'en' THEN 1 ELSE 2 END LIMIT 1""".trimIndent(),
            arrayOf(taxonId.toString(), locale),
        ).use { cursor -> if (!cursor.moveToFirst()) null else PublishedTaxonDescription(cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getString(3), cursor.getString(4), cursor.getString(5)) }
        val conservation = database.rawQuery(
            "SELECT status, authority, source_url, retrieved_at FROM taxon_conservation WHERE taxon_id = ?", arrayOf(taxonId.toString()),
        ).use { cursor -> if (!cursor.moveToFirst()) null else PublishedTaxonConservation(cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getString(3)) }
        val assets = database.rawQuery(
            """SELECT asset_id, media_type, provider, provider_asset_id, source_url, creator, licence_code, licence_url, match_rank, matched_taxon_name
                FROM media_asset WHERE taxon_id = ? ORDER BY media_type, asset_id""".trimIndent(), arrayOf(taxonId.toString()),
        ).use { cursor -> buildList {
            while (cursor.moveToNext()) {
                val assetId = cursor.getString(0)
                val variants = database.rawQuery(
                    "SELECT variant, direct_url, mime_type, width, height, expected_bytes, content_sha256 FROM media_variant WHERE asset_id = ? ORDER BY variant", arrayOf(assetId),
                ).use { variantsCursor -> buildList {
                    while (variantsCursor.moveToNext()) add(PublishedMediaVariant(
                        variantsCursor.getString(0), variantsCursor.getString(1), variantsCursor.getString(2), variantsCursor.getInt(3), variantsCursor.getInt(4),
                        if (variantsCursor.isNull(5)) null else variantsCursor.getLong(5), if (variantsCursor.isNull(6)) null else variantsCursor.getString(6),
                    ))
                } }
                add(PublishedMediaAsset(
                    assetId, cursor.getString(1), cursor.getString(2), cursor.getString(3), cursor.getString(4), cursor.getString(5), cursor.getString(6),
                    if (cursor.isNull(7)) null else cursor.getString(7), cursor.getString(8), cursor.getString(9), variants,
                ))
            }
        } }
        val taxonomy = JSONObject(identity[2])
        return PublishedTaxonContent(
            taxonId, identity[0], identity[3], identity[1],
            taxonomy.optString("class", ""), description, conservation, assets,
            taxonomy.optString("family").takeIf(String::isNotBlank),
        )
    }

    fun removeInstalledBasePack(): Boolean = synchronized(CONTENT_LOCK) {
        closeActiveDatabaseLocked()
        val removed = ownedFiles().all { !it.exists() || it.delete() }
        preferences().edit().clear().commit() && removed
    }

    internal fun swapFilesForTesting() = RegionalContentSwapFiles(
        destinationFile(), stagedFile(), backupFile(),
    )

    /** Removes only the superseded species cache after the replacement generation is readable. */
    fun migrateLegacyCacheIfNeeded(): Boolean = synchronized(CONTENT_LOCK) {
        val activeGeneration = generation().generationId
        if (preferences().getString(KEY_LEGACY_MIGRATED_GENERATION, null) == activeGeneration) return@synchronized true
        val legacy = appContext.getDatabasePath(LEGACY_DATABASE)
        val removed = !legacy.exists() || appContext.deleteDatabase(LEGACY_DATABASE)
        removed && preferences().edit().putString(KEY_LEGACY_MIGRATED_GENERATION, activeGeneration).commit()
    }

    fun installContentPack(archive: File): RegionalContentPackInstallResult = synchronized(CONTENT_LOCK) {
        if (!archive.isFile || archive.length() !in 1..MAX_ARCHIVE_BYTES) return@synchronized RegionalContentPackInstallResult(false, errorCode = "invalid_archive")
        val staged = stagedFile()
        try {
            ZipFile(archive).use { zip ->
                val entries = zip.entries().asSequence().toList()
                if (entries.any { it.isDirectory } || entries.map { it.name }.toSet() != PACK_ENTRIES || entries.size != PACK_ENTRIES.size) {
                    return@synchronized RegionalContentPackInstallResult(false, errorCode = "invalid_entries")
                }
                val manifestBytes = readBounded(zip.getInputStream(zip.getEntry(MANIFEST)), MAX_MANIFEST_BYTES)
                val manifest = RegionalContentPackManifest.fromJson(manifestBytes.toString(Charsets.UTF_8))
                    ?: return@synchronized RegionalContentPackInstallResult(false, errorCode = "invalid_manifest")
                val reportBytes = readBounded(zip.getInputStream(zip.getEntry(PACK_REPORT)), MAX_REPORT_BYTES)
                if (sha256(reportBytes) != manifest.reportSha256 || !reportMatches(JSONObject(reportBytes.toString(Charsets.UTF_8)), manifest)) {
                    return@synchronized RegionalContentPackInstallResult(false, errorCode = "invalid_report")
                }
                staged.delete()
                zip.getInputStream(zip.getEntry(PACK_DATABASE)).use { copyBounded(it, staged, MAX_DATABASE_BYTES) }
                if (sha256(staged) != manifest.databaseSha256) return@synchronized RegionalContentPackInstallResult(false, errorCode = "checksum_mismatch")
                val candidate = validateDatabase(staged, manifest)
                val current = activeGeneration ?: validInstalledGeneration()
                PublishedContentInstallPolicy.rejectionCode(candidate, current, appVersionCode, allowDraftContent)?.let { code ->
                    return@synchronized RegionalContentPackInstallResult(false, candidate.sourceDigest, code)
                }
                replaceInstalledPack(staged, candidate, ORIGIN_EXTERNAL)
                RegionalContentPackInstallResult(true, candidate.sourceDigest)
            }
        } catch (_: Exception) {
            staged.delete()
            RegionalContentPackInstallResult(false, errorCode = "invalid_pack")
        }
    }

    private fun <T> withDatabase(block: (SQLiteDatabase) -> T): T = synchronized(CONTENT_LOCK) {
        block(openActiveDatabaseLocked())
    }

    private fun openActiveDatabaseLocked(): SQLiteDatabase {
        activeDatabase?.takeIf(SQLiteDatabase::isOpen)?.let { return it }
        val file = installedDatabaseLocked()
        return openReadOnly(file).also { database ->
            val generation = readGeneration(database)
            check(PublishedContentInstallPolicy.rejectionCode(generation, null, appVersionCode, allowDraftContent) == null) {
                "No compatible published content is installed."
            }
            activeDatabase = database
            activeGeneration = generation
            activeDatabaseOpenCount++
        }
    }

    private fun installedDatabaseLocked(): File {
        val current = recoverInterruptedSwap()
            ?.takeIf { PublishedContentInstallPolicy.rejectionCode(it, null, appVersionCode, allowDraftContent) == null }
        val bundled = bundledDescriptor
        val updateCode = PublishedContentInstallPolicy.rejectionCode(bundled.generation, current, appVersionCode, allowDraftContent)
        if (current == null || updateCode == null) installBasePack(bundled)
        return destinationFile()
    }

    private fun recoverInterruptedSwap(): PublishedContentGeneration? {
        stagedFile().delete()
        val destination = destinationFile()
        val backup = backupFile()
        val destinationGeneration = validGeneration(destination)
        if (destinationGeneration == null) {
            val backupGeneration = validGeneration(backup) ?: return null
            destination.delete()
            check(backup.renameTo(destination)) { "Could not restore the previous content generation." }
            return backupGeneration
        }
        backup.delete()
        return destinationGeneration
    }

    private fun installBasePack(descriptor: BundledDescriptor) {
        val staged = stagedFile(); staged.delete()
        appContext.assets.open(ASSET).use { copyBounded(it, staged, MAX_DATABASE_BYTES) }
        check(sha256(staged) == descriptor.databaseSha256) { "Bundled content checksum mismatch." }
        val generation = validateDatabase(staged, null)
        check(generation == descriptor.generation) { "Bundled report does not match its database." }
        replaceInstalledPack(staged, generation, ORIGIN_BUNDLED)
    }

    private fun replaceInstalledPack(staged: File, generation: PublishedContentGeneration, origin: String) {
        closeActiveDatabaseLocked()
        val destination = destinationFile(); val backup = backupFile()
        destination.parentFile?.mkdirs(); backup.delete()
        if (destination.exists()) check(destination.renameTo(backup)) { "Could not preserve the active content generation." }
        try {
            check(staged.renameTo(destination)) { "Could not activate staged content." }
            check(validateDatabase(destination, null) == generation)
            check(preferences().edit().putString(KEY_DIGEST, generation.sourceDigest).putString(KEY_ORIGIN, origin).putLong(KEY_INSTALLED_AT_MS, System.currentTimeMillis()).commit())
            backup.delete()
            clearProjectionCachesLocked()
        } catch (error: Exception) {
            destination.delete()
            if (backup.exists()) check(backup.renameTo(destination))
            throw error
        }
    }

    private fun validateDatabase(file: File, manifest: RegionalContentPackManifest?): PublishedContentGeneration {
        fullValidationCount++
        check(file.isFile && file.length() in 1..MAX_DATABASE_BYTES)
        return openReadOnly(file).use { database ->
            database.rawQuery("PRAGMA quick_check", null).use { cursor -> check(cursor.moveToFirst() && cursor.getString(0) == "ok") }
            database.rawQuery("PRAGMA user_version", null).use { cursor -> check(cursor.moveToFirst() && cursor.getInt(0) == CONTENT_SCHEMA_VERSION) }
            database.rawQuery("PRAGMA foreign_key_check", null).use { cursor -> check(!cursor.moveToFirst()) }
            REQUIRED_TABLES.forEach { table -> database.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table)).use { cursor -> check(cursor.moveToFirst()) } }
            val generation = readGeneration(database)
            validatePublicationSemantics(database, generation)
            check(database.rawQuery("SELECT 1 FROM regional_taxon LIMIT 1", null).use { it.moveToFirst() })
            check(database.rawQuery("SELECT 1 FROM regional_achievement LIMIT 1", null).use { it.moveToFirst() })
            if (manifest != null) check(manifest.matches(generation))
            generation
        }
    }

    private fun validatePublicationSemantics(database: SQLiteDatabase, generation: PublishedContentGeneration) {
        val regionCount = database.rawQuery("SELECT COUNT(*) FROM region", null).use {
            check(it.moveToFirst()); it.getInt(0)
        }
        check(regionCount in 24..25) { "Published content must contain the adopted region index." }
        val catalogueCount = database.rawQuery("SELECT COUNT(*) FROM catalogue_version", null).use {
            check(it.moveToFirst()); it.getInt(0)
        }
        check(catalogueCount in 1..regionCount)
        check(database.rawQuery(
            """SELECT 1 FROM regional_taxon rt JOIN taxon t ON t.taxon_id=rt.taxon_id
                WHERE t.accepted_taxon_id!=t.taxon_id LIMIT 1""".trimIndent(), null,
        ).use { !it.moveToFirst() }) { "Regional catalogues contain a superseded taxon." }
        check(database.rawQuery(
            """SELECT 1 FROM media_asset ma JOIN taxon t ON t.taxon_id=ma.taxon_id
                WHERE t.accepted_taxon_id!=t.taxon_id LIMIT 1""".trimIndent(), null,
        ).use { !it.moveToFirst() }) { "Published media is assigned to a superseded taxon." }
        if (generation.releaseStatus != "release") return
        check(database.rawQuery("SELECT 1 FROM catalogue_version WHERE status!='frozen' LIMIT 1", null).use { !it.moveToFirst() })
        database.rawQuery(
            """SELECT cv.region_key,
                       (SELECT COUNT(*) FROM regional_achievement ra WHERE ra.region_key=cv.region_key AND ra.achievement_type='essentials'),
                       (SELECT COUNT(*) FROM regional_achievement_taxon rat JOIN regional_achievement ra
                          ON ra.region_key=rat.region_key AND ra.catalogue_version=rat.catalogue_version AND ra.achievement_key=rat.achievement_key
                        WHERE ra.region_key=cv.region_key AND ra.achievement_type='essentials'),
                       (SELECT COUNT(*) FROM regional_achievement ra WHERE ra.region_key=cv.region_key AND ra.achievement_type='icons'),
                       (SELECT COUNT(*) FROM regional_achievement_taxon rat JOIN regional_achievement ra
                          ON ra.region_key=rat.region_key AND ra.catalogue_version=rat.catalogue_version AND ra.achievement_key=rat.achievement_key
                        WHERE ra.region_key=cv.region_key AND ra.achievement_type='icons')
                FROM catalogue_version cv""".trimIndent(), null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                check(cursor.getInt(1) == 1 && cursor.getInt(2) == 10) { "${cursor.getString(0)} requires 10 Essentials." }
                check(cursor.getInt(3) == 1 && cursor.getInt(4) == 5) { "${cursor.getString(0)} requires 5 Icons." }
            }
        }
    }

    private fun readGeneration(database: SQLiteDatabase): PublishedContentGeneration = database.rawQuery(
        "SELECT generation_id,generation_sequence,schema_version,source_digest,release_status,generated_at,minimum_app_version FROM content_generation", null,
    ).use { cursor ->
        check(cursor.moveToFirst())
        val value = PublishedContentGeneration(cursor.getString(0), cursor.getLong(1).also { check(it > 0) }, cursor.getInt(2), cursor.getString(3).requireSha256(), cursor.getString(4), cursor.getString(5).also(::parseInstant), cursor.getInt(6))
        check(!cursor.moveToNext()) { "Multiple content generations found." }; value
    }

    private fun validInstalledGeneration() = validGeneration(destinationFile())?.takeIf { PublishedContentInstallPolicy.rejectionCode(it, null, appVersionCode, allowDraftContent) == null }
    private fun validGeneration(file: File): PublishedContentGeneration? = runCatching { validateDatabase(file, null) }.getOrNull()

    private fun readBundledDescriptor(): BundledDescriptor = appContext.assets.open(REPORT).bufferedReader().use { reader ->
        val report = JSONObject(reader.readText())
        BundledDescriptor(generationFromReport(report), report.getString("database_sha256").requireSha256())
    }
    private fun generationFromReport(report: JSONObject) = PublishedContentGeneration(
        report.getString("generation_id"), report.getLong("generation_sequence"), report.getInt("schema_version"), report.getString("source_digest").requireSha256(),
        report.getString("release_status"), report.getString("generated_at").also(::parseInstant), report.getInt("minimum_app_version"),
    )
    private fun reportMatches(report: JSONObject, manifest: RegionalContentPackManifest): Boolean = runCatching {
        generationFromReport(report) == PublishedContentGeneration(manifest.generationId, manifest.generationSequence, manifest.contentSchemaVersion, manifest.sourceDigest, manifest.releaseStatus, manifest.generatedAt, manifest.minimumAppVersion) && report.getString("database_sha256") == manifest.databaseSha256
    }.getOrDefault(false)

    private fun openReadOnly(file: File) = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)

    override fun close() = synchronized(CONTENT_LOCK) { closeActiveDatabaseLocked() }

    internal fun diagnostics() = synchronized(CONTENT_LOCK) {
        PublishedContentStoreDiagnostics(activeDatabaseOpenCount, fullValidationCount, regionalProjectionQueryCount)
    }

    private fun closeActiveDatabaseLocked() {
        activeDatabase?.close()
        activeDatabase = null
        activeGeneration = null
        clearProjectionCachesLocked()
    }

    private fun clearProjectionCachesLocked() {
        catalogueCache = null
        taxaContentCache.clear()
        mediaByRegionCache.clear()
    }
    private fun sha256(file: File) = file.inputStream().use(::sha256)
    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()
    private fun sha256(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
        return digest.digest().toHex()
    }
    private fun readBounded(input: InputStream, maximum: Long): ByteArray = input.use {
        val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(DEFAULT_BUFFER_SIZE); var total = 0L
        while (true) { val count = it.read(buffer); if (count < 0) break; total += count; check(total <= maximum); output.write(buffer, 0, count) }
        output.toByteArray()
    }
    private fun copyBounded(input: InputStream, target: File, maximum: Long) {
        target.parentFile?.mkdirs()
        target.outputStream().use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE); var total = 0L
            while (true) { val count = input.read(buffer); if (count < 0) break; total += count; check(total <= maximum); output.write(buffer, 0, count) }
            output.flush()
        }
    }

    private fun destinationFile() = File(appContext.noBackupFilesDir, DATABASE)
    private fun stagedFile() = File(appContext.noBackupFilesDir, "$DATABASE.staged")
    private fun backupFile() = File(appContext.noBackupFilesDir, "$DATABASE.backup")
    private fun ownedFiles() = listOf(destinationFile(), stagedFile(), backupFile())
    private fun preferences() = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private data class BundledDescriptor(val generation: PublishedContentGeneration, val databaseSha256: String)

    private companion object {
        val CONTENT_LOCK = Any()
        const val ASSET = "catalogues/catalogue.sqlite"
        const val REPORT = "catalogues/catalogue-report.json"
        const val DATABASE = "regional-catalogue.sqlite"
        const val PREFERENCES = "regional_catalogue_content"
        const val KEY_DIGEST = "source_digest"
        const val KEY_ORIGIN = "content_origin"
        const val KEY_INSTALLED_AT_MS = "installed_at_ms"
        const val KEY_LEGACY_MIGRATED_GENERATION = "legacy_migrated_generation"
        const val LEGACY_DATABASE = "wildlife_catalogue.db"
        const val ORIGIN_BUNDLED = "bundled"
        const val ORIGIN_EXTERNAL = "external"
        const val MAX_ARCHIVE_BYTES = 50L * 1024 * 1024
        const val MAX_DATABASE_BYTES = 45L * 1024 * 1024
        const val MAX_MANIFEST_BYTES = 32L * 1024
        const val MAX_REPORT_BYTES = 256L * 1024
        const val MAX_REGION_CACHE_ENTRIES = 3
        const val REGIONAL_PROJECTION_QUERY_COUNT = 4
        val PACK_ENTRIES = setOf(MANIFEST, PACK_DATABASE, PACK_REPORT)
        val REQUIRED_TABLES = setOf("content_generation", "taxon", "taxon_name", "taxon_description", "taxon_conservation", "region", "catalogue_version", "regional_taxon", "regional_achievement", "regional_achievement_taxon", "media_asset", "media_variant")
    }
}

private data class RegionLocaleKey(val regionKey: String, val locale: String)

private data class PublishedTaxonBuilder(
    val taxonId: Long,
    val scientificName: String,
    val commonName: String,
    val rank: String,
    val taxonClass: String,
    val familyName: String?,
    var description: PublishedTaxonDescription? = null,
    var conservation: PublishedTaxonConservation? = null,
    var media: List<PublishedMediaAsset> = emptyList(),
) {
    fun build() = PublishedTaxonContent(
        taxonId, scientificName, commonName, rank, taxonClass, description, conservation, media,
        familyName,
    )
}

private data class PublishedMediaBuilder(
    val assetId: String,
    val mediaType: String,
    val provider: String,
    val providerAssetId: String,
    val sourceUrl: String,
    val creator: String?,
    val licenceCode: String,
    val licenceUrl: String?,
    val matchRank: String,
    val matchedTaxonName: String,
    val variants: MutableList<PublishedMediaVariant> = mutableListOf(),
) {
    fun build() = PublishedMediaAsset(
        assetId, mediaType, provider, providerAssetId, sourceUrl, creator, licenceCode,
        licenceUrl, matchRank, matchedTaxonName, variants.toList(),
    )
}

private fun Cursor.toMediaBuilder(offset: Int) = PublishedMediaBuilder(
    assetId = getString(offset),
    mediaType = getString(offset + 1),
    provider = getString(offset + 2),
    providerAssetId = getString(offset + 3),
    sourceUrl = getString(offset + 4),
    creator = if (isNull(offset + 5)) null else getString(offset + 5),
    licenceCode = getString(offset + 6),
    licenceUrl = if (isNull(offset + 7)) null else getString(offset + 7),
    matchRank = getString(offset + 8),
    matchedTaxonName = getString(offset + 9),
)

private fun Cursor.toMediaVariant(offset: Int) = PublishedMediaVariant(
    variant = getString(offset),
    directUrl = getString(offset + 1),
    mimeType = getString(offset + 2),
    width = getInt(offset + 3),
    height = getInt(offset + 4),
    expectedBytes = if (isNull(offset + 5)) null else getLong(offset + 5),
    contentSha256 = if (isNull(offset + 6)) null else getString(offset + 6),
)

private const val CONTENT_SCHEMA_VERSION = 4
private const val PACK_SCHEMA_VERSION = 2
private const val PACK_DATABASE = "catalogue.sqlite"
private const val PACK_REPORT = "catalogue-report.json"
private const val MANIFEST = "manifest.json"
private fun String.requireSha256(): String = also { require(matches(Regex("[a-f0-9]{64}"))) }
private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
private fun parseInstant(value: String): Instant = Instant.parse(value)
private fun RegionalContentPackManifest.matches(generation: PublishedContentGeneration) = generation == PublishedContentGeneration(generationId, generationSequence, contentSchemaVersion, sourceDigest, releaseStatus, generatedAt, minimumAppVersion)
