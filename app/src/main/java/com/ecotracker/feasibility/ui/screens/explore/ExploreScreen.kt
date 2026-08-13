package com.wildlife.feasibility.ui.screens.explore

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.wildlife.feasibility.CatalogueSnapshot
import com.wildlife.feasibility.NearbySpecies
import com.wildlife.feasibility.ui.components.CollectionSearchBar
import com.wildlife.feasibility.ui.components.SpeciesCard
import com.wildlife.feasibility.ui.components.SpeciesCardModel
import com.wildlife.feasibility.ui.components.SpeciesCardStatus
import com.wildlife.feasibility.ui.components.TaxonFilterRow
import com.wildlife.feasibility.ui.components.WildlifeScaffold
import com.wildlife.feasibility.ui.components.responsiveSpeciesGridColumns
import com.wildlife.feasibility.ui.theme.WildlifeSpacing
import com.wildlife.feasibility.ui.theme.WildlifeTheme
import java.text.DateFormat
import java.util.Date

enum class ExploreFilter(val label: String, val taxonGroup: String? = null) {
    ALL("All"),
    OBSERVED("Observed"),
    MISSING("Not observed"),
    MAMMALS("Mammals", "Mammalia"),
    BIRDS("Birds", "Aves"),
    REPTILES("Reptiles", "Reptilia"),
    AMPHIBIANS("Amphibians", "Amphibia"),
    BUTTERFLIES("Butterflies", "Papilionoidea"),
    DRAGONFLIES("Dragonflies", "Odonata"),
}

private enum class ExploreSection(val label: String) {
    NEARBY("Near me"),
    GUIDE("Species guide"),
}

