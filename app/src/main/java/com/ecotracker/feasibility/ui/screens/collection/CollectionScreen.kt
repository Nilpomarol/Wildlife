package com.wildlife.feasibility.ui.screens.collection

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Egg
import androidx.compose.material.icons.filled.FlutterDash
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SetMeal
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Water
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.wildlife.feasibility.CollectionSpecies
import com.wildlife.feasibility.EncounterRarity
import com.wildlife.feasibility.InstalledRegionalAchievement
import com.wildlife.feasibility.InstalledRegionalCatalogue
import com.wildlife.feasibility.ProgressionLevel
import com.wildlife.feasibility.ProgressionProjection
import com.wildlife.feasibility.ProgressionState
import com.wildlife.feasibility.ui.components.CollectionSearchBar
import com.wildlife.feasibility.ui.components.RegionPill
import com.wildlife.feasibility.ui.components.regionVisual
import com.wildlife.feasibility.ui.components.SpeciesCardModel
import com.wildlife.feasibility.ui.components.SpeciesCardPhotoKind
import com.wildlife.feasibility.ui.components.SpeciesCardRarity
import com.wildlife.feasibility.ui.components.SpeciesCardStatus
import com.wildlife.feasibility.ui.components.SpeciesGrid
import com.wildlife.feasibility.ui.components.WildlifeDropdown
import com.wildlife.feasibility.ui.components.WildlifeScaffold
import com.wildlife.feasibility.ui.components.RangerHeader
import com.wildlife.feasibility.ui.components.RangerStat
import com.wildlife.feasibility.ui.components.SectionRule
import com.wildlife.feasibility.ui.components.FieldGuidePage
import com.wildlife.feasibility.ui.components.bleedHorizontally
import com.wildlife.feasibility.ui.art.ClearGlyph
import com.wildlife.feasibility.ui.art.FieldMark
import com.wildlife.feasibility.ui.art.StatMark
import com.wildlife.feasibility.ui.theme.WildlifeGold
import com.wildlife.feasibility.ui.theme.WildlifeOliveStrong
import com.wildlife.feasibility.ui.theme.WildlifeSpacing
import com.wildlife.feasibility.ui.theme.DisplayFontFamily
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.unit.sp
import com.wildlife.feasibility.ui.components.RegionalStandingChip
import com.wildlife.feasibility.ui.components.JournalProgressBar
import com.wildlife.feasibility.ui.theme.FieldLabelStyle
import com.wildlife.feasibility.ui.theme.WildlifeTheme

/** A playful teal reserved for the island catalogue; adds a splash of game colour to the dark base. */

enum class CollectionSort(val label: String, val icon: ImageVector) {
    NAME("A–Z", Icons.Filled.SortByAlpha),
    RARITY("Rarity", Icons.Filled.Category),
    RECENT("Recent", Icons.Filled.Schedule),
}

/** Taxonomic-group filter/silhouette, keyed by [CollectionSpecies.taxonGroup]. */
enum class SpeciesGroup(val key: String, val label: String, val icon: ImageVector) {
    MAMMALS("mammals", "Mammals", Icons.Filled.Pets),
    BIRDS("birds", "Birds", Icons.Filled.FlutterDash),
    REPTILES("reptiles", "Reptiles", Icons.Filled.Egg),
    AMPHIBIANS("amphibians", "Amphibians", Icons.Filled.Water),
    FISH("fish", "Fish", Icons.Filled.SetMeal),
}

private fun groupFor(key: String?): SpeciesGroup? =
    SpeciesGroup.entries.firstOrNull { it.key == key }

