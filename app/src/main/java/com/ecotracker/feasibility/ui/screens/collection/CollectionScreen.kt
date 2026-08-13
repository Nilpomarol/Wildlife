package com.wildlife.feasibility.ui.screens.collection

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wildlife.feasibility.CollectionSpecies
import com.wildlife.feasibility.ui.components.CollectionSearchBar
import com.wildlife.feasibility.ui.components.SpeciesCard
import com.wildlife.feasibility.ui.components.SpeciesCardModel
import com.wildlife.feasibility.ui.components.SpeciesCardPhotoKind
import com.wildlife.feasibility.ui.components.SpeciesCardStatus
import com.wildlife.feasibility.ui.components.TaxonFilterRow
import com.wildlife.feasibility.ui.components.WildlifeScaffold
import com.wildlife.feasibility.ui.components.responsiveSpeciesGridColumns
import com.wildlife.feasibility.ui.components.sampleRarityFor
import com.wildlife.feasibility.ui.theme.WildlifeSpacing
import com.wildlife.feasibility.ui.theme.WildlifeTheme
import kotlin.math.min

enum class CollectionFilter(val label: String) {
    ALL("All"),
    RESEARCH_GRADE("Research grade"),
    NEEDS_ID("Needs ID"),
    AWAITING_SPECIES("Awaiting species"),
}

// Visual-only provisional regional target so the completion bar reads like a collectible
// progress meter. NOT a curated denominator; always shown with a "provisional" label.
private const val SAMPLE_REGION_TARGET = 580

@Composable
fun CollectionScreen(
    state: CollectionUiState,
    onBack: (() -> Unit)?,
    onOpenSpecies: (CollectionSpecies) -> Unit,
    onLinkAccount: () -> Unit,
    onRetry: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
) {
    var selectedFilter by rememberSaveable { mutableStateOf(CollectionFilter.ALL) }
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = state.entries
        .filter { entry ->
            when (selectedFilter) {
                CollectionFilter.ALL -> true
                CollectionFilter.RESEARCH_GRADE -> entry.bestQualityGrade == "research"
                CollectionFilter.NEEDS_ID ->
                    !entry.awaitingSpeciesIdentification && entry.bestQualityGrade == "needs_id"
                CollectionFilter.AWAITING_SPECIES -> entry.awaitingSpeciesIdentification
            }
        }
        .filter { entry -> query.isBlank() || entry.label.contains(query, ignoreCase = true) }

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
            state.entries.isEmpty() -> CollectionMessage(
                message = "Your collection is empty. Sync your verified iNaturalist account from Home.",
                modifier = Modifier.padding(innerPadding),
            )
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                CollectorHeader(
                    collected = state.entries.size,
                    identifiedSpecies = state.identifiedSpeciesCount,
                    researchGrade = state.entries.count { it.bestQualityGrade == "research" },
                    xp = state.totalXp,
                    modifier = Modifier.padding(
                        start = WildlifeSpacing.Screen,
                        end = WildlifeSpacing.Screen,
                        top = WildlifeSpacing.Small,
                    ),
                )
                CollectionSearchBar(
                    query = query,
                    onQueryChange = { query = it },
                    modifier = Modifier.padding(
                        start = WildlifeSpacing.Screen,
                        end = WildlifeSpacing.Screen,
                        top = WildlifeSpacing.Card,
                    ),
                )
                TaxonFilterRow(
                    options = CollectionFilter.entries,
                    selected = selectedFilter,
                    label = CollectionFilter::label,
                    onSelected = { selectedFilter = it },
                    modifier = Modifier.padding(
                        start = WildlifeSpacing.Screen,
                        top = WildlifeSpacing.Small,
                        bottom = WildlifeSpacing.Small,
                    ),
                )
                if (filtered.isEmpty()) {
                    CollectionMessage(
                        message = "No collection entries match this filter.",
                    )
                } else {
                    SpeciesGrid(
                        entries = filtered,
                        onOpenSpecies = onOpenSpecies,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun CollectorHeader(
    collected: Int,
    identifiedSpecies: Int,
    researchGrade: Int,
    xp: Int,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.Card(
        modifier = modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(0.dp),
    ) {
        Column(
            modifier = Modifier.padding(WildlifeSpacing.Card),
            verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Card),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RegionPill()
                RankPill(rankFor(collected))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                CollectorStat("Collected", collected.toString())
                CollectorStat("Confirmed", researchGrade.toString())
                CollectorStat("XP", xp.toString())
            }

            val target = maxOf(SAMPLE_REGION_TARGET, collected)
            val fraction = min(1f, collected.toFloat() / target)
            val percent = (fraction * 100).toInt()
            Column(verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Micro)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "$identifiedSpecies / $target species",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "$percent%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = WildlifeTheme.colors.oliveStrong,
                    )
                }
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    color = WildlifeTheme.colors.oliveStrong,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Text(
                    text = "Provisional preview · completion and rarity are a visual sample, not a curated total.",
                    style = MaterialTheme.typography.labelSmall,
                    color = WildlifeTheme.colors.mutedText,
                )
            }
        }
    }
}