@Composable
fun ExploreScreen(
    state: ExploreUiState,
    onBack: (() -> Unit)?,
    onSync: () -> Unit,
    onOpenTaxon: (Long) -> Unit,
    onDiscoverNearby: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
) {
    var selectedSection by rememberSaveable { mutableStateOf(ExploreSection.NEARBY) }
    var selectedFilter by rememberSaveable { mutableStateOf(ExploreFilter.ALL) }
    var query by rememberSaveable { mutableStateOf("") }
    var confirmRefresh by rememberSaveable { mutableStateOf(false) }
    val availableFilters = ExploreFilter.entries.filter { filter ->
        filter.taxonGroup == null || state.entries.any { it.taxonGroup == filter.taxonGroup }
    }
    val filtered = state.entries.filter { entry ->
        when (selectedFilter) {
            ExploreFilter.ALL -> true
            ExploreFilter.OBSERVED -> entry.observed
            ExploreFilter.MISSING -> !entry.observed
            else -> entry.taxonGroup == selectedFilter.taxonGroup
        }
    }.filter { entry ->
        query.isBlank() || entry.commonName?.contains(query, ignoreCase = true) == true ||
            entry.scientificName.contains(query, ignoreCase = true)
    }

    WildlifeScaffold(
        title = "Explore",
        onBack = onBack,
        actions = {
            if (selectedSection == ExploreSection.GUIDE) {
                IconButton(
                    onClick = {
                        if (state.snapshot == null) onSync() else confirmRefresh = true
                    },
                    enabled = !state.syncing && !state.silhouetteEnrichmentRunning,
                ) {
                    if (state.syncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(WildlifeSpacing.Small),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            Icons.Outlined.Refresh,
                            contentDescription = "Refresh stored Catalonia catalogue",
                        )
                    }
                }
            }
        },
        bottomBar = bottomBar,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            ExploreSectionSelector(
                selected = selectedSection,
                onSelected = { selectedSection = it },
            )
            when (selectedSection) {
                ExploreSection.GUIDE -> if (state.snapshot == null) {
            EmptyCatalogue(
                message = state.errorMessage
                    ?: "Download the provisional Catalonia catalogue to explore species offline.",
                syncing = state.syncing,
                onSync = onSync,
                modifier = Modifier.weight(1f),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize(),
            ) {
                ExploreIntro(
                    snapshot = state.snapshot,
                    observedCount = state.observedCount,
                    errorMessage = state.errorMessage,
                    modifier = Modifier.padding(horizontal = WildlifeSpacing.Screen),
                )
                CollectionSearchBar(
                    query = query,
                    onQueryChange = { query = it },
                    placeholder = "Search species in Catalonia",
                    modifier = Modifier.padding(
                        start = WildlifeSpacing.Screen,
                        end = WildlifeSpacing.Screen,
                        top = WildlifeSpacing.Small,
                    ),
                )
                TaxonFilterRow(
                    options = availableFilters,
                    selected = selectedFilter,
                    label = ExploreFilter::label,
                    onSelected = { selectedFilter = it },
                    modifier = Modifier.padding(
                        start = WildlifeSpacing.Screen,
                        top = WildlifeSpacing.Small,
                        bottom = WildlifeSpacing.Small,
                    ),
                )
                if (state.syncing) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (state.silhouetteEnrichmentRunning) {
                    Text(
                        text = "Improving stored silhouettes in the background…",
                        style = MaterialTheme.typography.labelSmall,
                        color = WildlifeTheme.colors.mutedText,
                        modifier = Modifier.padding(horizontal = WildlifeSpacing.Screen),
                    )
                }
                if (filtered.isEmpty()) {
                    ExploreMessage("No species match this search and filter.")
                } else {
                    ExploreGrid(
                        entries = filtered,
                        onOpenTaxon = onOpenTaxon,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
                ExploreSection.NEARBY -> NearbyDiscoveryContent(
                    state = state.nearby,
                    onDiscover = onDiscoverNearby,
                    onOpenTaxon = onOpenTaxon,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    if (confirmRefresh) {
        AlertDialog(
            onDismissRequest = { confirmRefresh = false },
            title = { Text("Refresh the Catalonia guide?") },
            text = {
                Text(
                    "Wildlife will check iNaturalist for a new provisional species list. " +
                        "Your current stored guide and media remain available if it fails.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRefresh = false
                        onSync()
                    },
                ) { Text("Refresh") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRefresh = false }) { Text("Keep current guide") }
            },
        )
    }
}

@Composable
private fun ExploreSectionSelector(
    selected: ExploreSection,
    onSelected: (ExploreSection) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = WildlifeSpacing.Screen,
                vertical = WildlifeSpacing.Small,
            ),
        horizontalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small),
    ) {
        ExploreSection.entries.forEach { section ->
            FilterChip(
                selected = section == selected,
                onClick = { onSelected(section) },
                label = { Text(section.label) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun NearbyDiscoveryContent(
    state: NearbyDiscoveryState,
    onDiscover: () -> Unit,
    onOpenTaxon: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = WildlifeSpacing.Screen,
            end = WildlifeSpacing.Screen,
            bottom = WildlifeSpacing.Section,
        ),
        verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small),
    ) {
        item {
            Text(
                text = "What has been seen nearby?",
                style = MaterialTheme.typography.titleLarge,
                color = WildlifeTheme.colors.parchment,
            )
            Text(
                text = "Wildlife samples your location once when you ask, then finds species from the provisional guide reported within ${state.radiusKm} km during this calendar month across available years.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = WildlifeSpacing.Micro),
            )
            Text(
                text = "Your location is sent directly to iNaturalist for this request. Wildlife does not track it continuously or store this search.",
                style = MaterialTheme.typography.labelMedium,
                color = WildlifeTheme.colors.mutedText,
                modifier = Modifier.padding(top = WildlifeSpacing.Small),
            )
            Button(
                onClick = onDiscover,
                enabled = !state.loading,
                modifier = Modifier.padding(top = WildlifeSpacing.Small),
            ) {
                Text(if (state.loading) "Checking nearby…" else "Check near me")
            }
        }
        if (state.loading) {
            item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        }
        state.errorMessage?.let { message ->
            item {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        if (state.requested && !state.loading && state.errorMessage == null) {
            if (state.species.isEmpty()) {
                item {
                    Text(
                        text = "No species from the provisional guide appeared in the returned research-grade reports for this area and calendar month.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                item {
                    Text(
                        text = "${state.species.size} reported species",
                        style = MaterialTheme.typography.titleMedium,
                        color = WildlifeTheme.colors.parchment,
                        modifier = Modifier.padding(top = WildlifeSpacing.Small),
                    )
                    Text(
                        text = "Ordered by iNaturalist reporting frequency. This is not rarity or a prediction that a species will be present.",
                        style = MaterialTheme.typography.labelMedium,
                        color = WildlifeTheme.colors.mutedText,
                    )
                }
                items(state.species, key = NearbySpecies::taxonId) { species ->
                    NearbySpeciesRow(species, onOpenTaxon)
                }
            }
        }
    }
}

@Composable
private fun NearbySpeciesRow(
    species: NearbySpecies,
    onOpenTaxon: (Long) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenTaxon(species.taxonId) },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(WildlifeSpacing.Card),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = species.commonName ?: species.scientificName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (species.commonName != null) {
                    Text(
                        text = species.scientificName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    )
                }
            }
            Text(
                text = "${species.observationCount} reports",
                style = MaterialTheme.typography.labelLarge,
                color = WildlifeTheme.colors.oliveStrong,
                modifier = Modifier.padding(start = WildlifeSpacing.Small),
            )
        }
    }
}

@Composable
private fun ExploreIntro(
    snapshot: CatalogueSnapshot,
    observedCount: Int,
    errorMessage: String?,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Catalonia / Provisional catalogue",
                style = MaterialTheme.typography.labelLarge,
                color = WildlifeTheme.colors.oliveStrong,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "$observedCount discovered",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val updated = snapshot.updatedAtMs?.let {
            DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it))
        } ?: "unknown"
        Text(
            text = "${snapshot.species.size} stored species / updated $updated",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Based on regional iNaturalist records; frequency is not rarity.",
            style = MaterialTheme.typography.labelSmall,
            color = WildlifeTheme.colors.mutedText,
        )
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun ExploreGrid(
    entries: List<ExploreSpecies>,
    onOpenTaxon: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier) {
        val columns = responsiveSpeciesGridColumns(maxWidth.value, fontScale)
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            contentPadding = PaddingValues(
                start = WildlifeSpacing.Screen,
                end = WildlifeSpacing.Screen,
                bottom = WildlifeSpacing.Section,
            ),
            horizontalArrangement = Arrangement.spacedBy(WildlifeSpacing.Grid),
            verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Grid),
        ) {
            items(entries, key = ExploreSpecies::taxonId) { entry ->
                SpeciesCard(
                    species = entry.card,
                    onClick = { onOpenTaxon(entry.taxonId) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.94f)
                        .animateItem(),
                )
            }
        }
    }
}

