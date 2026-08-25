package com.wildlife.feasibility.ui.screens.speciesdetail

import com.wildlife.feasibility.SyncedObservation
import com.wildlife.feasibility.TaxonDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeciesDetailProjectionTest {
    @Test
    fun `reference photo drives hero while personal photo remains in history`() {
        val state = build(details(photoUrl = "file:///reference.jpg"), listOf(observation()))

        assertEquals("European robin", state.commonName)
        assertEquals("file:///reference.jpg", state.heroPhotoUrl)
        assertTrue(state.heroFromCatalogue)
        assertTrue(state.researchGrade)
        assertEquals("https://example.test/personal.jpg", state.observations.single().photoUrl)
    }

    @Test
    fun `personal photo becomes hero when no reusable reference exists`() {
        val state = build(details(photoRecoveryStatus = "no_compatible_photo"), listOf(observation()))

        assertEquals("https://example.test/personal.jpg", state.heroPhotoUrl)
        assertFalse(state.heroFromCatalogue)
    }

    @Test
    fun `cached off catalogue details are projected without a regional snapshot`() {
        val state = build(details(silhouetteUrl = "file:///robin.png"), emptyList())

        assertEquals("Muscicapidae", state.familyName)
        assertEquals("lc", state.conservationStatus)
        assertEquals("file:///robin.png", state.silhouetteUrl)
    }

    @Test
    fun `wikimedia source and missing-photo state stay explicit`() {
        val wikimedia = build(
            details(
                photoUrl = "file:///reference.jpg",
                photoSourceUrl = "https://commons.wikimedia.org/wiki/File:Robin.jpg",
                photoRecoveryStatus = "wikimedia_featured",
            ),
            emptyList(),
        )
        val missing = build(details(photoRecoveryStatus = "no_compatible_photo"), emptyList())

        assertEquals("View image on Wikimedia Commons", wikimedia.referencePhotoSourceLabel)
        assertEquals("No reusable reference photo is available.", missing.mediaMessage)
        assertNull(missing.heroPhotoUrl)
        assertEquals("birds", missing.fallbackSilhouetteGroup)
        assertFalse(missing.mediaRetryAvailable)
    }

    @Test
    fun `published media failure remains retryable`() {
        val state = build(details(photoRecoveryStatus = "wikimedia_commons"), emptyList())

        assertTrue(state.mediaRetryAvailable)
    }

    private fun build(details: TaxonDetails, observations: List<SyncedObservation>) =
        SpeciesDetailProjection.build(42, null, observations, details)

    private fun observation() = SyncedObservation(
        id = 1, uuid = "uuid", taxonId = 42, taxonRank = "species",
        collectionTaxonId = 42, collectionTaxonRank = "species", label = "European robin",
        observedAtMs = 100, latitude = null, longitude = null, obscured = false,
        createdAtMs = 100, qualityGrade = "research",
        photoUrl = "https://example.test/personal.jpg", confirmed = true,
    )

    private fun details(
        photoUrl: String? = null,
        silhouetteUrl: String? = null,
        photoSourceUrl: String? = null,
        photoRecoveryStatus: String? = null,
    ) = TaxonDetails(
        taxonId = 42, scientificName = "Erithacus rubecula", commonName = "European robin",
        taxonGroup = "Aves", familyName = "Muscicapidae", wikipediaSummary = "A small bird.",
        wikipediaUrl = "https://example.test/wiki", conservationStatus = "lc",
        conservationAuthority = "IUCN Red List", conservationUrl = "https://example.test/iucn",
        photoUrl = photoUrl, photoAttribution = photoUrl?.let { "Open photographer" },
        photoLicenseCode = photoUrl?.let { "cc-by" }, silhouetteUrl = silhouetteUrl,
        silhouetteSourceUrl = silhouetteUrl?.let { "https://example.test/source" },
        silhouetteAttribution = silhouetteUrl?.let { "Artist" },
        silhouetteLicenseCode = silhouetteUrl?.let { "cc-by-3.0" },
        silhouetteLicenseUrl = silhouetteUrl?.let { "https://example.test/license" },
        silhouetteTaxonName = silhouetteUrl?.let { "Erithacus rubecula" },
        silhouetteMatchRank = silhouetteUrl?.let { "species" }, updatedAtMs = 0,
        photoSourceUrl = photoSourceUrl, photoRecoveryStatus = photoRecoveryStatus,
        photoLocalUri = photoUrl,
    )
}
