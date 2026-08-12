package com.wildlife.feasibility

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class ObservationStore(context: Context) : SQLiteOpenHelper(context, DATABASE, null, VERSION) {
    override fun onCreate(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE observations (
                user_id INTEGER NOT NULL,
                uuid TEXT NOT NULL,
                inat_id INTEGER NOT NULL,
                taxon_id INTEGER,
                taxon_rank TEXT,
                collection_taxon_id INTEGER,
                collection_taxon_rank TEXT,
                label TEXT NOT NULL,
                observed_at_ms INTEGER NOT NULL,
                latitude REAL,
                longitude REAL,
                obscured INTEGER NOT NULL,
                created_at_ms INTEGER,
                quality_grade TEXT NOT NULL,
                photo_url TEXT,
                confirmed INTEGER NOT NULL,
                PRIMARY KEY(user_id, uuid)
            )
            """.trimIndent(),
        )
        database.execSQL(
            """
            CREATE TABLE collection_summary (
                user_id INTEGER PRIMARY KEY,
                confirmed_count INTEGER NOT NULL,
                total_xp INTEGER NOT NULL,
                last_synced_at_ms INTEGER
            )
            """.trimIndent(),
        )
    }

    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            database.execSQL("ALTER TABLE observations ADD COLUMN taxon_rank TEXT")
            database.execSQL("ALTER TABLE observations ADD COLUMN collection_taxon_id INTEGER")
            database.execSQL("ALTER TABLE observations ADD COLUMN collection_taxon_rank TEXT")
        }
    }

    fun replaceSnapshot(userId: Long, result: BackendSyncResult) {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete("observations", "user_id = ?", arrayOf(userId.toString()))
            result.observations.forEach { observation ->
                writableDatabase.insertOrThrow("observations", null, observation.values(userId))
            }
            saveSummary(userId, result.summary, writableDatabase)
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun candidates(userId: Long): List<ObservationCandidate> {
        val cursor = readableDatabase.query(
            "observations",
            arrayOf("uuid", "observed_at_ms", "latitude", "longitude", "obscured", "created_at_ms"),
            "user_id = ?",
            arrayOf(userId.toString()),
            null,
            null,
            "inat_id ASC",
        )
        return cursor.use {
            buildList {
                while (it.moveToNext()) {
                    add(
                        ObservationCandidate(
                            uuid = it.getString(0),
                            observedAtMs = it.getLong(1),
                            latitude = if (it.isNull(2)) null else it.getDouble(2),
                            longitude = if (it.isNull(3)) null else it.getDouble(3),
                            obscured = it.getInt(4) == 1,
                            createdAtMs = if (it.isNull(5)) null else it.getLong(5),
                        ),
                    )
                }
            }
        }
    }

    fun observations(userId: Long): List<SyncedObservation> {
        val cursor = readableDatabase.query(
            "observations",
            arrayOf(
                "inat_id", "uuid", "taxon_id", "taxon_rank", "collection_taxon_id",
                "collection_taxon_rank", "label", "observed_at_ms", "latitude", "longitude",
                "obscured", "created_at_ms", "quality_grade", "photo_url", "confirmed",
            ),
            "user_id = ?",
            arrayOf(userId.toString()),
            null,
            null,
            "observed_at_ms DESC",
        )
        return cursor.use {
            buildList {
                while (it.moveToNext()) {
                    add(
                        SyncedObservation(
                            id = it.getLong(0),
                            uuid = it.getString(1),
                            taxonId = if (it.isNull(2)) null else it.getLong(2),
                            taxonRank = if (it.isNull(3)) null else it.getString(3),
                            collectionTaxonId = if (it.isNull(4)) null else it.getLong(4),
                            collectionTaxonRank = if (it.isNull(5)) null else it.getString(5),
                            label = it.getString(6),
                            observedAtMs = it.getLong(7),
                            latitude = if (it.isNull(8)) null else it.getDouble(8),
                            longitude = if (it.isNull(9)) null else it.getDouble(9),
                            obscured = it.getInt(10) == 1,
                            createdAtMs = if (it.isNull(11)) null else it.getLong(11),
                            qualityGrade = it.getString(12),
                            photoUrl = if (it.isNull(13)) null else it.getString(13),
                            confirmed = it.getInt(14) == 1,
                        ),
                    )
                }
            }
        }
    }

    fun updateConfirmation(userId: Long, uuid: String, summary: CollectionSummary) {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.update(
                "observations",
                ContentValues().apply { put("confirmed", 1) },
                "user_id = ? AND uuid = ?",
                arrayOf(userId.toString(), uuid),
            )
            saveSummary(userId, summary, writableDatabase)
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun summary(userId: Long): CollectionSummary {
        val cursor = readableDatabase.query(
            "collection_summary",
            arrayOf("confirmed_count", "total_xp", "last_synced_at_ms"),
            "user_id = ?",
            arrayOf(userId.toString()),
            null,
            null,
            null,
        )
        return cursor.use {
            if (!it.moveToFirst()) CollectionSummary() else CollectionSummary(
                confirmedCount = it.getInt(0),
                totalXp = it.getInt(1),
                lastSyncedAtMs = if (it.isNull(2)) null else it.getLong(2),
            )
        }
    }

    private fun saveSummary(userId: Long, summary: CollectionSummary, database: SQLiteDatabase) {
        database.insertWithOnConflict(
            "collection_summary",
            null,
            ContentValues().apply {
                put("user_id", userId)
                put("confirmed_count", summary.confirmedCount)
                put("total_xp", summary.totalXp)
                summary.lastSyncedAtMs?.let { put("last_synced_at_ms", it) }
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    private fun SyncedObservation.values(userId: Long) = ContentValues().apply {
        put("user_id", userId)
        put("uuid", uuid)
        put("inat_id", id)
        taxonId?.let { put("taxon_id", it) }
        taxonRank?.let { put("taxon_rank", it) }
        collectionTaxonId?.let { put("collection_taxon_id", it) }
        collectionTaxonRank?.let { put("collection_taxon_rank", it) }
        put("label", label)
        put("observed_at_ms", observedAtMs)
        latitude?.let { put("latitude", it) }
        longitude?.let { put("longitude", it) }
        put("obscured", if (obscured) 1 else 0)
        createdAtMs?.let { put("created_at_ms", it) }
        put("quality_grade", qualityGrade)
        photoUrl?.let { put("photo_url", it) }
        put("confirmed", if (confirmed) 1 else 0)
    }

    companion object {
        private const val DATABASE = "wildlife_observations.db"
        private const val VERSION = 2
    }
}