@Composable
fun CollectionScreen(
    state: CollectionUiState,
    onBack: (() -> Unit)?,
    onOpenSpecies: (CollectionSpecies) -> Unit,
    onLinkAccount: () -> Unit,
    onRetry: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
) {
    // The axes are stored separately rather than as one saveable object, so each survives
    // process death without a custom Saver for the aggregate.
    var status by rememberSaveable { mutableStateOf(StatusFilter.ANY) }
    var standing by rememberSaveable { mutableStateOf(StandingFilter.ANY) }
    var rarity by rememberSaveable { mutableStateOf(RarityFilter.ANY) }
    var selectedGroup by rememberSaveable { mutableStateOf<SpeciesGroup?>(null) }
    var sort by rememberSaveable { mutableStateOf(CollectionSort.NAME) }
    var query by rememberSaveable { mutableStateOf("") }
    var filtersOpen by rememberSaveable { mutableStateOf(false) }

    val presentGroups = remember(state.entries) {
        SpeciesGroup.entries.filter { group -> state.entries.any { it.taxonGroup == group.key } }
    }
    // A region switch can leave a group selected that the new region lacks; fall back to Any.
    if (selectedGroup != null && selectedGroup !in presentGroups) selectedGroup = null

    val filters = CollectionFilters(status, standing, rarity, selectedGroup)
    val onFilters: (CollectionFilters) -> Unit = {
        status = it.status
        standing = it.standing
        rarity = it.rarity
        selectedGroup = it.group
    }

    val filtered = state.entries
        .filter(filters::matches)
        .filter { entry -> query.isBlank() || entry.label.contains(query, ignoreCase = true) }
        .sortedWith(
            when (sort) {
                CollectionSort.NAME -> compareBy { it.label.lowercase() }
                CollectionSort.RARITY -> compareByDescending<CollectionSpecies> { it.rarityWeight() }
                    .thenBy { it.label.lowercase() }
                CollectionSort.RECENT -> compareByDescending { it.latestObservedAtMs }
            },
        )

    WildlifeScaffold(
        title = "Collection",
        onBack = onBack,
        bottomBar = bottomBar,
        showTopBar = onBack != null,
    ) { innerPadding ->
      FieldGuidePage {
        when {
            state.errorMessage != null -> CollectionMessage(
                message = state.errorMessage,
                actionLabel = "Try again",
                onAction = onRetry,
                modifier = Modifier.padding(innerPadding),
            )
            else -> SpeciesGrid(
                entries = filtered,
                key = CollectionSpecies::key,
                model = CollectionSpecies::toCardModel,
                onClick = onOpenSpecies,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                header = {
                    Column(
                        modifier = Modifier.padding(top = WildlifeSpacing.Small),
                        verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Card),
                    ) {
                        CollectorHeader(
                            collected = state.entries.count { it.observationCount > 0 },
                            total = state.entries.size,
                            commonCount = state.entries.count {
                                it.observationCount > 0 && it.encounterRarity == EncounterRarity.COMMON
                            },
                            uncommonCount = state.entries.count {
                                it.observationCount > 0 && it.encounterRarity == EncounterRarity.UNCOMMON
                            },
                            rareCount = state.entries.count {
                                it.observationCount > 0 && it.encounterRarity == EncounterRarity.RARE
                            },
                            veryRareCount = state.entries.count {
                                it.observationCount > 0 && it.encounterRarity == EncounterRarity.VERY_RARE
                            },
                            xp = state.totalXp,
                            progression = state.progression,
                            selectedCatalogue = state.selectedCatalogue,
                            achievements = state.achievements,
                            observedTaxa = state.observedRegionalTaxa,
                        )
                        if (!state.linked) {
                            UnlinkedCollectionBanner(onLinkAccount = onLinkAccount)
                        }
                        SearchRow(
                            query = query,
                            onQueryChange = { query = it },
                            activeCount = filters.activeCount,
                            onOpenFilters = { filtersOpen = true },
                            onClearFilters = { onFilters(CollectionFilters.None) },
                        )
                        FilterBar(
                            filters = filters,
                            onFilters = onFilters,
                            sort = sort,
                            onSort = { sort = it },
                            count = filtered.size,
                        )
                        SectionRule("The guide")
                        if (filtered.isEmpty()) {
                            EmptyFilterNote(filters.activeLabels())
                        }
                    }
                },
            )
        }
        if (filtersOpen) {
            CollectionFilterSheet(
                filters = filters,
                onFilters = onFilters,
                presentGroups = presentGroups,
                matchCount = filtered.size,
                onDismiss = { filtersOpen = false },
            )
        }
      }
    }
}

// region — Header

