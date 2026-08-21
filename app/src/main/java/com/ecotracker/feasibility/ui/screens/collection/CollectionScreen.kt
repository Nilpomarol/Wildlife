package com.wildlife.feasibility.ui.screens.collection

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Egg
import androidx.compose.material.icons.filled.FlutterDash
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SetMeal
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Water
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.wildlife.feasibility.CollectionSpecies
import com.wildlife.feasibility.EncounterRarity
import com.wildlife.feasibility.InstalledRegionalAchievement
import com.wildlife.feasibility.InstalledRegionalCatalogue
import com.wildlife.feasibility.RegionalPrestige
import com.wildlife.feasibility.ui.components.CollectionSearchBar
import com.wildlife.feasibility.ui.components.EncounterTrace
import com.wildlife.feasibility.ui.components.RegionalCollectionMark
import com.wildlife.feasibility.ui.components.RegionalCollectionStamp
import com.wildlife.feasibility.ui.components.RegionalLegendMark
import com.wildlife.feasibility.ui.components.SpeciesCardModel
import com.wildlife.feasibility.ui.components.SpeciesCardPhotoKind
import com.wildlife.feasibility.ui.components.SpeciesCardRarity
import com.wildlife.feasibility.ui.components.SpeciesCardStatus
import com.wildlife.feasibility.ui.components.SpeciesGrid
import com.wildlife.feasibility.ui.components.TaxonGroupGlyph
import com.wildlife.feasibility.ui.components.WildlifeDropdown
import com.wildlife.feasibility.ui.components.WildlifeScaffold
import com.wildlife.feasibility.ui.theme.GameFontFamily
import com.wildlife.feasibility.ui.theme.WildlifeGold
import com.wildlife.feasibility.ui.theme.WildlifeOliveStrong
import com.wildlife.feasibility.ui.theme.WildlifeSpacing
import com.wildlife.feasibility.ui.theme.WildlifeTheme

/** A playful teal reserved for the island catalogue; adds a splash of game colour to the dark base. */
private val CaribbeanTeal = Color(0xFF4FB3B0)

enum class CollectionFilter(val label: String, val icon: ImageVector) {
    ALL("All", Icons.Filled.GridView),
    COLLECTED("Collected", Icons.Filled.CheckCircle),
    MISSING("Missing", Icons.AutoMirrored.Filled.HelpOutline),
    CONFIRMED("Confirmed", Icons.Filled.Verified),
    RARE("Rare finds", Icons.Filled.Category),
    ESSENTIALS("Essentials", Icons.Filled.Category),
    ICONS("Icons", Icons.Filled.Category),
    LEGENDS("Legends", Icons.Filled.Category),
    AWAITING("Awaiting ID", Icons.Filled.HourglassEmpty),
}

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

private fun CollectionSpecies.isRare(): Boolean =
    encounterRarity == EncounterRarity.RARE ||
        encounterRarity == EncounterRarity.VERY_RARE

