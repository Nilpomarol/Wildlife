package com.wildlife.feasibility.ui.screens.explore

import com.wildlife.feasibility.EncounterRarity
import com.wildlife.feasibility.ui.components.SpeciesCardModel
import com.wildlife.feasibility.ui.components.SpeciesCardStatus
import com.wildlife.feasibility.ui.screens.collection.CollectionFilters
import com.wildlife.feasibility.ui.screens.collection.RarityFilter
import com.wildlife.feasibility.ui.screens.collection.SpeciesGroup
import com.wildlife.feasibility.ui.screens.collection.StandingFilter
import com.wildlife.feasibility.ui.screens.collection.StatusFilter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExploreFiltersTest {
    @Test
    fun `missing regional icons and rarity combine on the guide`() {
        val filters = CollectionFilters(
            status = StatusFilter.MISSING,
            standing = StandingFilter.ICONS,
            rarity = RarityFilter.VERY_RARE,
            group = SpeciesGroup.MAMMALS,
        )

        assertTrue(filters.matches(species(observed = false, icon = true)))
        assertFalse(filters.matches(species(observed = true, icon = true)))
        assertFalse(filters.matches(species(observed = false, icon = false)))
        assertFalse(filters.matches(species(observed = false, icon = true, rarity = EncounterRarity.RARE)))
        assertFalse(filters.matches(species(observed = false, icon = true, group = "birds")))
    }

    @Test
    fun `confirmed only includes research grade regional records`() {
        val filters = CollectionFilters(status = StatusFilter.CONFIRMED)

        assertTrue(filters.matches(species(observed = true, status = SpeciesCardStatus.RESEARCH_GRADE)))
        assertFalse(filters.matches(species(observed = true, status = SpeciesCardStatus.NONE)))
        assertFalse(filters.matches(species(observed = false, status = SpeciesCardStatus.RESEARCH_GRADE)))
    }

    private fun species(
        observed: Boolean,
        icon: Boolean = true,
        rarity: EncounterRarity = EncounterRarity.VERY_RARE,
        group: String = "mammals",
        status: SpeciesCardStatus = SpeciesCardStatus.NONE,
    ) = ExploreSpecies(
        taxonId = 1,
        taxonGroup = group,
        commonName = "Iberian lynx",
        scientificName = "Lynx pardinus",
        observed = observed,
        encounterRarity = rarity,
        card = SpeciesCardModel(
            key = "taxon:1",
            label = "Iberian lynx",
            supportingText = "Lynx pardinus",
            photoUrl = null,
            regionalIcon = icon,
            status = status,
        ),
    )
}
