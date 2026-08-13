package com.wildlife.feasibility

import android.text.Html
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder

class WikimediaCommonsClient {
    fun curatedPhoto(wikipediaUrl: String): CuratedReferencePhoto? {
        val article = normalizedArticleUri(wikipediaUrl) ?: return null
        val host = article.host?.takeIf { it.endsWith(".wikipedia.org") } ?: return null
        val encodedTitle = article.rawPath.substringAfter("/wiki/", "")
        if (encodedTitle.isBlank()) return null
        val title = URLDecoder.decode(encodedTitle, Charsets.UTF_8.name()).replace('_', ' ')
        val articleApi = "https://$host/w/api.php"
        val page = getJson(
            articleApi,
            linkedMapOf(
                "action" to "query",
                "format" to "json",
                "formatversion" to "2",
                "redirects" to "1",
                "prop" to "pageimages",
                "piprop" to "name",
                "titles" to title,
            ),
        )
        val fileName = page.optJSONObject("query")?.optJSONArray("pages")
            ?.optJSONObject(0)?.optString("pageimage")?.takeIf(String::isNotBlank)
            ?: return null
        return imageInfo(COMMONS_API, fileName) ?: imageInfo(articleApi, fileName)
    }

    private fun imageInfo(apiUrl: String, fileName: String): CuratedReferencePhoto? {
        val root = getJson(
            apiUrl,
            linkedMapOf(
                "action" to "query",
                "format" to "json",
                "formatversion" to "2",
                "prop" to "imageinfo",
                "titles" to if (fileName.startsWith("File:")) fileName else "File:$fileName",
                "iiprop" to "url|extmetadata",
                "iiurlwidth" to "1600",
                "iiextmetadatafilter" to
                    "LicenseShortName|LicenseUrl|Artist|Credit|Assessments",
            ),
        )
        val info = root.optJSONObject("query")?.optJSONArray("pages")
            ?.optJSONObject(0)?.optJSONArray("imageinfo")?.optJSONObject(0) ?: return null
        val metadata = info.optJSONObject("extmetadata") ?: return null
        val rawAssessment = metadata.metadataValue("Assessments")
        val rawLicence = metadata.metadataValue("LicenseShortName")
        val assessment = qualityAssessment(rawAssessment) ?: return null
        val licence = normalizedLicence(rawLicence) ?: return null
        val rawArtist = metadata.metadataValue("Artist") ?: metadata.metadataValue("Credit")
        val artist = rawArtist?.let(::plainText)?.takeIf(String::isNotBlank)
        if (licence !in setOf("cc0", "pdm") && artist == null) return null
        val imageUrl = info.optString("thumburl").takeIf(String::isNotBlank)
            ?: info.optString("url").takeIf(String::isNotBlank) ?: return null
        val sourceUrl = info.optString("descriptionurl").takeIf(String::isNotBlank) ?: return null
        val licenceUrl = metadata.metadataValue("LicenseUrl")
        return CuratedReferencePhoto(
            url = imageUrl,
            attribution = artist ?: "Public domain",
            licenceCode = licence,
            licenceUrl = licenceUrl,
            sourceUrl = sourceUrl,
            quality = assessment,
        )
    }

    private fun getJson(endpoint: String, parameters: Map<String, String>): JSONObject {
        val query = parameters.entries.joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, Charsets.UTF_8.name())}=" +
                URLEncoder.encode(value, Charsets.UTF_8.name())
        }
        val connection = URL("$endpoint?$query").openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 20_000
        connection.setRequestProperty("User-Agent", "Wildlife-Android/0.1 (reference media)")
        try {
            val code = connection.responseCode
            if (code !in 200..299) error("Wikimedia returned HTTP $code")
            return JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    private fun JSONObject.metadataValue(key: String): String? = optJSONObject(key)
        ?.optString("value")?.trim()?.takeIf(String::isNotBlank)

    private fun plainText(value: String): String = Html.fromHtml(
        value,
        Html.FROM_HTML_MODE_LEGACY,
    ).toString().replace(Regex("\\s+"), " ").trim()

    internal fun qualityAssessment(value: String?): CuratedPhotoQuality? {
        val normalized = value?.lowercase().orEmpty()
        return when {
            "featured" in normalized -> CuratedPhotoQuality.FEATURED
            "quality" in normalized -> CuratedPhotoQuality.QUALITY
            else -> null
        }
    }

    internal fun normalizedLicence(value: String?): String? {
        val normalized = value?.lowercase()?.replace('_', ' ')
            ?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
        return when {
            "-nc" in normalized || " nc" in normalized ||
                "-nd" in normalized || " nd" in normalized -> null
            normalized.startsWith("cc by-sa") || normalized.startsWith("cc-by-sa") -> "cc-by-sa"
            normalized.startsWith("cc by") || normalized.startsWith("cc-by") -> "cc-by"
            normalized.startsWith("cc0") -> "cc0"
            normalized == "public domain" || normalized.startsWith("pd-") -> "pdm"
            else -> null
        }
    }

    internal fun normalizedArticleUri(value: String): URI? = runCatching {
        URI(value.trim().replace(" ", "%20"))
    }.getOrNull()

    companion object {
        private const val COMMONS_API = "https://commons.wikimedia.org/w/api.php"
    }
}

data class CuratedReferencePhoto(
    val url: String,
    val attribution: String,
    val licenceCode: String,
    val licenceUrl: String?,
    val sourceUrl: String,
    val quality: CuratedPhotoQuality,
)

enum class CuratedPhotoQuality {
    FEATURED,
    QUALITY,
}
