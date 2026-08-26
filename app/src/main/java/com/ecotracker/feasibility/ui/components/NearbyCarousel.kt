package com.wildlife.feasibility.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.wildlife.feasibility.CataloguePhotoPolicy
import com.wildlife.feasibility.NearbySpecies
import com.wildlife.feasibility.WildlifeNetworkIdentity
import coil.request.ImageRequest
import com.wildlife.feasibility.ui.art.TaxonSilhouette
import com.wildlife.feasibility.ui.theme.FieldStampStyle
import com.wildlife.feasibility.ui.theme.FieldTallyStyle
import com.wildlife.feasibility.ui.theme.ScientificNameStyle
import com.wildlife.feasibility.ui.theme.WildlifeBackground
import com.wildlife.feasibility.ui.theme.WildlifeOutlineSubtle
import com.wildlife.feasibility.ui.theme.WildlifePlate
import com.wildlife.feasibility.ui.theme.WildlifeSpacing
import com.wildlife.feasibility.ui.theme.WildlifeTheme

/**
 * The reported-nearby extract as a shelf of plates.
 *
 * A carousel rather than a list because this section answers "what is about?", which is
 * browsed by looking, not read in order. The ranking still survives: the shelf runs in
 * reporting-frequency order and each plate carries its tally, so the leftmost plate is the
 * most reported one without the row having to be a table.
 *
 * It bleeds past the page margin on both sides so the shelf runs off the edge of the paper.
 * A carousel that stops short of the margin looks like a list that failed to wrap.
 */
@Composable
fun NearbySpeciesCarousel(
    species: List<NearbySpecies>,
    onOpenTaxon: (Long) -> Unit,
    modifier: Modifier = Modifier,
    /** Horizontal page margin the shelf should bleed past, so it can run to the screen edge. */
    bleed: Dp = WildlifeSpacing.Screen,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth().bleedHorizontally(bleed),
        contentPadding = PaddingValues(horizontal = bleed),
        horizontalArrangement = Arrangement.spacedBy(WildlifeSpacing.Grid),
    ) {
        items(species, key = NearbySpecies::taxonId) { entry ->
            NearbySpeciesPlate(species = entry, onClick = { onOpenTaxon(entry.taxonId) })
        }
    }
}

/** Wide enough for a two-word common name at one line in most cases, narrow enough to show ~2.5. */
private val PlateWidth = 132.dp
private val PlateArtHeight = 108.dp

/**
 * One plate on Home's shelf. This is the one compact discovery surface allowed to show an
 * attributed provider photo directly: licensed provider photo, region-scoped user observation,
 * locally validated catalogue silhouette, then the bundled group mark. Explore's full Near me
 * table deliberately keeps the collection-style personal-photo/silhouette rule.
 */
