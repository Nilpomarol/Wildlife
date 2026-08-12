package com.wildlife.feasibility

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

class BackendClient(
    private val baseUrl: String = "http://127.0.0.1:8765",
) {
    fun sync(account: VerifiedAccount): BackendSyncResult {
        val root = request(
            URL("$baseUrl/v1/users/${account.userId}/sync"),
            JSONObject().put("login", account.login),
        )
        return parseSync(root)
    }

    fun confirm(userId: Long, observationUuid: String): BackendConfirmationResult {
        val root = request(
            URL("$baseUrl/v1/users/$userId/observations/$observationUuid/confirm"),
            JSONObject(),
        )
        return BackendConfirmationResult(
            xpAwarded = root.optInt("xp_awarded"),
            summary = CollectionSummary(
                confirmedCount = root.optInt("confirmed_count"),
                totalXp = root.optInt("total_xp"),
                lastSyncedAtMs = System.currentTimeMillis(),
            ),
        )
    }

    internal fun parseSync(root: JSONObject): BackendSyncResult {
        val array = root.optJSONArray("observations")
        val observations = buildList {
            if (array != null) {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val uuid = item.optString("inat_uuid")
                    val observedAt = parseTimestamp(item.optString("observed_at")) ?: continue
                    if (uuid.isBlank()) continue
                    add(
                        SyncedObservation(
                            id = item.optLong("inat_id"),
                            uuid = uuid,
                            taxonId = nullableLong(item, "taxon_id"),
                            taxonRank = nullableString(item, "taxon_rank"),
                            collectionTaxonId = nullableLong(item, "collection_taxon_id"),
                            collectionTaxonRank = nullableString(item, "collection_taxon_rank"),
                            label = item.optString("label").ifBlank { "Unidentified" },
                            observedAtMs = observedAt,
                            latitude = nullableDouble(item, "latitude"),
                            longitude = nullableDouble(item, "longitude"),
                            obscured = item.optInt("obscured") == 1,
                            createdAtMs = parseTimestamp(item.optString("created_at")),
                            qualityGrade = item.optString("quality_grade").ifBlank { "unknown" },
                            photoUrl = nullableString(item, "photo_url"),
                            confirmed = item.optInt("confirmed") == 1,
                        ),
                    )
                }
            }
        }
        return BackendSyncResult(
            observations = observations,
            summary = CollectionSummary(
                confirmedCount = root.optInt("confirmed_count"),
                totalXp = root.optInt("total_xp"),
                lastSyncedAtMs = parseTimestamp(root.optString("last_synced_at")),
            ),
            cached = root.optBoolean("cached"),
        )
    }

    private fun request(endpoint: URL, body: JSONObject): JSONObject {
        val connection = endpoint.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 10_000
        connection.readTimeout = 30_000
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("User-Agent", "Wildlife-Android/0.1")
        try {
            connection.outputStream.bufferedWriter().use { it.write(body.toString()) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val response = runCatching { JSONObject(responseBody) }.getOrDefault(JSONObject())
            if (code !in 200..299) {
                error(response.optString("error").ifBlank { "Wildlife sync returned HTTP $code" })
            }
            return response
        } finally {
            connection.disconnect()
        }
    }

    private fun parseTimestamp(raw: String): Long? =
        runCatching { OffsetDateTime.parse(raw).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { LocalDate.parse(raw).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() }
                .getOrNull()

    private fun nullableLong(json: JSONObject, key: String): Long? =
        if (json.isNull(key)) null else json.optLong(key)

    private fun nullableDouble(json: JSONObject, key: String): Double? =
        if (json.isNull(key)) null else json.optDouble(key).takeUnless(Double::isNaN)

    private fun nullableString(json: JSONObject, key: String): String? =
        if (json.isNull(key)) null else json.optString(key).takeIf(String::isNotBlank)
}
