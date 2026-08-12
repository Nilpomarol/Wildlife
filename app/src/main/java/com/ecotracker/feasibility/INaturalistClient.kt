package com.wildlife.feasibility

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class INaturalistClient {
    fun resolveExactUser(username: String): INaturalistUser {
        val encodedUser = URLEncoder.encode(username, Charsets.UTF_8.name())
        val root = getJson(
            URL("https://api.inaturalist.org/v1/users/autocomplete?q=$encodedUser&per_page=30"),
        )
        return parseExactUser(root, username)
            ?: error("No exact iNaturalist username match was found.")
    }

    fun recentObservations(userId: Long, earliestCaptureMs: Long): List<ObservationCandidate> {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val startDate = formatter.format(earliestCaptureMs - 24L * 60L * 60L * 1_000L)
        val endpoint = URL(
            "https://api.inaturalist.org/v1/observations" +
                "?user_id=$userId&d1=$startDate&order_by=created_at&order=desc&per_page=200",
        )
        return parse(getJson(endpoint))
    }

    fun publicObservations(
        userId: Long,
        idAbove: Long? = null,
        perPage: Int = 100,
    ): PublicObservationPage {
        val cursor = idAbove?.let { "&id_above=$it" }.orEmpty()
        val endpoint = URL(
            "https://api.inaturalist.org/v1/observations" +
                "?user_id=$userId&order_by=id&order=asc&per_page=$perPage$cursor",
        )
        return parsePublicObservationPage(getJson(endpoint))
    }

    fun publicUserProfile(userId: Long): PublicINaturalistProfile {
        val root = getJson(
            URL("https://api.inaturalist.org/v2/users/$userId?fields=id,login,description"),
        )
        return parsePublicUserProfile(root, userId)
            ?: error("The public iNaturalist profile could not be read.")
    }

    internal fun parsePublicUserProfile(
        root: JSONObject,
        expectedUserId: Long,
    ): PublicINaturalistProfile? {
        val results = root.optJSONArray("results") ?: return null
        for (index in 0 until results.length()) {
            val user = results.optJSONObject(index) ?: continue
            val id = user.optLong("id", -1L)
            val login = user.optString("login")
            if (id == expectedUserId && login.isNotBlank()) {
                return PublicINaturalistProfile(
                    id = id,
                    login = login,
                    description = if (user.isNull("description")) null else user.optString("description"),
                )
            }
        }
        return null
    }

    internal fun parsePublicObservationPage(root: JSONObject): PublicObservationPage {
        val results = root.optJSONArray("results")
        val observations = buildList {
            if (results != null) {
                for (index in 0 until results.length()) {
                    val observation = results.optJSONObject(index) ?: continue
                    val id = observation.optLong("id", -1L)
                    val uuid = observation.optString("uuid")
                    if (id <= 0L || uuid.isBlank()) continue
                    val taxon = observation.optJSONObject("taxon")
                    val label = observation.optString("species_guess").takeIf(String::isNotBlank)
                        ?: taxon?.optString("preferred_common_name")?.takeIf(String::isNotBlank)
                        ?: taxon?.optString("name")?.takeIf(String::isNotBlank)
                        ?: "Unidentified"
                    add(
                        PublicObservation(
                            id = id,
                            uuid = uuid,
                            label = label,
                            observedOn = observation.optString("observed_on_string")
                                .ifBlank { observation.optString("observed_on") },
                            qualityGrade = observation.optString("quality_grade").ifBlank { "unknown" },
                        ),
                    )
                }
            }
        }
        return PublicObservationPage(root.optInt("total_results", observations.size), observations)
    }

    internal fun parseExactUser(root: JSONObject, username: String): INaturalistUser? {
        val results = root.optJSONArray("results") ?: return null
        val users = buildList {
            for (index in 0 until results.length()) {
                val user = results.optJSONObject(index) ?: continue
                val login = user.optString("login")
                val id = user.optLong("id", -1L)
                if (id > 0L && login.isNotBlank()) add(INaturalistUser(id, login))
            }
        }
        return exactUser(users, username)
    }

    internal fun exactUser(users: List<INaturalistUser>, username: String): INaturalistUser? =
        users.singleOrNull { it.login.equals(username, ignoreCase = true) }

    private fun getJson(endpoint: URL): JSONObject {
        val connection = endpoint.openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        connection.setRequestProperty("User-Agent", "Wildlife-Feasibility/0.1")
        try {
            val code = connection.responseCode
            if (code !in 200..299) error("iNaturalist returned HTTP $code")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            return JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    internal fun parse(root: JSONObject): List<ObservationCandidate> {
        val results = root.optJSONArray("results") ?: return emptyList()
        return buildList {
            for (index in 0 until results.length()) {
                val observation = results.optJSONObject(index) ?: continue
                val uuid = observation.optString("uuid").takeIf(String::isNotBlank) ?: continue
                val observedAt = parseTimestamp(observation.optString("time_observed_at")) ?: continue
                val createdAt = parseTimestamp(observation.optString("created_at"))
                val coordinates = observation.optJSONObject("geojson")
                    ?.optJSONArray("coordinates")
                add(
                    ObservationCandidate(
                        uuid = uuid,
                        observedAtMs = observedAt,
                        latitude = coordinates?.optDouble(1)?.takeUnless(Double::isNaN),
                        longitude = coordinates?.optDouble(0)?.takeUnless(Double::isNaN),
                        obscured = observation.optBoolean("obscured") ||
                            observation.optString("geoprivacy") == "obscured",
                        createdAtMs = createdAt,
                    ),
                )
            }
        }
    }

    private fun parseTimestamp(raw: String): Long? {
        if (raw.isBlank()) return null
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        )
        return formats.firstNotNullOfOrNull { pattern ->
            runCatching { SimpleDateFormat(pattern, Locale.US).parse(raw)?.time }.getOrNull()
        }
    }
}
