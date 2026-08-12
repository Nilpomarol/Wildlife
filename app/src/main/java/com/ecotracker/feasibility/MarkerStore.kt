package com.wildlife.feasibility

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class MarkerStore(context: Context) {
    private val preferences = context.getSharedPreferences("handoff_markers", Context.MODE_PRIVATE)

    fun load(): List<PendingMarker> {
        val raw = preferences.getString(KEY_MARKERS, "[]") ?: "[]"
        val array = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        return buildList {
            for (index in 0 until array.length()) {
                val json = array.optJSONObject(index) ?: continue
                runCatching { json.toMarker() }.getOrNull()?.let(::add)
            }
        }
    }

    fun save(markers: List<PendingMarker>) {
        val array = JSONArray()
        markers.forEach { marker -> array.put(marker.toJson()) }
        preferences.edit().putString(KEY_MARKERS, array.toString()).apply()
    }

    private fun PendingMarker.toJson() = JSONObject().apply {
        put("id", id)
        put("imageUri", imageUri)
        put("source", source)
        put("capturedAtMs", capturedAtMs)
        put("latitude", latitude ?: JSONObject.NULL)
        put("longitude", longitude ?: JSONObject.NULL)
        put("state", state.name)
        put("sharedAtMs", sharedAtMs ?: JSONObject.NULL)
        put("matchedObservationUuid", matchedObservationUuid ?: JSONObject.NULL)
        put("handoffId", handoffId ?: JSONObject.NULL)
        put("capturedAtReliable", capturedAtReliable)
        put("locationReliable", locationReliable)
    }

    private fun JSONObject.toMarker(): PendingMarker {
        val rawState = optString("state")
        val state = if (rawState == "SHARED") {
            MarkerState.PENDING
        } else {
            runCatching { MarkerState.valueOf(rawState) }.getOrDefault(MarkerState.CAPTURED)
        }
        val id = getString("id")
        return PendingMarker(
        id = id,
        imageUri = getString("imageUri"),
        source = optString("source", "unknown"),
        capturedAtMs = getLong("capturedAtMs"),
        latitude = nullableDouble("latitude"),
        longitude = nullableDouble("longitude"),
        state = state,
        sharedAtMs = nullableLong("sharedAtMs"),
        matchedObservationUuid = nullableString("matchedObservationUuid"),
        handoffId = nullableString("handoffId") ?: if (state != MarkerState.CAPTURED) id else null,
        capturedAtReliable = if (has("capturedAtReliable")) optBoolean("capturedAtReliable") else true,
        locationReliable = if (has("locationReliable")) optBoolean("locationReliable")
            else !isNull("latitude") && !isNull("longitude"),
    )
    }

    private fun JSONObject.nullableDouble(key: String): Double? =
        if (isNull(key)) null else optDouble(key)

    private fun JSONObject.nullableLong(key: String): Long? =
        if (isNull(key)) null else optLong(key)

    private fun JSONObject.nullableString(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf(String::isNotBlank)

    companion object {
        private const val KEY_MARKERS = "markers"
    }
}
