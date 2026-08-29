package com.wildlife.feasibility

import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale

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

    fun syncedObservations(
        userId: Long,
        pageSize: Int = OBSERVATION_PAGE_SIZE,
    ): List<SyncedObservation> {
        val observations = mutableListOf<SyncedObservation>()
        var cursor: Long? = null
        var size = pageSize
        do {
            val page = observationPage(userId, cursor, size)
            // Keep whatever size actually fit: whatever made one page heavy — a long
            // identification thread, a record with many photos — tends to affect its
            // neighbours, and the end-of-walk test below has to use the size that was
            // really requested.
            size = page.pageSize
            observations += parseSyncedObservations(page.root)
            cursor = nextPageCursor(page.root, cursor, size)
        } while (cursor != null)
        return observations
    }

    /**
     * One page, narrowed until it fits.
     *
     * Page weight is not under Wildlife's control. The field selection makes a full page
     * small — around 123 KiB against an 8 MiB ceiling — but that is a measurement, not a
     * guarantee: field selection bounds the number of fields, not the length of the values
     * in them. Rather than fail an entire sync on one unusually heavy page, halve the
     * request and ask again, down to [MINIMUM_OBSERVATION_PAGE_SIZE]; below that the
     * response is genuinely unreadable and the error stands.
     *
     * Narrowing mid-walk is safe precisely because the cursor is `id_above` and not an
     * offset — a smaller page resumes from the same id and simply returns fewer records.
     * It would *not* be safe on a single unpaginated request, where a smaller page would
     * silently drop records instead of deferring them to the next one.
     */
    private fun observationPage(userId: Long, cursor: Long?, pageSize: Int): ObservationPage {
        var size = pageSize
        while (true) {
            try {
                val root = getJson(
                    observationsUrl(observationPageParameters(userId, cursor, size)),
                )
                return ObservationPage(root, size)
            } catch (oversized: RemoteDataException.ResponseTooLarge) {
                if (size <= MINIMUM_OBSERVATION_PAGE_SIZE) throw oversized
                size = (size / 2).coerceAtLeast(MINIMUM_OBSERVATION_PAGE_SIZE)
            }
        }
    }

    /** A fetched page together with the page size that actually fit. */
    private class ObservationPage(val root: JSONObject, val pageSize: Int)

    /** One ascending id-cursor page of the account's observations. */
    private fun observationPageParameters(
        userId: Long,
        idAbove: Long?,
        perPage: Int,
    ): Map<String, String> = buildMap {
        put("user_id", userId.toString())
        put("order_by", "id")
        put("order", "asc")
        put("per_page", perPage.toString())
        idAbove?.let { put("id_above", it.toString()) }
    }

    /**
     * The observation endpoint, always with the explicit field selection attached.
     *
     * Routing every observation read through here is what keeps the v1 full-graph
     * response from coming back: v2 returns only the fields named in [OBSERVATION_FIELDS].
     */
    private fun observationsUrl(parameters: Map<String, String>): URL = urlWithParameters(
        OBSERVATIONS_ENDPOINT,
        parameters + ("fields" to OBSERVATION_FIELDS),
    )

    /**
     * The next `id_above` cursor, derived from the raw page rather than the parsed list.
     *
     * Both the page length and the highest id have to come from the response itself.
     * [parseSyncedObservations] drops any record it cannot key — iNaturalist permits an
     * observation with no date — so a full page can parse to fewer rows. Ending
     * pagination on the parsed count truncated the user's history, and because
     * `ObservationStore.replaceSnapshot` deletes and rewrites the whole account, every
     * observation past the short page was then removed from the collection.
     *
     * Returns null on the last page, so a partial page ends the walk.
     */
    internal fun nextPageCursor(root: JSONObject, current: Long?, pageSize: Int): Long? {
        val results = root.optJSONArray("results") ?: return null
        if (results.length() < pageSize) return null
        var highest = current ?: 0L
        for (index in 0 until results.length()) {
            val id = results.optJSONObject(index)?.optLong("id", -1L) ?: -1L
            if (id > highest) highest = id
        }
        return highest.takeIf { it > 0L && it != current }
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
                // The default photo rides along on this response, so a carousel tile costs no
                // extra request. `medium_url` rather than `square_url`: the square is 75px and
                // visibly soft on a tile, while medium is 500px and already cached by Coil.
                val photo = taxon.optJSONObject("default_photo")
                add(
                    NearbySpecies(
                        taxonId = taxonId,
                        commonName = taxon.optString("preferred_common_name")
                            .takeIf { it.isNotBlank() && it != "null" },
                        scientificName = scientificName,
                        // Normalized here, not at the render site. `iconic_taxon_name` is
                        // "Aves"; the silhouette assets are named "birds", and the loader
                        // fails silently on a miss, so an un-normalized value would draw an
                        // empty plate with no error anywhere.
                        taxonGroup = taxonGroupForClass(
                            taxon.optString("iconic_taxon_name")
                                .takeIf { it.isNotBlank() && it != "null" },
                        ),
                        observationCount = result.optInt("count", 0).coerceAtLeast(0),
                        photoUrl = photo?.optString("medium_url")
                            ?.takeIf { it.isNotBlank() && it != "null" }
                            ?: photo?.optString("url")?.takeIf { it.isNotBlank() && it != "null" },
                        photoAttribution = photo?.optString("attribution")
                            ?.takeIf { it.isNotBlank() && it != "null" },
                        photoLicenseCode = photo?.optString("license_code")
                            ?.takeIf { it.isNotBlank() && it != "null" },
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

    fun referencePhoto(taxonId: Long, placeId: Long? = null): ReferencePhoto? {
        val parameters = linkedMapOf(
            "taxon_id" to taxonId.toString(),
            "quality_grade" to "research",
            "photos" to "true",
            "order_by" to "votes",
            "order" to "desc",
            "per_page" to "30",
        )
        placeId?.let { parameters["place_id"] = it.toString() }
        val root = getJson(
            url(
                "https://api.inaturalist.org/v1/observations",
                parameters,
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
        /**
         * The observation endpoint. v2 rather than v1 because only v2 honours a field
         * selection, and the v1 response is not survivable on a phone: it returns the
         * whole object graph per record — every identification with its own nested taxon,
         * every comment, `ofvs`, `annotations`, `project_observations`, `taxon.ancestors`.
         * One 200-record page measured 27.2 MiB against this client's 8 MiB ceiling, so
         * the sync failed outright for any account past its first page. The same page
         * under [OBSERVATION_FIELDS] measures ~115 KiB.
         *
         * The ceiling is not the thing to raise: 8 MiB of JSON is ~8 MB buffered, a ~16 MB
         * String and a parse tree several times that, which is an OOM on the low-end
         * devices in the release matrix rather than a catchable error.
         */
        private const val OBSERVATIONS_ENDPOINT = "https://api.inaturalist.org/v2/observations"

        /**
         * Exactly the fields the three observation parsers read, and nothing else.
         *
         * Keep this in step with `parseSyncedObservations`: a field dropped here reads
         * back as absent rather than as an error, so an omission is silent.
         * `taxon.parent_id` and `taxon.rank_level` are
         * load-bearing — they drive the subspecies-to-species collection-taxon rule — and
         * `photos.url` arrives as the `square` variant that the parser rewrites.
         *
         * This is also the data-minimisation posture the PRD asks for: Wildlife no longer
         * downloads other people's comments and identification threads to discard them.
         */
        private const val OBSERVATION_FIELDS = "id,uuid,observed_on,observed_on_string," +
            "time_observed_at,created_at,quality_grade,species_guess,geoprivacy,obscured," +
            "taxon.id,taxon.name,taxon.rank,taxon.rank_level,taxon.parent_id," +
            "taxon.preferred_common_name,photos.url,geojson.coordinates"

        /** iNaturalist's per-page maximum. Safe now that a page is field-selected. */
        internal const val OBSERVATION_PAGE_SIZE = 200

        /**
         * The floor the adaptive retry will narrow a page to before giving up.
         *
         * Low enough that reaching it means something is genuinely wrong rather than
         * merely heavy, and high enough that a long history does not turn into hundreds
         * of paced requests.
         */
        internal const val MINIMUM_OBSERVATION_PAGE_SIZE = 25

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
