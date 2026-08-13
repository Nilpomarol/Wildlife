package com.wildlife.feasibility

object CataloguePhotoPolicy {
    private val detailLicences = setOf("pdm", "cc0", "cc0-1.0", "cc-by", "cc-by-sa")

    fun cardUrl(species: CatalogueSpecies): String? {
        val licence = normalizedLicence(species)
        return species.photoUrl?.takeIf { licence == "cc0" || licence == "cc0-1.0" }
    }

    fun detailUrl(species: CatalogueSpecies): String? {
        val licence = normalizedLicence(species) ?: return null
        if (licence !in detailLicences) return null
        if (!licence.startsWith("cc0") && species.photoAttribution.isNullOrBlank()) return null
        return species.photoUrl
    }

    fun attribution(species: CatalogueSpecies): String? {
        if (detailUrl(species) == null) return null
        val licence = normalizedLicence(species)?.uppercase() ?: return null
        return species.photoAttribution?.trim()?.takeIf(String::isNotBlank)?.let { attribution ->
            val spacedLicence = licence.replace('-', ' ')
            if (
                attribution.contains(licence, ignoreCase = true) ||
                attribution.contains(spacedLicence, ignoreCase = true)
            ) {
                attribution
            } else {
                "$attribution · $licence"
            }
        } ?: licence
    }

    fun detailUrl(details: TaxonDetails): String? = detailUrl(
        details.photoUrl, details.photoAttribution, details.photoLicenseCode,
    )

    fun attribution(details: TaxonDetails): String? = attribution(
        details.photoUrl, details.photoAttribution, details.photoLicenseCode,
    )

    private fun normalizedLicence(species: CatalogueSpecies): String? =
        species.photoLicenseCode?.trim()?.lowercase()?.takeIf(String::isNotBlank)

    private fun detailUrl(url: String?, attribution: String?, licenceCode: String?): String? {
        val licence = licenceCode?.trim()?.lowercase()?.takeIf(String::isNotBlank) ?: return null
        if (licence !in detailLicences) return null
        if (!licence.startsWith("cc0") && attribution.isNullOrBlank()) return null
        return url
    }

    private fun attribution(url: String?, attribution: String?, licenceCode: String?): String? {
        if (detailUrl(url, attribution, licenceCode) == null) return null
        val licence = licenceCode?.trim()?.uppercase() ?: return null
        return attribution?.trim()?.takeIf(String::isNotBlank)?.let { value ->
            if (value.contains(licence, ignoreCase = true)) value else "$value · $licence"
        } ?: licence
    }
}
