package com.wildlife.feasibility

import android.content.Context
import java.io.File
import org.json.JSONObject

data class UserBackupPreview(val userId: Long, val login: String, val observations: Int, val exportedAt: String)

/** Restores only Wildlife-owned per-user state. Call restore only after the UI obtains confirmation. */
class UserDataBackupImporter(context: Context) {
    private val appContext = context.applicationContext

    fun preview(file: File): UserBackupPreview {
        val root = JSONObject(file.readText())
        require(root.getInt("schema_version") == 1) { "Unsupported backup format." }
        val account = root.getJSONObject("account")
        val userId = account.getLong("iNaturalist_user_id")
        require(userId > 0) { "Backup has no verified account." }
        return UserBackupPreview(userId, account.getString("login"), root.getJSONArray("observations").length(), root.getString("exported_at"))
    }

    fun restoreConfirmed(file: File): UserBackupPreview {
        val preview = preview(file)
        // Safety copy happens before any local replacement. It contains only the current user's data.
        StructuredLocalDataExporter(appContext).autoBackup()
        val root = JSONObject(file.readText())
        val accountJson = root.getJSONObject("account")
        val account = VerifiedAccount(preview.userId, preview.login, accountJson.getLong("verified_at_ms"))
        AccountStore(appContext).restoreVerified(account)
        val observations = root.getJSONArray("observations").let { array -> buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(SyncedObservation(item.getLong("iNaturalist_id"), item.getString("uuid"), item.longOrNull("taxon_id"), item.stringOrNull("taxon_rank"), item.longOrNull("collection_taxon_id"), item.stringOrNull("collection_taxon_rank"), item.getString("label"), item.getLong("observed_at_ms"), item.doubleOrNull("latitude"), item.doubleOrNull("longitude"), item.getBoolean("obscured"), item.longOrNull("created_at_ms"), item.getString("quality_grade"), item.stringOrNull("photo_url"), item.getBoolean("confirmed_in_wildlife")))
            }
        } }
        val regions = observations.mapNotNull { observation ->
            root.getJSONArray("observations").let { rows -> (0 until rows.length()).firstOrNull { rows.getJSONObject(it).getString("uuid") == observation.uuid }?.let(rows::getJSONObject) }
                ?.optJSONObject("region_assignment")?.takeIf { it.length() > 0 }?.let { region ->
                    ObservationRegion(observation.uuid, region.stringOrNull("region_key"), region.getString("boundary_version"), ObservationRegionAssignment.valueOf(region.getString("assignment")))
                }
        }
        val hidden = root.getJSONArray("observations").let { rows -> buildSet {
            for (index in 0 until rows.length()) rows.getJSONObject(index).takeIf { !it.optBoolean("map_visible", true) }
                ?.getString("uuid")?.let(::add)
        } }
        val xpEvents = root.getJSONArray("xp_events").let { rows -> buildList {
            for (index in 0 until rows.length()) rows.getJSONObject(index).let { item -> add(
                XpEventRecord(item.getString("event_key"), XpEventType.fromKey(item.getString("type")), item.stringOrNull("observation_uuid"), item.longOrNull("subject_taxon_id"), item.stringOrNull("label"), item.getInt("points"), item.getLong("created_at_ms")))
            }
        } }
        val transitions = root.getJSONArray("quality_transitions").let { rows -> buildList {
            for (index in 0 until rows.length()) rows.getJSONObject(index).let { item -> add(
                ObservationQualityTransition(item.getString("observation_uuid"), item.getString("label"), item.getString("from_quality_grade"), item.getString("to_quality_grade"), item.getLong("detected_at_ms")))
            }
        } }
        ObservationStore(appContext).use { store -> store.restoreUserBackup(preview.userId, observations, regions, hidden, xpEvents, transitions) }
        val progression = root.optJSONObject("progression")
        ProgressionStore(appContext).restore(preview.userId, progression?.stringOrNull("selected_level_key"), progression?.stringOrNull("highest_level_key"))
        return preview
    }

    private fun JSONObject.stringOrNull(key: String): String? = if (isNull(key)) null else optString(key).takeIf(String::isNotBlank)
    private fun JSONObject.longOrNull(key: String): Long? = if (isNull(key)) null else getLong(key)
    private fun JSONObject.doubleOrNull(key: String): Double? = if (isNull(key)) null else getDouble(key)
}
