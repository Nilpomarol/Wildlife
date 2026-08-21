package com.wildlife.feasibility.ui.screens.shell

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Biotech
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.wildlife.feasibility.VerifiedAccount
import com.wildlife.feasibility.WildlifeNetworkIdentity
import com.wildlife.feasibility.ui.components.WildlifeScaffold
import com.wildlife.feasibility.ui.theme.WildlifeSpacing
import com.wildlife.feasibility.ui.theme.WildlifeTheme
import java.text.DateFormat
import java.util.Date

@Composable
fun HomeScreen(
    state: ShellUiState,
    onCapture: () -> Unit,
    onCollection: () -> Unit,
    onExplore: () -> Unit,
    onMyMap: () -> Unit,
    onObservations: () -> Unit,
    onOpenSpecies: (Long) -> Unit,
    mappedObservationCount: Int,
    onLinkAccount: () -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    WildlifeScaffold(title = "Wildlife", bottomBar = bottomBar) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = WildlifeSpacing.Screen,
                end = WildlifeSpacing.Screen,
                bottom = WildlifeSpacing.Section,
            ),
            verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Screen),
        ) {
            item { Greeting(state) }

            if (state.account == null) {
                item { UnlinkedWelcome(onLinkAccount = onLinkAccount, onExplore = onExplore) }
            } else {
                val discovery = state.latestDiscovery
                if (discovery != null) {
                    item {
                        LatestDiscoveryHero(
                            highlight = discovery,
                            onClick = {
                                discovery.taxonId?.let(onOpenSpecies) ?: onCollection()
                            },
                        )
                    }
                    item { FieldRecordCard(state = state, onClick = onCollection) }
                } else {
                    item { EmptyCollectionCard(onCapture = onCapture) }
                }

                item { MyMapCard(mappedObservationCount = mappedObservationCount, onMyMap = onMyMap) }
            }

            item { ObservationQueueCard(state = state, onReview = onObservations) }

            state.errorMessage?.let { message ->
                item {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun Greeting(state: ShellUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Micro)) {
        Text(
            text = state.account?.let { "Welcome back, ${it.login}" }
                ?: "Your field guide to Catalonia",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = if (state.account == null) {
                "Explore wildlife now. Link iNaturalist when you want a personal collection."
            } else {
                "Record one sighting at a time and let iNaturalist remain the scientific record."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LatestDiscoveryHero(
    highlight: HomeHighlight,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(208.dp),
        ) {
            if (highlight.photoUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(highlight.photoUrl)
                        .setHeader("User-Agent", WildlifeNetworkIdentity.REFERENCE_MEDIA_USER_AGENT)
                        .build(),
                    contentDescription = "${highlight.label}, your most recent observation photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = highlight.label.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.displayLarge,
                        color = WildlifeTheme.colors.silhouette,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.4f to Color.Transparent,
                            1f to Color(0xE6080B09),
                        ),
                    ),
            )
            if (highlight.researchGrade) {
                Surface(
                    shape = CircleShape,
                    color = WildlifeTheme.colors.confirmed.copy(alpha = 0.94f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(WildlifeSpacing.Small)
                        .size(30.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Biotech,
                            contentDescription = "Research grade",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(WildlifeSpacing.Card),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "Latest discovery",
                    style = MaterialTheme.typography.labelMedium,
                    color = WildlifeTheme.colors.oliveStrong,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = highlight.label,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = latestDiscoverySupport(highlight),
                    style = MaterialTheme.typography.labelMedium,
                    fontStyle = if (highlight.awaitingSpeciesIdentification) {
                        FontStyle.Italic
                    } else {
                        FontStyle.Normal
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun latestDiscoverySupport(highlight: HomeHighlight): String {
    val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(highlight.observedAtMs))
    val count = if (highlight.observationCount == 1) {
        "1 observation"
    } else {
        "${highlight.observationCount} observations"
    }
    return if (highlight.awaitingSpeciesIdentification) {
        "Awaiting species ID · $date"
    } else {
        "$date · $count"
    }
}

@Composable
private fun FieldRecordCard(state: ShellUiState, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(
            modifier = Modifier.padding(WildlifeSpacing.Card),
            verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Card),
        ) {
            Text("Field record", style = MaterialTheme.typography.titleMedium)
            state.regionalProgress?.let { progress ->
                Text(
                    text = "${progress.displayName} · ${progress.observedSpecies} / ${progress.totalSpecies}",
                    style = MaterialTheme.typography.labelMedium,
                    color = WildlifeTheme.colors.oliveStrong,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                HomeFact("Species", state.identifiedSpecies.toString())
                HomeFact("Observations", state.observations.toString())
                HomeFact("XP", state.totalXp.toString())
            }
            Text(
                text = state.regionalProgress?.let { progress ->
                    "Essentials ${progress.essentialsObserved}/${progress.essentialsTotal} · " +
                        "Icons ${progress.iconsObserved}/${progress.iconsTotal}"
                } ?: if (state.catalogueSpecies > 0) {
                    "${state.catalogueSpecies} species available in the provisional Catalonia guide"
                } else {
                    "Catalonia guide not downloaded yet"
                },
                style = MaterialTheme.typography.labelMedium,
                color = WildlifeTheme.colors.mutedText,
            )
        }
    }
}

@Composable
private fun HomeFact(label: String, value: String) {
    Column {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
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
private fun EmptyCollectionCard(onCapture: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(
            modifier = Modifier.padding(WildlifeSpacing.Card),
            verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small),
        ) {
            Text("Start your collection", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Record your first sighting and it will appear here once iNaturalist confirms it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onCapture) {
                Icon(Icons.Outlined.CameraAlt, contentDescription = null)
                Text("Record a sighting", Modifier.padding(start = WildlifeSpacing.Small))
            }
        }
    }
}

@Composable
private fun ObservationQueueCard(state: ShellUiState, onReview: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(
            modifier = Modifier.padding(WildlifeSpacing.Card),
            verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Micro),
        ) {
            Text("Your observations", style = MaterialTheme.typography.titleMedium)
            Text(
                when {
                    state.account == null -> "Review Wildlife drafts and link iNaturalist to check public submissions."
                    state.draftObservations > 0 || state.pendingHandoffs > 0 ->
                        "${state.draftObservations} draft · ${state.pendingHandoffs} awaiting iNaturalist"
                    else -> "Review your public observation history and local map visibility."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.pendingMatchesReady > 0) {
                Text(
                    "${state.pendingMatchesReady} ready to inspect and confirm",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            OutlinedButton(
                onClick = onReview,
                modifier = Modifier.padding(top = WildlifeSpacing.Micro),
            ) { Text("Open observations") }
        }
    }
}

@Composable
private fun MyMapCard(mappedObservationCount: Int, onMyMap: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(
            modifier = Modifier.padding(WildlifeSpacing.Card),
            verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Micro),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small)) {
                Icon(Icons.Outlined.Map, contentDescription = null)
                Text("My observation map", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                if (mappedObservationCount > 0) {
                    "$mappedObservationCount public observations mapped in privacy-safe areas"
                } else {
                    "Your public observation history will appear here when locations are available"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = onMyMap,
                modifier = Modifier.padding(top = WildlifeSpacing.Micro),
            ) { Text("Open my map") }
        }
    }
}

@Composable
private fun UnlinkedWelcome(onLinkAccount: () -> Unit, onExplore: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(
            modifier = Modifier.padding(WildlifeSpacing.Card),
            verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small),
        ) {
            Text("Browse the regional field guide", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "The Catalonia species guide and \"what can I see here\" work right away. " +
                    "Link an iNaturalist account when you want to build a personal collection.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onExplore, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Explore, contentDescription = null)
                Text("Explore Catalonia", Modifier.padding(start = WildlifeSpacing.Small))
            }
            OutlinedButton(onClick = onLinkAccount, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Link, contentDescription = null)
                Text("Link iNaturalist", Modifier.padding(start = WildlifeSpacing.Small))
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF080B09, widthDp = 390, heightDp = 820)
@Composable
private fun HomePreview() {
    WildlifeTheme {
        HomeScreen(
            state = ShellUiState(
                collectionEntries = 57,
                identifiedSpecies = 55,
                observations = 89,
                totalXp = 510,
                catalogueSpecies = 568,
                pendingHandoffs = 1,
                account = VerifiedAccount(1, "naturalist", 0),
                latestDiscovery = HomeHighlight(
                    taxonId = 42,
                    label = "European robin",
                    photoUrl = null,
                    observedAtMs = 1_723_000_000_000,
                    researchGrade = true,
                    observationCount = 3,
                    awaitingSpeciesIdentification = false,
                ),
            ),
            onCapture = {}, onCollection = {}, onExplore = {}, onMyMap = {}, onObservations = {},
            onOpenSpecies = {}, mappedObservationCount = 72, onLinkAccount = {},
            bottomBar = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF080B09, widthDp = 390, heightDp = 720)
@Composable
private fun HomeUnlinkedPreview() {
    WildlifeTheme {
        HomeScreen(
            state = ShellUiState(catalogueSpecies = 568),
            onCapture = {}, onCollection = {}, onExplore = {}, onMyMap = {}, onObservations = {},
            onOpenSpecies = {}, mappedObservationCount = 0, onLinkAccount = {},
            bottomBar = {},
        )
    }
}
