package com.wildlife.feasibility.ui.screens.collection

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
import androidx.compose.material3.MaterialTheme
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
import com.wildlife.feasibility.ui.components.SpeciesCardStatus
import com.wildlife.feasibility.ui.components.TaxonFilterRow
import com.wildlife.feasibility.ui.components.WildlifeScaffold
import com.wildlife.feasibility.ui.theme.WildlifeSpacing
import com.wildlife.feasibility.ui.theme.WildlifeTheme

enum class CollectionFilter(val label: String) {
    ALL("All"),
    RESEARCH_GRADE("Research grade"),
    NEEDS_ID("Needs ID"),
    AWAITING_SPECIES("Awaiting species"),
}

@Composable
fun CollectionScreen(
    state: CollectionUiState,
    onBack: (() -> Unit)?,
    onOpenSpecies: (CollectionSpecies) -> Unit,
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
                modifier = Modifier.padding(innerPadding),
            )
            state.errorMessage != null -> CollectionMessage(
                message = state.errorMessage,
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
                CollectionSearchBar(
                    query = query,
                    onQueryChange = { query = it },
                    modifier = Modifier.padding(horizontal = WildlifeSpacing.Screen),
                )
                CollectionStatsRow(
                    entries = state.entries.size,
                    species = state.identifiedSpeciesCount,
                    xp = state.totalXp,
                    modifier = Modifier.padding(
                        start = WildlifeSpacing.Screen,
                        end = WildlifeSpacing.Screen,
                        top = WildlifeSpacing.Small,
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
private fun SpeciesGrid(
    entries: List<CollectionSpecies>,
    onOpenSpecies: (CollectionSpecies) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier = modifier) {
        val columns = if (maxWidth < 360.dp || fontScale >= 1.3f) 2 else 3
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
                        .aspectRatio(0.94f)
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
    supportingTextItalic = awaitingSpeciesIdentification,
    status = if (bestQualityGrade == "research") {
        SpeciesCardStatus.RESEARCH_GRADE
    } else {
        SpeciesCardStatus.NONE
    },
)

@Composable
private fun CollectionStatsRow(
    entries: Int,
    species: Int,
    xp: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$entries entries · $species species",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "$xp XP",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = WildlifeTheme.colors.oliveStrong,
        )
    }
}

@Composable
private fun CollectionMessage(
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(WildlifeSpacing.Section),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF080B09, widthDp = 390, heightDp = 780)
@Composable
private fun CollectionScreenPreview() {
    WildlifeTheme {
        CollectionScreen(
            state = CollectionUiState(
                linked = true,
                entries = listOf(
                    previewSpecies("European robin", "robin", "research"),
                    previewSpecies("Observed species", "observed", "needs_id"),
                    previewSpecies("Genus identification", "genus", "needs_id", awaiting = true),
                ),
                observationCount = 5,
                totalXp = 510,
            ),
            onBack = {},
            onOpenSpecies = {},
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
