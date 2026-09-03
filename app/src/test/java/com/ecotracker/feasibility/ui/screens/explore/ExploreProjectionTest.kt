package com.wildlife.feasibility.ui.screens.explore

import com.wildlife.feasibility.EncounterRarity
import com.wildlife.feasibility.CollectionSpecies
import com.wildlife.feasibility.ExtraDiscoveryContext
import com.wildlife.feasibility.InstalledRegionalTaxon
import com.wildlife.feasibility.InstalledRegionalAchievement
import com.wildlife.feasibility.NearbySpecies
import com.wildlife.feasibility.SyncedObservation
import com.wildlife.feasibility.TaxonDetails
import com.wildlife.feasibility.ui.components.SpeciesCardPhotoKind
import com.wildlife.feasibility.ui.components.SpeciesCardStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExploreProjectionTest {
    @Test
    fun `nearby discovery is scoped to published regional taxa`() {
        val catalogue = ExploreProjection.regionalEntries(listOf(taxon()), emptyList())
        val nearby = listOf(
            NearbySpecies(42, "European robin", "Erithacus rubecula", "Aves", 12),
            NearbySpecies(99, "Unscoped taxon", "Taxon example", "Plantae", 50),
        )

        assertEquals(
            listOf(42L),
            NearbyDiscoveryProjection.withinCatalogue(nearby, catalogue)
                .map(NearbySpecies::taxonId),
        )
    }

    @Test
    fun `nearby artwork uses regional personal photo then catalogue silhouette`() {
        val observedCatalogue = ExploreProjection.regionalEntries(
            listOf(taxon()), listOf(observation()), mapOf(42L to details()),
        )
        val nearby = listOf(
            NearbySpecies(
                42, "European robin", "Erithacus rubecula", "birds", 12,
                photoUrl = "https://provider.test/default.jpg",
                photoAttribution = "Provider photographer",
                photoLicenseCode = "cc-by",
            ),
        )

        val observed = NearbyDiscoveryProjection.withLocalMedia(nearby, observedCatalogue).single()
        val missing = NearbyDiscoveryProjection.withLocalMedia(
            nearby,
            ExploreProjection.regionalEntries(
                listOf(taxon()), emptyList(), mapOf(42L to details()),
            ),
        ).single()

        assertEquals("https://example.test/personal.jpg", observed.personalPhotoUrl)
        assertEquals("file:///stored/robin.jpg", observed.photoUrl)
        assertEquals("Test photographer", observed.photoAttribution)
        assertEquals("cc-by", observed.photoLicenseCode)
        assertEquals("file:///stored/robin.png", observed.silhouetteUrl)
        assertNull(missing.personalPhotoUrl)
        assertEquals("file:///stored/robin.jpg", missing.photoUrl)
        assertEquals("file:///stored/robin.png", missing.silhouetteUrl)
    }

    @Test
    fun `nearby keeps provider photo when no validated local reference is stored`() {
        val providerPhoto = NearbySpecies(
            42, "European robin", "Erithacus rubecula", "birds", 12,
            photoUrl = "https://provider.test/default.jpg",
            photoAttribution = "Provider photographer",
            photoLicenseCode = "cc-by",
        )
        val catalogue = ExploreProjection.regionalEntries(listOf(taxon()), emptyList())

        val projected = NearbyDiscoveryProjection.withLocalMedia(
            listOf(providerPhoto), catalogue,
        ).single()

        assertEquals(providerPhoto.photoUrl, projected.photoUrl)
        assertEquals(providerPhoto.photoAttribution, projected.photoAttribution)
        assertEquals(providerPhoto.photoLicenseCode, projected.photoLicenseCode)
    }

    @Test
    fun `observed species prefers personal photo and carries published identity`() {
        val entry = ExploreProjection.regionalEntries(
            listOf(taxon()), listOf(observation()), mapOf(42L to details()),
        ).single()

        assertEquals("Erithacus rubecula", entry.card.supportingText)
        assertEquals(SpeciesCardStatus.RESEARCH_GRADE, entry.card.status)
        assertEquals("https://example.test/personal.jpg", entry.card.photoUrl)
        assertEquals(SpeciesCardPhotoKind.PERSONAL, entry.card.photoKind)
        assertNull(entry.card.photoAttribution)
    }

    @Test
    fun `regional guide carries rarity and standing moved from collection`() {
        val entry = ExploreProjection.regionalEntries(
            taxa = listOf(taxon()),
            observations = listOf(observation()),
            achievements = listOf(
                InstalledRegionalAchievement("essentials", setOf(42L)),
                InstalledRegionalAchievement("icons", setOf(42L)),
            ),
        ).single()

        assertEquals(EncounterRarity.COMMON, entry.encounterRarity)
        assertEquals(true, entry.card.regionalEssential)
        assertEquals(true, entry.card.regionalIcon)
    }

    @Test
    fun `unobserved species hides reference photo but retains local silhouette`() {
        val entry = ExploreProjection.regionalEntries(
            listOf(taxon()), emptyList(), mapOf(42L to details()),
        ).single()

        assertNull(entry.card.photoUrl)
        assertEquals("file:///stored/robin.png", entry.card.silhouetteUrl)
        assertTrue(entry.card.additionalPhotoFallbacks.isEmpty())
    }

    @Test
    fun `observed species without a personal photo still uses the silhouette`() {
        val entry = ExploreProjection.regionalEntries(
            listOf(taxon()), listOf(observation().copy(photoUrl = null)), mapOf(42L to details()),
        ).single()

        assertTrue(entry.observed)
        assertNull(entry.card.photoUrl)
        assertEquals("file:///stored/robin.png", entry.card.silhouetteUrl)
    }

    @Test
    fun `extra entry keeps personal media and has no regional rarity or standing`() {
        val context = ExtraDiscoveryContext("mediterranean", "Mediterranean Europe", "2026.1")
        val entry = ExploreProjection.extraEntries(
            entries = listOf(
                CollectionSpecies(
                    key = "taxon:42",
                    taxonId = 42,
                    label = "European robin",
                    observationCount = 1,
                    rewardedObservationCount = 1,
                    latestObservationUuid = "uuid",
                    latestObservedAtMs = 1,
                    bestQualityGrade = "needs_id",
                    awaitingSpeciesIdentification = false,
                    photoUrl = "https://example.test/personal.jpg",
                    extraDiscoveryContexts = listOf(context),
                ),
            ),
            detailsByTaxon = mapOf(42L to details()),
            context = context,
        ).single()

        assertTrue(entry.observed)
        assertNull(entry.encounterRarity)
        assertFalse(entry.card.regionalEssential)
        assertFalse(entry.card.regionalIcon)
        assertEquals("Extra", entry.card.extraDiscoveryLabel)
        assertEquals(
            "Extra discovery in Mediterranean Europe, catalogue 2026.1",
            entry.card.extraDiscoveryDescription,
        )
        assertEquals(SpeciesCardStatus.OBSERVED, entry.card.status)
        assertEquals(SpeciesCardPhotoKind.PERSONAL, entry.card.photoKind)
    }

    private fun taxon() = InstalledRegionalTaxon(
        taxonId = 42, commonName = "European robin", scientificName = "Erithacus rubecula",
        taxonClass = "Aves", rarity = EncounterRarity.COMMON,
    )

    private fun details() = TaxonDetails(
        taxonId = 42, scientificName = "Erithacus rubecula", commonName = "European robin",
        taxonGroup = "Aves", familyName = "Muscicapidae", wikipediaSummary = null,
        wikipediaUrl = null, conservationStatus = null, conservationAuthority = null,
        conservationUrl = null, photoUrl = "file:///stored/robin.jpg",
        photoAttribution = "Test photographer", photoLicenseCode = "cc-by",
        silhouetteUrl = "file:///stored/robin.png", silhouetteSourceUrl = "https://example.test/source",
        silhouetteAttribution = "Artist", silhouetteLicenseCode = "cc-by-3.0",
        silhouetteLicenseUrl = "https://example.test/license", silhouetteTaxonName = "Muscicapidae",
        silhouetteMatchRank = "family", updatedAtMs = null,
        photoLocalUri = "file:///stored/robin.jpg", silhouetteLocalUri = "file:///stored/robin.png",
    )

    private fun observation() = SyncedObservation(
        id = 1, uuid = "uuid", taxonId = 42, taxonRank = "species",
        collectionTaxonId = 42, collectionTaxonRank = "species", label = "European robin",
        observedAtMs = 0, latitude = null, longitude = null, obscured = false,
        createdAtMs = 0, qualityGrade = "research",
        photoUrl = "https://example.test/personal.jpg", confirmed = false,
    )
}
