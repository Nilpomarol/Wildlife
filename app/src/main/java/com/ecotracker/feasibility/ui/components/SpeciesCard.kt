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
import androidx.compose.runtime.staticCompositionLocalOf
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.wildlife.feasibility.WildlifeNetworkIdentity
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.sp
import com.wildlife.feasibility.ui.art.ContourField
import com.wildlife.feasibility.ui.art.FieldMark
import com.wildlife.feasibility.ui.art.EdgeLight
import com.wildlife.feasibility.ui.art.Gilding
import com.wildlife.feasibility.ui.art.IconHalo
import com.wildlife.feasibility.ui.art.PlateSheen
import com.wildlife.feasibility.ui.art.StatGlyph
import com.wildlife.feasibility.ui.art.StatMark
import com.wildlife.feasibility.ui.art.TaxonSilhouette
import com.wildlife.feasibility.ui.theme.DisplayFontFamily
import com.wildlife.feasibility.ui.theme.WildlifeSpacing
import com.wildlife.feasibility.ui.theme.WildlifeBackground
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
    /**
     * Whether the viewer has actually recorded this species. Gates the regional-standing
     * effects: gilding a legend you have never found would claim you had earned it.
     */
    val collected: Boolean = false,
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

/**
 * A stable 0..1 value for a key, so per-item variation survives recomposition, scrolling
 * and process death — and stays identical between screenshot runs.
 */
internal fun stableFraction(key: Any?, salt: Int): Float {
    var h = key.hashCode() * 31 + salt
    // A cheap avalanche: raw hashCodes of similar keys ("taxon:1000", "taxon:1001") differ
    // in their low bits only, which would leave adjacent cards nearly in step.
    h = h xor (h ushr 16)
    h *= -2048144789
    h = h xor (h ushr 13)
    return ((h and 0x7fffffff) % 10_000) / 10_000f
}

/** Where this species' bead starts around the loop. */
internal fun speciesBeadPhaseOffset(key: Any?): Float = stableFraction(key, salt = 0)

/** How long this species' bead takes to travel the loop. */
internal fun speciesBeadPeriodMillis(key: Any?): Int =
    (3200 + stableFraction(key, salt = 1) * 1800f).toInt()

/**
 * Whether plate effects run their infinite animations.
 *
 * Screenshot tests provide `false`. An infinite transition anywhere in the tree keeps the
 * Compose test clock permanently busy, so `waitForIdle` never returns and every capture
 * containing a grid would hang.
 */
val LocalPlateEffectsAnimated = staticCompositionLocalOf { true }

/** Roughly what the overlaid caption covers, used to keep artwork clear of it. */
private val CaptionInset = 44.dp

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
    val colors = WildlifeTheme.colors
    val plateEffectsAnimated = LocalPlateEffectsAnimated.current
    // Derived from the species, not drawn fresh: a random taken at composition would jump
    // on every recomposition and would make screenshots differ between runs.
    val beadPhaseOffset = remember(species.key) { speciesBeadPhaseOffset(species.key) }
    // A spread of speeds as well as starts, so the beads keep drifting apart instead of
    // holding formation for ever at a fixed distance.
    val beadPeriodMillis = remember(species.key) { speciesBeadPeriodMillis(species.key) }

    // The frame carries regional standing, not rarity: rarity is already stated by the
    // pill under the plate, so the border is free to mean something else.
    val standingBorder = when {
        species.regionalLegend -> colors.legend
        species.regionalIcon -> colors.icon
        species.regionalEssential -> colors.essential
        else -> null
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
        colors = CardDefaults.cardColors(containerColor = colors.plate),
        border = BorderStroke(
            width = when {
                species.regionalLegend -> 2.dp
                standingBorder != null -> 1.5.dp
                else -> 1.dp
            },
            color = standingBorder ?: MaterialTheme.colorScheme.outlineVariant,
        ),
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
                            captionInset = CaptionInset,
                            silhouetteUrl = species.silhouetteUrl,
                            silhouetteFallbackUrl = species.silhouetteFallbackUrl,
                            silhouetteMatchRank = species.silhouetteMatchRank,
                            fallbackSilhouetteGroup = species.fallbackSilhouetteGroup,
                            placeholderIcon = species.placeholderIcon,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                // Species of regional standing catch the light; legends are gilded on top.
                // Only once recorded — the effect marks an earned specimen, not a target.
                // Two standings, two different effects — not one effect at two strengths.
                //
                // Legend gilding is strictly earned: a warm glow and gold motes on a
                // species you have never seen reads as a trophy you own, which it is not.
                //
                // An Icon is different. Its standing says "this is one of the notable
                // species here", which is useful precisely *before* you find it, so the
                // travelling light runs on every Icon as a beacon. Recording one adds the
                // halo, so a specimen you hold is lit rather than merely flagged.
                if (species.collected && species.regionalLegend) {
                    PlateSheen(
                        tint = colors.legend,
                        strength = 0.20f,
                        modifier = Modifier.matchParentSize(),
                    )
                    Gilding(colors.legend, Modifier.matchParentSize())
                } else if (species.regionalIcon) {
                    if (species.collected) {
                        IconHalo(colors.icon, Modifier.matchParentSize())
                        PlateSheen(
                            tint = colors.icon,
                            strength = 0.22f,
                            modifier = Modifier.matchParentSize(),
                        )
                    }
                    EdgeLight(
                        tint = colors.icon,
                        modifier = Modifier.matchParentSize(),
                        // Quieter while it is still a target, so the beacon never
                        // outshines a plate that has actually been earned.
                        alpha = if (species.collected) 1f else 0.62f,
                        phaseOffset = beadPhaseOffset,
                        periodMillis = beadPeriodMillis,
                        animated = plateEffectsAnimated,
                    )
                }

                // Standing on the right, record status on the left. Standing is the
                // card's headline — it is what the frame and the gilding also carry — so
                // it takes the corner the eye reaches last and rests on when scanning a
                // row left to right.
                RegionalStandingColumn(
                    legend = species.regionalLegend,
                    icon = species.regionalIcon,
                    essential = species.regionalEssential,
                    modifier = Modifier.align(Alignment.TopEnd).padding(WildlifeSpacing.Micro),
                )

                if (species.status != SpeciesCardStatus.NONE) {
                    SpeciesStatusBadge(
                        status = species.status,
                        modifier = Modifier.align(Alignment.TopStart).padding(WildlifeSpacing.Micro),
                    )
                }

                if (!imageFailed && activePhoto?.attribution != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(top = 34.dp, start = 4.dp)
                            .background(
                                MaterialTheme.colorScheme.background.copy(alpha = 0.66f),
                                MaterialTheme.shapes.extraSmall,
                            )
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    ) {
                        Text(
                            text = activePhoto.attribution,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.parchmentDim,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // ---- the caption, over the plate ----
                //
                // A scrim rather than a solid band: the artwork should still read behind
                // the name, and a hard edge across the tile would reintroduce the two-part
                // card the square shape exists to remove. It is tall enough to cover a
                // two-line name so the text never sits on bare artwork.
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.35f to WildlifeBackground.copy(alpha = 0.58f),
                                1f to WildlifeBackground.copy(alpha = 0.92f),
                            ),
                        )
                        .padding(start = 8.dp, end = 8.dp, top = 20.dp, bottom = 8.dp),
                ) {
                    Text(
                        text = species.label,
                        fontFamily = DisplayFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        lineHeight = 15.sp,
                        color = colors.parchment,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (rarity != null) {
                        Spacer(Modifier.height(5.dp))
                        FieldMarkPill(
                            markName = rarity.fieldMarkName(),
                            label = rarity.label,
                            tint = rarityColor(rarity),
                        )
                    }
                }
            }
        }
}

