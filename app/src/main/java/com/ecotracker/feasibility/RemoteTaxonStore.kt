package com.wildlife.feasibility

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.Closeable

/**
 * Stale-while-revalidate cache for complete off-catalogue records and media-only repairs that are
 * merged beneath immutable published content.
 */
interface RemoteTaxonRepository : Closeable {
    fun cached(taxonId: Long): TaxonDetails?
    fun store(detail: TaxonDetails)
    fun clear()
}

class RemoteTaxonStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE, null, VERSION),
    RemoteTaxonRepository {

    override fun onCreate(database: SQLiteDatabase) = database.execSQL(CREATE_TABLE)

    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        database.execSQL("DROP TABLE IF EXISTS remote_taxon_details")
        onCreate(database)
    }

    override fun cached(taxonId: Long): TaxonDetails? = readableDatabase.query(
        "remote_taxon_details",
        READ_COLUMNS,
        "taxon_id = ?",
        arrayOf(taxonId.toString()),
        null,
        null,
        null,
    ).use { cursor -> if (cursor.moveToFirst()) cursor.toTaxonDetails() else null }

    override fun store(detail: TaxonDetails) {
        writableDatabase.insertWithOnConflict(
            "remote_taxon_details",
            null,
            detail.values(),
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    override fun clear() {
        writableDatabase.delete("remote_taxon_details", null, null)
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
        updatedAtMs?.let { put("updated_at_ms", it) }
        putNullable("photo_source_url", photoSourceUrl)
        putNullable("photo_recovery_status", photoRecoveryStatus)
        mediaPipelineVersion?.let { put("media_pipeline_version", it) }
        putNullable("photo_local_uri", photoLocalUri)
        putNullable("silhouette_local_uri", silhouetteLocalUri)
        photoPipelineVersion?.let { put("photo_pipeline_version", it) }
        silhouetteResolverVersion?.let { put("silhouette_resolver_version", it) }
    }

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
        mediaPipelineVersion = nullableInt(23), photoLocalUri = nullableString(24),
        silhouetteLocalUri = nullableString(25), photoPipelineVersion = nullableInt(26),
        silhouetteResolverVersion = nullableInt(27),
    )

    private fun ContentValues.putNullable(key: String, value: String?) {
        if (value == null) putNull(key) else put(key, value)
    }

    private fun Cursor.nullableString(index: Int) = if (isNull(index)) null else getString(index)
    private fun Cursor.nullableLong(index: Int) = if (isNull(index)) null else getLong(index)
    private fun Cursor.nullableInt(index: Int) = if (isNull(index)) null else getInt(index)

    companion object {
        const val DATABASE = "remote-taxon-cache.db"
        private const val VERSION = 1
        private val READ_COLUMNS = arrayOf(
            "taxon_id", "scientific_name", "common_name", "taxon_group", "family_name",
            "wikipedia_summary", "wikipedia_url", "conservation_status",
            "conservation_authority", "conservation_url", "photo_url", "photo_attribution",
            "photo_license_code", "silhouette_url", "silhouette_source_url",
            "silhouette_attribution", "silhouette_license_code", "silhouette_license_url",
            "silhouette_taxon_name", "silhouette_match_rank", "updated_at_ms",
            "photo_source_url", "photo_recovery_status", "media_pipeline_version",
            "photo_local_uri", "silhouette_local_uri", "photo_pipeline_version",
            "silhouette_resolver_version",
        )
        private const val CREATE_TABLE = """
            CREATE TABLE remote_taxon_details (
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
    }
}