@Composable
private fun NearbySpeciesPlate(
    species: NearbySpecies,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WildlifeTheme.colors
    val context = LocalContext.current
    val referencePhotoUrl = CataloguePhotoPolicy.creditedUrl(
        species.photoUrl,
        species.photoAttribution,
        species.photoLicenseCode,
    )
    var referencePhotoFailed by remember(referencePhotoUrl) {
        mutableStateOf(referencePhotoUrl == null)
    }
    var personalPhotoFailed by remember(species.personalPhotoUrl) {
        mutableStateOf(species.personalPhotoUrl == null)
    }
    val photoUrl = when {
        !referencePhotoFailed -> referencePhotoUrl
        !personalPhotoFailed -> species.personalPhotoUrl
        else -> null
    }
    val showingReferencePhoto = photoUrl != null && photoUrl == referencePhotoUrl
    val referenceCredit = referencePhotoUrl?.takeIf { showingReferencePhoto }?.let {
        CataloguePhotoPolicy.compactCredit(species.photoAttribution, species.photoLicenseCode)
    }
    var silhouetteFailed by remember(species.silhouetteUrl) {
        mutableStateOf(species.silhouetteUrl == null)
    }

    Column(modifier.width(PlateWidth).clickable(onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(PlateArtHeight)
                .clip(RoundedCornerShape(8.dp))
                .background(WildlifePlate)
                .border(1.dp, WildlifeOutlineSubtle, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (photoUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(photoUrl)
                        .setHeader("User-Agent", WildlifeNetworkIdentity.REFERENCE_MEDIA_USER_AGENT)
                        .build(),
                    contentDescription = if (showingReferencePhoto) {
                        "${species.commonName ?: species.scientificName}, attributed reference photo"
                    } else {
                        "${species.commonName ?: species.scientificName}, your observation photo"
                    },
                    contentScale = ContentScale.Crop,
                    onError = {
                        if (showingReferencePhoto) referencePhotoFailed = true
                        else personalPhotoFailed = true
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                // The tally sits on the artwork, so it needs a foot of shade to stay legible
                // over a bright photograph.
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.55f to Color.Transparent,
                                1f to WildlifeBackground.copy(alpha = 0.85f),
                            )
                        )
                )
            } else if (!silhouetteFailed) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(species.silhouetteUrl)
                        .setHeader("User-Agent", WildlifeNetworkIdentity.REFERENCE_MEDIA_USER_AGENT)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.tint(colors.silhouette),
                    onError = { silhouetteFailed = true },
                    modifier = Modifier.size(64.dp),
                )
            } else {
                species.taxonGroup?.let { group ->
                    TaxonSilhouette(group, colors.silhouette, Modifier.size(64.dp))
                }
            }
            // The tally stays clear of the caption gradient and remains easy to compare.
            Text(
                text = species.observationCount.toString(),
                style = FieldTallyStyle,
                color = colors.oliveStrong,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(5.dp))
                    // Its own matting, since there is no scrim at the top of the plate.
                    .background(WildlifeBackground.copy(alpha = 0.62f))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        // Two lines always, whether or not the name needs them: without a reserved second
        // line a wrapped name pushes its scientific name down and the shelf's baselines go
        // ragged, which reads as broken rather than as varied.
        Text(
            text = species.commonName ?: species.scientificName,
            style = MaterialTheme.typography.titleMedium,
            color = colors.parchment,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 17.sp,
        )
        // Both remaining lines are always emitted, even when empty. Every tile then occupies
        // exactly the same height whatever it happens to carry, which is what keeps the shelf
        // aligned; rendering them conditionally made photographed tiles taller than the rest.
        Text(
            text = if (species.commonName != null) species.scientificName else "",
            style = ScientificNameStyle,
            color = colors.parchmentFaint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // Keep the creator and licence directly attached to provider artwork. An empty line on
        // personal-photo and silhouette plates preserves the carousel's shared baseline.
        Text(
            text = referenceCredit.orEmpty(),
            style = MaterialTheme.typography.labelSmall,
            color = colors.parchmentFaint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

    }
}

/**
 * The shelf's footnote: how many species, how they are ordered, and when it was checked.
 *
 * The ordering caveat is stated identically here and in Explore's full section, because the
 * count is reporting frequency and never rarity. Capped at two lines: it sits beside
 * [NearbyHeaderRow]'s "See all" button rather than above the full shelf width, so it no
 * longer has the room to run past that without pushing the button down.
 */
@Composable
fun NearbyFootnote(
    total: Int,
    checkedLabel: String?,
    modifier: Modifier = Modifier,
) {
    val text = buildString {
        append("$total reported species · most frequently reported first")
        if (checkedLabel != null) append(" · checked $checkedLabel")
    }
    Text(
        text,
        style = FieldStampStyle,
        color = WildlifeTheme.colors.parchmentFaint,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/**
 * The footnote and the "see all" action on one line: the footnote takes the room it needs
 * up to two lines, the button sits fixed at its side rather than under the full-width shelf.
 *
 * [seeAllLabel] is null when there is nothing more to see, in which case the row is just the
 * footnote — a button offering to show what is already fully shown said nothing.
 */
@Composable
fun NearbyHeaderRow(
    total: Int,
    checkedLabel: String?,
    seeAllLabel: String?,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        NearbyFootnote(total, checkedLabel, modifier = Modifier.weight(1f))
        if (seeAllLabel != null) {
            Spacer(Modifier.width(WildlifeSpacing.Small))
            JournalButton(seeAllLabel, onSeeAll)
        }
    }
}
