package com.wildlife.feasibility

object CataloguePhotoPolicy {
    private val detailLicences = setOf("pdm", "cc0", "cc-by", "cc-by-sa")

    /**
     * The card rule for an externally sourced photo — currently the taxon default photo on a
     * nearby result.
     *
     * CC0 only, and only with an attribution string. Cards appear in
     * grids and carousels with no room for a credit line, so anything short of a public-domain
     * dedication stays out and the caller falls back to the bundled silhouette.
     */
    fun cardUrl(url: String?, attribution: String?, licenceCode: String?): String? {
        val licence = licenceCode?.trim()?.lowercase()?.takeIf(String::isNotBlank) ?: return null
        if (licence != "cc0") return null
        if (attribution.isNullOrBlank()) return null
        return url?.takeIf(String::isNotBlank)
    }

    fun detailUrl(details: TaxonDetails): String? = detailUrl(
        details.photoUrl, details.photoAttribution, details.photoLicenseCode,
    )

    fun attribution(details: TaxonDetails): String? = attribution(
        details.photoUrl, details.photoAttribution, details.photoLicenseCode,
    )

    /**
     * A photo that may be shown **as long as its credit is shown with it**.
     *
     * Wider than [cardUrl] on purpose: the CC0-only bar exists for surfaces with nowhere to
     * put a credit, and applying it where a credit *does* fit throws away almost every
     * photograph for no benefit. iNaturalist default photos are overwhelmingly CC-BY, so a
     * CC0-only rule means a wall of silhouettes.
     *
     * A caller using this **must** render [creditedAttribution] next to the image.
     */
    fun creditedUrl(url: String?, attribution: String?, licenceCode: String?): String? =
        detailUrl(url, attribution, licenceCode)

    /** The credit line that must accompany a [creditedUrl] image. */
    fun creditedAttribution(url: String?, attribution: String?, licenceCode: String?): String? =
        attribution(url, attribution, licenceCode)

    /**
     * The credit shortened to fit one narrow line, as "Jorg Hempel / CC BY-SA".
     *
     * Necessary rather than cosmetic. iNaturalist credits read
     * "(c) Name, some rights reserved (CC BY-SA)", and letting a 132dp tile ellipsise that
     * yields "(c) Name, ..." - which drops the licence and is therefore a *worse* attribution
     * than this, not a shorter one. Keeping the author and the licence and discarding only
     * the boilerplate between them credits more completely in less space.
     */
    fun compactCredit(attribution: String?, licenceCode: String?): String? {
        val licence = displayLicence(licenceCode) ?: return null
        val raw = attribution?.trim()?.takeIf(String::isNotBlank) ?: return null
        val author = raw
            .removePrefix("(c)").removePrefix("(C)").removePrefix("©")
            .trim()
            .substringBefore(',')
            .substringBefore(" some rights reserved")
            .substringBefore(" no rights reserved")
            .trim()
            .takeIf(String::isNotBlank)
            ?: return licence
        return "$author / $licence"
    }
    private fun detailUrl(url: String?, attribution: String?, licenceCode: String?): String? {
        val licence = licenceCode?.trim()?.lowercase()?.takeIf(String::isNotBlank) ?: return null
        if (licence !in detailLicences) return null
        if (attribution.isNullOrBlank()) return null
        return url
    }

    private fun attribution(url: String?, attribution: String?, licenceCode: String?): String? {
        if (detailUrl(url, attribution, licenceCode) == null) return null
        val licence = displayLicence(licenceCode) ?: return null
        return attribution?.trim()?.takeIf(String::isNotBlank)?.let { value ->
            val spacedLicence = licence.replace('-', ' ')
            if (
                value.contains(licence, ignoreCase = true) ||
                value.contains(spacedLicence, ignoreCase = true)
            ) {
                value
            } else {
                "$value / $licence"
            }
        }
    }

    private fun displayLicence(value: String?): String? = when (value?.trim()?.lowercase()) {
        "pdm" -> "Public Domain"
        "cc0" -> "CC0"
        "cc-by" -> "CC BY"
        "cc-by-sa" -> "CC BY-SA"
        else -> null
    }
}