@Composable
private fun CollectorHeader(
    collected: Int,
    total: Int,
    commonCount: Int,
    uncommonCount: Int,
    rareCount: Int,
    veryRareCount: Int,
    xp: Int,
    progression: ProgressionState?,
    selectedCatalogue: InstalledRegionalCatalogue?,
    achievements: List<InstalledRegionalAchievement>,
    observedTaxa: Set<Long>,
    modifier: Modifier = Modifier,
) {
    // Unlinked users have no XP account yet, so fall back to the entry level.
    val levels = progression ?: ProgressionProjection.project(xp)
    val title = levels.selectedTitle
    val completion = if (total == 0) 0f else (collected.toFloat() / total).coerceIn(0f, 1f)

    Column(modifier.fillMaxWidth()) {
        RangerHeader(
            regionName = selectedCatalogue?.displayName ?: "Your collection",
            regionKey = selectedCatalogue?.regionKey,
            levelKey = title.key,
            levelName = title.displayName,
            // The bar measures this region; the level measures lifetime XP. Labelling the
            // bar explicitly keeps the two from reading as one number.
            progressLabel = "$collected OF $total IN THIS REGION",
            progressTrailing = "${(completion * 100).toInt()}%",
            progressFraction = completion,
            // Regional tallies only. XP is lifetime and account-wide, so it never
            // belonged in a block measuring one region.
            stats = listOf(
                RangerStat(
                    value = commonCount.toString(),
                    label = "COMMON",
                    tint = WildlifeTheme.colors.rarityCommon,
                    mark = StatMark.DOT,
                ),
                RangerStat(
                    value = uncommonCount.toString(),
                    label = "UNCOMMON",
                    tint = WildlifeTheme.colors.rarityUncommon,
                    fieldMark = "rarity_uncommon",
                ),
                RangerStat(
                    value = rareCount.toString(),
                    label = "RARE",
                    tint = WildlifeTheme.colors.rarityRare,
                    fieldMark = "rarity_rare",
                ),
                RangerStat(
                    value = veryRareCount.toString(),
                    label = "VERY RARE",
                    tint = WildlifeTheme.colors.rarityVeryRare,
                    fieldMark = "rarity_very_rare",
                ),
            ),
            // The header is a plate mounted on the page, not the top of it. Its own
            // ground settles to near-solid while leaving the moon and canopy open at the
            // top, and the torn edge gives the boundary a hard line to stop at.
            showBackgroundScrim = true,
            showBottomEdge = true,
            modifier = Modifier.bleedHorizontally(WildlifeSpacing.Screen),
            belowStats = if (achievements.isEmpty()) {
                null
            } else {
                {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small),
                    ) {
                        achievements.forEach { achievement ->
                            QuestBadge(
                                achievement = achievement,
                                observedTaxa = observedTaxa,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            },
        )

    }
}

@Composable
private fun QuestBadge(
    achievement: InstalledRegionalAchievement,
    observedTaxa: Set<Long>,
    modifier: Modifier = Modifier,
) {
    val colors = WildlifeTheme.colors
    val isIcons = achievement.label == "icons"
    // "Regional" is dropped: these sit inside a header already titled with the region, so
    // the word did no work and pushed the longer label onto a second line.
    val title = if (isIcons) "Icons" else "Essentials"
    // Takes the regional-standing tokens, so a quest is coloured the same as the marks it
    // asks you to collect rather than by an unrelated gold/olive pair.
    val accent = if (isIcons) colors.icon else colors.essential
    val markName = if (isIcons) "regional_icon" else "regional_essential"
    val done = achievement.taxonIds.count { it in observedTaxa }
    val goal = achievement.taxonIds.size
    val complete = goal > 0 && done >= goal
    val fraction = if (goal == 0) 0f else done.toFloat() / goal

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = if (complete) 1.5.dp else 1.dp,
                color = if (complete) accent else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(12.dp),
            )
            .padding(11.dp),
    ) {
        // Two rows: mark, title and tally on one line; the measure beneath it.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Drawn bare, not matted: nothing sits behind it here, so the disc that keeps
            // the mark readable over photography would just be a circle around a circle.
            FieldMark(markName, accent, Modifier.size(21.dp))
            Text(
                text = title.uppercase(),
                style = FieldLabelStyle,
                color = colors.parchmentDim,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = done.toString(),
                    fontFamily = DisplayFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 19.sp,
                    color = if (complete) accent else colors.parchment,
                )
                Text(
                    text = "/$goal",
                    fontFamily = DisplayFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = colors.parchmentFaint,
                    modifier = Modifier.padding(bottom = 1.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        JournalProgressBar(fraction = fraction, height = 4.dp, accent = accent)
    }
}

/**
 * The search field with the filter sheet's entry point beside it.
 *
 * Filters sits here rather than in the selector row below because the two together are
 * "narrow what you are looking at": typing a name and setting an axis are the same
 * intent, and the pairing leaves the selector row to carry only the axes it shows.
 */
@Composable
private fun SearchRow(
    query: String,
    onQueryChange: (String) -> Unit,
    activeCount: Int,
    onOpenFilters: () -> Unit,
    onClearFilters: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CollectionSearchBar(
            query = query,
            onQueryChange = onQueryChange,
            modifier = Modifier.weight(1f),
        )
        FiltersButton(activeCount = activeCount, onClick = onOpenFilters)
        if (activeCount > 0) {
            ClearFiltersButton(onClear = onClearFilters)
        }
    }
}

/**
 * The axis selectors and sort.
 *
 * Status and Standing are inline because they are the pair the axis split exists to
 * combine — "which Essentials am I still missing?" is answerable here without the sheet.
 * Rarity and Group stay behind the Filters button above.
 *
 * The row does not scroll. A control the user has to drag sideways to find is a control
 * most people never find, so everything here is sized to fit a phone at default text size.
 *
 * Each axis carries its own accent; sort is deliberately achromatic, which is what
 * separates the things that narrow the grid from the things that do not.
 */
@Composable
private fun FilterBar(
    filters: CollectionFilters,
    onFilters: (CollectionFilters) -> Unit,
    sort: CollectionSort,
    onSort: (CollectionSort) -> Unit,
    count: Int,
) {
    val colors = WildlifeTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Micro)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WildlifeDropdown(
                selected = filters.status,
                options = StatusFilter.entries,
                label = { it.label },
                onSelected = { onFilters(filters.copy(status = it)) },
                accent = colors.axisStatus,
                leading = { StatusMark(it, colors.axisStatus) },
                pillLabel = { if (it == StatusFilter.ANY) "Status" else it.label },
            )
            WildlifeDropdown(
                selected = filters.standing,
                options = StandingFilter.entries,
                label = { it.label },
                onSelected = { onFilters(filters.copy(standing = it)) },
                accent = colors.axisStanding,
                leading = { StandingMark(it, colors.axisStanding) },
                pillLabel = { if (it == StandingFilter.ANY) "Standing" else it.label },
            )
            WildlifeDropdown(
                selected = sort,
                options = CollectionSort.entries,
                label = { it.label },
                onSelected = onSort,
                // Achromatic on purpose: sort never narrows the set, and giving it an axis
                // colour would file it alongside the filters.
                accent = colors.parchmentDim,
                leading = { option -> SortMark(option, colors.parchmentDim) },
            )
        }
        Text(
            text = if (count == 1) "1 species" else "$count species",
            style = MaterialTheme.typography.labelMedium,
            color = colors.mutedText,
        )
    }
}