@Composable
private fun EmptyCatalogue(
    message: String,
    syncing: Boolean,
    onSync: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(WildlifeSpacing.Section),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = onSync,
            enabled = !syncing,
            modifier = Modifier.padding(top = WildlifeSpacing.Screen),
        ) {
            Text(if (syncing) "Downloading…" else "Download catalogue")
        }
    }
}

@Composable
private fun ExploreMessage(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(WildlifeSpacing.Section),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF080B09, widthDp = 390, heightDp = 780)
@Preview(
    name = "Explore large text",
    showBackground = true,
    backgroundColor = 0xFF080B09,
    widthDp = 390,
    heightDp = 780,
    fontScale = 2f,
)
@Composable
private fun ExplorePreview() {
    WildlifeTheme {
        ExploreScreen(
            state = ExploreUiState(
                snapshot = CatalogueSnapshot(
                    regionKey = "catalonia",
                    placeId = 12997,
                    version = "preview",
                    updatedAtMs = 0,
                    provisional = true,
                    species = emptyList(),
                    cached = true,
                ),
                entries = listOf(
                    previewEntry(1, "European robin", "Erithacus rubecula", "Aves", true),
                    previewEntry(2, "Iberian lynx", "Lynx pardinus", "Mammalia", false),
                ),
            ),
            onBack = {},
            onSync = {},
            onOpenTaxon = {},
            onDiscoverNearby = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF080B09, widthDp = 390, heightDp = 720)
@Composable
private fun NearbyErrorPreview() {
    WildlifeTheme {
        NearbyDiscoveryContent(
            state = NearbyDiscoveryState(
                requested = true,
                errorMessage = "Location is unavailable. Try again with device location enabled.",
            ),
            onDiscover = {},
            onOpenTaxon = {},
        )
    }
}

private fun previewEntry(
    id: Long,
    name: String,
    scientificName: String,
    group: String,
    observed: Boolean,
) = ExploreSpecies(
    taxonId = id,
    taxonGroup = group,
    commonName = name,
    scientificName = scientificName,
    observed = observed,
    card = SpeciesCardModel(
        key = "taxon:$id",
        label = name,
        supportingText = scientificName,
        photoUrl = null,
        supportingTextItalic = true,
        status = if (observed) SpeciesCardStatus.RESEARCH_GRADE else SpeciesCardStatus.NONE,
    ),
)