/** Common has no mark by design, so an unmarked plate reads as ordinary at a glance. */
private fun SpeciesCardRarity.fieldMarkName(): String? = when (this) {
    SpeciesCardRarity.COMMON -> null
    SpeciesCardRarity.UNCOMMON -> "rarity_uncommon"
    SpeciesCardRarity.RARE -> "rarity_rare"
    SpeciesCardRarity.VERY_RARE -> "rarity_very_rare"
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
    // Icon and Legend were both gold, which left the two standings hard to tell apart.
    // They now take their own tokens: parchment for Icon, brass for Legend.
    val tint = if (mark == RegionalCollectionMark.ICON) {
        WildlifeTheme.colors.icon
    } else {
        WildlifeTheme.colors.essential
    }
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

/**
 * Field marks render from parsed paths rather than through Coil's SVG decoder: they are
 * bundled assets, so there is nothing to load asynchronously, and drawing them directly
 * keeps them available offline and in screenshot tests.
 */
@Composable
private fun FieldMarkAsset(assetName: String, tint: Color, modifier: Modifier = Modifier) {
    FieldMark(
        name = assetName.removeSuffix(".svg"),
        color = tint,
        modifier = modifier.fillMaxSize(),
    )
}

@Composable
private fun SpeciesStatusBadge(
    status: SpeciesCardStatus,
    modifier: Modifier = Modifier,
) {
    if (status == SpeciesCardStatus.NONE) return
    val colors = WildlifeTheme.colors
    // Matted rather than solid, so it sits on the plate like the standing marks opposite
    // it instead of reading as a floating UI chip.
    Box(
        modifier = modifier
            .size(23.dp)
            .background(colors.oliveDark.copy(alpha = 0.90f), CircleShape)
            .border(BorderStroke(1.dp, colors.confirmed), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        StatGlyph(
            mark = when (status) {
                SpeciesCardStatus.RESEARCH_GRADE -> StatMark.ROSETTE
                else -> StatMark.TICK
            },
            color = colors.parchment,
            modifier = Modifier.size(if (status == SpeciesCardStatus.RESEARCH_GRADE) 13.dp else 11.dp),
        )
    }
}

@Composable
private fun NoPhotoState(
    label: String,
    /**
     * Height the caption scrim occupies at the foot of the tile. The silhouette is inset
     * by it so the animal centres in the clear part of the plate — filling the square
     * outright buried half of every bird behind its own name.
     */
    captionInset: Dp,
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
                0f to WildlifeTheme.colors.plateHigh,
                1f to WildlifeTheme.colors.plate,
            ),
        ),
        contentAlignment = Alignment.Center,
    ) {
        // A species you have not found yet is an unfilled plate in the guide — contoured
        // ground with its group silhouette — rather than a blank or a locked wall.
        ContourField(
            WildlifeTheme.colors.plateContour,
            Modifier.matchParentSize(),
            lines = 5,
            seed = label.length,
        )
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
                    .padding(WildlifeSpacing.Small)
                    .padding(bottom = captionInset),
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
            TaxonSilhouette(
                groupKey = fallbackSilhouetteGroup,
                color = WildlifeTheme.colors.silhouette,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(WildlifeSpacing.Small)
                    .padding(bottom = captionInset),
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