/**
 * Undo every axis at once, immediately beside the Filters button.
 *
 * Clearing used to live only inside the sheet, which meant getting back to the full guide
 * took opening a sheet to press a button and dismissing it again — three actions to undo
 * one. It sits next to Filters rather than under the grid controls because it belongs to
 * the same idea, and because a row of its own would push the grid down whenever a filter
 * was set.
 *
 * It renders only when something is active: a clear control with nothing to clear
 * advertises an action that does nothing.
 */
@Composable
private fun ClearFiltersButton(onClear: () -> Unit) {
    val colors = WildlifeTheme.colors
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .border(1.dp, colors.parchmentDim.copy(alpha = 0.40f), CircleShape)
            .clickable(onClick = onClear)
            .semantics { contentDescription = "Clear all filters" },
        contentAlignment = Alignment.Center,
    ) {
        ClearGlyph(colors.parchmentDim, Modifier.size(14.dp))
    }
}

/**
 * The sheet entry point: glyph and count only.
 *
 * It carries no label so that the search field beside it keeps a usable width. The count
 * is what carries the state — a filter set on a dismissed sheet is otherwise invisible —
 * so the badge is the one part that must never be dropped for space.
 */
@Composable
private fun FiltersButton(activeCount: Int, onClick: () -> Unit) {
    val colors = WildlifeTheme.colors
    val active = activeCount > 0
    // parchmentDim rather than parchmentFaint when inactive: faint put the button close
    // enough to the background that it read as disabled rather than as merely quiet.
    val chrome = if (active) colors.parchment else colors.parchmentDim
    Row(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(CircleShape)
            .background(if (active) chrome.copy(alpha = 0.12f) else Color.Transparent)
            .border(
                width = if (active) 1.5.dp else 1.dp,
                color = chrome.copy(alpha = if (active) 0.75f else 0.50f),
                shape = CircleShape,
            )
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = if (active) "Filters, $activeCount active" else "Filters"
            }
            .padding(horizontal = WildlifeSpacing.Card),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FilterGlyph(Icons.Filled.Tune, chrome)
        if (active) {
            Text(
                text = activeCount.toString(),
                fontFamily = DisplayFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = colors.parchment,
            )
        }
    }
}

