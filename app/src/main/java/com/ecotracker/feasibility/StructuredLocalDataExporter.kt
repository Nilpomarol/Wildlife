package com.wildlife.feasibility

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant

/**
 * Exports only data owned by Wildlife. It never calls iNaturalist and intentionally keeps the
 * diagnostic report separate: this file includes the user's own linked-account and coordinate
 * data so it can serve as a meaningful portability record.
 */
class StructuredLocalDataExporter(context: Context) {
    private val appContext = context.applicationContext

    fun export(): File {
        val account = AccountStore(appContext).verified()
        val snapshot = account?.let { linked ->
            ObservationStore(appContext).use { store ->
                val observations = store.observations(linked.userId)
                ExportSnapshot(
                    observations = observations,
                    hiddenObservationUuids = store.mapHiddenObservationUuids(linked.userId),
                    regionsByUuid = observations.mapNotNull { observation ->
                        store.observationRegion(linked.userId, observation.uuid)
                            ?.let { observation.uuid to it }
                    }.toMap(),
                    xpEvents = store.xpEvents(linked.userId, Int.MAX_VALUE),
                    qualityTransitions = store.qualityTransitions(linked.userId, Int.MAX_VALUE),
                )
            }
        } ?: ExportSnapshot()
        val payload = JSONObject().apply {
            put("schema_version", 1)
            put("exported_at", Instant.now().toString())
            put("source", "Wildlife local data")
            put("account", account?.toJson() ?: JSONObject.NULL)
            put("observations", JSONArray().apply {
                snapshot.observations.forEach { observation ->
                    put(observation.toJson(
                        mapVisible = observation.uuid !in snapshot.hiddenObservationUuids,
                        region = snapshot.regionsByUuid[observation.uuid],
                    ))
                }
            })
            put("xp_events", JSONArray().apply { snapshot.xpEvents.forEach { put(it.toJson()) } })
            put("quality_transitions", JSONArray().apply {
                snapshot.qualityTransitions.forEach { put(it.toJson()) }
            })
            put("pending_handoffs", JSONArray().apply { MarkerStore(appContext).load().forEach { put(it.toJson()) } })
            put("progression", JSONObject().apply {
                put("selected_level_key", account?.let { ProgressionStore(appContext).selectedLevelKey(it.userId) } ?: JSONObject.NULL)
                put("highest_level_key", account?.let { ProgressionStore(appContext).highestLevelKey(it.userId) } ?: JSONObject.NULL)
            })
        }
        val directory = File(appContext.filesDir, EXPORT_DIRECTORY).apply { mkdirs() }
        return File(directory, "wildlife-local-data-${System.currentTimeMillis()}.json").apply {
            writeText(payload.toString(2))
        }
    }

    /** Keeps three rolling local copies; these never leave the device or include shared content. */
    fun autoBackup(): File {
        val directory = File(appContext.filesDir, AUTO_BACKUP_DIRECTORY).apply { mkdirs() }
        val backup = export().copyTo(File(directory, "user-backup-${System.currentTimeMillis()}.json"), overwrite = true)
        directory.listFiles().orEmpty().sortedByDescending(File::lastModified).drop(MAX_AUTO_BACKUPS).forEach(File::delete)
        return backup
    }

    private data class ExportSnapshot(
        val observations: List<SyncedObservation> = emptyList(),
        val hiddenObservationUuids: Set<String> = emptySet(),
        val regionsByUuid: Map<String, ObservationRegion> = emptyMap(),
        val xpEvents: List<XpEventRecord> = emptyList(),
        val qualityTransitions: List<ObservationQualityTransition> = emptyList(),
    )

    private fun VerifiedAccount.toJson() = JSONObject().apply {
        put("iNaturalist_user_id", userId)
        put("login", login)
        put("verified_at_ms", verifiedAtMs)
    }

    private fun SyncedObservation.toJson(mapVisible: Boolean, region: ObservationRegion?) = JSONObject().apply {
        put("iNaturalist_id", id)
        put("uuid", uuid)
        putNullable("taxon_id", taxonId)
        putNullable("taxon_rank", taxonRank)
        putNullable("collection_taxon_id", collectionTaxonId)
        putNullable("collection_taxon_rank", collectionTaxonRank)
        put("label", label)
        put("observed_at_ms", observedAtMs)
        putNullable("latitude", latitude)
        putNullable("longitude", longitude)
        put("obscured", obscured)
        putNullable("created_at_ms", createdAtMs)
        put("quality_grade", qualityGrade)
        putNullable("photo_url", photoUrl)
        put("confirmed_in_wildlife", confirmed)
        put("map_visible", mapVisible)
        put("region_assignment", region?.toJson() ?: JSONObject.NULL)
    }

    private fun ObservationRegion.toJson() = JSONObject().apply {
        putNullable("region_key", regionKey)
        put("boundary_version", boundaryVersion)
        put("assignment", assignment.name)
        put("earns_regional_progress", earnsRegionalProgress)
    }

    private fun XpEventRecord.toJson() = JSONObject().apply {
        put("event_key", eventKey)
        put("type", type.key)
        putNullable("observation_uuid", observationUuid)
        putNullable("subject_taxon_id", subjectTaxonId)
        putNullable("label", label)
        put("points", points)
        put("created_at_ms", createdAtMs)
    }

    private fun ObservationQualityTransition.toJson() = JSONObject().apply {
        put("observation_uuid", observationUuid)
        put("label", label)
        putNullable("from_quality_grade", fromQualityGrade)
        put("to_quality_grade", toQualityGrade)
        put("detected_at_ms", detectedAtMs)
    }

    private fun PendingMarker.toJson() = JSONObject().apply {
        put("id", id)
        put("source", source)
        put("captured_at_ms", capturedAtMs)
        putNullable("latitude", latitude)
        putNullable("longitude", longitude)
        put("state", state.name)
        putNullable("shared_at_ms", sharedAtMs)
        putNullable("matched_observation_uuid", matchedObservationUuid)
        putNullable("handoff_id", handoffId)
        put("captured_at_reliable", capturedAtReliable)
        put("location_reliable", locationReliable)
    }

    private fun JSONObject.putNullable(key: String, value: Any?) = put(key, value ?: JSONObject.NULL)

    private companion object {
        const val EXPORT_DIRECTORY = "exports"
        const val AUTO_BACKUP_DIRECTORY = "auto-backups"
        const val MAX_AUTO_BACKUPS = 3
    }
}
