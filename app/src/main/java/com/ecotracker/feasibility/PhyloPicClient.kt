package com.wildlife.feasibility

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class PhyloPicClient {
    fun resolve(taxonName: String, matchRank: String): SilhouetteAsset? {
        val normalized = taxonName.lowercase().replace(Regex("[^a-z\\s]"), " ")
            .replace(Regex("\\s+"), " ").trim()
        if (normalized.isBlank()) return null
        val encoded = URLEncoder.encode(normalized, Charsets.UTF_8.name())
        val metadata = getJson("$BASE_URL/nodes?filter_name=$encoded")
        val build = metadata.optInt("build", -1)
        if (build <= 0 || metadata.optInt("totalItems") <= 0) return null
        val page = getJson(
            "$BASE_URL/nodes?build=$build&embed_items=true&filter_name=$encoded&page=0",
        )
        val items = page.optJSONObject("_embedded")?.optJSONArray("items") ?: return null
        val node = (0 until items.length()).asSequence().mapNotNull(items::optJSONObject)
            .firstOrNull {
                it.optJSONObject("_links")?.optJSONObject("self")?.optString("title")
                    ?.equals(normalized, ignoreCase = true) == true
            } ?: items.optJSONObject(0) ?: return null
        val imageHref = node.optJSONObject("_links")?.optJSONObject("primaryImage")
            ?.optString("href")?.takeIf(String::isNotBlank) ?: return null
        val image = getJson(absolute(imageHref))
        val licenceUrl = image.optJSONObject("_links")?.optJSONObject("license")
            ?.optString("href")?.takeIf(String::isNotBlank) ?: return null
        val licence = licenceCode(licenceUrl) ?: return null
        val rasterFiles = image.optJSONObject("_links")?.optJSONArray("rasterFiles") ?: return null
        val rasterUrl = (rasterFiles.length() - 1 downTo 0).asSequence()
            .mapNotNull { rasterFiles.optJSONObject(it)?.optString("href")?.takeIf(String::isNotBlank) }
            .firstOrNull() ?: return null
        val imageUuid = image.optString("uuid").takeIf(String::isNotBlank) ?: return null
        return SilhouetteAsset(
            url = absolute(rasterUrl),
            sourceUrl = "https://www.phylopic.org/images/$imageUuid",
            attribution = image.optString("attribution").takeIf(String::isNotBlank),
            licenceCode = licence,
            licenceUrl = absolute(licenceUrl),
            taxonName = node.optJSONObject("_links")?.optJSONObject("self")
                ?.optString("title")?.takeIf(String::isNotBlank) ?: taxonName,
            matchRank = matchRank,
        )
    }

    private fun getJson(endpoint: String): JSONObject {
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 20_000
        connection.setRequestProperty("User-Agent", "Wildlife-Android/0.1")
        connection.setRequestProperty("Accept", "application/vnd.phylopic.v2+json")
        try {
            val code = connection.responseCode
            if (code !in 200..299) error("PhyloPic returned HTTP $code")
            return JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
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
)
