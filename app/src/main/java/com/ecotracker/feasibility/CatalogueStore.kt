package com.wildlife.feasibility

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class CatalogueStore(context: Context) : SQLiteOpenHelper(context, DATABASE, null, VERSION) {
    override fun onCreate(database: SQLiteDatabase) {
        database.execSQL(CREATE_CATALOGUE)
        database.execSQL(
            """
            CREATE TABLE catalogue_state (
                region_key TEXT PRIMARY KEY,
                place_id INTEGER NOT NULL,
                version TEXT NOT NULL,
                updated_at_ms INTEGER,
                provisional INTEGER NOT NULL,
                silhouette_pipeline_version INTEGER
            )
            """.trimIndent(),
        )
        database.execSQL(CREATE_TAXON_DETAILS)
        database.execSQL(CREATE_REFRESH_STATE)
    }

    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            database.addColumnIfMissing("catalogue_species", "taxon_group", "TEXT")
        }
        if (oldVersion < 3) {
            CATALOGUE_EXTRA_COLUMNS.forEach { (name, type) ->
                database.addColumnIfMissing("catalogue_species", name, type)
            }
            database.execSQL(CREATE_TAXON_DETAILS)
        }
        if (oldVersion < 4) {
            database.addColumnIfMissing("taxon_details", "photo_source_url", "TEXT")
            database.addColumnIfMissing("taxon_details", "photo_recovery_status", "TEXT")
        }
        if (oldVersion < 5) {
            database.addColumnIfMissing("taxon_details", "media_pipeline_version", "INTEGER")
        }
        if (oldVersion < 6) {
            database.addColumnIfMissing(
                "catalogue_state", "silhouette_pipeline_version", "INTEGER",
            )
        }
        if (oldVersion < 7) {
            database.addColumnIfMissing("catalogue_species", "photo_local_uri", "TEXT")
            database.addColumnIfMissing("catalogue_species", "silhouette_local_uri", "TEXT")
            database.addColumnIfMissing(
                "catalogue_species", "silhouette_resolver_version", "INTEGER",
            )
            database.addColumnIfMissing("taxon_details", "photo_local_uri", "TEXT")
            database.addColumnIfMissing("taxon_details", "silhouette_local_uri", "TEXT")
            database.addColumnIfMissing("taxon_details", "photo_pipeline_version", "INTEGER")
            database.addColumnIfMissing(
                "taxon_details", "silhouette_resolver_version", "INTEGER",
            )
        }
        if (oldVersion < 8) database.execSQL(CREATE_REFRESH_STATE)
    }

    fun recordRefreshStarted(regionKey: String, attemptedAtMs: Long) {
        writableDatabase.execSQL(
            "INSERT OR IGNORE INTO catalogue_refresh_state(region_key) VALUES(?)",
            arrayOf(regionKey),
        )
        writableDatabase.update(
            "catalogue_refresh_state",
            ContentValues().apply {
                put("last_attempt_ms", attemptedAtMs)
                put("in_progress", 1)
                putNull("last_error_code")
            },
            "region_key = ?",
            arrayOf(regionKey),
        )
    }

    fun recordRefreshFailed(regionKey: String, errorCode: String) {
        writableDatabase.execSQL(
            "INSERT OR IGNORE INTO catalogue_refresh_state(region_key) VALUES(?)",
            arrayOf(regionKey),
        )
        writableDatabase.update(
            "catalogue_refresh_state",
            ContentValues().apply {
                put("in_progress", 0)
                put("last_error_code", errorCode.take(80))
            },
            "region_key = ?",
            arrayOf(regionKey),
        )
    }

    fun replace(
        snapshot: CatalogueSnapshot,
        details: List<TaxonDetails> = emptyList(),
        preserveEnrichedMedia: Boolean = false,
        refreshSucceededAtMs: Long? = null,
    ) {
        val storedDetails = details.map { detail ->
            val existing = if (preserveEnrichedMedia) loadTaxonDetail(detail.taxonId) else null
            mergeTaxonDetail(detail, existing)
        }
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete(
                "catalogue_species", "region_key = ?", arrayOf(snapshot.regionKey),
            )
            snapshot.species.forEach { species ->
                writableDatabase.insertOrThrow(
                    "catalogue_species", null, species.values(snapshot.regionKey),
                )
            }
            writableDatabase.insertWithOnConflict(
                "catalogue_state",
                null,
                ContentValues().apply {
                    put("region_key", snapshot.regionKey)
                    put("place_id", snapshot.placeId)
                    put("version", snapshot.version)
                    snapshot.updatedAtMs?.let { put("updated_at_ms", it) }
                    put("provisional", if (snapshot.provisional) 1 else 0)
                    snapshot.silhouettePipelineVersion?.let {
                        put("silhouette_pipeline_version", it)
                    }
                },
                SQLiteDatabase.CONFLICT_REPLACE,
            )
            storedDetails.forEach { detail ->
                writableDatabase.insertWithOnConflict(
                    "taxon_details", null, detail.values(), SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
            refreshSucceededAtMs?.let { succeededAtMs ->
                writableDatabase.execSQL(
                    "INSERT OR IGNORE INTO catalogue_refresh_state(region_key) VALUES(?)",
                    arrayOf(snapshot.regionKey),
                )
                writableDatabase.update(
                    "catalogue_refresh_state",
                    ContentValues().apply {
                        put("last_success_ms", succeededAtMs)
                        put("in_progress", 0)
                        putNull("last_error_code")
                    },
                    "region_key = ?",
                    arrayOf(snapshot.regionKey),
                )
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun load(regionKey: String = CATALONIA): CatalogueSnapshot? {
        val stateCursor = readableDatabase.query(
            "catalogue_state",
            arrayOf(
                "place_id", "version", "updated_at_ms", "provisional",
                "silhouette_pipeline_version",
            ),
            "region_key = ?", arrayOf(regionKey), null, null, null,
        )
        val state = stateCursor.use {
            if (!it.moveToFirst()) return null
            CatalogueState(
                placeId = it.getLong(0),
                version = it.getString(1),
                updatedAtMs = it.nullableLong(2),
                provisional = it.getInt(3) == 1,
                silhouettePipelineVersion = it.nullableInt(4),
            )
        }
        val cursor = readableDatabase.query(
            "catalogue_species", CATALOGUE_READ_COLUMNS,
            "region_key = ?", arrayOf(regionKey), null, null, "position ASC",
        )
        val species = cursor.use {
            buildList {
                while (it.moveToNext()) add(it.toCatalogueSpecies())
            }
        }
        val refresh = loadRefreshState(regionKey)
        return CatalogueSnapshot(
            regionKey = regionKey,
            placeId = state.placeId,
            version = state.version,
            updatedAtMs = state.updatedAtMs,
            provisional = state.provisional,
            species = species,
            cached = true,
            silhouettePipelineVersion = state.silhouettePipelineVersion,
            lastRefreshAttemptMs = refresh?.lastAttemptMs,
            lastRefreshSuccessMs = refresh?.lastSuccessMs,
            lastRefreshErrorCode = refresh?.lastErrorCode,
            refreshInProgress = refresh?.inProgress == true,
        )
    }

    private fun loadRefreshState(regionKey: String): RefreshState? {
        val cursor = readableDatabase.query(
            "catalogue_refresh_state",
            arrayOf("last_attempt_ms", "last_success_ms", "last_error_code", "in_progress"),
            "region_key = ?", arrayOf(regionKey), null, null, null,
        )
        return cursor.use {
            if (!it.moveToFirst()) return null
            RefreshState(
                lastAttemptMs = it.nullableLong(0),
                lastSuccessMs = it.nullableLong(1),
                lastErrorCode = it.nullableString(2),
                inProgress = it.getInt(3) == 1,
            )
        }
    }

    fun updateFamilySilhouette(
        regionKey: String,
        familyName: String,
        asset: SilhouetteAsset,
        resolverVersion: Int,
    ) {
        val values = asset.values(resolverVersion)
        writableDatabase.beginTransaction()
        try {
            writableDatabase.update(
                "catalogue_species",
                values,
                "region_key = ? AND family_name = ? AND " +
                    "(silhouette_match_rank IS NULL OR silhouette_match_rank IN (?, ?) OR " +
                    "(silhouette_match_rank = ? AND " +
                    "(silhouette_resolver_version IS NULL OR silhouette_resolver_version < ?)))",
                arrayOf(regionKey, familyName, "group", "order", "family", resolverVersion.toString()),
            )
            writableDatabase.update(
                "taxon_details",
                values,
                "family_name = ? AND " +
                    "(silhouette_match_rank IS NULL OR silhouette_match_rank IN (?, ?) OR " +
                    "(silhouette_match_rank = ? AND " +
                    "(silhouette_resolver_version IS NULL OR silhouette_resolver_version < ?)))",
                arrayOf(familyName, "group", "order", "family", resolverVersion.toString()),
            )
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun markSilhouettePipeline(regionKey: String, version: Int) {
        writableDatabase.update(
            "catalogue_state",
            ContentValues().apply { put("silhouette_pipeline_version", version) },
            "region_key = ?",
            arrayOf(regionKey),
        )
    }

    fun upsertTaxonDetail(detail: TaxonDetails) {
        writableDatabase.insertWithOnConflict(
            "taxon_details", null, detail.values(), SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun upsertTaxonDetails(details: List<TaxonDetails>, preserveEnrichedMedia: Boolean) {
        if (details.isEmpty()) return
        writableDatabase.beginTransaction()
        try {
            details.forEach { detail ->
                val existing = if (preserveEnrichedMedia) {
                    loadTaxonDetail(detail.taxonId)
                } else {
                    null
                }
                val stored = mergeTaxonDetail(detail, existing)
                writableDatabase.insertWithOnConflict(
                    "taxon_details", null, stored.values(), SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    private fun mergeTaxonDetail(
        detail: TaxonDetails,
        existing: TaxonDetails?,
    ): TaxonDetails {
        if (existing == null) return detail
        val preservePhoto = existing.photoUrl != null || existing.photoPipelineVersion != null
        val preserveSilhouette = existing.silhouetteUrl != null ||
            existing.silhouetteResolverVersion != null
        return detail.copy(
            photoUrl = if (preservePhoto) existing.photoUrl else detail.photoUrl,
            photoAttribution = if (preservePhoto) existing.photoAttribution
            else detail.photoAttribution,
            photoLicenseCode = if (preservePhoto) existing.photoLicenseCode
            else detail.photoLicenseCode,
            photoSourceUrl = if (preservePhoto) existing.photoSourceUrl else detail.photoSourceUrl,
            photoRecoveryStatus = if (preservePhoto) existing.photoRecoveryStatus
            else detail.photoRecoveryStatus,
            photoLocalUri = if (preservePhoto) existing.photoLocalUri else null,
            photoPipelineVersion = if (preservePhoto) existing.photoPipelineVersion else null,
            silhouetteUrl = if (preserveSilhouette) existing.silhouetteUrl
            else detail.silhouetteUrl,
            silhouetteSourceUrl = if (preserveSilhouette) existing.silhouetteSourceUrl
            else detail.silhouetteSourceUrl,
            silhouetteAttribution = if (preserveSilhouette) existing.silhouetteAttribution
            else detail.silhouetteAttribution,
            silhouetteLicenseCode = if (preserveSilhouette) existing.silhouetteLicenseCode
            else detail.silhouetteLicenseCode,
            silhouetteLicenseUrl = if (preserveSilhouette) existing.silhouetteLicenseUrl
            else detail.silhouetteLicenseUrl,
            silhouetteTaxonName = if (preserveSilhouette) existing.silhouetteTaxonName
            else detail.silhouetteTaxonName,
            silhouetteMatchRank = if (preserveSilhouette) existing.silhouetteMatchRank
            else detail.silhouetteMatchRank,
            silhouetteLocalUri = if (preserveSilhouette) existing.silhouetteLocalUri else null,
            silhouetteResolverVersion = if (preserveSilhouette) {
                existing.silhouetteResolverVersion
            } else null,
            updatedAtMs = existing.updatedAtMs,
            mediaPipelineVersion = existing.mediaPipelineVersion,
        )
    }

    fun loadTaxonDetail(taxonId: Long): TaxonDetails? {
        val cursor = readableDatabase.query(
            "taxon_details", TAXON_READ_COLUMNS, "taxon_id = ?", arrayOf(taxonId.toString()),
            null, null, null,
        )
        return cursor.use { if (it.moveToFirst()) it.toTaxonDetails() else null }
    }

    fun loadTaxonDetails(): Map<Long, TaxonDetails> {
        val cursor = readableDatabase.query(
            "taxon_details", TAXON_READ_COLUMNS, null, null, null, null, null,
        )
        return cursor.use {
            buildMap {
                while (it.moveToNext()) {
                    val detail = it.toTaxonDetails()
                    put(detail.taxonId, detail)
                }
            }
        }
    }

    private fun CatalogueSpecies.values(regionKey: String) = ContentValues().apply {
        put("region_key", regionKey)
        put("taxon_id", taxonId)
        put("scientific_name", scientificName)
        putNullable("common_name", commonName)
        putNullable("taxon_group", taxonGroup)
        put("observation_count", observationCount)
        put("position", position)
        putNullable("photo_url", photoUrl)
        putNullable("photo_attribution", photoAttribution)
        putNullable("photo_license_code", photoLicenseCode)
        putNullable("family_name", familyName)
        putNullable("wikipedia_summary", wikipediaSummary)
        putNullable("wikipedia_url", wikipediaUrl)
        putNullable("conservation_status", conservationStatus)
        putNullable("conservation_authority", conservationAuthority)
        putNullable("conservation_url", conservationUrl)
        putNullable("silhouette_url", silhouetteUrl)
        putNullable("silhouette_source_url", silhouetteSourceUrl)
        putNullable("silhouette_attribution", silhouetteAttribution)
        putNullable("silhouette_license_code", silhouetteLicenseCode)
        putNullable("silhouette_license_url", silhouetteLicenseUrl)
        putNullable("silhouette_taxon_name", silhouetteTaxonName)
        putNullable("silhouette_match_rank", silhouetteMatchRank)
        putNullable("photo_local_uri", photoLocalUri)
        putNullable("silhouette_local_uri", silhouetteLocalUri)
        silhouetteResolverVersion?.let { put("silhouette_resolver_version", it) }
    }

    private fun TaxonDetails.values() = ContentValues().apply {
        put("taxon_id", taxonId)
        put("scientific_name", scientificName)
        putNullable("common_name", commonName)
        putNullable("taxon_group", taxonGroup)
        putNullable("family_name", familyName)
        putNullable("wikipedia_summary", wikipediaSummary)
        putNullable("wikipedia_url", wikipediaUrl)
        putNullable("conservation_status", conservationStatus)
        putNullable("conservation_authority", conservationAuthority)
        putNullable("conservation_url", conservationUrl)
        putNullable("photo_url", photoUrl)
        putNullable("photo_attribution", photoAttribution)
        putNullable("photo_license_code", photoLicenseCode)
        putNullable("silhouette_url", silhouetteUrl)
        putNullable("silhouette_source_url", silhouetteSourceUrl)
        putNullable("silhouette_attribution", silhouetteAttribution)
        putNullable("silhouette_license_code", silhouetteLicenseCode)
        putNullable("silhouette_license_url", silhouetteLicenseUrl)
        putNullable("silhouette_taxon_name", silhouetteTaxonName)
        putNullable("silhouette_match_rank", silhouetteMatchRank)
        putNullable("photo_source_url", photoSourceUrl)
        putNullable("photo_recovery_status", photoRecoveryStatus)
        mediaPipelineVersion?.let { put("media_pipeline_version", it) }
        putNullable("photo_local_uri", photoLocalUri)
        putNullable("silhouette_local_uri", silhouetteLocalUri)
        photoPipelineVersion?.let { put("photo_pipeline_version", it) }
        silhouetteResolverVersion?.let { put("silhouette_resolver_version", it) }
        updatedAtMs?.let { put("updated_at_ms", it) }
    }

    private fun Cursor.toCatalogueSpecies() = CatalogueSpecies(
        taxonId = getLong(0), scientificName = getString(1), commonName = nullableString(2),
        taxonGroup = nullableString(3), observationCount = getInt(4), position = getInt(5),
        photoUrl = nullableString(6), photoAttribution = nullableString(7),
        photoLicenseCode = nullableString(8), familyName = nullableString(9),
        wikipediaSummary = nullableString(10), wikipediaUrl = nullableString(11),
        conservationStatus = nullableString(12), conservationAuthority = nullableString(13),
        conservationUrl = nullableString(14), silhouetteUrl = nullableString(15),
        silhouetteSourceUrl = nullableString(16), silhouetteAttribution = nullableString(17),
        silhouetteLicenseCode = nullableString(18), silhouetteLicenseUrl = nullableString(19),
        silhouetteTaxonName = nullableString(20), silhouetteMatchRank = nullableString(21),
        photoLocalUri = nullableString(22), silhouetteLocalUri = nullableString(23),
        silhouetteResolverVersion = nullableInt(24),
    )

    private fun Cursor.toTaxonDetails() = TaxonDetails(
        taxonId = getLong(0), scientificName = getString(1), commonName = nullableString(2),
        taxonGroup = nullableString(3), familyName = nullableString(4),
        wikipediaSummary = nullableString(5), wikipediaUrl = nullableString(6),
        conservationStatus = nullableString(7), conservationAuthority = nullableString(8),
        conservationUrl = nullableString(9), photoUrl = nullableString(10),
        photoAttribution = nullableString(11), photoLicenseCode = nullableString(12),
        silhouetteUrl = nullableString(13), silhouetteSourceUrl = nullableString(14),
        silhouetteAttribution = nullableString(15), silhouetteLicenseCode = nullableString(16),
        silhouetteLicenseUrl = nullableString(17), silhouetteTaxonName = nullableString(18),
        silhouetteMatchRank = nullableString(19), updatedAtMs = nullableLong(20),
        photoSourceUrl = nullableString(21), photoRecoveryStatus = nullableString(22),
        mediaPipelineVersion = nullableInt(23),
        photoLocalUri = nullableString(24), silhouetteLocalUri = nullableString(25),
        photoPipelineVersion = nullableInt(26), silhouetteResolverVersion = nullableInt(27),
    )

    private fun ContentValues.putNullable(key: String, value: String?) {
        if (value == null) putNull(key) else put(key, value)
    }

    private fun SilhouetteAsset.values(resolverVersion: Int) = ContentValues().apply {
        put("silhouette_url", url)
        put("silhouette_source_url", sourceUrl)
        putNullable("silhouette_attribution", attribution)
        put("silhouette_license_code", licenceCode)
        put("silhouette_license_url", licenceUrl)
        put("silhouette_taxon_name", taxonName)
        put("silhouette_match_rank", matchRank)
        putNullable("silhouette_local_uri", localUri)
        put("silhouette_resolver_version", resolverVersion)
    }

    private fun SQLiteDatabase.addColumnIfMissing(table: String, name: String, type: String) {
        val exists = rawQuery("PRAGMA table_info($table)", null).use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            generateSequence { if (cursor.moveToNext()) cursor.getString(nameIndex) else null }
                .any { it == name }
        }
        if (!exists) execSQL("ALTER TABLE $table ADD COLUMN $name $type")
    }

    private fun Cursor.nullableString(index: Int) = if (isNull(index)) null else getString(index)
    private fun Cursor.nullableLong(index: Int) = if (isNull(index)) null else getLong(index)
    private fun Cursor.nullableInt(index: Int) = if (isNull(index)) null else getInt(index)

    private data class CatalogueState(
        val placeId: Long,
        val version: String,
        val updatedAtMs: Long?,
        val provisional: Boolean,
        val silhouettePipelineVersion: Int?,
    )

    private data class RefreshState(
        val lastAttemptMs: Long?,
        val lastSuccessMs: Long?,
        val lastErrorCode: String?,
        val inProgress: Boolean,
    )

    companion object {
        const val CATALONIA = "catalonia"
        private const val DATABASE = "wildlife_catalogue.db"
        private const val VERSION = 8
        private val CATALOGUE_EXTRA_COLUMNS = listOf(
            "family_name" to "TEXT", "wikipedia_summary" to "TEXT",
            "wikipedia_url" to "TEXT", "conservation_status" to "TEXT",
            "conservation_authority" to "TEXT", "conservation_url" to "TEXT",
            "silhouette_url" to "TEXT", "silhouette_source_url" to "TEXT",
            "silhouette_attribution" to "TEXT", "silhouette_license_code" to "TEXT",
            "silhouette_license_url" to "TEXT", "silhouette_taxon_name" to "TEXT",
            "silhouette_match_rank" to "TEXT",
        )
        private val CATALOGUE_READ_COLUMNS = arrayOf(
            "taxon_id", "scientific_name", "common_name", "taxon_group", "observation_count",
            "position", "photo_url", "photo_attribution", "photo_license_code", "family_name",
            "wikipedia_summary", "wikipedia_url", "conservation_status",
            "conservation_authority", "conservation_url", "silhouette_url",
            "silhouette_source_url", "silhouette_attribution", "silhouette_license_code",
            "silhouette_license_url", "silhouette_taxon_name", "silhouette_match_rank",
            "photo_local_uri", "silhouette_local_uri", "silhouette_resolver_version",
        )
        private val TAXON_READ_COLUMNS = arrayOf(
            "taxon_id", "scientific_name", "common_name", "taxon_group", "family_name",
            "wikipedia_summary", "wikipedia_url", "conservation_status",
            "conservation_authority", "conservation_url", "photo_url", "photo_attribution",
            "photo_license_code", "silhouette_url", "silhouette_source_url",
            "silhouette_attribution", "silhouette_license_code", "silhouette_license_url",
            "silhouette_taxon_name", "silhouette_match_rank", "updated_at_ms",
            "photo_source_url", "photo_recovery_status",
            "media_pipeline_version",
            "photo_local_uri", "silhouette_local_uri", "photo_pipeline_version",
            "silhouette_resolver_version",
        )
        private const val CREATE_CATALOGUE = """
            CREATE TABLE catalogue_species (
                region_key TEXT NOT NULL, taxon_id INTEGER NOT NULL,
                scientific_name TEXT NOT NULL, common_name TEXT, taxon_group TEXT,
                observation_count INTEGER NOT NULL, position INTEGER NOT NULL,
                photo_url TEXT, photo_attribution TEXT, photo_license_code TEXT,
                family_name TEXT, wikipedia_summary TEXT, wikipedia_url TEXT,
                conservation_status TEXT, conservation_authority TEXT, conservation_url TEXT,
                silhouette_url TEXT, silhouette_source_url TEXT, silhouette_attribution TEXT,
                silhouette_license_code TEXT, silhouette_license_url TEXT,
                silhouette_taxon_name TEXT, silhouette_match_rank TEXT,
                photo_local_uri TEXT, silhouette_local_uri TEXT,
                silhouette_resolver_version INTEGER,
                PRIMARY KEY(region_key, taxon_id)
            )
        """
        private const val CREATE_TAXON_DETAILS = """
            CREATE TABLE IF NOT EXISTS taxon_details (
                taxon_id INTEGER PRIMARY KEY, scientific_name TEXT NOT NULL, common_name TEXT,
                taxon_group TEXT, family_name TEXT, wikipedia_summary TEXT, wikipedia_url TEXT,
                conservation_status TEXT, conservation_authority TEXT, conservation_url TEXT,
                photo_url TEXT, photo_attribution TEXT, photo_license_code TEXT,
                silhouette_url TEXT, silhouette_source_url TEXT, silhouette_attribution TEXT,
                silhouette_license_code TEXT, silhouette_license_url TEXT,
                silhouette_taxon_name TEXT, silhouette_match_rank TEXT, updated_at_ms INTEGER,
                photo_source_url TEXT, photo_recovery_status TEXT,
                media_pipeline_version INTEGER, photo_local_uri TEXT,
                silhouette_local_uri TEXT, photo_pipeline_version INTEGER,
                silhouette_resolver_version INTEGER
            )
        """
        private const val CREATE_REFRESH_STATE = """
            CREATE TABLE IF NOT EXISTS catalogue_refresh_state (
                region_key TEXT PRIMARY KEY,
                last_attempt_ms INTEGER,
                last_success_ms INTEGER,
                last_error_code TEXT,
                in_progress INTEGER NOT NULL DEFAULT 0
            )
        """
    }
}
