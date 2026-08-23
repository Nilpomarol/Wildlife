package com.wildlife.feasibility.ui.screens.collection

import com.wildlife.feasibility.CollectionSpecies
import com.wildlife.feasibility.EncounterRarity
import com.wildlife.feasibility.RegionalPrestige
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The combinations that the old single-enum filter could not express.
 *
 * Each axis has its own narrow test, but the point of the split is the AND behaviour, so
 * most of these exercise two axes at once.
 */
class CollectionFiltersTest {

    private fun species(
        label: String = "Test species",
        observations: Int = 0,
        grade: String = "",
        awaiting: Boolean = false,
        rarity: EncounterRarity = EncounterRarity.COMMON,
        essential: Boolean = false,
        icon: Boolean = false,
        legend: Boolean = false,
        group: String = "birds",
    ) = CollectionSpecies(
        key = "taxon:1",
        taxonId = 1L,
        label = label,
        observationCount = observations,
        rewardedObservationCount = 0,
        latestObservationUuid = "",
        latestObservedAtMs = 0,
        bestQualityGrade = grade,
        awaitingSpeciesIdentification = awaiting,
        photoUrl = null,
        regionalEssential = essential,
        regionalIcon = icon,
        scientificName = null,
        encounterRarity = rarity,
        regionalPrestige = if (legend) RegionalPrestige.LEGENDARY else RegionalPrestige.STANDARD,
        taxonGroup = group,
    )

    @Test
    fun `no filters match everything`() {
        val filters = CollectionFilters.None
        assertTrue(filters.matches(species()))
        assertTrue(filters.matches(species(observations = 3, legend = true)))
        assertEquals(0, filters.activeCount)
        assertFalse(filters.isActive)
    }

    /** The query the old flat enum could not ask: standing AND status together. */
    @Test
    fun `missing essentials excludes essentials already recorded`() {
        val filters = CollectionFilters(
            status = StatusFilter.MISSING,
            standing = StandingFilter.ESSENTIALS,
        )
        val missingEssential = species(essential = true, observations = 0)
        val recordedEssential = species(essential = true, observations = 2)
        val missingOrdinary = species(essential = false, observations = 0)

        assertTrue(filters.matches(missingEssential))
        assertFalse(filters.matches(recordedEssential))
        assertFalse(filters.matches(missingOrdinary))
    }

    @Test
    fun `missing legends excludes legends already recorded`() {
        val filters = CollectionFilters(
            status = StatusFilter.MISSING,
            standing = StandingFilter.LEGENDS,
        )
        assertTrue(filters.matches(species(legend = true, observations = 0)))
        assertFalse(filters.matches(species(legend = true, observations = 1)))
        assertFalse(filters.matches(species(legend = false, observations = 0)))
    }

    @Test
    fun `awaiting id narrows within a group`() {
        val filters = CollectionFilters(
            status = StatusFilter.AWAITING,
            group = SpeciesGroup.BIRDS,
        )
        assertTrue(filters.matches(species(awaiting = true, observations = 1, group = "birds")))
        assertFalse(filters.matches(species(awaiting = true, observations = 1, group = "mammals")))
        assertFalse(filters.matches(species(awaiting = false, observations = 1, group = "birds")))
    }

    @Test
    fun `confirmed requires a research grade record`() {
        val filters = CollectionFilters(status = StatusFilter.CONFIRMED)
        assertTrue(filters.matches(species(observations = 1, grade = "research")))
        assertFalse(filters.matches(species(observations = 1, grade = "needs_id")))
        assertFalse(filters.matches(species(observations = 0, grade = "")))
    }

    /** Each tier is its own option now, so Rare must not also match Very rare. */
    @Test
    fun `rarity tiers do not overlap`() {
        val rare = CollectionFilters(rarity = RarityFilter.RARE)
        assertTrue(rare.matches(species(rarity = EncounterRarity.RARE)))
        assertFalse(rare.matches(species(rarity = EncounterRarity.VERY_RARE)))
        assertFalse(rare.matches(species(rarity = EncounterRarity.COMMON)))

        val veryRare = CollectionFilters(rarity = RarityFilter.VERY_RARE)
        assertTrue(veryRare.matches(species(rarity = EncounterRarity.VERY_RARE)))
        assertFalse(veryRare.matches(species(rarity = EncounterRarity.RARE)))
    }

    @Test
    fun `three axes combine`() {
        val filters = CollectionFilters(
            status = StatusFilter.MISSING,
            standing = StandingFilter.ICONS,
            rarity = RarityFilter.VERY_RARE,
        )
        val match = species(observations = 0, icon = true, rarity = EncounterRarity.VERY_RARE)
        assertTrue(filters.matches(match))
        // Each axis on its own is enough to exclude.
        assertFalse(filters.matches(match.copy(observationCount = 1)))
        assertFalse(filters.matches(match.copy(regionalIcon = false)))
        assertFalse(filters.matches(match.copy(encounterRarity = EncounterRarity.RARE)))
    }

    @Test
    fun `active count reports axes off their default`() {
        assertEquals(0, CollectionFilters.None.activeCount)
        assertEquals(1, CollectionFilters(status = StatusFilter.MISSING).activeCount)
        assertEquals(
            3,
            CollectionFilters(
                status = StatusFilter.MISSING,
                standing = StandingFilter.ICONS,
                group = SpeciesGroup.BIRDS,
            ).activeCount,
        )
        assertEquals(
            4,
            CollectionFilters(
                status = StatusFilter.RECORDED,
                standing = StandingFilter.LEGENDS,
                rarity = RarityFilter.COMMON,
                group = SpeciesGroup.FISH,
            ).activeCount,
        )
    }

    /** The empty-result note names these, so they have to be the user-facing labels. */
    @Test
    fun `active labels list only the narrowing axes`() {
        assertEquals(emptyList<String>(), CollectionFilters.None.activeLabels())
        assertEquals(
            listOf("Missing", "Essentials"),
            CollectionFilters(
                status = StatusFilter.MISSING,
                standing = StandingFilter.ESSENTIALS,
            ).activeLabels(),
        )
    }
}