@Composable
fun CollectionScreen(
    state: CollectionUiState,
    onBack: (() -> Unit)?,
    onOpenSpecies: (CollectionSpecies) -> Unit,
    onLinkAccount: () -> Unit,
    onRetry: () -> Unit,
    onSelectCatalogue: (String) -> Unit,
    bottomBar: @Composable () -> Unit = {},
) {
    var selectedFilter by rememberSaveable { mutableStateOf(CollectionFilter.ALL) }
    var selectedGroup by rememberSaveable { mutableStateOf<SpeciesGroup?>(null) }
    var sort by rememberSaveable { mutableStateOf(CollectionSort.NAME) }
    var query by rememberSaveable { mutableStateOf("") }

    val presentGroups = remember(state.entries) {
        SpeciesGroup.entries.filter { group -> state.entries.any { it.taxonGroup == group.key } }
    }
    // A region switch can leave a group selected that the new region lacks; fall back to All.
    if (selectedGroup != null && selectedGroup !in presentGroups) selectedGroup = null

    val filtered = state.entries
        .filter { entry ->
            when (selectedFilter) {
                CollectionFilter.ALL -> true
                CollectionFilter.COLLECTED -> entry.observationCount > 0
                CollectionFilter.MISSING -> entry.observationCount == 0
                CollectionFilter.CONFIRMED -> entry.bestQualityGrade == "research"
                CollectionFilter.RARE -> entry.isRare()
                CollectionFilter.ESSENTIALS -> entry.regionalEssential
                CollectionFilter.ICONS -> entry.regionalIcon
                CollectionFilter.LEGENDS -> entry.regionalPrestige == RegionalPrestige.LEGENDARY
                CollectionFilter.AWAITING -> entry.awaitingSpeciesIdentification
            }
        }
        .filter { entry -> selectedGroup == null || entry.taxonGroup == selectedGroup!!.key }
        .filter { entry -> query.isBlank() || entry.label.contains(query, ignoreCase = true) }
        .sortedWith(
            when (sort) {
                CollectionSort.NAME -> compareBy { it.label.lowercase() }
                CollectionSort.RARITY -> compareByDescending<CollectionSpecies> { it.rarityWeight() }
                    .thenBy { it.label.lowercase() }
                CollectionSort.RECENT -> compareByDescending { it.latestObservedAtMs }
            },
        )

    WildlifeScaffold(title = "Collection", onBack = onBack, bottomBar = bottomBar) { innerPadding ->
        when {
            !state.linked -> CollectionMessage(
                message = "Verify your iNaturalist account to build your personal field collection.",
                actionLabel = "Link iNaturalist",
                onAction = onLinkAccount,
                modifier = Modifier.padding(innerPadding),
            )
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
                            researchGrade = state.entries.count { it.bestQualityGrade == "research" },
                            rareCount = state.entries.count { it.observationCount > 0 && it.isRare() },
                            xp = state.totalXp,
                            selectedCatalogue = state.selectedCatalogue,
                            catalogues = state.installedCatalogues,
                            onSelectCatalogue = onSelectCatalogue,
                            achievements = state.achievements,
                            observedTaxa = state.observedRegionalTaxa,
                        )
                        CollectionSearchBar(query = query, onQueryChange = { query = it })
                        FilterBar(
                            selectedFilter = selectedFilter,
                            onFilter = { selectedFilter = it },
                            presentGroups = presentGroups,
                            selectedGroup = selectedGroup,
                            onGroup = { selectedGroup = it },
                            sort = sort,
                            onSort = { sort = it },
                            count = filtered.size,
                        )
                        if (filtered.isEmpty()) {
                            EmptyFilterNote()
                        }
                    }
                },
            )
        }
    }
}

// region — Header

