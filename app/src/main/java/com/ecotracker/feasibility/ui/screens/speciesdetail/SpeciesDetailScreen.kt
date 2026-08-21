package com.wildlife.feasibility.ui.screens.speciesdetail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Biotech
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.wildlife.feasibility.WildlifeNetworkIdentity
import com.wildlife.feasibility.ui.components.EncounterTrace
import com.wildlife.feasibility.ui.components.RegionalCollectionMark
import com.wildlife.feasibility.ui.components.RegionalCollectionStamp
import com.wildlife.feasibility.ui.components.RegionalLegendMark
import com.wildlife.feasibility.ui.components.SpeciesCardRarity
import com.wildlife.feasibility.ui.theme.WildlifeSpacing
import com.wildlife.feasibility.ui.theme.WildlifeTheme
import java.text.DateFormat
import java.util.Date

@Composable
fun SpeciesDetailScreen(
    state: SpeciesDetailUiState,
    onBack: () -> Unit,
    onOpenTaxon: (Long) -> Unit,
    onOpenObservation: (String) -> Unit,
    onSeeAllObservations: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onRetryMedia: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { innerPadding ->
        if (state.errorMessage != null) {
            DetailError(
                message = state.errorMessage,
                onBack = onBack,
                onRetry = onRetryMedia,
                modifier = Modifier.padding(innerPadding),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(bottom = WildlifeSpacing.Large),
            ) {
                if (state.enriching) {
                    item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                }
                item {
                    SpeciesHero(
                        state = state,
                        onBack = onBack,
                        onOpenReferenceSource = {
                            state.referencePhotoSourceUrl?.let(onOpenUrl)
                                ?: onOpenTaxon(state.taxonId)
                        },
                        onRetryMedia = onRetryMedia,
                    )
                }
                item {
                    SpeciesIdentity(
                        state = state,
                        modifier = Modifier.padding(
                            horizontal = WildlifeSpacing.Screen,
                            vertical = WildlifeSpacing.Screen,
                        ),
                    )
                }
                item {
                    DiscoveryPanel(
                        state = state,
                        modifier = Modifier.padding(horizontal = WildlifeSpacing.Screen),
                    )
                }
                state.regionalContext?.let { context ->
                    item {
                        RegionalContextPanel(
                            context = context,
                            modifier = Modifier.padding(
                                horizontal = WildlifeSpacing.Screen,
                                vertical = WildlifeSpacing.Small,
                            ),
                        )
                    }
                }
                item {
                    FactPanel(
                        state = state,
                        modifier = Modifier.padding(
                            horizontal = WildlifeSpacing.Screen,
                            vertical = WildlifeSpacing.Screen,
                        ),
                    )
                }
                if (state.aboutSummary != null) {
                    item {
                        AboutSection(
                            summary = state.aboutSummary,
                            wikipediaUrl = state.wikipediaUrl,
                            onOpenUrl = onOpenUrl,
                            modifier = Modifier.padding(horizontal = WildlifeSpacing.Screen),
                        )
                    }
                }
                if (state.observations.isNotEmpty()) {
                    item {
                        ObservationSection(
                            observations = state.observations,
                            onOpenObservation = onOpenObservation,
                            onSeeAllObservations = onSeeAllObservations,
                        )
                    }
                }
                if (state.silhouetteSourceUrl != null) {
                    item {
                        SilhouetteAttributionSection(
                            state = state,
                            onOpenUrl = onOpenUrl,
                            modifier = Modifier.padding(
                                horizontal = WildlifeSpacing.Screen,
                                vertical = WildlifeSpacing.Section,
                            ),
                        )
                    }
                }
                if (!state.enriching && state.mediaMessage != null) {
                    item {
                        Text(
                            text = state.mediaMessage,
                            style = MaterialTheme.typography.labelSmall,
                            color = WildlifeTheme.colors.mutedText,
                            modifier = Modifier.padding(horizontal = WildlifeSpacing.Screen),
                        )
                    }
                }
                if (state.enrichmentMessage != null) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = WildlifeSpacing.Screen),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = state.enrichmentMessage,
                                style = MaterialTheme.typography.labelSmall,
                                color = WildlifeTheme.colors.mutedText,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = onRetryMedia) { Text("Try again") }
                        }
                    }
                }
                item {
                    OutlinedButton(
                        onClick = { onOpenTaxon(state.taxonId) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = WildlifeSpacing.Screen,
                                end = WildlifeSpacing.Screen,
                                top = WildlifeSpacing.Section,
                            ),
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
                        Text(
                            text = "Open species in iNaturalist",
                            modifier = Modifier.padding(start = WildlifeSpacing.Small),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RegionalContextPanel(
    context: SpeciesRegionalContext,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(modifier = Modifier.padding(WildlifeSpacing.Card)) {
            Text(context.regionName, style = MaterialTheme.typography.labelMedium, color = WildlifeTheme.colors.parchment)
            val rarity = when (context.rarity) {
                com.wildlife.feasibility.EncounterRarity.UNKNOWN -> "Rarity under editorial review"
                com.wildlife.feasibility.EncounterRarity.COMMON -> "Common"
                com.wildlife.feasibility.EncounterRarity.UNCOMMON -> "Uncommon"
                com.wildlife.feasibility.EncounterRarity.RARE -> "Rare"
                com.wildlife.feasibility.EncounterRarity.VERY_RARE -> "Very rare"
            }
            Row(
                modifier = Modifier.padding(top = WildlifeSpacing.Small),
                horizontalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                context.rarity.toCardRarity()?.let { EncounterTrace(it) }
                Text(rarity, style = MaterialTheme.typography.bodyMedium, color = WildlifeTheme.colors.oliveStrong)
                if (context.prestige == com.wildlife.feasibility.RegionalPrestige.LEGENDARY) {
                    RegionalLegendMark()
                    Text("Regional Legend", style = MaterialTheme.typography.bodyMedium, color = WildlifeTheme.colors.gold)
                }
            }
            val achievementLabels = context.achievementLabels
            if (achievementLabels.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(top = WildlifeSpacing.Small),
                    horizontalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small),
                ) {
                    if ("essentials" in achievementLabels) RegionalCollectionStamp(RegionalCollectionMark.ESSENTIAL)
                    if ("icons" in achievementLabels) RegionalCollectionStamp(RegionalCollectionMark.ICON)
                }
            }
        }
    }
}

