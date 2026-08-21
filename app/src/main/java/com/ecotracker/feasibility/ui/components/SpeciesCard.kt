package com.wildlife.feasibility.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Biotech
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.wildlife.feasibility.WildlifeNetworkIdentity
import com.wildlife.feasibility.ui.theme.WildlifeSpacing
import com.wildlife.feasibility.ui.theme.WildlifeTheme

data class SpeciesCardModel(
    val key: String,
    val label: String,
    val supportingText: String,
    val photoUrl: String?,
    val photoKind: SpeciesCardPhotoKind? = null,
    val photoFallbackUrl: String? = null,
    val photoFallbackKind: SpeciesCardPhotoKind? = null,
    val photoFallbackAttribution: String? = null,
    val additionalPhotoFallbacks: List<SpeciesCardPhotoCandidate> = emptyList(),
    val photoAttribution: String? = null,
    val silhouetteUrl: String? = null,
    val silhouetteFallbackUrl: String? = null,
    val silhouetteMatchRank: String? = null,
    /** Bundled zoological group glyph used only when no resolved silhouette is available. */
    val fallbackSilhouetteGroup: String? = null,
    val regionalEssential: Boolean = false,
    val regionalIcon: Boolean = false,
    /** Curated regional prestige. This is independent from encounter rarity and achievements. */
    val regionalLegend: Boolean = false,
    /** Offline fallback glyph (e.g. a taxonomic-group silhouette) shown when no photo or
     *  remote silhouette is available, in place of the bare first-letter placeholder. */
    val placeholderIcon: ImageVector? = null,
    val supportingTextItalic: Boolean = false,
    val status: SpeciesCardStatus = SpeciesCardStatus.NONE,
    val rarity: SpeciesCardRarity? = null,
)

enum class SpeciesCardPhotoKind {
    PERSONAL,
    REFERENCE,
}

data class SpeciesCardPhotoCandidate(
    val url: String,
    val kind: SpeciesCardPhotoKind,
    val attribution: String? = null,
)

enum class SpeciesCardStatus {
    NONE,
    OBSERVED,
    RESEARCH_GRADE,
}

/**
 * Regional encounter-rarity tiers. They express encounter difficulty only, never prestige,
 * conservation or verification.
 */
enum class SpeciesCardRarity(val label: String) {
    COMMON("Common"),
    UNCOMMON("Uncommon"),
    RARE("Rare"),
    VERY_RARE("Very rare"),
}

@Composable
fun rarityColor(rarity: SpeciesCardRarity): Color = when (rarity) {
    SpeciesCardRarity.COMMON -> WildlifeTheme.colors.rarityCommon
    SpeciesCardRarity.UNCOMMON -> WildlifeTheme.colors.rarityUncommon
    SpeciesCardRarity.RARE -> WildlifeTheme.colors.rarityRare
    SpeciesCardRarity.VERY_RARE -> WildlifeTheme.colors.rarityVeryRare
}

