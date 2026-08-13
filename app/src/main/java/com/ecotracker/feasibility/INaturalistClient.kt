package com.wildlife.feasibility

import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class INaturalistClient(
    private val http: ReadOnlyHttpClient = ReadOnlyHttpClient(),
) {
    fun resolveExactUser(username: String): INaturalistUser {
        val root = getJson(
            urlWithParameters(
                "https://api.inaturalist.org/v1/users/autocomplete",
                mapOf("q" to username, "per_page" to "30"),
            ),
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

    fun syncedObservations(userId: Long): List<SyncedObservation> {
        val observations = mutableListOf<SyncedObservation>()
        var cursor: Long? = null
        do {
            val cursorQuery = cursor?.let { "&id_above=$it" }.orEmpty()
            val root = getJson(
                URL(
                    "https://api.inaturalist.org/v1/observations" +
                        "?user_id=$userId&order_by=id&order=asc&per_page=200$cursorQuery",
                ),
            )
            val page = parseSyncedObservations(root)
            observations += page
            val next = page.maxOfOrNull(SyncedObservation::id)
            cursor = next?.takeIf { page.size == 200 && it != cursor }
        } while (cursor != null)
        return observations
    }

    fun regionalSpecies(
        placeId: Long,
        limit: Int,
        parameters: Map<String, String>,
    ): List<JSONObject> {
        val results = mutableListOf<JSONObject>()
        var page = 1
        while (results.size < limit) {
            val perPage = minOf(200, limit - results.size)
            val query = linkedMapOf(
                "place_id" to placeId.toString(),
                "quality_grade" to "research",
                "hrank" to "species",
                "lrank" to "species",
                "locale" to "ca",
                "per_page" to perPage.toString(),
                "page" to page.toString(),
            ).apply { putAll(parameters) }
            val array = getJson(url("https://api.inaturalist.org/v1/observations/species_counts", query))
                .optJSONArray("results") ?: break
            for (index in 0 until array.length()) {
                array.optJSONObject(index)?.let(results::add)
            }
            if (array.length() < perPage) break
            page++
        }
        return results.take(limit)
    }

    fun nearbySpecies(
        latitude: Double,
        longitude: Double,
        radiusKm: Int = 25,
        month: Int,
        limit: Int = 100,
    ): List<NearbySpecies> {
        require(latitude in -90.0..90.0 && longitude in -180.0..180.0)
        require(radiusKm in 1..200)
        require(month in 1..12)
        val root = getJson(
            url(
                "https://api.inaturalist.org/v1/observations/species_counts",
                linkedMapOf(
                    "lat" to latitude.toString(),
                    "lng" to longitude.toString(),
                    "radius" to radiusKm.toString(),
                    "month" to month.toString(),
                    "quality_grade" to "research",
                    "hrank" to "species",
                    "lrank" to "species",
                    "locale" to "ca",
                    "per_page" to limit.coerceIn(1, 200).toString(),
                ),
            ),
        )
        return parseNearbySpecies(root)
    }

    internal fun parseNearbySpecies(root: JSONObject): List<NearbySpecies> {
        val results = root.optJSONArray("results") ?: return emptyList()
        return buildList {
            for (index in 0 until results.length()) {
                val result = results.optJSONObject(index) ?: continue
                val taxon = result.optJSONObject("taxon") ?: continue
                val taxonId = taxon.optLong("id", -1L)
                val scientificName = taxon.optString("name").trim()
                if (taxonId <= 0L || scientificName.isBlank()) continue
                add(
                    NearbySpecies(
                        taxonId = taxonId,
                        commonName = taxon.optString("preferred_common_name")
                            .takeIf { it.isNotBlank() && it != "null" },
                        scientificName = scientificName,
                        taxonGroup = taxon.optString("iconic_taxon_name")
                            .takeIf { it.isNotBlank() && it != "null" },
                        observationCount = result.optInt("count", 0).coerceAtLeast(0),
                    ),
                )
            }
        }
    }

    fun taxa(taxonIds: List<Long>, locale: String = "en"): List<JSONObject> {
        if (taxonIds.isEmpty()) return emptyList()
        val root = getJson(
            url(
                "https://api.inaturalist.org/v1/taxa/${taxonIds.joinToString(",")}",
                mapOf("locale" to locale, "preferred_place_id" to "12997"),
            ),
        )
        val array = root.optJSONArray("results") ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) array.optJSONObject(index)?.let(::add)
        }
    }

    fun referencePhoto(taxonId: Long, placeId: Long = 12997): ReferencePhoto? {
        val root = getJson(
            url(
                "https://api.inaturalist.org/v1/observations",
                linkedMapOf(
                    "taxon_id" to taxonId.toString(),
                    "place_id" to placeId.toString(),
                    "quality_grade" to "research",
                    "photos" to "true",
                    "order_by" to "votes",
                    "order" to "desc",
                    "per_page" to "30",
                ),
            ),
        )
        val observations = root.optJSONArray("results") ?: return null
        for (index in 0 until observations.length()) {
            val observation = observations.optJSONObject(index) ?: continue
            val photos = observation.optJSONArray("photos") ?: continue
            for (photoIndex in 0 until photos.length()) {
                val photo = photos.optJSONObject(photoIndex) ?: continue
                val licence = normalizedPhotoLicence(photo.optString("license_code")) ?: continue
                val attribution = photo.optString("attribution").trim()
                if (attribution.isBlank()) continue
                val photoUrl = photo.optString("url").ifBlank { photo.optString("medium_url") }
                if (photoUrl.isBlank()) continue
                val photoId = photo.optLong("id", -1L)
                val source = if (photoId > 0) {
                    "https://www.inaturalist.org/photos/$photoId"
                } else {
                    "https://www.inaturalist.org/observations/${observation.optString("uuid")}"
                }
                return ReferencePhoto(
                    url = photoUrl.replace("square", "medium"),
                    attribution = attribution,
                    licenceCode = licence,
                    sourceUrl = source,
                )
            }
        }
        return null
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

    internal fun parseSyncedObservations(root: JSONObject): List<SyncedObservation> {
        val results = root.optJSONArray("results") ?: return emptyList()
        return buildList {
            for (index in 0 until results.length()) {
                val raw = results.optJSONObject(index) ?: continue
                val id = raw.optLong("id", -1L)
                val uuid = raw.optString("uuid")
                val observedAt = parseTimestamp(
                    raw.optString("time_observed_at").ifBlank { raw.optString("observed_on") },
                ) ?: continue
                if (id <= 0 || uuid.isBlank()) continue
                val taxon = raw.optJSONObject("taxon")
                val taxonId = taxon?.optLong("id", -1L)?.takeIf { it > 0 }
                val rank = taxon?.optString("rank")?.takeIf(String::isNotBlank)
                val rankLevel = taxon?.optDouble("rank_level", Double.NaN)
                    ?.takeUnless(Double::isNaN)
                val infraspecies = rank in setOf("subspecies", "variety", "form") ||
                    (rankLevel != null && rankLevel < 10.0)
                val collectionTaxonId = if (infraspecies) {
                    taxon?.optLong("parent_id", -1L)?.takeIf { it > 0 } ?: taxonId
                } else {
                    taxonId
                }
                val coordinates = raw.optJSONObject("geojson")?.optJSONArray("coordinates")
                val photos = raw.optJSONArray("photos")
                val photoUrl = photos?.optJSONObject(0)?.optString("url")
                    ?.takeIf(String::isNotBlank)?.replace("square", "medium")
                add(
                    SyncedObservation(
                        id = id,
                        uuid = uuid,
                        taxonId = taxonId,
                        taxonRank = rank,
                        collectionTaxonId = collectionTaxonId,
                        collectionTaxonRank = if (infraspecies) "species" else rank,
                        label = raw.optString("species_guess").takeIf(String::isNotBlank)
                            ?: taxon?.optString("preferred_common_name")?.takeIf(String::isNotBlank)
                            ?: taxon?.optString("name")?.takeIf(String::isNotBlank)
                            ?: "Unidentified",
                        observedAtMs = observedAt,
                        latitude = coordinates?.optDouble(1)?.takeUnless(Double::isNaN),
                        longitude = coordinates?.optDouble(0)?.takeUnless(Double::isNaN),
                        obscured = raw.optBoolean("obscured") ||
                            raw.optString("geoprivacy") == "obscured",
                        createdAtMs = parseTimestamp(raw.optString("created_at")),
                        qualityGrade = raw.optString("quality_grade").ifBlank { "unknown" },
                        photoUrl = photoUrl,
                        confirmed = false,
                    ),
                )
            }
        }
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

    internal fun getJson(endpoint: URL): JSONObject {
        return http.getJson(endpoint, REQUEST_POLICY)
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
        } ?: runCatching {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(raw)?.time
        }.getOrNull()
    }

    private fun url(endpoint: String, parameters: Map<String, String>): URL {
        return urlWithParameters(endpoint, parameters)
    }

    companion object {
        private val REQUEST_POLICY = ReadOnlyRequestPolicy(
            serviceName = "iNaturalist",
            userAgent = WildlifeNetworkIdentity.BIOLOGICAL_DATA_USER_AGENT,
            rateLimitKey = "api.inaturalist.org",
            minimumIntervalMs = 1_100L,
            readTimeoutMs = 15_000,
        )

        fun normalizedPhotoLicence(value: String?): String? = value
            ?.trim()
            ?.lowercase()
            ?.takeIf { it in setOf("cc0", "cc-by", "cc-by-sa") }
    }
}

data class ReferencePhoto(
    val url: String,
    val attribution: String,
    val licenceCode: String,
    val sourceUrl: String,
)
