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
import com.wildlife.feasibility.CurrentRegionSource
import com.wildlife.feasibility.ProgressionLevel
import com.wildlife.feasibility.ProgressionProjection
import com.wildlife.feasibility.ProgressionState
import com.wildlife.feasibility.ui.components.SpeciesSearchFilterRow
import com.wildlife.feasibility.ui.components.regionVisual
import com.wildlife.feasibility.ui.components.SpeciesCardModel
import com.wildlife.feasibility.ui.components.SpeciesCardPhotoKind
import com.wildlife.feasibility.ui.components.SpeciesCardRarity
import com.wildlife.feasibility.ui.components.SpeciesCardStatus
import com.wildlife.feasibility.ui.components.SpeciesGrid
import com.wildlife.feasibility.ui.components.MediaPrefetchStatus
import com.wildlife.feasibility.ui.components.WildlifeDropdown
import com.wildlife.feasibility.ui.components.WildlifeScaffold
import com.wildlife.feasibility.ui.components.WildlifeLoadingState
import com.wildlife.feasibility.ui.components.RecordHead
import com.wildlife.feasibility.ui.components.RecordTally
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
    selectedSection: CollectionSection = CollectionSection.SPECIES,
    onSectionChange: (CollectionSection) -> Unit = {},
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

    val personalEntries = remember(state.entries) {
        state.entries.filter { it.observationCount > 0 }
    }
    val presentGroups = remember(personalEntries) {
        SpeciesGroup.entries.filter { group -> personalEntries.any { it.taxonGroup == group.key } }
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

    val filtered = personalEntries
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
    ) { innerPadding ->
      FieldGuidePage {
        // The index strip is pinned above the grid rather than scrolled with its header:
        // it belongs to the destination, not to this one section's content.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            CollectionSectionSelector(selectedSection, onSectionChange)
            when {
                state.isLoading && state.entries.isEmpty() -> WildlifeLoadingState(
                    label = "Opening your collection…",
                    modifier = Modifier.weight(1f),
                )
                state.errorMessage != null -> CollectionMessage(
                    message = state.errorMessage,
                    actionLabel = "Try again",
                    onAction = onRetry,
                    modifier = Modifier.weight(1f),
                )
                else -> SpeciesGrid(
                    entries = filtered,
                    key = CollectionSpecies::key,
                    model = CollectionSpecies::toCardModel,
                    onClick = onOpenSpecies,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    header = {
                        Column(
                            modifier = Modifier.padding(top = WildlifeSpacing.Card),
                            verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Card),
                        ) {
                            PersonalCollectionHeader(
                                speciesCount = personalEntries.size,
                                observationCount = state.observationCount,
                                researchGradeCount = personalEntries.count {
                                    it.bestQualityGrade == "research"
                                },
                                awaitingCount = personalEntries.count {
                                    it.awaitingSpeciesIdentification
                                },
                                groupsRecorded = presentGroups.size,
                                groupsTotal = SpeciesGroup.entries.size,
                            )
                            if (!state.linked) {
                                UnlinkedCollectionBanner(onLinkAccount = onLinkAccount)
                            }
                            SpeciesSearchFilterRow(
                                query = query,
                                onQueryChange = { query = it },
                                placeholder = "Search your collection",
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
                            SectionRule("Recorded species")
                            if (filtered.isEmpty()) {
                                if (personalEntries.isEmpty()) {
                                    EmptyCollectionNote()
                                } else {
                                    EmptyFilterNote(filters.activeLabels())
                                }
                            }
                        }
                    },
                )
            }
        }
        if (filtersOpen) {
            CollectionFilterSheet(
                filters = filters,
                onFilters = onFilters,
                presentGroups = presentGroups,
                matchCount = filtered.size,
                personalOnly = true,
                onDismiss = { filtersOpen = false },
            )
        }
      }
    }
}

// region — Header

/**
 * The Species chapter's record head.
 *
 * Collection has no denominator — it is every region the user has ever recorded in, not a
 * checklist — so the head states what the record *holds* rather than how complete it is,
 * and carries no completion bar or percentage. The species count leads because that is the
 * collection; the readouts under it annotate the same record.
 *
 * Three things are deliberately absent. Regional standing and encounter rarity need a
 * selected region and stay on Explore's guide. Lifetime XP and the rank ladder are Home's,
 * where a rank counting a lifetime cannot be mistaken for a measure of this screen.
 *
 * No "My collection" heading either: the destination's own title says it once already, and
 * the chapter rail directly above says which of its views this is.
 */
@Composable
private fun PersonalCollectionHeader(
    speciesCount: Int,
    observationCount: Int,
    researchGradeCount: Int,
    awaitingCount: Int,
    groupsRecorded: Int,
    groupsTotal: Int,
) {
    val colors = WildlifeTheme.colors
    RecordHead(
        eyebrow = "Your record",
        headlineValue = speciesCount.toString(),
        // "Species" is its own plural, so the line reads correctly at one as well as none.
        headlineLabel = "species recorded",
        // Ordered as size, then breadth, then how settled the record is. Copper and brass
        // are kept apart in the row: side by side the two warm accents read as one.
        tallies = listOf(
            RecordTally.of(observationCount, "Observations", colors.parchment),
            // Breadth rather than completeness. The denominator is the taxonomic filter
            // axis, which is the one axis this all-regions projection legitimately owns —
            // not a count of what lives in any region.
            RecordTally(
                value = "$groupsRecorded/$groupsTotal",
                label = "Groups",
                tint = colors.axisGroup,
                emphasised = groupsRecorded > 0,
                description = "$groupsRecorded of $groupsTotal taxonomic groups recorded",
            ),
            RecordTally.of(
                researchGradeCount,
                "Confirmed",
                colors.confirmed,
                description = "$researchGradeCount species with a Research Grade observation",
            ),
            // Brass, as on the Observations chapter: the only readout here that means
            // something is still unsettled.
            RecordTally.of(
                awaitingCount,
                "Awaiting ID",
                colors.gold,
                description = "$awaitingCount species awaiting a species identification",
            ),
        ),
        // A legend entry, not a sentence: "confirmed" is the one word on the head that
        // means something specific, and it fits one stamped line when glossed rather than
        // explained.
        note = "Confirmed: a Research Grade observation",
    )
}

@Composable
private fun EmptyCollectionNote() {
    Text(
        text = "Your recorded species will appear here after Wildlife finds your public iNaturalist observations.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * The axis selectors and sort.
 *
 * Collection is an all-regions personal projection, so its visible axes are verification
 * status and taxonomic group. Regional standing and encounter rarity stay in Explore.
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
                options = listOf(StatusFilter.ANY, StatusFilter.CONFIRMED, StatusFilter.AWAITING),
                label = { it.label },
                onSelected = { onFilters(filters.copy(status = it)) },
                accent = colors.axisStatus,
                leading = { StatusMark(it, colors.axisStatus) },
                pillLabel = { if (it == StatusFilter.ANY) "Status" else it.label },
            )
            WildlifeDropdown(
                selected = sort,
                options = CollectionSort.entries.filter { it != CollectionSort.RARITY },
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
    extraDiscoveryLabel = when (extraDiscoveryContexts.size) {
        0 -> null
        1 -> "Extra · ${extraDiscoveryContexts.single().regionName}"
        else -> "Extra · ${extraDiscoveryContexts.size} regions"
    },
    extraDiscoveryDescription = extraDiscoveryContexts.takeIf { it.isNotEmpty() }
        ?.joinToString(prefix = "Extra discovery in ", separator = "; ") {
            "${it.regionName}, catalogue ${it.catalogueVersion}"
        },
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