private fun com.wildlife.feasibility.EncounterRarity.toCardRarity(): SpeciesCardRarity? = when (this) {
    com.wildlife.feasibility.EncounterRarity.UNKNOWN -> null
    com.wildlife.feasibility.EncounterRarity.COMMON -> SpeciesCardRarity.COMMON
    com.wildlife.feasibility.EncounterRarity.UNCOMMON -> SpeciesCardRarity.UNCOMMON
    com.wildlife.feasibility.EncounterRarity.RARE -> SpeciesCardRarity.RARE
    com.wildlife.feasibility.EncounterRarity.VERY_RARE -> SpeciesCardRarity.VERY_RARE
}

@Composable
private fun SpeciesHero(
    state: SpeciesDetailUiState,
    onBack: () -> Unit,
    onOpenReferenceSource: () -> Unit,
    onRetryMedia: () -> Unit,
) {
    val context = LocalContext.current
    var activePhotoUrl by remember(
        state.heroPhotoUrl,
        state.heroPhotoFallbackUrl,
        state.mediaAttempt,
    ) {
        mutableStateOf(state.heroPhotoUrl)
    }
    var imageFailed by remember(
        state.heroPhotoUrl,
        state.heroPhotoFallbackUrl,
        state.mediaAttempt,
    ) {
        mutableStateOf(state.heroPhotoUrl == null)
    }
    var activeSilhouetteUrl by remember(
        state.silhouetteUrl,
        state.silhouetteFallbackUrl,
        state.mediaAttempt,
    ) {
        mutableStateOf(state.silhouetteUrl)
    }
    var silhouetteFailed by remember(
        state.silhouetteUrl,
        state.silhouetteFallbackUrl,
        state.mediaAttempt,
    ) {
        mutableStateOf(state.silhouetteUrl == null)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(290.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (!imageFailed) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(activePhotoUrl)
                    .setHeader(
                        "User-Agent",
                        WildlifeNetworkIdentity.REFERENCE_MEDIA_USER_AGENT,
                    )
                    .build(),
                contentDescription = if (state.heroFromCatalogue) {
                    "${state.commonName}, attributed reference photo"
                } else {
                    "${state.commonName}, your observation photo"
                },
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onError = {
                    if (activePhotoUrl != state.heroPhotoFallbackUrl &&
                        state.heroPhotoFallbackUrl != null
                    ) {
                        activePhotoUrl = state.heroPhotoFallbackUrl
                    } else {
                        imageFailed = true
                    }
                },
            )
        } else if (!silhouetteFailed) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(activeSilhouetteUrl)
                    .setHeader(
                        "User-Agent",
                        WildlifeNetworkIdentity.REFERENCE_MEDIA_USER_AGENT,
                    )
                    .build(),
                contentDescription = if (state.silhouetteMatchRank == "species") {
                    "Silhouette for ${state.commonName}"
                } else {
                    "Representative silhouette for ${state.commonName}"
                },
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(WildlifeTheme.colors.silhouette),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(56.dp),
                onError = {
                    if (activeSilhouetteUrl != state.silhouetteFallbackUrl &&
                        state.silhouetteFallbackUrl != null
                    ) {
                        activeSilhouetteUrl = state.silhouetteFallbackUrl
                    } else {
                        silhouetteFailed = true
                    }
                },
            )
        } else {
            Text(
                text = state.commonName.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.displayLarge,
                color = WildlifeTheme.colors.silhouette,
                modifier = Modifier.align(Alignment.Center),
            )
            Text(
                text = "Reference photo unavailable",
                style = MaterialTheme.typography.labelMedium,
                color = WildlifeTheme.colors.mutedText,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = 70.dp),
            )
            OutlinedButton(
                onClick = onRetryMedia,
                enabled = !state.enriching,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = 160.dp),
            ) {
                Icon(Icons.Outlined.Refresh, contentDescription = null)
                Text("Try again", modifier = Modifier.padding(start = WildlifeSpacing.Small))
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color(0x6B080B09),
                        0.42f to Color.Transparent,
                        1f to Color(0xE6080B09),
                    ),
                ),
        )
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(WildlifeSpacing.Small),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
        if (!imageFailed) {
            if (state.heroFromCatalogue && state.heroAttribution != null) {
                Surface(
                    onClick = onOpenReferenceSource,
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .defaultMinSize(minHeight = 48.dp)
                        .padding(WildlifeSpacing.Screen),
                ) {
                    Row(
                        modifier = Modifier.padding(
                            horizontal = WildlifeSpacing.Small,
                            vertical = WildlifeSpacing.Micro,
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small),
                    ) {
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text(
                                text = "Reference photo",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Text(
                                text = state.heroAttribution,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Outlined.OpenInNew,
                            contentDescription = state.referencePhotoSourceLabel,
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            } else {
                Text(
                    text = "Your observation photo",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(WildlifeSpacing.Screen),
                )
            }
        }
    }
}

@Composable
private fun SpeciesIdentity(state: SpeciesDetailUiState, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Micro)) {
        Text(
            text = state.commonName,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = state.scientificName ?: "Scientific name unavailable offline",
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DiscoveryPanel(state: SpeciesDetailUiState, modifier: Modifier = Modifier) {
    val regionSuffix = state.regionalContext?.let { " in ${it.regionName}" }.orEmpty()
    val title = when {
        state.researchGrade -> "Research-grade discovery$regionSuffix"
        state.observed -> "Species discovered$regionSuffix"
        else -> "Not observed yet$regionSuffix"
    }
    val explanation = when {
        state.researchGrade -> "The iNaturalist community has confirmed at least one of your observations for this region."
        state.observed -> "This species is in this regional collection and is still awaiting research grade."
        else -> "Record this species in this region through Wildlife and iNaturalist to add it to the collection."
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Row(
            modifier = Modifier.padding(WildlifeSpacing.Card),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WildlifeSpacing.Card),
        ) {
            Surface(
                shape = CircleShape,
                color = if (state.observed) {
                    WildlifeTheme.colors.confirmed
                } else {
                    MaterialTheme.colorScheme.surface
                },
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = when {
                            state.researchGrade -> Icons.Outlined.Biotech
                            state.observed -> Icons.Outlined.Check
                            else -> Icons.Outlined.Visibility
                        },
                        contentDescription = null,
                        tint = if (state.observed) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = explanation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FactPanel(state: SpeciesDetailUiState, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(WildlifeSpacing.Card),
            verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Card),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small)) {
                Fact("Group", state.taxonGroup ?: "Unavailable", Modifier.weight(1f))
                Fact("Family", state.familyName ?: "Unavailable", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small)) {
                Fact("Seen by you", state.observations.size.toString(), Modifier.weight(1f))
                Fact(
                    "Global IUCN status",
                    friendlyConservationStatus(state.conservationStatus),
                    Modifier.weight(1f),
                )
            }
        }
    }
}

private fun friendlyConservationStatus(value: String?): String = when (value?.lowercase()) {
    "lc" -> "Least concern"
    "nt" -> "Near threatened"
    "vu" -> "Vulnerable"
    "en" -> "Endangered"
    "cr" -> "Critically endangered"
    "ew" -> "Extinct in the wild"
    "ex" -> "Extinct"
    "dd" -> "Data deficient"
    else -> "Unavailable"
}

@Composable
private fun AboutSection(
    summary: String,
    wikipediaUrl: String?,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small),
    ) {
        Text("About", style = MaterialTheme.typography.titleLarge)
        Text(
            text = summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (wikipediaUrl != null) {
            TextButton(onClick = { onOpenUrl(wikipediaUrl) }, contentPadding = PaddingValues(0.dp)) {
                Text("Read more on Wikipedia")
            }
        }
    }
}

