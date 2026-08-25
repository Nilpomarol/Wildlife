package com.wildlife.feasibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CataloguePhotoPolicyTest {
    @Test
    fun `cc0 photo without a creator is not reusable`() {
        val details = details(licence = "cc0", attribution = null)

        assertNull(CataloguePhotoPolicy.cardUrl(details.photoUrl, details.photoAttribution, details.photoLicenseCode))
        assertNull(CataloguePhotoPolicy.detailUrl(details))
        assertNull(CataloguePhotoPolicy.attribution(details))
    }

    @Test
    fun `attributed cc0 photo remains reusable`() {
        val details = details(licence = "cc0", attribution = "Open photographer")

        assertEquals(
            "https://example.test/photo.jpg",
            CataloguePhotoPolicy.cardUrl(details.photoUrl, details.photoAttribution, details.photoLicenseCode),
        )
        assertEquals(
            "Open photographer / CC0",
            CataloguePhotoPolicy.attribution(details),
        )
    }

    @Test
    fun `existing spaced licence text is not duplicated in a credit`() {
        val details = details(attribution = "Photographer, some rights reserved (CC BY)")

        assertEquals(
            "Photographer, some rights reserved (CC BY)",
            CataloguePhotoPolicy.attribution(details),
        )
    }

    private fun details(attribution: String?, licence: String = "cc-by") = TaxonDetails(
        taxonId = 42,
        scientificName = "Erithacus rubecula",
        commonName = "European robin",
        taxonGroup = "Aves",
        familyName = "Muscicapidae",
        wikipediaSummary = null,
        wikipediaUrl = null,
        conservationStatus = null,
        conservationAuthority = null,
        conservationUrl = null,
        photoUrl = "https://example.test/photo.jpg",
        photoAttribution = attribution,
        photoLicenseCode = licence,
        silhouetteUrl = null,
        silhouetteSourceUrl = null,
        silhouetteAttribution = null,
        silhouetteLicenseCode = null,
        silhouetteLicenseUrl = null,
        silhouetteTaxonName = null,
        silhouetteMatchRank = null,
        updatedAtMs = 0,
    )
}
