package com.wildlife.feasibility.ui.screens.shell

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.wildlife.feasibility.ui.components.WildlifeScaffold
import com.wildlife.feasibility.ui.theme.WildlifeSpacing
import com.wildlife.feasibility.ui.theme.WildlifeTheme

@Composable
fun HomeScreen(
    state: ShellUiState,
    onCapture: () -> Unit,
    onCollection: () -> Unit,
    onExplore: () -> Unit,
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
            item {
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
            item {
                Button(onClick = onCapture, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.CameraAlt, contentDescription = null)
                    Text("Record a new observation", Modifier.padding(start = WildlifeSpacing.Small))
                }
            }
            item {
                FieldStatusCard(state)
            }
            if (state.pendingHandoffs > 0 || state.draftObservations > 0) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Column(
                            modifier = Modifier.padding(WildlifeSpacing.Card),
                            verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Micro),
                        ) {
                            Text("Observation queue", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${state.draftObservations} draft · ${state.pendingHandoffs} awaiting iNaturalist",
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
                            OutlinedButton(onClick = onCapture) { Text("Review observations") }
                        }
                    }
                }
            }
            if (state.account == null) {
                item {
                    OutlinedButton(onClick = onLinkAccount, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Link, contentDescription = null)
                        Text("Link iNaturalist", Modifier.padding(start = WildlifeSpacing.Small))
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small),
                ) {
                    OutlinedButton(onClick = onCollection, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.PhotoLibrary, contentDescription = null)
                        Text("Collection", Modifier.padding(start = WildlifeSpacing.Micro))
                    }
                    OutlinedButton(onClick = onExplore, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Explore, contentDescription = null)
                        Text("Explore", Modifier.padding(start = WildlifeSpacing.Micro))
                    }
                }
            }
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
private fun FieldStatusCard(state: ShellUiState) {
    Card(
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                HomeFact("Species", state.identifiedSpecies.toString())
                HomeFact("Observations", state.observations.toString())
                HomeFact("XP", state.totalXp.toString())
            }
            Text(
                text = if (state.catalogueSpecies > 0) {
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

@Preview(showBackground = true, backgroundColor = 0xFF080B09, widthDp = 390, heightDp = 780)
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
            ),
            onCapture = {}, onCollection = {}, onExplore = {}, onLinkAccount = {},
            bottomBar = {},
        )
    }
}
