package com.wildlife.feasibility.ui.screenshot

import com.wildlife.feasibility.CollectionSpecies
import com.wildlife.feasibility.EncounterRarity
import com.wildlife.feasibility.InstalledRegionalAchievement
import com.wildlife.feasibility.InstalledRegionalCatalogue
import com.wildlife.feasibility.ProgressionProjection
import com.wildlife.feasibility.ui.screens.collection.CollectionUiState

/**
 * A spread of Collection states across the whole progression arc, so the design can be
 * reviewed at moments that would otherwise take months of real use to reach.
 *
 * Every value here is fixture data. Species names are real, but counts, rarity tiers,
 * regional standing and XP are invented for layout review and must never be read as
 * biology or as a claim about a real account.
 */
object CollectionScenarios {

    data class Scenario(
        val key: String,
        val title: String,
        val state: CollectionUiState,
    )

    /** Region → the species pool it draws on, with rarity, standing and group. */
    private data class Seed(
        val name: String,
        val rarity: EncounterRarity,
        val group: String,
        val essential: Boolean = false,
        val icon: Boolean = false,
    )

    private val mediterranean = listOf(
        Seed("European robin", EncounterRarity.COMMON, "birds", essential = true),
        Seed("Red fox", EncounterRarity.COMMON, "mammals", essential = true),
        Seed("Wild boar", EncounterRarity.COMMON, "mammals"),
        Seed("Common kingfisher", EncounterRarity.UNCOMMON, "birds"),
        Seed("Eurasian otter", EncounterRarity.UNCOMMON, "mammals", essential = true),
        Seed("Hoopoe", EncounterRarity.UNCOMMON, "birds"),
        Seed("Fire salamander", EncounterRarity.RARE, "amphibians"),
        Seed("Golden eagle", EncounterRarity.RARE, "birds", icon = true),
        Seed("Griffon vulture", EncounterRarity.RARE, "birds", icon = true),
        Seed("Ocellated lizard", EncounterRarity.RARE, "reptiles"),
        Seed("Iberian lynx", EncounterRarity.VERY_RARE, "mammals", icon = true),
        Seed("Bearded vulture", EncounterRarity.VERY_RARE, "birds"),
    )

    private val catalogue =
        InstalledRegionalCatalogue("mediterranean_europe", "Mediterranean Europe", "2025.1")

    /**
     * Builds a state where the first [collected] species of the pool have been recorded.
     * Taking a prefix keeps each scenario a superset of the one before it, so the set
     * reads as one account progressing rather than unrelated snapshots.
     */
    private fun state(
        collected: Int,
        totalXp: Int,
        confirmedOf: Int = collected,
        awaitingLast: Boolean = false,
    ): CollectionUiState {
        val entries = mediterranean.mapIndexed { index, seed ->
            val isCollected = index < collected
            val count = if (isCollected) 1 + (index % 3) else 0
            CollectionSpecies(
                key = "taxon:${1000L + index}",
                taxonId = 1000L + index,
                label = seed.name,
                observationCount = count,
                rewardedObservationCount = if (isCollected) 1 else 0,
                latestObservationUuid = "uuid-$index",
                latestObservedAtMs = if (isCollected) 1_700_000_000_000L + index * 86_400_000L else 0,
                bestQualityGrade = if (isCollected && index < confirmedOf) "research" else "",
                awaitingSpeciesIdentification = awaitingLast && index == collected,
                photoUrl = null,
                regionalEssential = seed.essential,
                regionalIcon = seed.icon,
                scientificName = null,
                encounterRarity = seed.rarity,
                taxonGroup = seed.group,
            )
        }
        val observed = entries.filter { it.observationCount > 0 }.mapNotNull { it.taxonId }.toSet()
        return CollectionUiState(
            linked = true,
            entries = entries,
            observationCount = entries.sumOf { it.observationCount },
            totalXp = totalXp,
            selectedCatalogue = catalogue,
            installedCatalogues = listOf(catalogue),
            achievements = listOf(
                InstalledRegionalAchievement(
                    label = "essentials",
                    taxonIds = entries.filter { it.regionalEssential }.mapNotNull { it.taxonId }.toSet(),
                ),
                InstalledRegionalAchievement(
                    label = "icons",
                    taxonIds = entries.filter { it.regionalIcon }.mapNotNull { it.taxonId }.toSet(),
                ),
            ),
            observedRegionalTaxa = observed,
            progression = ProgressionProjection.project(totalXp),
        )
    }

    /**
     * One scenario per progression level, with collection and quest completion rising
     * alongside XP so each badge is seen in a plausible context rather than in isolation.
     */
    val all: List<Scenario> = listOf(
        Scenario(
            key = "01-tourist-empty",
            title = "Tourist · nothing recorded yet",
            state = state(collected = 0, totalXp = 0),
        ),
        Scenario(
            key = "02-explorer-first-finds",
            title = "Explorer · first finds, one awaiting ID",
            state = state(collected = 2, totalXp = 700, confirmedOf = 1, awaitingLast = true),
        ),
        Scenario(
            key = "03-naturalist-essentials-done",
            title = "Naturalist · essentials complete",
            state = state(collected = 5, totalXp = 3_100),
        ),
        Scenario(
            key = "04-tracker-halfway",
            title = "Tracker · halfway, first rare finds",
            state = state(collected = 7, totalXp = 9_400, confirmedOf = 5),
        ),
        Scenario(
            // The last icon is the Iberian lynx, the twelfth seed, so icons cannot
            // complete before scenario 06. Named for what this state actually shows.
            key = "05-field-ranger-rares",
            title = "Field Ranger · rares recorded, one icon short",
            state = state(collected = 10, totalXp = 24_000, confirmedOf = 8),
        ),
        Scenario(
            key = "06-master-ranger-icons-complete",
            title = "Master Ranger · last icon recorded, icons complete",
            state = state(collected = 11, totalXp = 62_000, confirmedOf = 10),
        ),
        Scenario(
            key = "07-legendary-complete",
            title = "Legendary Ranger · region complete",
            state = state(collected = 12, totalXp = 120_000),
        ),
    )
}