@Composable
private fun RegionPill() {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = WildlifeSpacing.Small, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WildlifeSpacing.Micro),
        ) {
            Text(
                text = "Catalonia",
                style = MaterialTheme.typography.labelLarge,
                color = WildlifeTheme.colors.parchment,
            )
            Icon(
                imageVector = Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(0.dp),
            )
        }
    }
}

@Composable
private fun RankPill(rank: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = WildlifeTheme.colors.oliveStrong.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, WildlifeTheme.colors.olive),
    ) {
        Text(
            text = rank,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = WildlifeTheme.colors.oliveStrong,
            modifier = Modifier.padding(horizontal = WildlifeSpacing.Small, vertical = 6.dp),
        )
    }
}

// UI-only playful rank tied to collected count. Distinct from Profile's real XP levels; a
// lightweight "collector rank" flavour label for the header only.
private fun rankFor(collected: Int): String = when {
    collected >= 200 -> "Ranger"
    collected >= 100 -> "Field naturalist"
    collected >= 40 -> "Tracker"
    collected >= 10 -> "Observer"
    else -> "Novice"
}

@Composable
private fun CollectorStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            color = WildlifeTheme.colors.parchment,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SpeciesGrid(
    entries: List<CollectionSpecies>,
    onOpenSpecies: (CollectionSpecies) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier = modifier) {
        val columns = responsiveSpeciesGridColumns(maxWidth.value, fontScale)
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = WildlifeSpacing.Screen,
                end = WildlifeSpacing.Screen,
                bottom = WildlifeSpacing.Section,
            ),
            horizontalArrangement = Arrangement.spacedBy(WildlifeSpacing.Grid),
            verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Grid),
        ) {
            items(entries, key = CollectionSpecies::key) { entry ->
                SpeciesCard(
                    species = entry.toCardModel(),
                    onClick = { onOpenSpecies(entry) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.72f)
                        .animateItem(),
                )
            }
        }
    }
}

private fun CollectionSpecies.toCardModel() = SpeciesCardModel(
    key = key,
    label = label,
    supportingText = when {
        awaitingSpeciesIdentification -> "Awaiting species ID"
        observationCount == 1 -> "1 observation"
        else -> "$observationCount observations"
    },
    photoUrl = photoUrl,
    photoKind = photoUrl?.let { SpeciesCardPhotoKind.PERSONAL },
    supportingTextItalic = awaitingSpeciesIdentification,
    status = if (bestQualityGrade == "research") {
        SpeciesCardStatus.RESEARCH_GRADE
    } else {
        SpeciesCardStatus.NONE
    },
    rarity = sampleRarityFor(key),
)

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
                    previewSpecies("European robin", "robin", "research"),
                    previewSpecies("Iberian lynx", "lynx", "research"),
                    previewSpecies("Otter", "otter", "needs_id"),
                    previewSpecies("Golden eagle", "eagle", "needs_id"),
                    previewSpecies("Genus identification", "genus", "needs_id", awaiting = true),
                    previewSpecies("Fire salamander", "salamander", "research"),
                ),
                observationCount = 12,
                totalXp = 1_240,
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
)