@Composable
fun SpeciesCard(
    species: SpeciesCardModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val photoCandidates = remember(
        species.photoUrl,
        species.photoKind,
        species.photoAttribution,
        species.photoFallbackUrl,
        species.photoFallbackKind,
        species.photoFallbackAttribution,
        species.additionalPhotoFallbacks,
    ) {
        species.orderedPhotoCandidates()
    }
    var activePhotoIndex by remember(photoCandidates) { mutableStateOf(0) }
    var imageFailed by remember(photoCandidates) {
        mutableStateOf(photoCandidates.isEmpty())
    }
    val activePhoto = photoCandidates.getOrNull(activePhotoIndex)
    val rarity = species.rarity
    val highTier = rarity == SpeciesCardRarity.RARE ||
        rarity == SpeciesCardRarity.VERY_RARE
    val borderColor = if (highTier) {
        rarityColor(requireNotNull(rarity)).copy(alpha = 0.85f)
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val cardDescription = buildString {
        append(species.label)
        append(", ")
        append(species.supportingText)
        rarity?.let { append(", ${it.label} tier") }
        if (species.regionalLegend) append(", Regional Legend")
        if (species.regionalIcon) append(", Regional Icon")
        else if (species.regionalEssential) append(", Regional Essential")
        when (species.status) {
            SpeciesCardStatus.RESEARCH_GRADE -> append(", research grade")
            SpeciesCardStatus.OBSERVED -> append(", observed")
            SpeciesCardStatus.NONE -> Unit
        }
    }
    Card(
        onClick = onClick,
        modifier = modifier.clearAndSetSemantics {
            this.contentDescription = cardDescription
        },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
        border = BorderStroke(if (highTier) 1.5.dp else 1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(Modifier.fillMaxSize()) {
            Crossfade(targetState = imageFailed, animationSpec = tween(220), label = "species-image") { failed ->
                if (!failed) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(activePhoto?.url)
                            .crossfade(true)
                            .setHeader(
                                "User-Agent",
                                WildlifeNetworkIdentity.REFERENCE_MEDIA_USER_AGENT,
                            )
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        onError = {
                            if (activePhotoIndex < photoCandidates.lastIndex) {
                                activePhotoIndex++
                            } else {
                                imageFailed = true
                            }
                        },
                    )
                } else {
                    NoPhotoState(
                        label = species.label,
                        silhouetteUrl = species.silhouetteUrl,
                        silhouetteFallbackUrl = species.silhouetteFallbackUrl,
                        silhouetteMatchRank = species.silhouetteMatchRank,
                        fallbackSilhouetteGroup = species.fallbackSilhouetteGroup,
                        placeholderIcon = species.placeholderIcon,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color(0x33080B09),
                            0.34f to Color.Transparent,
                            0.62f to Color(0x55080B09),
                            1f to Color(0xF2080B09),
                        ),
                    ),
            )
            if (rarity != null && rarity != SpeciesCardRarity.COMMON) {
                EncounterTrace(
                    rarity = rarity,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(WildlifeSpacing.Small),
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(WildlifeSpacing.Small),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                if (!imageFailed && activePhoto?.attribution != null) {
                    Text(
                        text = activePhoto.attribution,
                        style = MaterialTheme.typography.labelSmall,
                        color = WildlifeTheme.colors.mutedText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = species.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = species.supportingText,
                    style = MaterialTheme.typography.labelSmall,
                    fontStyle = if (species.supportingTextItalic) {
                        FontStyle.Italic
                    } else {
                        FontStyle.Normal
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier.align(Alignment.TopEnd).padding(WildlifeSpacing.Small),
                horizontalArrangement = Arrangement.spacedBy(WildlifeSpacing.Micro),
                verticalAlignment = Alignment.Top,
            ) {
                if (species.regionalLegend) RegionalLegendMark()
                if (species.regionalIcon) RegionalCollectionStamp(RegionalCollectionMark.ICON)
                else if (species.regionalEssential) RegionalCollectionStamp(RegionalCollectionMark.ESSENTIAL)
                if (species.status != SpeciesCardStatus.NONE) SpeciesStatusBadge(status = species.status)
            }
        }
    }
}

/** The owner-supplied Field Mark artwork for the curated regional checklists. */
enum class RegionalCollectionMark(val assetName: String, val contentLabel: String) {
    ESSENTIAL("regional_essential.svg", "Regional Essential"),
    ICON("regional_icon.svg", "Regional Icon"),
}

@Composable
fun RegionalCollectionStamp(
    mark: RegionalCollectionMark,
    modifier: Modifier = Modifier,
) {
    val tint = if (mark == RegionalCollectionMark.ICON) WildlifeTheme.colors.gold else WildlifeTheme.colors.oliveStrong
    Surface(
        modifier = modifier
            .size(30.dp)
            .background(Color(0xD9080B09), CircleShape)
            .border(BorderStroke(1.dp, tint.copy(alpha = 0.9f)), CircleShape),
        color = Color.Transparent,
        contentColor = tint,
        shape = CircleShape,
    ) {
        FieldMarkAsset(mark.assetName, tint, Modifier.padding(6.dp))
    }
}

internal fun SpeciesCardModel.orderedPhotoCandidates(): List<SpeciesCardPhotoCandidate> =
    buildList {
        photoUrl?.let { url ->
            add(
                SpeciesCardPhotoCandidate(
                    url,
                    photoKind ?: SpeciesCardPhotoKind.REFERENCE,
                    photoAttribution,
                ),
            )
        }
        photoFallbackUrl?.let { url ->
            add(
                SpeciesCardPhotoCandidate(
                    url,
                    photoFallbackKind ?: SpeciesCardPhotoKind.REFERENCE,
                    photoFallbackAttribution,
                ),
            )
        }
        addAll(additionalPhotoFallbacks)
    }.distinctBy(SpeciesCardPhotoCandidate::url)

@Composable
fun EncounterTrace(
    rarity: SpeciesCardRarity,
    modifier: Modifier = Modifier,
) {
    val assetName = when (rarity) {
        SpeciesCardRarity.COMMON -> return
        SpeciesCardRarity.UNCOMMON -> "rarity_uncommon.svg"
        SpeciesCardRarity.RARE -> "rarity_rare.svg"
        SpeciesCardRarity.VERY_RARE -> "rarity_very_rare.svg"
    }
    val tint = rarityColor(rarity)
    Box(
        modifier = modifier
            .size(30.dp)
            .background(color = Color(0xB3080B09), shape = CircleShape)
            .border(BorderStroke(1.dp, tint.copy(alpha = 0.7f)), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        FieldMarkAsset(assetName, tint, Modifier.padding(6.dp))
    }
}

/** Owner-supplied prestige artwork, distinct from rarity and Icon membership. */
@Composable
fun RegionalLegendMark(modifier: Modifier = Modifier) {
    val tint = WildlifeTheme.colors.legendary
    Box(
        modifier = modifier
            .size(30.dp)
            .background(Color(0xB3080B09), CircleShape)
            .border(BorderStroke(1.dp, tint.copy(alpha = 0.85f)), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        FieldMarkAsset("regional_legend.svg", tint, Modifier.padding(6.dp))
    }
}

@Composable
private fun FieldMarkAsset(assetName: String, tint: Color, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data("file:///android_asset/field_marks/$assetName")
            .decoderFactory(SvgDecoder.Factory())
            .build(),
        contentDescription = null,
        colorFilter = ColorFilter.tint(tint),
        contentScale = ContentScale.Fit,
        modifier = modifier.fillMaxSize(),
    )
}

@Composable
private fun SpeciesStatusBadge(
    status: SpeciesCardStatus,
    modifier: Modifier = Modifier,
) {
    if (status == SpeciesCardStatus.NONE) return
    Box(
        modifier = modifier
            .size(30.dp)
            .background(
                color = WildlifeTheme.colors.confirmed.copy(alpha = 0.94f),
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = when (status) {
                SpeciesCardStatus.OBSERVED -> Icons.Outlined.Check
                SpeciesCardStatus.RESEARCH_GRADE -> Icons.Outlined.Biotech
                SpeciesCardStatus.NONE -> Icons.Outlined.Check
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(19.dp),
        )
    }
}

@Composable
private fun NoPhotoState(
    label: String,
    silhouetteUrl: String?,
    silhouetteFallbackUrl: String?,
    silhouetteMatchRank: String?,
    fallbackSilhouetteGroup: String?,
    placeholderIcon: ImageVector? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var activeSilhouetteUrl by remember(silhouetteUrl, silhouetteFallbackUrl) {
        mutableStateOf(silhouetteUrl)
    }
    var silhouetteFailed by remember(silhouetteUrl, silhouetteFallbackUrl) {
        mutableStateOf(silhouetteUrl == null)
    }
    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                0f to MaterialTheme.colorScheme.surface,
                1f to MaterialTheme.colorScheme.background,
            ),
        ),
        contentAlignment = Alignment.Center,
    ) {
        if (!silhouetteFailed) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(activeSilhouetteUrl)
                    .crossfade(true)
                    .setHeader(
                        "User-Agent",
                        WildlifeNetworkIdentity.REFERENCE_MEDIA_USER_AGENT,
                    )
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(WildlifeTheme.colors.silhouette),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(WildlifeSpacing.Card),
                onError = {
                    if (activeSilhouetteUrl != silhouetteFallbackUrl &&
                        silhouetteFallbackUrl != null
                    ) {
                        activeSilhouetteUrl = silhouetteFallbackUrl
                    } else {
                        silhouetteFailed = true
                    }
                },
            )
        } else if (fallbackSilhouetteGroup != null) {
            TaxonGroupGlyph(
                groupKey = fallbackSilhouetteGroup,
                tint = WildlifeTheme.colors.silhouette,
                contentDescription = null,
                size = 72.dp,
            )
        } else if (placeholderIcon != null) {
            Icon(
                imageVector = placeholderIcon,
                contentDescription = null,
                tint = WildlifeTheme.colors.silhouette,
                modifier = Modifier
                    .fillMaxSize(0.44f)
                    .align(Alignment.Center),
            )
        } else {
            Text(
                text = label.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.displayLarge,
                color = WildlifeTheme.colors.silhouette,
            )
        }
    }
}
