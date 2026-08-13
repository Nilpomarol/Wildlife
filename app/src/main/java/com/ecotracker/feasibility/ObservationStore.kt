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
        database.execSQL(CREATE_XP_EVENTS)
    }

    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            database.execSQL("ALTER TABLE observations ADD COLUMN taxon_rank TEXT")
            database.execSQL("ALTER TABLE observations ADD COLUMN collection_taxon_id INTEGER")
            database.execSQL("ALTER TABLE observations ADD COLUMN collection_taxon_rank TEXT")
        }
        if (oldVersion < 3) {
            database.execSQL(CREATE_XP_EVENTS)
            database.execSQL(
                """
                INSERT OR IGNORE INTO xp_events(user_id, event_key, observation_uuid, points, created_at_ms)
                SELECT user_id, 'legacy-total', '', total_xp,
                    COALESCE(last_synced_at_ms, CAST(strftime('%s','now') AS INTEGER) * 1000)
                FROM collection_summary WHERE total_xp > 0
                """.trimIndent(),
            )
        }
    }

    fun replaceSnapshot(
        userId: Long,
        observations: List<SyncedObservation>,
        syncedAtMs: Long = System.currentTimeMillis(),
    ): ObservationSyncResult {
        val confirmedUuids = confirmedUuids(userId)
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete("observations", "user_id = ?", arrayOf(userId.toString()))
            observations.forEach { observation ->
                val localObservation = observation.copy(
                    confirmed = observation.confirmed || observation.uuid in confirmedUuids,
                )
                writableDatabase.insertOrThrow(
                    "observations", null, localObservation.values(userId),
                )
            }
            saveSummary(
                userId,
                calculatedSummary(userId, writableDatabase, syncedAtMs),
                writableDatabase,
            )
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
        return ObservationSyncResult(
            observations = observations(userId),
            summary = summary(userId),
            cached = false,
        )
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

    fun confirmObservation(userId: Long, uuid: String): ObservationConfirmationResult {
        writableDatabase.beginTransaction()
        try {
            val observation = writableDatabase.query(
                "observations",
                arrayOf("taxon_id", "collection_taxon_id", "confirmed"),
                "user_id = ? AND uuid = ?",
                arrayOf(userId.toString(), uuid),
                null,
                null,
                null,
            ).use { cursor ->
                if (!cursor.moveToFirst()) {
                    error("The observation is not present in this linked user's local cache.")
                }
                ConfirmationObservation(
                    taxonId = if (cursor.isNull(0)) null else cursor.getLong(0),
                    collectionTaxonId = if (cursor.isNull(1)) null else cursor.getLong(1),
                    confirmed = cursor.getInt(2) == 1,
                )
            }
            if (observation.confirmed) {
                writableDatabase.setTransactionSuccessful()
                return ObservationConfirmationResult(0, summary(userId))
            }

            val now = System.currentTimeMillis()
            var awarded = award(
                writableDatabase, userId, "observation:$uuid", uuid, 10, now,
            )
            val collectionTaxonId = observation.collectionTaxonId ?: observation.taxonId
            if (collectionTaxonId != null && !hasConfirmedTaxon(userId, collectionTaxonId, uuid)) {
                awarded += award(
                    writableDatabase,
                    userId,
                    "first-species:$collectionTaxonId",
                    uuid,
                    500,
                    now,
                )
            }
            writableDatabase.update(
                "observations",
                ContentValues().apply { put("confirmed", 1) },
                "user_id = ? AND uuid = ?",
                arrayOf(userId.toString(), uuid),
            )
            val updatedSummary = calculatedSummary(
                userId,
                writableDatabase,
                lastSyncedAt(userId, writableDatabase),
            )
            saveSummary(userId, updatedSummary, writableDatabase)
            writableDatabase.setTransactionSuccessful()
            return ObservationConfirmationResult(awarded, updatedSummary)
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

    private fun confirmedUuids(userId: Long): Set<String> = readableDatabase.query(
        "observations",
        arrayOf("uuid"),
        "user_id = ? AND confirmed = 1",
        arrayOf(userId.toString()),
        null,
        null,
        null,
    ).use { cursor ->
        buildSet {
            while (cursor.moveToNext()) add(cursor.getString(0))
        }
    }

    private fun hasConfirmedTaxon(userId: Long, taxonId: Long, excludingUuid: String): Boolean =
        readableDatabase.rawQuery(
            """
            SELECT 1 FROM observations
            WHERE user_id = ? AND confirmed = 1 AND uuid != ?
              AND COALESCE(collection_taxon_id, taxon_id) = ? LIMIT 1
            """.trimIndent(),
            arrayOf(userId.toString(), excludingUuid, taxonId.toString()),
        ).use { it.moveToFirst() }

    private fun award(
        database: SQLiteDatabase,
        userId: Long,
        eventKey: String,
        uuid: String,
        points: Int,
        createdAtMs: Long,
    ): Int {
        val inserted = database.insertWithOnConflict(
            "xp_events",
            null,
            ContentValues().apply {
                put("user_id", userId)
                put("event_key", eventKey)
                put("observation_uuid", uuid)
                put("points", points)
                put("created_at_ms", createdAtMs)
            },
            SQLiteDatabase.CONFLICT_IGNORE,
        )
        return if (inserted == -1L) 0 else points
    }

    private fun calculatedSummary(
        userId: Long,
        database: SQLiteDatabase,
        lastSyncedAtMs: Long?,
    ): CollectionSummary {
        val confirmed = database.rawQuery(
            "SELECT COUNT(*) FROM observations WHERE user_id = ? AND confirmed = 1",
            arrayOf(userId.toString()),
        ).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }
        val xp = database.rawQuery(
            "SELECT COALESCE(SUM(points), 0) FROM xp_events WHERE user_id = ?",
            arrayOf(userId.toString()),
        ).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }
        return CollectionSummary(confirmed, xp, lastSyncedAtMs)
    }

    private fun lastSyncedAt(userId: Long, database: SQLiteDatabase): Long? = database.query(
        "collection_summary",
        arrayOf("last_synced_at_ms"),
        "user_id = ?",
        arrayOf(userId.toString()),
        null,
        null,
        null,
    ).use { cursor ->
        if (!cursor.moveToFirst() || cursor.isNull(0)) null else cursor.getLong(0)
    }

    private data class ConfirmationObservation(
        val taxonId: Long?,
        val collectionTaxonId: Long?,
        val confirmed: Boolean,
    )

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
        private const val VERSION = 3
        private const val CREATE_XP_EVENTS = """
            CREATE TABLE IF NOT EXISTS xp_events (
                user_id INTEGER NOT NULL,
                event_key TEXT NOT NULL,
                observation_uuid TEXT NOT NULL,
                points INTEGER NOT NULL,
                created_at_ms INTEGER NOT NULL,
                PRIMARY KEY(user_id, event_key)
            )
        """
    }
}
