package com.wildlife.feasibility

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters

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
                positional_accuracy_m INTEGER,
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
        database.execSQL(CREATE_QUALITY_EVENTS)
        database.execSQL(CREATE_MAP_VISIBILITY_OVERRIDES)
        database.execSQL(CREATE_OBSERVATION_REGIONS)
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
        if (oldVersion == 3) {
            database.execSQL("ALTER TABLE xp_events ADD COLUMN event_type TEXT NOT NULL DEFAULT 'legacy'")
            database.execSQL("ALTER TABLE xp_events ADD COLUMN subject_taxon_id INTEGER")
            database.execSQL("ALTER TABLE xp_events ADD COLUMN label TEXT")
            database.execSQL(
                """
                UPDATE xp_events SET event_type = CASE
                    WHEN event_key LIKE 'observation:%' THEN 'confirmed_observation'
                    WHEN event_key LIKE 'first-species:%' THEN 'first_species'
                    ELSE 'legacy'
                END
                """.trimIndent(),
            )
            database.execSQL(
                """
                UPDATE xp_events SET
                    subject_taxon_id = (
                        SELECT COALESCE(o.collection_taxon_id, o.taxon_id)
                        FROM observations o
                        WHERE o.user_id = xp_events.user_id
                          AND o.uuid = xp_events.observation_uuid
                    ),
                    label = (
                        SELECT o.label FROM observations o
                        WHERE o.user_id = xp_events.user_id
                          AND o.uuid = xp_events.observation_uuid
                    )
                """.trimIndent(),
            )
        }
        if (oldVersion < 5) {
            database.execSQL(CREATE_QUALITY_EVENTS)
        }
        if (oldVersion < 6) {
            database.execSQL(CREATE_MAP_VISIBILITY_OVERRIDES)
        }
        if (oldVersion < 7) database.execSQL(CREATE_OBSERVATION_REGIONS)
        if (oldVersion < 8) {
            // The Legend tier collapsed into the Regional Icon standing. Rewriting the rows
            // rather than dropping them keeps earned XP and its history intact, and keeps the
            // idempotency key aligned with the one the award path now writes, so a species
            // already rewarded as a Legend is not rewarded a second time as an Icon.
            database.execSQL(
                """
                UPDATE OR IGNORE xp_events
                SET event_key = 'regional_icon_discovery:' || SUBSTR(event_key, LENGTH('regional_legend:') + 1),
                    event_type = 'regional_icon_discovery'
                WHERE event_key LIKE 'regional_legend:%'
                """.trimIndent(),
            )
        }
        if (oldVersion < 9) {
            // Nullable and never backfilled: the column means "iNaturalist stated this
            // accuracy", and a row synced before v9 stated nothing. The next full snapshot
            // replace fills it for records that carry one.
            database.execSQL("ALTER TABLE observations ADD COLUMN positional_accuracy_m INTEGER")
        }
    }

    fun replaceSnapshot(
        userId: Long,
        observations: List<SyncedObservation>,
        regionalAssignments: List<ObservationRegion> = emptyList(),
        syncedAtMs: Long = System.currentTimeMillis(),
    ): ObservationSyncResult {
        val confirmedUuids = confirmedUuids(userId)
        val previousQuality = qualitySnapshot(userId, writableDatabase)
        val detectedTransitions = mutableListOf<ObservationQualityTransition>()
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
                ObservationLifecyclePolicy.qualityTransition(
                    previousQualityGrade = previousQuality[observation.uuid],
                    observation = observation,
                    detectedAtMs = syncedAtMs,
                )?.let { transition ->
                    recordQualityTransition(
                        database = writableDatabase,
                        userId = userId,
                        transition = transition,
                    )?.let { recorded ->
                        detectedTransitions += recorded
                        if (
                            recorded.toQualityGrade == "research" &&
                            ProgressionRules.RESEARCH_GRADE_ENABLED
                        ) {
                            award(
                                database = writableDatabase,
                                userId = userId,
                                eventKey = "research_grade:${observation.uuid}",
                                eventType = XpEventType.RESEARCH_GRADE,
                                uuid = observation.uuid,
                                subjectTaxonId = observation.collectionTaxonId ?: observation.taxonId,
                                label = observation.label,
                                points = ProgressionRules.RESEARCH_GRADE_XP,
                                createdAtMs = syncedAtMs,
                            )
                        }
                    }
                }
            }
            regionalAssignments.forEach { assignment ->
                writableDatabase.insertWithOnConflict(
                    "observation_regions", null, assignment.values(userId), SQLiteDatabase.CONFLICT_IGNORE,
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
            qualityTransitions = detectedTransitions,
        )
    }

    /** Replaces only one user's portable Wildlife state; shared catalogues and media are untouched. */
    fun restoreUserBackup(
        userId: Long,
        observations: List<SyncedObservation>,
        regionalAssignments: List<ObservationRegion>,
        hiddenObservationUuids: Set<String>,
        xpEvents: List<XpEventRecord>,
        qualityTransitions: List<ObservationQualityTransition>,
    ) {
        writableDatabase.beginTransaction()
        try {
            listOf("map_visibility_overrides", "observation_quality_events", "xp_events", "observations", "observation_regions", "collection_summary")
                .forEach { table -> writableDatabase.delete(table, "user_id = ?", arrayOf(userId.toString())) }
            observations.forEach { writableDatabase.insertOrThrow("observations", null, it.values(userId)) }
            regionalAssignments.forEach { writableDatabase.insertOrThrow("observation_regions", null, it.values(userId)) }
            hiddenObservationUuids.forEach { uuid ->
                writableDatabase.insertOrThrow("map_visibility_overrides", null, ContentValues().apply {
                    put("user_id", userId); put("observation_uuid", uuid); put("visible", 0)
                })
            }
            xpEvents.forEach { event ->
                writableDatabase.insertOrThrow("xp_events", null, ContentValues().apply {
                    put("user_id", userId); put("event_key", event.eventKey); put("event_type", event.type.key)
                    put("observation_uuid", event.observationUuid ?: ""); event.subjectTaxonId?.let { put("subject_taxon_id", it) }
                    event.label?.let { put("label", it) }; put("points", event.points); put("created_at_ms", event.createdAtMs)
                })
            }
            qualityTransitions.forEach { transition ->
                writableDatabase.insertOrThrow("observation_quality_events", null, ContentValues().apply {
                    put("user_id", userId); put("event_key", "restored:${transition.observationUuid}:${transition.detectedAtMs}")
                    put("observation_uuid", transition.observationUuid); put("label", transition.label)
                    put("from_quality_grade", transition.fromQualityGrade); put("to_quality_grade", transition.toQualityGrade)
                    put("detected_at_ms", transition.detectedAtMs)
                })
            }
            saveSummary(userId, calculatedSummary(userId, writableDatabase, System.currentTimeMillis()), writableDatabase)
            writableDatabase.setTransactionSuccessful()
        } finally { writableDatabase.endTransaction() }
    }

    /**
     * Every stored record, as something the matcher can both score and show.
     *
     * Identity travels with the candidate because a proposal is presented for comparison,
     * not just for arithmetic: the photograph and the name are how a person recognises
     * their own sighting, and both are already in this table from the last sync.
     */
    fun candidates(userId: Long): List<ObservationCandidate> {
        val cursor = readableDatabase.query(
            "observations",
            arrayOf(
                "uuid", "observed_at_ms", "latitude", "longitude", "obscured", "created_at_ms",
                "label", "collection_taxon_id", "taxon_id", "photo_url", "positional_accuracy_m",
            ),
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
                            label = if (it.isNull(6)) null else it.getString(6),
                            taxonId = when {
                                !it.isNull(7) -> it.getLong(7)
                                !it.isNull(8) -> it.getLong(8)
                                else -> null
                            },
                            photoUrl = if (it.isNull(9)) null else it.getString(9),
                            positionalAccuracyM = if (it.isNull(10)) null else it.getInt(10),
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
                "positional_accuracy_m",
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
                            positionalAccuracyM = if (it.isNull(15)) null else it.getInt(15),
                        ),
                    )
                }
            }
        }
    }

    fun mapHiddenObservationUuids(userId: Long): Set<String> = readableDatabase.query(
        "map_visibility_overrides",
        arrayOf("observation_uuid"),
        "user_id = ? AND visible = 0",
        arrayOf(userId.toString()),
        null,
        null,
        null,
    ).use { cursor ->
        buildSet {
            while (cursor.moveToNext()) add(cursor.getString(0))
        }
    }

    fun observationRegion(userId: Long, observationUuid: String): ObservationRegion? = readableDatabase.query(
        "observation_regions",
        arrayOf("region_key", "boundary_version", "assignment"),
        "user_id = ? AND observation_uuid = ?",
        arrayOf(userId.toString(), observationUuid),
        null, null, null,
    ).use { cursor ->
        if (!cursor.moveToFirst()) return null
        ObservationRegion(
            observationUuid = observationUuid,
            regionKey = if (cursor.isNull(0)) null else cursor.getString(0),
            boundaryVersion = cursor.getString(1),
            assignment = ObservationRegionAssignment.valueOf(cursor.getString(2)),
        )
    }

    fun setObservationMapVisible(userId: Long, observationUuid: String, visible: Boolean) {
        if (visible) {
            writableDatabase.delete(
                "map_visibility_overrides",
                "user_id = ? AND observation_uuid = ?",
                arrayOf(userId.toString(), observationUuid),
            )
        } else {
            writableDatabase.insertWithOnConflict(
                "map_visibility_overrides",
                null,
                ContentValues().apply {
                    put("user_id", userId)
                    put("observation_uuid", observationUuid)
                    put("visible", 0)
                },
                SQLiteDatabase.CONFLICT_REPLACE,
            )
        }
    }

    fun clearAllLocalData() {
        writableDatabase.beginTransaction()
        try {
            listOf(
                "map_visibility_overrides",
                "observation_quality_events",
                "xp_events",
                "observations",
                "observation_regions",
                "collection_summary",
            ).forEach { table -> writableDatabase.delete(table, null, null) }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun confirmObservation(
        userId: Long,
        uuid: String,
        regionalReward: RegionalRewardContext? = null,
    ): ObservationConfirmationResult {
        writableDatabase.beginTransaction()
        try {
            val observation = writableDatabase.query(
                "observations",
                arrayOf(
                    "taxon_id", "taxon_rank", "collection_taxon_id",
                    "collection_taxon_rank", "label", "confirmed",
                ),
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
                    taxonRank = if (cursor.isNull(1)) null else cursor.getString(1),
                    collectionTaxonId = if (cursor.isNull(2)) null else cursor.getLong(2),
                    collectionTaxonRank = if (cursor.isNull(3)) null else cursor.getString(3),
                    label = cursor.getString(4),
                    confirmed = cursor.getInt(5) == 1,
                )
            }
            if (observation.confirmed) {
                writableDatabase.setTransactionSuccessful()
                return ObservationConfirmationResult(0, summary(userId))
            }

            val now = System.currentTimeMillis()
            val collectionTaxonId = observation.collectionTaxonId ?: observation.taxonId
            val collectionRank = observation.collectionTaxonRank ?: observation.taxonRank
            val previousThisWeek = if (collectionTaxonId != null && collectionRank == "species") {
                rewardedObservationsThisWeek(userId, collectionTaxonId, now, writableDatabase)
            } else {
                0
            }
            val observationXp = ProgressionRules.confirmedObservationXp(previousThisWeek)
            var awarded = award(
                database = writableDatabase,
                userId = userId,
                eventKey = "observation:$uuid",
                eventType = XpEventType.CONFIRMED_OBSERVATION,
                uuid = uuid,
                subjectTaxonId = collectionTaxonId,
                label = observation.label,
                points = observationXp,
                createdAtMs = now,
            )
            if (
                collectionTaxonId != null &&
                collectionRank == "species" &&
                !hasConfirmedTaxon(userId, collectionTaxonId, uuid)
            ) {
                awarded += award(
                    database = writableDatabase,
                    userId = userId,
                    eventKey = "first-species:$collectionTaxonId",
                    eventType = XpEventType.FIRST_SPECIES,
                    uuid = uuid,
                    subjectTaxonId = collectionTaxonId,
                    label = observation.label,
                    points = ProgressionRules.FIRST_SPECIES_XP,
                    createdAtMs = now,
                )
            }
            writableDatabase.update(
                "observations",
                ContentValues().apply { put("confirmed", 1) },
                "user_id = ? AND uuid = ?",
                arrayOf(userId.toString(), uuid),
            )
            if (regionalReward != null && collectionTaxonId == regionalReward.taxonId) {
                awarded += award(
                    writableDatabase, userId,
                    "regional_discovery:${regionalReward.regionKey}:${regionalReward.catalogueVersion}:$collectionTaxonId",
                    XpEventType.REGIONAL_DISCOVERY, uuid, collectionTaxonId, observation.label,
                    ProgressionRules.REGIONAL_DISCOVERY_XP, now,
                )
                ProgressionRules.regionalRarityXp(regionalReward.rarity).takeIf { it > 0 }?.let { points ->
                    awarded += award(
                        writableDatabase, userId,
                        "regional_rarity:${regionalReward.regionKey}:${regionalReward.catalogueVersion}:$collectionTaxonId",
                        XpEventType.REGIONAL_RARITY, uuid, collectionTaxonId, observation.label, points, now,
                    )
                }
                // The five Icons of a region carry a discovery bonus of their own. This was
                // once a separate Legend prestige tier, but it always named exactly the same
                // five species, so the standing and the tier were one thing wearing two names.
                if (regionalReward.taxonId in regionalReward.icons) {
                    awarded += award(
                        writableDatabase, userId,
                        "regional_icon_discovery:${regionalReward.regionKey}:${regionalReward.catalogueVersion}:$collectionTaxonId",
                        XpEventType.REGIONAL_ICON_DISCOVERY, uuid, collectionTaxonId, observation.label,
                        ProgressionRules.REGIONAL_ICON_DISCOVERY_XP, now,
                    )
                }
                if (hasAllRegionalTaxa(userId, regionalReward.regionKey, regionalReward.essentials, writableDatabase)) {
                    awarded += award(writableDatabase, userId,
                        "regional_essentials:${regionalReward.regionKey}:${regionalReward.catalogueVersion}",
                        XpEventType.REGIONAL_ESSENTIALS, uuid, null, "Regional Essentials",
                        ProgressionRules.REGIONAL_ESSENTIALS_XP, now)
                }
                if (hasAllRegionalTaxa(userId, regionalReward.regionKey, regionalReward.icons, writableDatabase)) {
                    awarded += award(writableDatabase, userId,
                        "regional_icons:${regionalReward.regionKey}:${regionalReward.catalogueVersion}",
                        XpEventType.REGIONAL_ICONS, uuid, null, "Regional Icons",
                        ProgressionRules.REGIONAL_ICONS_XP, now)
                }
            }
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

    /**
     * Reverses a confirmation, restoring the ledger to what it was before it.
     *
     * Undoing an automatic match has to be a real reversal rather than a hidden marker,
     * because everything downstream — collection count, XP, rank — is recomputed from
     * `xp_events` and `observations.confirmed`. Leaving the award behind would credit a
     * sighting the user has just told us was not theirs.
     *
     * The two kinds of award unwind differently. `observation:<uuid>` belongs to this
     * record alone and always goes. The species and regional milestones are keyed by taxon
     * and region, so they survive as long as *some* confirmed observation still carries
     * that taxon — they are re-attributed to it rather than deleted, which keeps the
     * idempotency key that prevents a second award for the same milestone.
     */
    fun unconfirmObservation(userId: Long, uuid: String): CollectionSummary {
        writableDatabase.beginTransaction()
        try {
            val taxonId = writableDatabase.rawQuery(
                """
                SELECT COALESCE(collection_taxon_id, taxon_id) FROM observations
                WHERE user_id = ? AND uuid = ? AND confirmed = 1
                """.trimIndent(),
                arrayOf(userId.toString(), uuid),
            ).use { cursor ->
                if (!cursor.moveToFirst()) return summary(userId)
                if (cursor.isNull(0)) null else cursor.getLong(0)
            }

            writableDatabase.update(
                "observations",
                ContentValues().apply { put("confirmed", 0) },
                "user_id = ? AND uuid = ?",
                arrayOf(userId.toString(), uuid),
            )
            writableDatabase.delete(
                "xp_events",
                "user_id = ? AND event_key = ?",
                arrayOf(userId.toString(), "observation:$uuid"),
            )

            // Now that this record is no longer confirmed, does any other confirmed record
            // still hold the taxon its milestones were earned for?
            val heir = taxonId?.let {
                writableDatabase.rawQuery(
                    """
                    SELECT uuid FROM observations
                    WHERE user_id = ? AND confirmed = 1
                      AND COALESCE(collection_taxon_id, taxon_id) = ?
                    ORDER BY observed_at_ms ASC LIMIT 1
                    """.trimIndent(),
                    arrayOf(userId.toString(), it.toString()),
                ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            }
            if (heir != null) {
                writableDatabase.update(
                    "xp_events",
                    ContentValues().apply { put("observation_uuid", heir) },
                    "user_id = ? AND observation_uuid = ?",
                    arrayOf(userId.toString(), uuid),
                )
            } else {
                writableDatabase.delete(
                    "xp_events",
                    "user_id = ? AND observation_uuid = ?",
                    arrayOf(userId.toString(), uuid),
                )
            }

            val updated = calculatedSummary(
                userId, writableDatabase, lastSyncedAt(userId, writableDatabase),
            )
            saveSummary(userId, updated, writableDatabase)
            writableDatabase.setTransactionSuccessful()
            return updated
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

    fun xpEvents(userId: Long, limit: Int = 20): List<XpEventRecord> = readableDatabase.query(
        "xp_events",
        arrayOf(
            "event_key", "event_type", "observation_uuid", "subject_taxon_id",
            "label", "points", "created_at_ms",
        ),
        "user_id = ? AND points > 0",
        arrayOf(userId.toString()),
        null,
        null,
        "created_at_ms DESC",
        limit.coerceAtLeast(0).toString(),
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    XpEventRecord(
                        eventKey = cursor.getString(0),
                        type = XpEventType.fromKey(cursor.getString(1)),
                        observationUuid = cursor.getString(2).ifBlank { null },
                        subjectTaxonId = if (cursor.isNull(3)) null else cursor.getLong(3),
                        label = if (cursor.isNull(4)) null else cursor.getString(4),
                        points = cursor.getInt(5),
                        createdAtMs = cursor.getLong(6),
                    ),
                )
            }
        }
    }

    fun qualityTransitions(
        userId: Long,
        limit: Int = 5,
    ): List<ObservationQualityTransition> = readableDatabase.query(
        "observation_quality_events",
        arrayOf(
            "observation_uuid", "label", "from_quality_grade",
            "to_quality_grade", "detected_at_ms",
        ),
        "user_id = ?",
        arrayOf(userId.toString()),
        null,
        null,
        "detected_at_ms DESC",
        limit.coerceAtLeast(0).toString(),
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    ObservationQualityTransition(
                        observationUuid = cursor.getString(0),
                        label = cursor.getString(1),
                        fromQualityGrade = cursor.getString(2),
                        toQualityGrade = cursor.getString(3),
                        detectedAtMs = cursor.getLong(4),
                    ),
                )
            }
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

    private fun hasAllRegionalTaxa(
        userId: Long, regionKey: String, taxonIds: Set<Long>, database: SQLiteDatabase,
    ): Boolean {
        if (taxonIds.isEmpty()) return false
        val placeholders = taxonIds.joinToString(",") { "?" }
        val args = arrayOf(userId.toString(), regionKey) + taxonIds.map(Long::toString)
        return database.rawQuery(
            """SELECT COUNT(DISTINCT COALESCE(o.collection_taxon_id, o.taxon_id))
                FROM observations o JOIN observation_regions r
                ON r.user_id = o.user_id AND r.observation_uuid = o.uuid
                WHERE o.user_id = ? AND o.confirmed = 1 AND r.region_key = ?
                AND r.assignment IN ('LAND_POLYGON','OFFSHORE_BUFFER')
                AND COALESCE(o.collection_taxon_id, o.taxon_id) IN ($placeholders)""".trimIndent(),
            args,
        ).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) == taxonIds.size }
    }

    private fun award(
        database: SQLiteDatabase,
        userId: Long,
        eventKey: String,
        eventType: XpEventType,
        uuid: String,
        subjectTaxonId: Long?,
        label: String?,
        points: Int,
        createdAtMs: Long,
    ): Int {
        val inserted = database.insertWithOnConflict(
            "xp_events",
            null,
            ContentValues().apply {
                put("user_id", userId)
                put("event_key", eventKey)
                put("event_type", eventType.key)
                put("observation_uuid", uuid)
                subjectTaxonId?.let { put("subject_taxon_id", it) }
                label?.let { put("label", it) }
                put("points", points)
                put("created_at_ms", createdAtMs)
            },
            SQLiteDatabase.CONFLICT_IGNORE,
        )
        return if (inserted == -1L) 0 else points
    }

    private fun rewardedObservationsThisWeek(
        userId: Long,
        collectionTaxonId: Long,
        nowMs: Long,
        database: SQLiteDatabase,
    ): Int {
        val date = Instant.ofEpochMilli(nowMs).atZone(ZoneOffset.UTC).toLocalDate()
        val weekStart = date.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val weekEnd = weekStart + 7L * 24L * 60L * 60L * 1_000L
        return database.rawQuery(
            """
            SELECT COUNT(*) FROM xp_events
            WHERE user_id = ?
              AND event_type = ?
              AND subject_taxon_id = ?
              AND created_at_ms >= ? AND created_at_ms < ?
            """.trimIndent(),
            arrayOf(
                userId.toString(), XpEventType.CONFIRMED_OBSERVATION.key,
                collectionTaxonId.toString(), weekStart.toString(), weekEnd.toString(),
            ),
        ).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }
    }

    private fun qualitySnapshot(
        userId: Long,
        database: SQLiteDatabase,
    ): Map<String, String> = database.query(
        "observations",
        arrayOf("uuid", "quality_grade"),
        "user_id = ?",
        arrayOf(userId.toString()),
        null,
        null,
        null,
    ).use { cursor ->
        buildMap {
            while (cursor.moveToNext()) put(cursor.getString(0), cursor.getString(1))
        }
    }

    private fun recordQualityTransition(
        database: SQLiteDatabase,
        userId: Long,
        transition: ObservationQualityTransition,
    ): ObservationQualityTransition? {
        val inserted = database.insertWithOnConflict(
            "observation_quality_events",
            null,
            ContentValues().apply {
                put("user_id", userId)
                put("event_key", "${transition.observationUuid}:${transition.toQualityGrade}")
                put("observation_uuid", transition.observationUuid)
                put("label", transition.label)
                put("from_quality_grade", transition.fromQualityGrade)
                put("to_quality_grade", transition.toQualityGrade)
                put("detected_at_ms", transition.detectedAtMs)
            },
            SQLiteDatabase.CONFLICT_IGNORE,
        )
        return transition.takeUnless { inserted == -1L }
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
        val taxonRank: String?,
        val collectionTaxonId: Long?,
        val collectionTaxonRank: String?,
        val label: String,
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
        positionalAccuracyM?.let { put("positional_accuracy_m", it) }
    }

    private fun ObservationRegion.values(userId: Long) = ContentValues().apply {
        put("user_id", userId)
        put("observation_uuid", observationUuid)
        regionKey?.let { put("region_key", it) }
        put("boundary_version", boundaryVersion)
        put("assignment", assignment.name)
    }

    companion object {
        private const val DATABASE = "wildlife_observations.db"
        private const val VERSION = 9
        private const val CREATE_XP_EVENTS = """
            CREATE TABLE IF NOT EXISTS xp_events (
                user_id INTEGER NOT NULL,
                event_key TEXT NOT NULL,
                event_type TEXT NOT NULL DEFAULT 'legacy',
                observation_uuid TEXT NOT NULL,
                subject_taxon_id INTEGER,
                label TEXT,
                points INTEGER NOT NULL,
                created_at_ms INTEGER NOT NULL,
                PRIMARY KEY(user_id, event_key)
            )
        """
        private const val CREATE_QUALITY_EVENTS = """
            CREATE TABLE IF NOT EXISTS observation_quality_events (
                user_id INTEGER NOT NULL,
                event_key TEXT NOT NULL,
                observation_uuid TEXT NOT NULL,
                label TEXT NOT NULL,
                from_quality_grade TEXT NOT NULL,
                to_quality_grade TEXT NOT NULL,
                detected_at_ms INTEGER NOT NULL,
                PRIMARY KEY(user_id, event_key)
            )
        """
        private const val CREATE_MAP_VISIBILITY_OVERRIDES = """
            CREATE TABLE IF NOT EXISTS map_visibility_overrides (
                user_id INTEGER NOT NULL,
                observation_uuid TEXT NOT NULL,
                visible INTEGER NOT NULL,
                PRIMARY KEY(user_id, observation_uuid)
            )
        """
        private const val CREATE_OBSERVATION_REGIONS = """
            CREATE TABLE IF NOT EXISTS observation_regions (
                user_id INTEGER NOT NULL,
                observation_uuid TEXT NOT NULL,
                region_key TEXT,
                boundary_version TEXT NOT NULL,
                assignment TEXT NOT NULL,
                PRIMARY KEY(user_id, observation_uuid)
            )
        """
    }
}