@Composable
private fun CollectorHeader(
    collected: Int,
    total: Int,
    researchGrade: Int,
    rareCount: Int,
    xp: Int,
    selectedCatalogue: InstalledRegionalCatalogue?,
    catalogues: List<InstalledRegionalCatalogue>,
    onSelectCatalogue: (String) -> Unit,
    achievements: List<InstalledRegionalAchievement>,
    observedTaxa: Set<Long>,
    modifier: Modifier = Modifier,
) {
    val accent = selectedCatalogue?.let { regionVisual(it.regionKey).accent } ?: WildlifeOliveStrong
    val tier = rankTierFor(collected)
    val next = nextRankTier(collected)
    val rankFraction = if (next == null) 1f else {
        ((collected - tier.floor).toFloat() / (next.floor - tier.floor)).coerceIn(0f, 1f)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.5.dp, accent.copy(alpha = 0.45f)),
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        0f to accent.copy(alpha = 0.20f),
                        0.55f to accent.copy(alpha = 0.05f),
                        1f to Color.Transparent,
                    ),
                )
                .padding(WildlifeSpacing.Card),
            verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selectedCatalogue != null) {
                    WildlifeDropdown(
                        selected = selectedCatalogue,
                        options = catalogues,
                        label = InstalledRegionalCatalogue::displayName,
                        onSelected = { onSelectCatalogue(it.regionKey) },
                        accent = accent,
                        leading = { RegionGlyph(regionVisual(it.regionKey)) },
                    )
                }
                RankBadge(tier = tier, accent = accent)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(WildlifeSpacing.Card),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompletionRing(collected = collected, total = total, accent = accent)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Micro),
                ) {
                    Text(
                        text = tier.title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontFamily = GameFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = WildlifeTheme.colors.parchment,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (next != null) {
                            "${next.floor - collected} more to ${next.title}"
                        } else {
                            "Top rank reached — legend of the field"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    GameProgressBar(fraction = rankFraction, accent = accent)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small),
            ) {
                StatChip(
                    icon = Icons.Filled.Verified,
                    value = researchGrade.toString(),
                    label = "Confirmed",
                    accent = WildlifeTheme.colors.confirmed,
                    modifier = Modifier.weight(1f),
                )
                StatChip(
                    value = rareCount.toString(),
                    label = "Rare finds",
                    accent = WildlifeTheme.colors.rarityRare,
                    marker = { EncounterTrace(SpeciesCardRarity.RARE) },
                    modifier = Modifier.weight(1f),
                )
                StatChip(
                    icon = Icons.Filled.WorkspacePremium,
                    value = xp.toString(),
                    label = "XP",
                    accent = WildlifeTheme.colors.gold,
                    modifier = Modifier.weight(1f),
                )
            }

            if (achievements.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small)) {
                    achievements.forEach { achievement ->
                        QuestBadge(
                            achievement = achievement,
                            observedTaxa = observedTaxa,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            Text(
                text = "Frozen ${selectedCatalogue?.version ?: "catalogue"} · sightings count only in this region.",
                style = MaterialTheme.typography.labelSmall,
                color = WildlifeTheme.colors.mutedText,
            )
        }
    }
}

@Composable
private fun CompletionRing(collected: Int, total: Int, accent: Color) {
    val fraction = if (total == 0) 0f else (collected.toFloat() / total).coerceIn(0f, 1f)
    val animated by animateFloatAsState(fraction, tween(700), label = "ring")
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier = Modifier.size(84.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 9.dp.toPx()
            val inset = strokeWidth / 2f
            val arcSize = androidx.compose.ui.geometry.Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(
                color = track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
            drawArc(
                color = accent,
                startAngle = -90f,
                sweepAngle = 360f * animated,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = collected.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontFamily = GameFontFamily,
                fontWeight = FontWeight.ExtraBold,
                color = WildlifeTheme.colors.parchment,
            )
            Text(
                text = "of $total",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = GameFontFamily,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GameProgressBar(fraction: Float, accent: Color) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(700), label = "xp-bar")
    val pill = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(pill)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(animated)
                .clip(pill)
                .background(
                    Brush.horizontalGradient(
                        listOf(accent.copy(alpha = 0.75f), accent),
                    ),
                ),
        )
    }
}

@Composable
private fun StatChip(
    icon: ImageVector? = null,
    value: String,
    label: String,
    accent: Color,
    marker: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = WildlifeSpacing.Small, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier.size(26.dp).background(accent.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    marker != null -> marker()
                    icon != null -> Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                }
            }
            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = GameFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    color = WildlifeTheme.colors.parchment,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun QuestBadge(
    achievement: InstalledRegionalAchievement,
    observedTaxa: Set<Long>,
    modifier: Modifier = Modifier,
) {
    val isIcons = achievement.label == "icons"
    val title = if (isIcons) "Regional Icons" else "Regional Essentials"
    val accent = if (isIcons) WildlifeTheme.colors.gold else WildlifeTheme.colors.oliveStrong
    val done = achievement.taxonIds.count { it in observedTaxa }
    val goal = achievement.taxonIds.size
    val complete = goal > 0 && done >= goal
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = accent.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, accent.copy(alpha = if (complete) 0.9f else 0.4f)),
    ) {
        Row(
            modifier = Modifier.padding(WildlifeSpacing.Small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small),
        ) {
            if (isIcons) {
                RegionalCollectionStamp(RegionalCollectionMark.ICON)
            } else {
                RegionalCollectionStamp(RegionalCollectionMark.ESSENTIAL)
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = GameFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    color = WildlifeTheme.colors.parchment,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "$done / $goal",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = GameFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
            }
        }
    }
}

