package com.wildlife.feasibility.ui.screens.explore

import com.wildlife.feasibility.CatalogueSnapshot
import com.wildlife.feasibility.CatalogueSpecies
import com.wildlife.feasibility.SyncedObservation
import com.wildlife.feasibility.TaxonDetails
import com.wildlife.feasibility.ui.components.SpeciesCardStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExploreProjectionTest {
    @Test
    fun `collection state is matched by species taxon and retains scientific name`() {
        val entries = ExploreProjection.entries(snapshot("cc-by"), listOf(observation()))

        assertEquals(1, entries.size)
        assertEquals("Erithacus rubecula", entries.single().card.supportingText)
        assertEquals(SpeciesCardStatus.RESEARCH_GRADE, entries.single().card.status)
        assertEquals("https://example.test/personal.jpg", entries.single().card.photoUrl)
    }

    @Test
    fun `catalogue images remain hidden unless conservatively approved`() {
        val attributed = ExploreProjection.entries(snapshot("cc-by"), emptyList()).single()
        val publicDomain = ExploreProjection.entries(snapshot("cc0"), emptyList()).single()

        assertNull(attributed.card.photoUrl)
        assertEquals("https://example.test/catalogue.jpg", publicDomain.card.photoUrl)
    }

    @Test
    fun `missing species card receives the cached silhouette`() {
        val entry = ExploreProjection.entries(snapshot("cc-by"), emptyList()).single()

        assertEquals("https://example.test/robin.png", entry.card.silhouetteUrl)
    }

    @Test
    fun `closer detail silhouette replaces the broad catalogue fallback`() {
        val detail = TaxonDetails(
            taxonId = 42, scientificName = "Erithacus rubecula", commonName = null,
            taxonGroup = "Aves", familyName = "Muscicapidae", wikipediaSummary = null,
            wikipediaUrl = null, conservationStatus = null, conservationAuthority = null,
            conservationUrl = null, photoUrl = null, photoAttribution = null,
            photoLicenseCode = null, silhouetteUrl = "https://example.test/family.png",
            silhouetteSourceUrl = "https://example.test/source", silhouetteAttribution = "Artist",
            silhouetteLicenseCode = "cc-by-3.0", silhouetteLicenseUrl = "https://example.test/license",
            silhouetteTaxonName = "Muscicapidae", silhouetteMatchRank = "family", updatedAtMs = 0,
        )

        val entry = ExploreProjection.entries(
            snapshot("cc-by"), emptyList(), mapOf(42L to detail),
        ).single()

        assertEquals("https://example.test/family.png", entry.card.silhouetteUrl)
    }

    private fun snapshot(licence: String) = CatalogueSnapshot(
        regionKey = "catalonia",
        placeId = 12997,
        version = "test",
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
                silhouetteUrl = "https://example.test/robin.png",
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
        observedAtMs = 0,
        latitude = null,
        longitude = null,
        obscured = false,
        createdAtMs = 0,
        qualityGrade = "research",
        photoUrl = "https://example.test/personal.jpg",
        confirmed = false,
    )
}
