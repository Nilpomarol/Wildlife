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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.wildlife.feasibility.ui.components.FieldGuidePage
import com.wildlife.feasibility.ui.components.FieldMarkPill
import com.wildlife.feasibility.ui.components.PlateSurface
import com.wildlife.feasibility.ui.components.RegionalCollectionMark
import com.wildlife.feasibility.ui.components.RegionalCollectionStamp
import com.wildlife.feasibility.ui.components.SectionRule
import com.wildlife.feasibility.ui.components.SpeciesCardRarity
import com.wildlife.feasibility.ui.components.WildlifeLoadingState
import com.wildlife.feasibility.ui.components.ObservationDensityMap
import com.wildlife.feasibility.ui.art.TaxonSilhouette
import com.wildlife.feasibility.ui.theme.WildlifeSpacing
import com.wildlife.feasibility.ui.theme.WildlifeTheme
import com.wildlife.feasibility.ui.theme.FieldLabelStyle
import com.wildlife.feasibility.ui.theme.FieldStampStyle
import com.wildlife.feasibility.ui.theme.FieldTallyStyle
import com.wildlife.feasibility.ui.theme.WildlifeBackground
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
    onRequestAlternativeImage: () -> Unit,
    onRetryObservationDensity: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { innerPadding ->
        if (state.isLoading && state.scientificName == null && state.observations.isEmpty()) {
            WildlifeLoadingState(
                label = "Opening the species record…",
                modifier = Modifier.padding(innerPadding),
            )
        } else if (state.errorMessage != null) {
            DetailError(
                message = state.errorMessage,
                onBack = onBack,
                onRetry = onRetryMedia,
                modifier = Modifier.padding(innerPadding),
            )
        } else {
            FieldGuidePage(modifier = Modifier.padding(innerPadding)) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = WildlifeSpacing.Large),
                    // Status rows are field annotations, not sections of their own. Keep the
                    // page compact and let the components that need a larger break provide it.
                    verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small),
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
                            modifier = Modifier.padding(horizontal = WildlifeSpacing.Screen),
                        )
                    }
                    item {
                        FactPanel(
                            state = state,
                            modifier = Modifier.padding(horizontal = WildlifeSpacing.Screen),
                        )
                    }
                    item {
                        AboutSection(
                            summary = state.aboutSummary,
                            wikipediaUrl = state.wikipediaUrl,
                            onOpenUrl = onOpenUrl,
                            modifier = Modifier.padding(horizontal = WildlifeSpacing.Screen),
                        )
                    }
                if (state.observationDensityLoading || state.observationDensity != null || state.observationDensityMessage != null) {
                    item {
                        ObservationDensitySection(
                            state = state,
                            onOpenUrl = onOpenUrl,
                            onRetry = onRetryObservationDensity,
                            modifier = Modifier.padding(
                                horizontal = WildlifeSpacing.Screen,
                                vertical = WildlifeSpacing.Section,
                            ),
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
                if (state.silhouetteSourceUrl != null || state.silhouetteFallbackSourceUrl != null) {
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
                if (state.alternativeImageAvailable) {
                    item {
                        OutlinedButton(
                            onClick = onRequestAlternativeImage,
                            enabled = !state.enriching,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = WildlifeSpacing.Screen,
                                    end = WildlifeSpacing.Screen,
                                    top = WildlifeSpacing.Section,
                                ),
                        ) {
                            Icon(Icons.Outlined.Refresh, contentDescription = null)
                            Text(
                                text = "Find another reference image",
                                modifier = Modifier.padding(start = WildlifeSpacing.Small),
                            )
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
}

@Composable
private fun RegionalContextPanel(
    context: SpeciesRegionalContext,
    modifier: Modifier = Modifier,
) {
    val rarity = when (context.rarity) {
        com.wildlife.feasibility.EncounterRarity.UNKNOWN -> "Under review"
        com.wildlife.feasibility.EncounterRarity.COMMON -> "Common"
        com.wildlife.feasibility.EncounterRarity.UNCOMMON -> "Uncommon"
        com.wildlife.feasibility.EncounterRarity.RARE -> "Rare"
        com.wildlife.feasibility.EncounterRarity.VERY_RARE -> "Very rare"
    }
    val standing = when {
        "icons" in context.achievementLabels -> RegionalCollectionMark.ICON
        "essentials" in context.achievementLabels -> RegionalCollectionMark.ESSENTIAL
        else -> null
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        standing?.let { RegionalCollectionStamp(it, Modifier.size(30.dp)) }
        Column(modifier = Modifier.weight(1f)) {
            Text("REGIONAL GUIDE", style = FieldLabelStyle, color = WildlifeTheme.colors.parchmentFaint)
            Text(
                context.regionName,
                style = MaterialTheme.typography.titleMedium,
                color = WildlifeTheme.colors.parchment,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        context.rarity.toCardRarity()?.let { EncounterTrace(it, Modifier.size(24.dp)) }
        FieldMarkPill(
            markName = null,
            label = rarity,
            tint = WildlifeTheme.colors.rarityFor(context.rarity),
        )
    }
}

private fun com.wildlife.feasibility.ui.theme.WildlifeColors.rarityFor(
    rarity: com.wildlife.feasibility.EncounterRarity,
): Color = when (rarity) {
    com.wildlife.feasibility.EncounterRarity.COMMON -> rarityCommon
    com.wildlife.feasibility.EncounterRarity.UNCOMMON -> rarityUncommon
    com.wildlife.feasibility.EncounterRarity.RARE -> rarityRare
    com.wildlife.feasibility.EncounterRarity.VERY_RARE -> rarityVeryRare
    com.wildlife.feasibility.EncounterRarity.UNKNOWN -> parchmentFaint
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
        // The local broad-group mark is the permanent bottom layer. Keeping it mounted while
        // Coil decodes a photo or a recovered PhyloPic file prevents an empty hero at every
        // loading transition and still lets the more specific media replace it naturally.
        if (state.fallbackSilhouetteGroup != null) {
            TaxonSilhouette(
                groupKey = state.fallbackSilhouetteGroup,
                color = WildlifeTheme.colors.silhouette,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(56.dp)
                    .then(
                        if (imageFailed && silhouetteFailed) {
                            Modifier.semantics {
                                contentDescription = "Representative ${state.taxonGroup ?: "animal"} silhouette for ${state.commonName}"
                            }
                        } else {
                            Modifier
                        },
                    ),
            )
        } else if (imageFailed && silhouetteFailed) {
            Text(
                text = state.commonName.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.displayLarge,
                color = WildlifeTheme.colors.silhouette,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        if (!silhouetteFailed) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(activeSilhouetteUrl)
                    .setHeader(
                        "User-Agent",
                        WildlifeNetworkIdentity.REFERENCE_MEDIA_USER_AGENT,
                    )
                    .build(),
                contentDescription = if (!imageFailed) null else {
                    val activeRank = if (activeSilhouetteUrl == state.silhouetteFallbackUrl) {
                        state.silhouetteFallbackMatchRank
                    } else {
                        state.silhouetteMatchRank
                    }
                    if (activeRank == "species") {
                        "Silhouette for ${state.commonName}"
                    } else {
                        "Representative silhouette for ${state.commonName}"
                    }
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
        }
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
        }
        if (imageFailed && state.mediaRetryAvailable && !state.enriching) {
            OutlinedButton(
                onClick = onRetryMedia,
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
                        0f to WildlifeBackground.copy(alpha = 0.42f),
                        0.42f to Color.Transparent,
                        1f to WildlifeBackground.copy(alpha = 0.90f),
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
            text = listOfNotNull("SPECIES RECORD", state.taxonGroup?.uppercase()).joinToString(" · "),
            style = FieldStampStyle,
            color = WildlifeTheme.colors.oliveStrong,
        )
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
    val title = when {
        state.researchGrade -> "Research grade"
        state.observed -> "Recorded"
        else -> "Not recorded"
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small),
    ) {
        Surface(
            shape = CircleShape,
            color = if (state.observed) WildlifeTheme.colors.confirmed else MaterialTheme.colorScheme.surface,
            modifier = Modifier.size(30.dp),
        ) {
            Icon(
                imageVector = when {
                    state.researchGrade -> Icons.Outlined.Biotech
                    state.observed -> Icons.Outlined.Check
                    else -> Icons.Outlined.Visibility
                },
                contentDescription = title,
                tint = if (state.observed) MaterialTheme.colorScheme.onPrimaryContainer else WildlifeTheme.colors.parchmentFaint,
                modifier = Modifier.padding(6.dp),
            )
        }
        Text("COLLECTION", style = FieldLabelStyle, color = WildlifeTheme.colors.parchmentFaint)
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = WildlifeTheme.colors.parchment,
            modifier = Modifier.weight(1f),
        )
        if (state.observed) {
            Text(
                text = state.observations.size.toString(),
                style = FieldTallyStyle,
                color = WildlifeTheme.colors.oliveStrong,
            )
            Text("RECORDS", style = FieldLabelStyle, color = WildlifeTheme.colors.parchmentFaint)
        }
    }
}

@Composable
private fun FactPanel(state: SpeciesDetailUiState, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionRule("Field notes", accent = WildlifeTheme.colors.oliveStrong)
        PlateSurface(
            standingBorder = null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = WildlifeSpacing.Card),
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
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small)) {
                Fact("Seen by you", state.observations.size.toString(), Modifier.weight(1f))
                Fact(
                    "Global IUCN status",
                    friendlyConservationStatus(state.conservationStatus),
                    Modifier.weight(1f),
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            DiscoveryPanel(state)
            state.regionalContext?.let { context ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                RegionalContextPanel(context)
            }
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
    summary: String?,
    wikipediaUrl: String?,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small),
    ) {
        SectionRule("About")
        Text(
            text = summary ?: "No sourced description is available yet.",
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
    val credits = listOfNotNull(
        state.silhouetteSourceUrl?.let {
            SilhouetteCredit(
                it, state.silhouetteAttribution, state.silhouetteLicenseCode,
                state.silhouetteTaxonName, state.silhouetteMatchRank,
            )
        },
        state.silhouetteFallbackSourceUrl?.let {
            SilhouetteCredit(
                it, state.silhouetteFallbackAttribution, state.silhouetteFallbackLicenseCode,
                state.silhouetteFallbackTaxonName, state.silhouetteFallbackMatchRank,
            )
        },
    ).distinctBy(SilhouetteCredit::sourceUrl)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(if (credits.size == 1) "Silhouette" else "Silhouette hierarchy", style = MaterialTheme.typography.titleMedium)
        credits.forEach { credit ->
            val licence = credit.licenceCode?.uppercase()?.replace('-', ' ')
            Text(
                text = buildString {
                    append(credit.attribution ?: "PhyloPic contributor")
                    if (licence != null) append(" / $licence")
                    append(" / ${silhouetteLabel(credit.matchRank, credit.taxonName).lowercase()}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = { onOpenUrl(credit.sourceUrl) },
                contentPadding = PaddingValues(0.dp),
            ) { Text("View silhouette source on PhyloPic") }
        }
    }
}

@Composable
private fun ObservationDensitySection(
    state: SpeciesDetailUiState,
    onOpenUrl: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snapshot = state.observationDensity
    Column(modifier, verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small)) {
        Text("Recent public observations", style = MaterialTheme.typography.titleLarge)
        if (state.observationDensityLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (snapshot != null && snapshot.cells.isNotEmpty()) {
            ObservationDensityMap(snapshot)
            Text(
                text = buildString {
                    append("${snapshot.sampledObservations} recent public research-grade observations grouped into ${snapshot.cells.size} coarse 1° areas")
                    if (snapshot.isCappedSample) append(" (from ${snapshot.totalResults} matching observations)")
                    append(". This shows iNaturalist activity density, not the species' biological range.")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = { onOpenUrl("https://www.inaturalist.org/observations?taxon_id=${state.taxonId}") },
                contentPadding = PaddingValues(0.dp),
            ) { Text("Explore observations on iNaturalist") }
        }
        state.observationDensityMessage?.let { message ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = WildlifeTheme.colors.mutedText,
                    modifier = Modifier.weight(1f),
                )
                if (!state.observationDensityLoading) TextButton(onClick = onRetry) { Text("Try again") }
            }
        }
    }
}

private fun silhouetteLabel(state: SpeciesDetailUiState): String {
    return silhouetteLabel(state.silhouetteMatchRank, state.silhouetteTaxonName)
}

private fun silhouetteLabel(matchRank: String?, value: String?): String {
    val taxonName = value?.takeIf(String::isNotBlank)
    return when (matchRank) {
        "species" -> "Species silhouette"
        "genus" -> "Representative ${taxonName ?: "genus"} silhouette"
        "family" -> "Representative ${taxonName ?: "family"} silhouette"
        "order" -> "Representative ${taxonName ?: "order"} silhouette"
        else -> "Broad ${taxonName ?: "group"} silhouette"
    }
}

private data class SilhouetteCredit(
    val sourceUrl: String,
    val attribution: String?,
    val licenceCode: String?,
    val taxonName: String?,
    val matchRank: String?,
)

@Composable
private fun Fact(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label.uppercase(),
            style = FieldLabelStyle,
            color = WildlifeTheme.colors.parchmentFaint,
        )
        Text(
            text = value,
            style = FieldTallyStyle,
            color = WildlifeTheme.colors.parchment,
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
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionRule("Your observations", modifier = Modifier.weight(1f))
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
        colors = CardDefaults.cardColors(containerColor = WildlifeTheme.colors.plate),
        border = BorderStroke(1.dp, WildlifeTheme.colors.parchmentFaint.copy(alpha = 0.35f)),
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
                fallbackSilhouetteGroup = "birds",
                observationDensityLoading = true,
            ),
            onBack = {},
            onOpenTaxon = {},
            onOpenObservation = {},
            onSeeAllObservations = {},
            onOpenUrl = {},
            onRetryMedia = {},
            onRequestAlternativeImage = {},
            onRetryObservationDensity = {},
        )
    }
}
