package com.wildlife.feasibility.ui.screenshot

import com.wildlife.feasibility.CollectionSpecies
import com.wildlife.feasibility.EncounterRarity
import com.wildlife.feasibility.InstalledRegionalCatalogue
import com.wildlife.feasibility.RegionalPrestige
import com.wildlife.feasibility.ui.screens.collection.CollectionUiState

/**
 * Shared sample data for redesign screenshots. Species names are real, but every
 * count, rarity and progression value here is fixture data for layout review only.
 */
object SampleCollection {

    private fun species(
        label: String,
        key: String,
        quality: String = "research",
        observations: Int = 1,
        awaiting: Boolean = false,
        rarity: EncounterRarity? = null,
        legendary: Boolean = false,
        essential: Boolean = false,
        icon: Boolean = false,
        group: String? = null,
    ) = CollectionSpecies(
        key = key,
        taxonId = null,
        label = label,
        observationCount = observations,
        rewardedObservationCount = 0,
        latestObservationUuid = key,
        latestObservedAtMs = 0,
        bestQualityGrade = quality,
        awaitingSpeciesIdentification = awaiting,
        photoUrl = null,
        encounterRarity = rarity,
        regionalPrestige = if (legendary) RegionalPrestige.LEGENDARY else RegionalPrestige.STANDARD,
        regionalEssential = essential,
        regionalIcon = icon,
        taxonGroup = group,
    )

    val entries = listOf(
        species("European robin", "robin", rarity = EncounterRarity.COMMON, essential = true, group = "birds"),
        species("Iberian lynx", "lynx", rarity = EncounterRarity.VERY_RARE, legendary = true, icon = true, group = "mammals"),
        species("Eurasian otter", "otter", "needs_id", rarity = EncounterRarity.UNCOMMON, group = "mammals"),
        species("Golden eagle", "eagle", "needs_id", rarity = EncounterRarity.RARE, icon = true, group = "birds"),
        species("Fire salamander", "salamander", rarity = EncounterRarity.RARE, group = "amphibians"),
        species("Common kingfisher", "kingfisher", rarity = EncounterRarity.UNCOMMON, group = "birds"),
        species("Red fox", "fox", rarity = EncounterRarity.COMMON, essential = true, group = "mammals"),
        species("Genus identification", "genus", "needs_id", awaiting = true),
        species("Ocellated lizard", "lizard", observations = 0, rarity = EncounterRarity.RARE, group = "reptiles"),
        species("Wild boar", "boar", observations = 0, rarity = EncounterRarity.COMMON, group = "mammals"),
        species("Hoopoe", "hoopoe", observations = 0, rarity = EncounterRarity.UNCOMMON, group = "birds"),
        species("Griffon vulture", "vulture", observations = 0, rarity = EncounterRarity.RARE, icon = true, group = "birds"),
    )

    private val mediterranean =
        InstalledRegionalCatalogue("mediterranean_europe", "Mediterranean Europe", "2025.1")

    val state = CollectionUiState(
        linked = true,
        entries = entries,
        observationCount = 12,
        totalXp = 1_240,
        selectedCatalogue = mediterranean,
        installedCatalogues = listOf(
            mediterranean,
            InstalledRegionalCatalogue("east_africa", "East Africa", "2025.1"),
            InstalledRegionalCatalogue("caribbean", "Caribbean", "2025.1"),
        ),
    )
}