/** Sort's leading marks, all at glyph size so no option outweighs its peers. */
@Composable
private fun SortMark(option: CollectionSort, tint: Color) {
    if (option == CollectionSort.RARITY) {
        FieldMark("rarity_rare", tint, Modifier.size(18.dp))
    } else {
        FilterGlyph(option.icon, tint)
    }
}

@Composable
private fun FilterGlyph(icon: ImageVector, tint: Color) {
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
}

@Composable
private fun EmptyFilterNote(activeLabels: List<String>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = WildlifeSpacing.Section),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(WildlifeTheme.colors.oliveStrong.copy(alpha = 0.14f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Pets,
                contentDescription = null,
                tint = WildlifeTheme.colors.oliveStrong,
                modifier = Modifier.size(26.dp),
            )
        }
        // Naming the active axes tells the user what to relax. With filters combining, an
        // empty grid is usually the result of two axes meeting, and a bare "no matches"
        // leaves them guessing which one to undo.
        Text(
            text = if (activeLabels.isEmpty()) {
                "No species match your search yet."
            } else {
                "Nothing matches " + activeLabels.joinToString(" · ") + " yet."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// endregion

// region — Rank + region metadata

// endregion

// region — Card mapping

private fun CollectionSpecies.rarityWeight(): Int = when {
    regionalIcon -> 5
    encounterRarity == EncounterRarity.VERY_RARE -> 4
    encounterRarity == EncounterRarity.RARE -> 3
    encounterRarity == EncounterRarity.UNCOMMON -> 2
    encounterRarity == EncounterRarity.COMMON -> 1
    else -> 0
}

private fun CollectionSpecies.toCardModel() = SpeciesCardModel(
    key = key,
    label = label,
    // The caption states your relationship to the species, never its rarity or standing:
    // the pill under the plate already says the rarity, and the frame says the standing.
    // Repeating them here read as a stutter ("UNCOMMON" above the word "Uncommon").
    supportingText = when {
        observationCount == 0 -> "Not yet recorded"
        awaitingSpeciesIdentification -> "Awaiting species ID"
        observationCount == 1 -> "1 observation"
        else -> "$observationCount observations"
    },
    photoUrl = photoUrl,
    photoKind = photoUrl?.let { SpeciesCardPhotoKind.PERSONAL },
    silhouetteUrl = silhouetteUrl,
    silhouetteFallbackUrl = silhouetteFallbackUrl,
    silhouetteMatchRank = silhouetteMatchRank,
    fallbackSilhouetteGroup = taxonGroup,
    regionalEssential = regionalEssential,
    regionalIcon = regionalIcon,
    placeholderIcon = groupFor(taxonGroup)?.icon,
    supportingTextItalic = observationCount == 0 || awaitingSpeciesIdentification,
    collected = observationCount > 0,
    status = if (bestQualityGrade == "research") {
        SpeciesCardStatus.RESEARCH_GRADE
    } else {
        SpeciesCardStatus.NONE
    },
    rarity = encounterRarity?.toCardRarity(),
)

private fun EncounterRarity.toCardRarity() = when (this) {
    EncounterRarity.UNKNOWN -> null
    EncounterRarity.COMMON -> SpeciesCardRarity.COMMON
    EncounterRarity.UNCOMMON -> SpeciesCardRarity.UNCOMMON
    EncounterRarity.RARE -> SpeciesCardRarity.RARE
    EncounterRarity.VERY_RARE -> SpeciesCardRarity.VERY_RARE
}

// endregion

/**
 * Shown instead of replacing the whole screen: an unlinked user can still browse the regional
 * guide as silhouettes, they just have nothing collected yet.
 */
@Composable
private fun UnlinkedCollectionBanner(onLinkAccount: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(
            modifier = Modifier.padding(WildlifeSpacing.Card),
            verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small),
        ) {
            Text("Nothing collected yet", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "This is the regional guide. Link iNaturalist and your confirmed sightings " +
                    "will fill these cards in.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onLinkAccount) { Text("Link iNaturalist") }
        }
    }
}

@Composable
private fun CollectionMessage(
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(WildlifeSpacing.Section),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            androidx.compose.material3.OutlinedButton(
                onClick = onAction,
                modifier = Modifier.padding(top = WildlifeSpacing.Screen),
            ) { Text(actionLabel) }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF080B09, widthDp = 390, heightDp = 820)
@Preview(
    name = "Collection large text",
    showBackground = true,
    backgroundColor = 0xFF080B09,
    widthDp = 390,
    heightDp = 820,
    fontScale = 2f,
)
@Composable
private fun CollectionScreenPreview() {
    WildlifeTheme {
        CollectionScreen(
            state = CollectionUiState(
                linked = true,
                entries = listOf(
                    previewSpecies("European robin", "robin", "research", rarity = EncounterRarity.COMMON, group = "birds"),
                    previewSpecies("Iberian lynx", "lynx", "research", rarity = EncounterRarity.VERY_RARE, icon = true, group = "mammals"),
                    previewSpecies("Otter", "otter", "needs_id", rarity = EncounterRarity.UNCOMMON, group = "mammals"),
                    previewSpecies("Golden eagle", "eagle", "needs_id", rarity = EncounterRarity.RARE, group = "birds"),
                    previewSpecies("Genus identification", "genus", "needs_id", awaiting = true),
                    previewSpecies("Fire salamander", "salamander", "research", rarity = EncounterRarity.RARE, group = "amphibians"),
                ),
                observationCount = 12,
                totalXp = 1_240,
                selectedCatalogue = InstalledRegionalCatalogue("mediterranean_europe", "Mediterranean Europe", "2025.1"),
                installedCatalogues = listOf(
                    InstalledRegionalCatalogue("mediterranean_europe", "Mediterranean Europe", "2025.1"),
                    InstalledRegionalCatalogue("east_africa", "East Africa", "2025.1"),
                    InstalledRegionalCatalogue("caribbean", "Caribbean", "2025.1"),
                ),
            ),
            onBack = {},
            onOpenSpecies = {},
            onLinkAccount = {},
            onRetry = {},
        )
    }
}

private fun previewSpecies(
    label: String,
    key: String,
    quality: String,
    awaiting: Boolean = false,
    rarity: EncounterRarity? = null,
    icon: Boolean = false,
    group: String? = null,
) = CollectionSpecies(
    key = key,
    taxonId = null,
    label = label,
    observationCount = 1,
    rewardedObservationCount = 0,
    latestObservationUuid = key,
    latestObservedAtMs = 0,
    bestQualityGrade = quality,
    awaitingSpeciesIdentification = awaiting,
    photoUrl = null,
    encounterRarity = rarity,
    regionalIcon = icon,
    taxonGroup = group,
)
