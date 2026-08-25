package com.wildlife.feasibility

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.Closeable
import java.net.URI

data class ReferenceMediaRejection(
    val taxonId: Long,
    val sourceUrl: String,
    val provider: String,
    val catalogueGenerationId: String?,
    val reason: String,
    val rejectedAtMs: Long,
)

/** Durable user-owned decisions that prevent unsuitable reference media from resurfacing. */
class ReferenceMediaRejectionStore internal constructor(
    context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
) : SQLiteOpenHelper(context.applicationContext, DATABASE, null, VERSION), Closeable {

    override fun onCreate(database: SQLiteDatabase) {
        database.execSQL(CREATE_TABLE)
        database.execSQL(CREATE_TAXON_INDEX)
    }

    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        database.execSQL("DROP TABLE IF EXISTS reference_media_rejection")
        onCreate(database)
    }

    fun reject(
        taxonId: Long,
        sourceUrl: String?,
        catalogueGenerationId: String?,
        reason: String = REASON_USER_REQUESTED_REPLACEMENT,
    ): Boolean {
        if (taxonId <= 0) return false
        val normalized = sourceUrl?.normalizedReferenceSource() ?: return false
        return writableDatabase.insertWithOnConflict(
            "reference_media_rejection",
            null,
            ContentValues().apply {
                put("taxon_id", taxonId)
                put("source_key", normalized)
                put("source_url", sourceUrl.trim())
                put("provider", providerFor(sourceUrl))
                if (catalogueGenerationId == null) putNull("catalogue_generation_id")
                else put("catalogue_generation_id", catalogueGenerationId)
                put("reason", reason)
                put("rejected_at_ms", clock())
            },
            SQLiteDatabase.CONFLICT_IGNORE,
        ) != -1L
    }

    fun sourceUrls(taxonId: Long): Set<String> = readableDatabase.query(
        "reference_media_rejection",
        arrayOf("source_url"),
        "taxon_id = ?",
        arrayOf(taxonId.toString()),
        null,
        null,
        "rejected_at_ms ASC",
    ).use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) } }

    fun records(): List<ReferenceMediaRejection> = readableDatabase.query(
        "reference_media_rejection",
        READ_COLUMNS,
        null,
        null,
        null,
        null,
        "rejected_at_ms ASC, taxon_id ASC",
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.toRecord()) } }

    fun clear(): Boolean = writableDatabase.delete("reference_media_rejection", null, null) >= 0

    private fun Cursor.toRecord() = ReferenceMediaRejection(
        taxonId = getLong(0),
        sourceUrl = getString(1),
        provider = getString(2),
        catalogueGenerationId = if (isNull(3)) null else getString(3),
        reason = getString(4),
        rejectedAtMs = getLong(5),
    )

    companion object {
        const val DATABASE = "reference-media-rejections.db"
        const val REASON_USER_REQUESTED_REPLACEMENT = "user_requested_replacement"
        private const val VERSION = 1
        private val READ_COLUMNS = arrayOf(
            "taxon_id", "source_url", "provider", "catalogue_generation_id", "reason",
            "rejected_at_ms",
        )
        private const val CREATE_TABLE = """
            CREATE TABLE reference_media_rejection (
                taxon_id INTEGER NOT NULL,
                source_key TEXT NOT NULL,
                source_url TEXT NOT NULL,
                provider TEXT NOT NULL,
                catalogue_generation_id TEXT,
                reason TEXT NOT NULL,
                rejected_at_ms INTEGER NOT NULL,
                PRIMARY KEY (taxon_id, source_key)
            )
        """
        private const val CREATE_TAXON_INDEX = """
            CREATE INDEX reference_media_rejection_taxon
            ON reference_media_rejection(taxon_id, rejected_at_ms)
        """
    }
}

internal fun String.normalizedReferenceSource(): String? = runCatching {
    val uri = URI(trim())
    require(uri.scheme == "https" && !uri.host.isNullOrBlank())
    uri.normalize().toString().removeSuffix("/").lowercase()
}.getOrNull()

private fun providerFor(sourceUrl: String): String = runCatching {
    when (URI(sourceUrl.trim()).host?.lowercase()) {
        "commons.wikimedia.org" -> "wikimedia_commons"
        "www.inaturalist.org", "inaturalist.org" -> "inaturalist"
        else -> "other"
    }
}.getOrDefault("other")
