package com.wildlife.feasibility.ui.screens.speciesdetail

import com.wildlife.feasibility.CatalogueSnapshot
import com.wildlife.feasibility.CatalogueSpecies
import com.wildlife.feasibility.SyncedObservation
import com.wildlife.feasibility.TaxonDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeciesDetailProjectionTest {
    @Test
    fun `reference photo drives hero while personal photo remains in observation history`() {
        val state = SpeciesDetailProjection.build(
            taxonId = 42,
            fallbackLabel = null,
            snapshot = snapshot("cc-by"),
            observations = listOf(observation()),
        )

        assertEquals("European robin", state.commonName)
        assertEquals("Erithacus rubecula", state.scientificName)
        assertEquals("Birds", state.taxonGroup)
        assertEquals("https://example.test/catalogue.jpg", state.heroPhotoUrl)
        assertTrue(state.heroFromCatalogue)
        assertTrue(state.researchGrade)
        assertEquals(1, state.observations.size)
        assertEquals("https://example.test/personal.jpg", state.observations.single().photoUrl)
    }

    @Test
    fun `personal photo becomes hero when no reusable reference exists`() {
        val state = SpeciesDetailProjection.build(
            taxonId = 42,
            fallbackLabel = null,
            snapshot = snapshot("cc-by-nc"),
            observations = listOf(observation()),
        )

        assertEquals("https://example.test/personal.jpg", state.heroPhotoUrl)
        assertFalse(state.heroFromCatalogue)
    }

    @Test
    fun `compatible attributed catalogue photo is available on detail`() {
        val state = SpeciesDetailProjection.build(42, null, snapshot("cc-by"), emptyList())

        assertEquals("https://example.test/catalogue.jpg", state.heroPhotoUrl)
        assertTrue(state.heroFromCatalogue)
        assertEquals("Test photographer / CC-BY", state.heroAttribution)
        assertFalse(state.observed)
    }

    @Test
    fun `noncommercial catalogue photo remains hidden`() {
        val state = SpeciesDetailProjection.build(42, null, snapshot("cc-by-nc"), emptyList())

        assertNull(state.heroPhotoUrl)
        assertNull(state.heroAttribution)
    }

    @Test
    fun `cached taxon details enrich a species outside the regional catalogue`() {
        val state = SpeciesDetailProjection.build(
            taxonId = 42,
            fallbackLabel = "Robin",
            snapshot = null,
            observations = emptyList(),
            details = TaxonDetails(
                taxonId = 42,
                scientificName = "Erithacus rubecula",
                commonName = "European robin",
                taxonGroup = "Aves",
                familyName = "Muscicapidae",
                wikipediaSummary = "A small bird.",
                wikipediaUrl = "https://example.test/wiki",
                conservationStatus = "lc",
                conservationAuthority = "IUCN Red List",
                conservationUrl = "https://example.test/iucn",
                photoUrl = null,
                photoAttribution = null,
                photoLicenseCode = null,
                silhouetteUrl = "https://example.test/robin.png",
                silhouetteSourceUrl = "https://example.test/source",
                silhouetteAttribution = "Artist",
                silhouetteLicenseCode = "cc-by-3.0",
                silhouetteLicenseUrl = "https://example.test/license",
                silhouetteTaxonName = "Erithacus rubecula",
                silhouetteMatchRank = "species",
                updatedAtMs = 0,
            ),
        )

        assertEquals("European robin", state.commonName)
        assertEquals("Muscicapidae", state.familyName)
        assertEquals("lc", state.conservationStatus)
        assertEquals("https://example.test/robin.png", state.silhouetteUrl)
    }

    @Test
    fun `recovered detail photo takes priority and keeps its direct source`() {
        val state = SpeciesDetailProjection.build(
            taxonId = 42,
            fallbackLabel = null,
            snapshot = snapshot("cc-by"),
            observations = emptyList(),
            details = details(
                photoUrl = "https://example.test/recovered.jpg",
                photoSourceUrl = "https://www.inaturalist.org/photos/7",
                photoRecoveryStatus = "recovered_observation",
            ),
        )

        assertEquals("https://example.test/recovered.jpg", state.heroPhotoUrl)
        assertEquals("https://www.inaturalist.org/photos/7", state.referencePhotoSourceUrl)
        assertEquals("recovered_observation", state.photoRecoveryStatus)
    }

    @Test
    fun `wikimedia curated photo exposes the correct source action`() {
        val state = SpeciesDetailProjection.build(
            taxonId = 42,
            fallbackLabel = null,
            snapshot = snapshot("cc-by"),
            observations = listOf(observation()),
            details = details(
                photoUrl = "https://upload.wikimedia.org/reference.jpg",
                photoSourceUrl = "https://commons.wikimedia.org/wiki/File:Robin.jpg",
                photoRecoveryStatus = "wikimedia_featured",
            ),
        )

        assertEquals("https://upload.wikimedia.org/reference.jpg", state.heroPhotoUrl)
        assertEquals("View image on Wikimedia Commons", state.referencePhotoSourceLabel)
    }

    @Test
    fun `missing compatible photo explains the silhouette fallback`() {
        val state = SpeciesDetailProjection.build(
            taxonId = 42,
            fallbackLabel = null,
            snapshot = snapshot("cc-by-nc"),
            observations = emptyList(),
            details = details(photoRecoveryStatus = "no_compatible_photo"),
        )

        assertEquals(
            "No reusable reference photo is available.",
            state.mediaMessage,
        )
    }

    private fun snapshot(licence: String) = CatalogueSnapshot(
        regionKey = "catalonia",
        placeId = 12997,
        version = "test-version",
        updatedAtMs = 0,
        provisional = true,
        species = listOf(
            CatalogueSpecies(
                taxonId = 42,
                scientificName = "Erithacus rubecula",
                commonName = "European robin",
                taxonGroup = "Aves",
                observationCount = 10,
                position = 1,
                photoUrl = "https://example.test/catalogue.jpg",
                photoAttribution = "Test photographer",
                photoLicenseCode = licence,
            ),
        ),
        cached = true,
    )

    private fun observation() = SyncedObservation(
        id = 1,
        uuid = "uuid",
        taxonId = 42,
        taxonRank = "species",
        collectionTaxonId = 42,
        collectionTaxonRank = "species",
        label = "European robin",
        observedAtMs = 100,
        latitude = null,
        longitude = null,
        obscured = false,
        createdAtMs = 100,
        qualityGrade = "research",
        photoUrl = "https://example.test/personal.jpg",
        confirmed = true,
    )

    private fun details(
        photoUrl: String? = null,
        photoSourceUrl: String? = null,
        photoRecoveryStatus: String? = null,
    ) = TaxonDetails(
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
        photoUrl = photoUrl,
        photoAttribution = photoUrl?.let { "Open photographer" },
        photoLicenseCode = photoUrl?.let { "cc-by" },
        silhouetteUrl = "https://example.test/family.png",
        silhouetteSourceUrl = "https://example.test/source",
        silhouetteAttribution = "Artist",
        silhouetteLicenseCode = "cc-by-3.0",
        silhouetteLicenseUrl = "https://example.test/license",
        silhouetteTaxonName = "Muscicapidae",
        silhouetteMatchRank = "family",
        updatedAtMs = 0,
        photoSourceUrl = photoSourceUrl,
        photoRecoveryStatus = photoRecoveryStatus,
    )
}
