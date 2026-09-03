package com.wildlife.feasibility.ui.screens.collection

import com.wildlife.feasibility.CollectionSpecies
import com.wildlife.feasibility.EncounterRarity

/**
 * The collection's filters, as independent axes.
 *
 * These were once a single nine-option enum, which made the most useful questions on the
 * screen unaskable: "which Essentials am I still missing?" and "which Icons do I not
 * have yet?" both need two axes at once. Each axis now defaults to `ANY` and they combine
 * with AND.
 *
 * [matches] is deliberately a pure function outside any composable, so the combination
 * logic can be tested directly rather than through the UI that displays it.
 */
data class CollectionFilters(
    val status: StatusFilter = StatusFilter.ANY,
    val standing: StandingFilter = StandingFilter.ANY,
    val rarity: RarityFilter = RarityFilter.ANY,
    val group: SpeciesGroup? = null,
    val discoverySource: DiscoverySourceFilter = DiscoverySourceFilter.ANY,
) {
    /** How many axes are off their default, for the badge on the Filters pill. */
    val activeCount: Int
        get() = listOf(
            status != StatusFilter.ANY,
            standing != StandingFilter.ANY,
            rarity != RarityFilter.ANY,
            group != null,
            discoverySource != DiscoverySourceFilter.ANY,
        ).count { it }

    val isActive: Boolean get() = activeCount > 0

    fun matches(entry: CollectionSpecies): Boolean =
        status.matches(entry) &&
            standing.matches(entry) &&
            rarity.matches(entry) &&
            (group == null || entry.taxonGroup == group.key) &&
            discoverySource.matches(entry.extraDiscoveryContexts.isNotEmpty())

    /** Human-readable names of the active axes, for the empty-result note. */
    fun activeLabels(): List<String> = buildList {
        if (status != StatusFilter.ANY) add(status.label)
        if (standing != StandingFilter.ANY) add(standing.label)
        if (rarity != RarityFilter.ANY) add(rarity.label)
        group?.let { add(it.label) }
        if (discoverySource != DiscoverySourceFilter.ANY) add(discoverySource.label)
    }

    companion object {
        val None = CollectionFilters()
    }
}

/** Whether an entry belongs to the frozen guide or was discovered outside it. */
enum class DiscoverySourceFilter(val label: String) {
    ANY("All species"),
    CATALOGUE("Catalogue"),
    EXTRAS("Extras"),
    ;

    fun matches(isExtraDiscovery: Boolean): Boolean = when (this) {
        ANY -> true
        CATALOGUE -> !isExtraDiscovery
        EXTRAS -> isExtraDiscovery
    }
}

/**
 * Where a species sits in the user's record, from absent through to verified.
 *
 * One axis rather than two: [CONFIRMED] and [AWAITING] are narrower cases of [RECORDED],
 * not peers of [MISSING], so offering them as separate controls would allow selections
 * that cannot describe anything ("missing and confirmed").
 */
enum class StatusFilter(val label: String) {
    ANY("Any status"),
    MISSING("Missing"),
    RECORDED("Recorded"),
    CONFIRMED("Confirmed"),
    AWAITING("Awaiting ID"),
    ;

    fun matches(entry: CollectionSpecies): Boolean = when (this) {
        ANY -> true
        MISSING -> entry.observationCount == 0
        RECORDED -> entry.observationCount > 0
        CONFIRMED -> entry.bestQualityGrade == "research"
        AWAITING -> entry.awaitingSpeciesIdentification
    }
}

/** Whether the region singles this species out. Orthogonal to whether the user has it. */
enum class StandingFilter(val label: String) {
    ANY("Any standing"),
    ESSENTIALS("Essentials"),
    ICONS("Icons"),
    ;

    fun matches(entry: CollectionSpecies): Boolean = when (this) {
        ANY -> true
        ESSENTIALS -> entry.regionalEssential
        ICONS -> entry.regionalIcon
    }
}

/**
 * Encounter rarity, one option per tier.
 *
 * The four tiers mirror the four tallies in the header — the screen teaches that
 * vocabulary, so it has to let the user act on it. Note that in v1 these tiers are a
 * labelled placeholder rather than measured biology (see `docs/style.md` §4); filtering to
 * a tier will therefore present placeholder data as a definite claim, and the resulting
 * sets will shift once real rarity data lands.
 */
enum class RarityFilter(val label: String, val rarity: EncounterRarity?) {
    ANY("Any rarity", null),
    COMMON("Common", EncounterRarity.COMMON),
    UNCOMMON("Uncommon", EncounterRarity.UNCOMMON),
    RARE("Rare", EncounterRarity.RARE),
    VERY_RARE("Very rare", EncounterRarity.VERY_RARE),
    ;

    fun matches(entry: CollectionSpecies): Boolean =
        rarity == null || entry.encounterRarity == rarity
}
