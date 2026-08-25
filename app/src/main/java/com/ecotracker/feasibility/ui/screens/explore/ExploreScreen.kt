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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.wildlife.feasibility.NearbySpecies
import com.wildlife.feasibility.ui.components.CollectionSearchBar
import com.wildlife.feasibility.ui.components.NearbySpeciesRow
import com.wildlife.feasibility.ui.components.RowRule
import com.wildlife.feasibility.ui.components.RegionSelector
import com.wildlife.feasibility.ui.components.MediaPrefetchStatus
import com.wildlife.feasibility.ui.screens.map.PersonalMapContent
import com.wildlife.feasibility.ui.components.SpeciesCard
import com.wildlife.feasibility.ui.components.SpeciesGrid
import com.wildlife.feasibility.ui.components.SpeciesCardModel
import com.wildlife.feasibility.ui.components.SpeciesCardStatus
import com.wildlife.feasibility.ui.components.TaxonFilterRow
import com.wildlife.feasibility.ui.components.WildlifeScaffold
import com.wildlife.feasibility.ui.components.WildlifeLoadingState
import com.wildlife.feasibility.ui.components.responsiveSpeciesGridColumns
import com.wildlife.feasibility.ui.theme.WildlifeSpacing
import com.wildlife.feasibility.ui.theme.WildlifeTheme

enum class ExploreFilter(val label: String, val taxonGroup: String? = null) {
    ALL("All"),
    OBSERVED("Observed"),
    MISSING("Not observed"),
    MAMMALS("Mammals", "mammals"),
    BIRDS("Birds", "birds"),
    REPTILES("Reptiles", "reptiles"),
    AMPHIBIANS("Amphibians", "amphibians"),
    FISH("Fish", "fish"),
}

enum class ExploreSection(val label: String) {
    GUIDE("Species guide"),
    NEARBY("Near me"),
    MAP("My map"),
}

