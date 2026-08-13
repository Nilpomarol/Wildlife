package com.wildlife.feasibility

import org.json.JSONObject

class PhyloPicClient(
    private val http: ReadOnlyHttpClient = ReadOnlyHttpClient(),
) {
    fun resolve(taxonName: String, matchRank: String): SilhouetteAsset? {
        val normalized = taxonName.lowercase().replace(Regex("[^a-z\\s]"), " ")
            .replace(Regex("\\s+"), " ").trim()
        if (normalized.isBlank()) return null
        val metadata = getJson(
            urlWithParameters("$BASE_URL/nodes", mapOf("filter_name" to normalized)).toString(),
        )
        val build = metadata.optInt("build", -1)
        if (build <= 0 || metadata.optInt("totalItems") <= 0) return null
        val page = getJson(
            urlWithParameters(
                "$BASE_URL/nodes",
                linkedMapOf(
                    "build" to build.toString(),
                    "embed_items" to "true",
                    "filter_name" to normalized,
                    "page" to "0",
                ),
            ).toString(),
        )
        val items = page.optJSONObject("_embedded")?.optJSONArray("items") ?: return null
        val node = (0 until items.length()).asSequence().mapNotNull(items::optJSONObject)
            .firstOrNull {
                it.optJSONObject("_links")?.optJSONObject("self")?.optString("title")
                    ?.equals(normalized, ignoreCase = true) == true
            } ?: return null
        val links = node.optJSONObject("_links") ?: return null
        val resolvedTaxonName = links.optJSONObject("self")
            ?.optString("title")?.takeIf(String::isNotBlank) ?: taxonName
        val primaryImageHref = links.optJSONObject("primaryImage")
            ?.optString("href")?.takeIf(String::isNotBlank)
        val primary = primaryImageHref?.let { href ->
            silhouetteAsset(getJson(absolute(href)), resolvedTaxonName, matchRank)
        }
        if (primary != null) return primary

        var imagesHref = links.optJSONObject("images")
            ?.optString("href")?.takeIf(String::isNotBlank) ?: return null
        while (true) {
            val page = getJson(withEmbeddedItems(absolute(imagesHref)))
            val images = page.optJSONObject("_embedded")?.optJSONArray("items")
            val alternative = images?.let { values ->
                firstCompatibleSilhouette(
                    values,
                    resolvedTaxonName,
                    matchRank,
                )
            }
            if (alternative != null) return alternative
            imagesHref = page.optJSONObject("_links")?.optJSONObject("next")
                ?.optString("href")?.takeIf(String::isNotBlank) ?: return null
        }
    }

    internal fun firstCompatibleSilhouette(
        images: org.json.JSONArray,
        taxonName: String,
        matchRank: String,
    ): SilhouetteAsset? = firstCompatibleSilhouette(
        candidates = (0 until images.length()).asSequence()
            .mapNotNull(images::optJSONObject)
            .mapNotNull(::imageCandidate)
            .toList(),
        taxonName = taxonName,
        matchRank = matchRank,
    )

    internal fun firstCompatibleSilhouette(
        candidates: List<SilhouetteImageCandidate>,
        taxonName: String,
        matchRank: String,
    ): SilhouetteAsset? = candidates.asSequence()
        .mapNotNull { candidate -> silhouetteAsset(candidate, taxonName, matchRank) }
        .firstOrNull()

    internal fun silhouetteAsset(
        image: JSONObject,
        taxonName: String,
        matchRank: String,
    ): SilhouetteAsset? = imageCandidate(image)?.let { candidate ->
        silhouetteAsset(candidate, taxonName, matchRank)
    }

    private fun imageCandidate(image: JSONObject): SilhouetteImageCandidate? {
        val links = image.optJSONObject("_links") ?: return null
        val licenceUrl = links.optJSONObject("license")
            ?.optString("href")?.takeIf(String::isNotBlank) ?: return null
        val rasterFiles = links.optJSONArray("rasterFiles") ?: return null
        val rasterUrls = (0 until rasterFiles.length()).asSequence()
            .mapNotNull { index ->
                rasterFiles.optJSONObject(index)?.optString("href")?.takeIf(String::isNotBlank)
            }
            .toList()
        val imageUuid = image.optString("uuid").takeIf(String::isNotBlank) ?: return null
        return SilhouetteImageCandidate(
            uuid = imageUuid,
            attribution = image.optString("attribution").takeIf(String::isNotBlank),
            licenceUrl = licenceUrl,
            rasterUrls = rasterUrls,
        )
    }

    private fun silhouetteAsset(
        candidate: SilhouetteImageCandidate,
        taxonName: String,
        matchRank: String,
    ): SilhouetteAsset? {
        val licence = licenceCode(candidate.licenceUrl) ?: return null
        val rasterUrl = candidate.rasterUrls.lastOrNull() ?: return null
        return SilhouetteAsset(
            url = absolute(rasterUrl),
            sourceUrl = "https://www.phylopic.org/images/${candidate.uuid}",
            attribution = candidate.attribution,
            licenceCode = licence,
            licenceUrl = absolute(candidate.licenceUrl),
            taxonName = taxonName,
            matchRank = matchRank,
        )
    }

    private fun withEmbeddedItems(endpoint: String): String = when {
        "embed_items=" in endpoint -> endpoint
        '?' in endpoint -> "$endpoint&embed_items=true"
        else -> "$endpoint?embed_items=true"
    }

    private fun getJson(endpoint: String): JSONObject {
        return http.getJson(urlWithParameters(endpoint, emptyMap()), REQUEST_POLICY)
    }

    private fun absolute(value: String): String = when {
        value.startsWith("http") -> value
        value.startsWith("/") -> "$BASE_URL$value"
        else -> "$BASE_URL/$value"
    }

    internal fun licenceCode(value: String): String? {
        val normalized = value.lowercase().trimEnd('/')
        if ("/publicdomain/mark/" in normalized) return "pdm"
        if ("/publicdomain/zero/" in normalized) return "cc0"
        val version = Regex("/licenses/by/([0-9.]+)$").find(normalized)?.groupValues?.get(1)
        return version?.let { "cc-by-$it" }
    }

    companion object {
        private const val BASE_URL = "https://api.phylopic.org"
        private val REQUEST_POLICY = ReadOnlyRequestPolicy(
            serviceName = "PhyloPic",
            userAgent = WildlifeNetworkIdentity.SILHOUETTE_METADATA_USER_AGENT,
            rateLimitKey = "api.phylopic.org",
            minimumIntervalMs = 300L,
            headers = mapOf("Accept" to "application/vnd.phylopic.v2+json"),
        )
    }
}

data class SilhouetteAsset(
    val url: String,
    val sourceUrl: String,
    val attribution: String?,
    val licenceCode: String,
    val licenceUrl: String,
    val taxonName: String,
    val matchRank: String,
    val localUri: String? = null,
    val resolverVersion: Int? = null,
)

internal data class SilhouetteImageCandidate(
    val uuid: String,
    val attribution: String?,
    val licenceUrl: String,
    val rasterUrls: List<String>,
)
