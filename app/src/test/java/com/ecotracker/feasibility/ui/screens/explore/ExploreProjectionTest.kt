package com.wildlife.feasibility.ui.screens.explore

import com.wildlife.feasibility.CatalogueSnapshot
import com.wildlife.feasibility.CatalogueSpecies
import com.wildlife.feasibility.NearbySpecies
import com.wildlife.feasibility.SyncedObservation
import com.wildlife.feasibility.TaxonDetails
import com.wildlife.feasibility.ui.components.SpeciesCardStatus
import com.wildlife.feasibility.ui.components.SpeciesCardPhotoKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExploreProjectionTest {
    @Test
    fun `nearby discovery excludes taxa outside the provisional guide`() {
        val catalogue = ExploreProjection.entries(snapshot("cc-by"), emptyList())
        val nearby = listOf(
            NearbySpecies(42, "European robin", "Erithacus rubecula", "Aves", 12),
            NearbySpecies(99, "Unscoped taxon", "Taxon example", "Plantae", 50),
        )

        val scoped = NearbyDiscoveryProjection.withinCatalogue(nearby, catalogue)

        assertEquals(listOf(42L), scoped.map(NearbySpecies::taxonId))
    }

    @Test
    fun `collection state is matched by species taxon and retains scientific name`() {
        val entries = ExploreProjection.entries(snapshot("cc-by"), listOf(observation()))

        assertEquals(1, entries.size)
        assertEquals("Erithacus rubecula", entries.single().card.supportingText)
        assertEquals(SpeciesCardStatus.RESEARCH_GRADE, entries.single().card.status)
        assertEquals("https://example.test/personal.jpg", entries.single().card.photoUrl)
        assertEquals(SpeciesCardPhotoKind.PERSONAL, entries.single().card.photoKind)
    }

    @Test
    fun `observed card can recover from personal photo to attributed reference`() {
        val entry = ExploreProjection.entries(snapshot("cc0"), listOf(observation())).single()

        assertEquals("https://example.test/personal.jpg", entry.card.photoUrl)
        assertEquals("https://example.test/catalogue.jpg", entry.card.photoFallbackUrl)
        assertEquals(SpeciesCardPhotoKind.REFERENCE, entry.card.photoFallbackKind)
        assertTrue(entry.card.photoFallbackAttribution.orEmpty().contains("Test photographer"))
    }

    @Test
    fun `unobserved species always hide stock photos`() {
        val attributed = ExploreProjection.entries(snapshot("cc-by"), emptyList()).single()
        val publicDomain = ExploreProjection.entries(snapshot("cc0"), emptyList()).single()

        assertNull(attributed.card.photoUrl)
        assertNull(publicDomain.card.photoUrl)
        assertNull(attributed.card.photoFallbackUrl)
        assertNull(publicDomain.card.photoFallbackUrl)
        assertTrue(attributed.card.additionalPhotoFallbacks.isEmpty())
        assertTrue(publicDomain.card.additionalPhotoFallbacks.isEmpty())
    }

    @Test
    fun `missing species card receives the cached silhouette`() {
        val entry = ExploreProjection.entries(snapshot("cc-by"), emptyList()).single()

        assertEquals("https://example.test/robin.png", entry.card.silhouetteUrl)
    }

    @Test
    fun `stored media is primary and remote provenance URL remains a recovery fallback`() {
        val base = snapshot("cc0")
        val stored = base.copy(
            species = base.species.map {
                it.copy(
                    photoLocalUri = "file:///stored/robin.jpg",
                    silhouetteLocalUri = "file:///stored/robin.png",
                )
            },
        )

        val observed = ExploreProjection.entries(
            stored,
            listOf(observation().copy(photoUrl = null)),
        ).single()
        val unseen = ExploreProjection.entries(stored, emptyList()).single()

        assertEquals("file:///stored/robin.jpg", observed.card.photoUrl)
        assertEquals(SpeciesCardPhotoKind.REFERENCE, observed.card.photoKind)
        assertEquals("https://example.test/catalogue.jpg", observed.card.photoFallbackUrl)
        assertNull(unseen.card.photoUrl)
        assertEquals("file:///stored/robin.png", unseen.card.silhouetteUrl)
        assertEquals("https://example.test/robin.png", unseen.card.silhouetteFallbackUrl)
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
        assertEquals("family", entry.card.silhouetteMatchRank)
    }

    @Test
    fun `personal observation takes priority over cached Wikimedia reference`() {
        val detail = TaxonDetails(
            taxonId = 42, scientificName = "Erithacus rubecula", commonName = null,
            taxonGroup = "Aves", familyName = "Muscicapidae", wikipediaSummary = null,
            wikipediaUrl = "https://en.wikipedia.org/wiki/Erithacus_rubecula",
            conservationStatus = null, conservationAuthority = null, conservationUrl = null,
            photoUrl = "https://upload.wikimedia.org/reference.jpg",
            photoAttribution = "Francis C. Franklin", photoLicenseCode = "cc-by-sa",
            silhouetteUrl = null, silhouetteSourceUrl = null, silhouetteAttribution = null,
            silhouetteLicenseCode = null, silhouetteLicenseUrl = null,
            silhouetteTaxonName = null, silhouetteMatchRank = null, updatedAtMs = 0,
            photoSourceUrl = "https://commons.wikimedia.org/wiki/File:Robin.jpg",
            photoRecoveryStatus = "wikimedia_featured",
        )

        val entry = ExploreProjection.entries(
            snapshot("cc-by"), listOf(observation()), mapOf(42L to detail),
        ).single()

        assertEquals("https://example.test/personal.jpg", entry.card.photoUrl)
        assertNull(entry.card.photoAttribution)
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