@Composable
private fun SilhouetteAttributionSection(
    state: SpeciesDetailUiState,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val attribution = state.silhouetteAttribution ?: "PhyloPic contributor"
    val licence = state.silhouetteLicenseCode?.uppercase()?.replace('-', ' ')
    Column(modifier, verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text("Silhouette", style = MaterialTheme.typography.titleMedium)
        Text(
            text = buildString {
                append(attribution)
                if (licence != null) append(" / $licence")
                if (state.silhouetteMatchRank != "species") {
                    append(" / ${silhouetteLabel(state).lowercase()}")
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(
            onClick = { onOpenUrl(state.silhouetteSourceUrl!!) },
            contentPadding = PaddingValues(0.dp),
        ) { Text("View silhouette source on PhyloPic") }
    }
}

private fun silhouetteLabel(state: SpeciesDetailUiState): String {
    val taxonName = state.silhouetteTaxonName?.takeIf(String::isNotBlank)
    return when (state.silhouetteMatchRank) {
        "species" -> "Species silhouette"
        "genus" -> "Representative ${taxonName ?: "genus"} silhouette"
        "family" -> "Representative ${taxonName ?: "family"} silhouette"
        "order" -> "Representative ${taxonName ?: "order"} silhouette"
        else -> "Broad ${taxonName ?: "group"} silhouette"
    }
}

@Composable
private fun Fact(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = WildlifeTheme.colors.mutedText,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ObservationSection(
    observations: List<SpeciesDetailObservation>,
    onOpenObservation: (String) -> Unit,
    onSeeAllObservations: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = WildlifeSpacing.Screen),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Your observations",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            TextButton(onClick = onSeeAllObservations) {
                Text("See all (${observations.size})")
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = WildlifeSpacing.Screen),
            horizontalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small),
        ) {
            items(observations, key = SpeciesDetailObservation::uuid) { observation ->
                ObservationTile(
                    observation = observation,
                    onClick = { onOpenObservation(observation.uuid) },
                )
            }
        }
    }
}