@Composable
private fun RankBadge(tier: RankTier, accent: Color) {
    Surface(
        shape = CircleShape,
        color = accent.copy(alpha = 0.16f),
        border = BorderStroke(1.5.dp, accent.copy(alpha = 0.55f)),
    ) {
        Row(
            modifier = Modifier.padding(start = 6.dp, end = WildlifeSpacing.Card, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier.size(26.dp).background(accent.copy(alpha = 0.9f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    tier.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                text = tier.title,
                style = MaterialTheme.typography.labelLarge,
                fontFamily = GameFontFamily,
                fontWeight = FontWeight.SemiBold,
                color = WildlifeTheme.colors.parchment,
            )
        }
    }
}

@Composable
private fun RegionGlyph(visual: RegionVisual) {
    Box(
        modifier = Modifier.size(26.dp).background(visual.accent.copy(alpha = 0.22f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(visual.icon, contentDescription = null, tint = visual.accent, modifier = Modifier.size(16.dp))
    }
}

/**
 * All collection controls on a single horizontally-scrolling row of dropdowns: status filter,
 * taxonomic group (when the region has any), and sort order. A compact count sits underneath.
 */
@Composable
private fun FilterBar(
    selectedFilter: CollectionFilter,
    onFilter: (CollectionFilter) -> Unit,
    presentGroups: List<SpeciesGroup>,
    selectedGroup: SpeciesGroup?,
    onGroup: (SpeciesGroup?) -> Unit,
    sort: CollectionSort,
    onSort: (CollectionSort) -> Unit,
    count: Int,
) {
    val filterAccent = WildlifeTheme.colors.oliveStrong
    val groupAccent = CaribbeanTeal
    val sortAccent = WildlifeTheme.colors.gold
    Column(verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Micro)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small),
        ) {
            WildlifeDropdown(
                selected = selectedFilter,
                options = CollectionFilter.entries,
                label = { it.label },
                onSelected = onFilter,
                accent = filterAccent,
                leading = { FilterMark(it, filterAccent) },
            )
            if (presentGroups.isNotEmpty()) {
                val groupOptions = remember(presentGroups) {
                    listOf<SpeciesGroup?>(null) + presentGroups
                }
                WildlifeDropdown(
                    selected = selectedGroup,
                    options = groupOptions,
                    label = { it?.label ?: "All groups" },
                    onSelected = onGroup,
                    accent = groupAccent,
                    leading = { group ->
                        if (group == null) FilterGlyph(Icons.Filled.Category, groupAccent)
                        else TaxonGroupGlyph(group.key, groupAccent, null, 20.dp)
                    },
                )
            }
            WildlifeDropdown(
                selected = sort,
                options = CollectionSort.entries,
                label = { it.label },
                onSelected = onSort,
                accent = sortAccent,
                leading = { sort ->
                    if (sort == CollectionSort.RARITY) EncounterTrace(SpeciesCardRarity.RARE)
                    else FilterGlyph(sort.icon, sortAccent)
                },
            )
        }
        Text(
            text = if (count == 1) "1 species" else "$count species",
            style = MaterialTheme.typography.labelMedium,
            color = WildlifeTheme.colors.mutedText,
        )
    }
}

@Composable
private fun FilterGlyph(icon: ImageVector, tint: Color) {
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
}

@Composable
private fun FilterMark(filter: CollectionFilter, tint: Color) {
    when (filter) {
        CollectionFilter.RARE -> EncounterTrace(SpeciesCardRarity.RARE)
        CollectionFilter.ESSENTIALS -> RegionalCollectionStamp(RegionalCollectionMark.ESSENTIAL)
        CollectionFilter.ICONS -> RegionalCollectionStamp(RegionalCollectionMark.ICON)
        CollectionFilter.LEGENDS -> RegionalLegendMark()
        else -> FilterGlyph(filter.icon, tint)
    }
}

@Composable
private fun EmptyFilterNote() {
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
        Text(
            text = "No species match this filter yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// endregion

// region — Rank + region metadata

private data class RankTier(val title: String, val icon: ImageVector, val floor: Int)

// UI-only playful collector ranks tied to collected count. Distinct from Profile's real XP levels.
private val RANK_TIERS = listOf(
    RankTier("Novice", Icons.Filled.Spa, 0),
    RankTier("Observer", Icons.Filled.Visibility, 10),
    RankTier("Tracker", Icons.Filled.Pets, 40),
    RankTier("Field Naturalist", Icons.Filled.Park, 100),
    RankTier("Ranger", Icons.Filled.MilitaryTech, 200),
)

private fun rankTierFor(collected: Int): RankTier =
    RANK_TIERS.last { collected >= it.floor }

private fun nextRankTier(collected: Int): RankTier? =
    RANK_TIERS.firstOrNull { collected < it.floor }

private data class RegionVisual(val icon: ImageVector, val accent: Color)

private fun regionVisual(regionKey: String): RegionVisual = when (regionKey) {
    "mediterranean_europe" -> RegionVisual(Icons.Filled.Forest, WildlifeOliveStrong)
    "east_africa" -> RegionVisual(Icons.Filled.Landscape, WildlifeGold)
    "caribbean" -> RegionVisual(Icons.Filled.Waves, CaribbeanTeal)
    else -> RegionVisual(Icons.Filled.Public, WildlifeOliveStrong)
}

// endregion

// region — Card mapping

private fun CollectionSpecies.rarityWeight(): Int = when {
    regionalPrestige == RegionalPrestige.LEGENDARY -> 5
    encounterRarity == EncounterRarity.VERY_RARE -> 4
    encounterRarity == EncounterRarity.RARE -> 3
    encounterRarity == EncounterRarity.UNCOMMON -> 2
    encounterRarity == EncounterRarity.COMMON -> 1
    else -> 0
}

private fun CollectionSpecies.toCardModel() = SpeciesCardModel(
    key = key,
    label = label,
    supportingText = when {
        observationCount == 0 -> listOfNotNull(
            encounterRarity?.label(),
            regionalPrestige?.takeIf { it == RegionalPrestige.LEGENDARY }?.let { "Regional Legend" },
        ).joinToString(" · ")
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
    regionalLegend = regionalPrestige == RegionalPrestige.LEGENDARY,
    placeholderIcon = groupFor(taxonGroup)?.icon,
    supportingTextItalic = observationCount == 0 || awaitingSpeciesIdentification,
    status = if (bestQualityGrade == "research") {
        SpeciesCardStatus.RESEARCH_GRADE
    } else {
        SpeciesCardStatus.NONE
    },
    rarity = encounterRarity?.toCardRarity(),
)

private fun EncounterRarity.label() = when (this) {
    EncounterRarity.UNKNOWN -> "Rarity under review"
    EncounterRarity.COMMON -> "Common"
    EncounterRarity.UNCOMMON -> "Uncommon"
    EncounterRarity.RARE -> "Rare"
    EncounterRarity.VERY_RARE -> "Very rare"
}

private fun EncounterRarity.toCardRarity() = when (this) {
    EncounterRarity.UNKNOWN -> null
    EncounterRarity.COMMON -> SpeciesCardRarity.COMMON
    EncounterRarity.UNCOMMON -> SpeciesCardRarity.UNCOMMON
    EncounterRarity.RARE -> SpeciesCardRarity.RARE
    EncounterRarity.VERY_RARE -> SpeciesCardRarity.VERY_RARE
}

// endregion

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
                    previewSpecies("Iberian lynx", "lynx", "research", rarity = EncounterRarity.VERY_RARE, legendary = true, group = "mammals"),
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
            onSelectCatalogue = {},
        )
    }
}

private fun previewSpecies(
    label: String,
    key: String,
    quality: String,
    awaiting: Boolean = false,
    rarity: EncounterRarity? = null,
    legendary: Boolean = false,
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
    regionalPrestige = if (legendary) RegionalPrestige.LEGENDARY else RegionalPrestige.STANDARD,
    taxonGroup = group,
)
