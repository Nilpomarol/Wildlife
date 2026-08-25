package com.wildlife.feasibility

import android.text.Html
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder

class WikimediaCommonsClient(
    private val http: ReadOnlyHttpClient = ReadOnlyHttpClient(),
) {
    fun curatedPhoto(
        wikipediaUrl: String?,
        scientificName: String? = null,
        iNaturalistTaxonId: Long? = null,
        excludedSourceUrls: Set<String> = emptySet(),
    ): CuratedReferencePhoto? {
        val linkedArticle = wikipediaUrl?.let(::normalizedArticleUri)
        val linkedResult = linkedArticle?.let {
            curatedPhotoFromArticle(it, excludedSourceUrls)
        }
        if (linkedResult != null) return linkedResult

        val verifiedFallback = scientificName?.takeIf(String::isNotBlank)?.let {
            wikidataFallback(it, iNaturalistTaxonId)
        } ?: return null
        val candidates = buildList {
            verifiedFallback.fileName?.let(::add)
            verifiedFallback.article?.let { article -> addAll(articleImageNames(article)) }
        }.distinct()
        return bestImage(candidates, verifiedFallback.article, excludedSourceUrls)
    }

    private fun curatedPhotoFromArticle(
        article: URI,
        excludedSourceUrls: Set<String>,
    ): CuratedReferencePhoto? =
        bestImage(articleImageNames(article), article, excludedSourceUrls)

    private fun articleImageNames(article: URI): List<String> {
        val host = article.host?.takeIf { it.endsWith(".wikipedia.org") } ?: return emptyList()
        val encodedTitle = article.rawPath.substringAfter("/wiki/", "")
        if (encodedTitle.isBlank()) return emptyList()
        val title = URLDecoder.decode(encodedTitle, Charsets.UTF_8.name()).replace('_', ' ')
        val articleApi = "https://$host/w/api.php"
        val names = linkedSetOf<String>()
        var continuation: String? = null
        do {
            val parameters = linkedMapOf(
                "action" to "query",
                "format" to "json",
                "formatversion" to "2",
                "redirects" to "1",
                "prop" to "pageimages|images",
                "piprop" to "name",
                "imlimit" to "max",
                "titles" to title,
            )
            continuation?.let { parameters["imcontinue"] = it }
            val page = getJson(articleApi, parameters)
            val result = page.optJSONObject("query")?.optJSONArray("pages")?.optJSONObject(0)
            result?.optString("pageimage")?.takeIf(String::isNotBlank)?.let(names::add)
            result?.optJSONArray("images")?.let { images ->
                for (index in 0 until images.length()) {
                    images.optJSONObject(index)?.optString("title")
                        ?.takeIf(::isRasterFile)?.let(names::add)
                    if (names.size >= MAX_ARTICLE_IMAGES) break
                }
            }
            continuation = page.optJSONObject("continue")?.optString("imcontinue")
                ?.takeIf(String::isNotBlank)
        } while (continuation != null && names.size < MAX_ARTICLE_IMAGES)
        return names.toList()
    }

    private fun bestImage(
        fileNames: List<String>,
        article: URI?,
        excludedSourceUrls: Set<String>,
    ): CuratedReferencePhoto? {
        if (fileNames.isEmpty()) return null
        val commons = fileNames.chunked(20).flatMap { imageInfos(COMMONS_API, it) }
        val missing = fileNames.filterNot { name ->
            commons.any { it.fileName == fileKey(name) }
        }
        val local = article?.host?.let { host ->
            missing.chunked(20).flatMap { imageInfos("https://$host/w/api.php", it) }
        }.orEmpty()
        val excludedKeys = excludedSourceUrls.mapTo(hashSetOf()) { it.normalizedSourceKey() }
        return (commons + local)
            .filterNot { it.photo.sourceUrl.normalizedSourceKey() in excludedKeys }
            .maxByOrNull { result ->
            when (result.photo.quality) {
                CuratedPhotoQuality.FEATURED -> 2
                CuratedPhotoQuality.QUALITY -> 1
            }
        }?.photo
    }

    private fun imageInfos(apiUrl: String, fileNames: List<String>): List<ImageResult> {
        if (fileNames.isEmpty()) return emptyList()
        val root = getJson(
            apiUrl,
            linkedMapOf(
                "action" to "query",
                "format" to "json",
                "formatversion" to "2",
                "prop" to "imageinfo",
                "titles" to fileNames.joinToString("|") { normalizedFile(it) },
                "iiprop" to "url|extmetadata",
                "iiurlwidth" to "1600",
                "iiextmetadatafilter" to
                    "LicenseShortName|LicenseUrl|Artist|Credit|Assessments",
            ),
        )
        val pages = root.optJSONObject("query")?.optJSONArray("pages") ?: return emptyList()
        return buildList {
            for (index in 0 until pages.length()) {
                val page = pages.optJSONObject(index) ?: continue
                val info = page.optJSONArray("imageinfo")?.optJSONObject(0) ?: continue
                photoFromInfo(info)?.let { add(ImageResult(fileKey(page.optString("title")), it)) }
            }
        }
    }

    private fun photoFromInfo(info: JSONObject): CuratedReferencePhoto? {
        val metadata = info.optJSONObject("extmetadata") ?: return null
        val rawAssessment = metadata.metadataValue("Assessments")
        val rawLicence = metadata.metadataValue("LicenseShortName")
        val assessment = qualityAssessment(rawAssessment) ?: return null
        val licence = normalizedLicence(rawLicence) ?: return null
        val rawArtist = metadata.metadataValue("Artist") ?: metadata.metadataValue("Credit")
        val artist = rawArtist?.let(::plainText)?.takeIf(String::isNotBlank) ?: return null
        val imageUrl = info.optString("thumburl").takeIf(String::isNotBlank)
            ?: info.optString("url").takeIf(String::isNotBlank) ?: return null
        val sourceUrl = info.optString("descriptionurl").takeIf(String::isNotBlank) ?: return null
        val licenceUrl = metadata.metadataValue("LicenseUrl")
        return CuratedReferencePhoto(
            url = imageUrl,
            attribution = artist,
            licenceCode = licence,
            licenceUrl = licenceUrl,
            sourceUrl = sourceUrl,
            quality = assessment,
        )
    }

    private fun wikidataFallback(
        scientificName: String,
        iNaturalistTaxonId: Long?,
    ): WikidataFallback? {
        val search = getJson(
            WIKIDATA_API,
            linkedMapOf(
                "action" to "wbsearchentities",
                "format" to "json",
                "language" to "en",
                "type" to "item",
                "limit" to "10",
                "search" to scientificName,
            ),
        )
        val results = search.optJSONArray("search") ?: return null
        val ids = buildList {
            for (index in 0 until results.length()) {
                results.optJSONObject(index)?.optString("id")
                    ?.takeIf(String::isNotBlank)?.let(::add)
            }
        }
        if (ids.isEmpty()) return null
        val entities = getJson(
            WIKIDATA_API,
            linkedMapOf(
                "action" to "wbgetentities",
                "format" to "json",
                "ids" to ids.joinToString("|"),
                "props" to "claims|sitelinks",
            ),
        ).optJSONObject("entities") ?: return null
        val candidates = ids.mapNotNull { id -> entities.optJSONObject(id) }.map { entity ->
            WikidataTaxonCandidate(
                entity = entity,
                scientificName = entity.claimString("P225"),
                iNaturalistTaxonId = entity.claimString("P3151"),
            )
        }
        val entity = verifiedTaxonEntity(candidates, scientificName, iNaturalistTaxonId)
            ?: return null
        val fileName = entity.claimString("P18")
        val sitelinks = entity.optJSONObject("sitelinks")
        val site = sequenceOf("enwiki", "cawiki", "eswiki")
            .firstNotNullOfOrNull { key -> sitelinks?.optJSONObject(key)?.let { key to it } }
            ?: sitelinks?.keys()?.asSequence()?.firstOrNull { it.endsWith("wiki") }
                ?.let { key -> sitelinks.optJSONObject(key)?.let { key to it } }
        val article = site?.let { (key, value) ->
            val language = key.removeSuffix("wiki")
            value.optString("title").takeIf(String::isNotBlank)?.let { title ->
                normalizedArticleUri(
                    "https://$language.wikipedia.org/wiki/${title.replace(' ', '_')}",
                )
            }
        }
        return WikidataFallback(fileName, article)
    }

    internal fun verifiedTaxonEntity(
        candidates: List<WikidataTaxonCandidate>,
        scientificName: String,
        iNaturalistTaxonId: Long?,
    ): JSONObject? {
        val exactId = iNaturalistTaxonId?.let { expected ->
            candidates.firstOrNull { it.iNaturalistTaxonId == expected.toString() }
        }
        if (exactId != null) return exactId.entity
        return candidates.filter {
            it.scientificName?.equals(scientificName, ignoreCase = true) == true
        }.singleOrNull()?.entity
    }

    internal fun JSONObject.claimString(property: String): String? {
        val value = optJSONObject("claims")?.optJSONArray(property)?.optJSONObject(0)
            ?.optJSONObject("mainsnak")?.optJSONObject("datavalue")?.opt("value")
        return value?.toString()?.trim()?.takeIf(String::isNotBlank)
    }

    private fun getJson(endpoint: String, parameters: Map<String, String>): JSONObject {
        return http.getJson(urlWithParameters(endpoint, parameters), REQUEST_POLICY)
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

    private fun isRasterFile(value: String): Boolean = value.substringAfterLast('.')
        .lowercase() in setOf("jpg", "jpeg", "png", "webp", "tif", "tiff")

    private fun normalizedFile(value: String): String =
        if (value.startsWith("File:")) value else "File:$value"

    private fun fileKey(value: String): String = normalizedFile(value)
        .replace('_', ' ').lowercase()

    private fun String.normalizedSourceKey(): String = trim().removeSuffix("/").lowercase()

    private data class ImageResult(val fileName: String, val photo: CuratedReferencePhoto)
    private data class WikidataFallback(val fileName: String?, val article: URI?)

    companion object {
        private const val COMMONS_API = "https://commons.wikimedia.org/w/api.php"
        private const val WIKIDATA_API = "https://www.wikidata.org/w/api.php"
        private const val MAX_ARTICLE_IMAGES = 200
        private val REQUEST_POLICY = ReadOnlyRequestPolicy(
            serviceName = "Wikimedia",
            userAgent = WildlifeNetworkIdentity.REFERENCE_MEDIA_USER_AGENT,
            rateLimitKey = "wikimedia",
            minimumIntervalMs = 1_000L,
        )
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

internal data class WikidataTaxonCandidate(
    val entity: JSONObject,
    val scientificName: String?,
    val iNaturalistTaxonId: String?,
)