@Composable
private fun ObservationTile(observation: SpeciesDetailObservation, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(116.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                if (observation.photoUrl != null) {
                    AsyncImage(
                        model = observation.photoUrl,
                        contentDescription = "Your observation photo from ${
                            DateFormat.getDateInstance(DateFormat.MEDIUM)
                                .format(Date(observation.observedAtMs))
                        }",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        Icons.Outlined.PhotoCamera,
                        contentDescription = "Photo unavailable",
                        tint = WildlifeTheme.colors.silhouette,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                if (observation.qualityGrade == "research") {
                    Surface(
                        shape = CircleShape,
                        color = WildlifeTheme.colors.confirmed,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(WildlifeSpacing.Micro),
                    ) {
                        Icon(
                            Icons.Outlined.Biotech,
                            contentDescription = "Research grade",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(WildlifeSpacing.Micro),
                        )
                    }
                }
            }
            Text(
                text = DateFormat.getDateInstance(DateFormat.MEDIUM)
                    .format(Date(observation.observedAtMs)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(WildlifeSpacing.Small),
            )
        }
    }
}

@Composable
private fun DetailError(
    message: String,
    onBack: () -> Unit,
    onRetry: () -> Unit,
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
        Row(
            modifier = Modifier.padding(top = WildlifeSpacing.Screen),
            horizontalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small),
        ) {
            TextButton(onClick = onBack) { Text("Back") }
            Button(onClick = onRetry) { Text("Try again") }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF080B09, widthDp = 390, heightDp = 820)
@Preview(
    name = "Species detail large text",
    showBackground = true,
    backgroundColor = 0xFF080B09,
    widthDp = 390,
    heightDp = 820,
    fontScale = 2f,
)
@Composable
private fun SpeciesDetailPreview() {
    WildlifeTheme {
        SpeciesDetailScreen(
            state = SpeciesDetailUiState(
                taxonId = 1,
                commonName = "European robin",
                scientificName = "Erithacus rubecula",
                taxonGroup = "Birds",
                familyName = "Muscicapidae",
                aboutSummary = "A familiar small bird found across Europe.",
                wikipediaUrl = null,
                conservationStatus = "lc",
                conservationAuthority = "IUCN Red List",
                conservationUrl = null,
                observed = true,
                researchGrade = true,
                bestQualityLabel = "Research grade",
                heroPhotoUrl = null,
                heroFromCatalogue = false,
                heroAttribution = null,
                silhouetteUrl = null,
                silhouetteSourceUrl = null,
                silhouetteAttribution = null,
                silhouetteLicenseCode = null,
                silhouetteTaxonName = null,
                silhouetteMatchRank = null,
                catalogueVersion = "preview",
                observations = listOf(
                    SpeciesDetailObservation("preview", 0, "research", null),
                ),
            ),
            onBack = {},
            onOpenTaxon = {},
            onOpenObservation = {},
            onSeeAllObservations = {},
            onOpenUrl = {},
            onRetryMedia = {},
        )
    }
}