@Composable
fun ExploreScreen(
    state: ExploreUiState,
    onBack: (() -> Unit)?,
    onRefresh: () -> Unit,
    onOpenTaxon: (Long) -> Unit,
    onDiscoverNearby: () -> Unit,
    onSelectRegion: (String) -> Unit,
    onOpenObservation: (String) -> Unit,
    onMapVisibilityChanged: (String, Boolean) -> Unit,
    onVisibleTaxaChanged: (Set<Long>) -> Unit,
    bottomBar: @Composable () -> Unit = {},
    selectedSection: ExploreSection,
    onSectionChange: (ExploreSection) -> Unit,
) {
    var selectedFilter by rememberSaveable { mutableStateOf(ExploreFilter.ALL) }
    var query by rememberSaveable { mutableStateOf("") }
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
            RegionSelector(
                selected = state.installedCatalogues.firstOrNull {
                    it.regionKey == state.browsedCatalogue?.regionKey
                },
                catalogues = state.installedCatalogues,
                onSelectRegion = onSelectRegion,
                modifier = Modifier.padding(end = WildlifeSpacing.Small),
            )
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
                onSelected = onSectionChange,
            )
            when (selectedSection) {
            ExploreSection.GUIDE -> if (state.isLoading && state.browsedCatalogue == null) {
            WildlifeLoadingState(
                label = "Opening the regional species guide…",
                modifier = Modifier.weight(1f),
            )
        } else if (state.browsedCatalogue == null) {
            EmptyCatalogue(
                message = state.errorMessage
                    ?: "The installed regional guide could not be read.",
                onRefresh = onRefresh,
                modifier = Modifier.weight(1f),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize(),
            ) {
                ExploreIntro(
                    catalogue = state.browsedCatalogue,
                    observedCount = state.observedCount,
                    errorMessage = state.errorMessage,
                    modifier = Modifier.padding(horizontal = WildlifeSpacing.Screen),
                )
                MediaPrefetchStatus(
                    state.mediaPrefetch,
                    Modifier.padding(horizontal = WildlifeSpacing.Screen),
                )
                CollectionSearchBar(
                    query = query,
                    onQueryChange = { query = it },
                    placeholder = "Search this regional guide",
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
                if (filtered.isEmpty()) {
                    ExploreMessage("No species match this search and filter.")
                } else {
                    ExploreGrid(
                        entries = filtered,
                        onOpenTaxon = onOpenTaxon,
                        onVisibleTaxaChanged = onVisibleTaxaChanged,
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

                ExploreSection.MAP -> PersonalMapContent(
                    accountLinked = state.accountLinked,
                    map = state.personalMap,
                    regionalProgress = state.regionalMapProgress,
                    onOpenObservation = onOpenObservation,
                    onMapVisibilityChanged = onMapVisibilityChanged,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExploreSectionSelector(
    selected: ExploreSection,
    onSelected: (ExploreSection) -> Unit,
) {
    SecondaryTabRow(
        selectedTabIndex = selected.ordinal,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = WildlifeTheme.colors.oliveStrong,
    ) {
        ExploreSection.entries.forEach { section ->
            Tab(
                selected = section == selected,
                onClick = { onSelected(section) },
                text = {
                    Text(
                        text = section.label,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                    )
                },
                selectedContentColor = WildlifeTheme.colors.oliveStrong,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
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
                text = "Wildlife samples your location once when you ask, then finds species in your selected regional guide reported within ${state.radiusKm} km during this calendar month across available years.",
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
                        text = "No species from the selected regional guide appeared in the returned research-grade reports for this area and calendar month.",
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
                // Ruled, like the Home extract: the row is a printed table line now, and
                // an unruled run of them loses the ranking the ordering exists to show.
                itemsIndexed(state.species, key = { _, s -> s.taxonId }) { index, species ->
                    Column {
                        if (index > 0) RowRule()
                        NearbySpeciesRow(species, onOpenTaxon)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExploreIntro(
    catalogue: RegionalExploreCatalogue,
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
                text = catalogue.displayName,
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
        Text(
            text = "${catalogue.speciesCount} species · catalogue ${catalogue.version}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "A frozen regional catalogue. Encounter rarity is calculated for this region.",
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
    onVisibleTaxaChanged: (Set<Long>) -> Unit,
    modifier: Modifier = Modifier,
) {
    SpeciesGrid(
        entries = entries, key = ExploreSpecies::taxonId, model = ExploreSpecies::card,
        onClick = { onOpenTaxon(it.taxonId) }, modifier = modifier, cardAspectRatio = 0.94f,
        onVisibleEntriesChanged = { visible ->
            visible.mapTo(linkedSetOf(), ExploreSpecies::taxonId).let(onVisibleTaxaChanged)
        },
    )
}

@Composable
private fun EmptyCatalogue(
    message: String,
    onRefresh: () -> Unit,
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
            onClick = onRefresh,
            modifier = Modifier.padding(top = WildlifeSpacing.Screen),
        ) {
            Text("Try again")
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
                browsedCatalogue = RegionalExploreCatalogue(
                    regionKey = "mediterranean_europe",
                    displayName = "Mediterranean Europe",
                    version = "preview",
                    speciesCount = 800,
                ),
                entries = listOf(
                    previewEntry(1, "European robin", "Erithacus rubecula", "Aves", true),
                    previewEntry(2, "Iberian lynx", "Lynx pardinus", "Mammalia", false),
                ),
            ),
            onBack = {},
            onRefresh = {},
            onOpenTaxon = {},
            onDiscoverNearby = {},
            onSelectRegion = {},
            onOpenObservation = {},
            onMapVisibilityChanged = { _, _ -> },
            onVisibleTaxaChanged = {},
            selectedSection = ExploreSection.GUIDE,
            onSectionChange = {},
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
        collected = observed,
        status = if (observed) SpeciesCardStatus.RESEARCH_GRADE else SpeciesCardStatus.NONE,
    ),
)
