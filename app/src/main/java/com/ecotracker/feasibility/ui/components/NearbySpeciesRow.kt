package com.wildlife.feasibility.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wildlife.feasibility.NearbySpecies
import com.wildlife.feasibility.WildlifeNetworkIdentity
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.wildlife.feasibility.ui.art.TaxonSilhouette
import com.wildlife.feasibility.ui.theme.FieldStampStyle
import com.wildlife.feasibility.ui.theme.FieldTallyStyle
import com.wildlife.feasibility.ui.theme.ScientificNameStyle
import com.wildlife.feasibility.ui.theme.WildlifeOutlineSubtle
import com.wildlife.feasibility.ui.theme.WildlifePlate
import com.wildlife.feasibility.ui.theme.WildlifeSpacing
import com.wildlife.feasibility.ui.theme.WildlifeTheme

/**
 * One reported-nearby species. Shared by the Explore Near me section and the Home preview so
 * both read the reporting count the same way; the count is reporting frequency, never rarity.
 *
 * Printed as a line in a table rather than as a card. A stack of bordered cards turned a
 * ranked list into a set of unrelated objects and hid the ranking that is the whole point of
 * the section — a ruled table lets the eye run down the tally column and compare.
 *
 * The count takes the mono tally, which is the app's mark for "a number read off a record"
 * rather than a number earned. That is exactly the caveat this row has to carry: these are
 * other people's reports, not the viewer's achievement.
 */
@Composable
fun NearbySpeciesRow(
    species: NearbySpecies,
    onOpenTaxon: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WildlifeTheme.colors
    val context = LocalContext.current
    var photoFailed by remember(species.personalPhotoUrl) {
        mutableStateOf(species.personalPhotoUrl == null)
    }
    var silhouetteFailed by remember(species.silhouetteUrl) {
        mutableStateOf(species.silhouetteUrl == null)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onOpenTaxon(species.taxonId) }
            .padding(vertical = WildlifeSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A small neutral plate, so the row is scannable by shape before it is read. The
        // group glyph is bundled art, so it resolves offline like the rest of the marks.
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(WildlifePlate)
                .border(1.dp, WildlifeOutlineSubtle, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (!photoFailed) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(species.personalPhotoUrl)
                        .setHeader("User-Agent", WildlifeNetworkIdentity.REFERENCE_MEDIA_USER_AGENT)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onError = { photoFailed = true },
                    modifier = Modifier.matchParentSize(),
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
                    modifier = Modifier.size(24.dp),
                )
            } else species.taxonGroup?.let { group ->
                TaxonSilhouette(group, colors.silhouette, Modifier.size(24.dp))
            }
        }
        Spacer(Modifier.size(WildlifeSpacing.Card))
        Column(Modifier.weight(1f)) {
            Text(
                text = species.commonName ?: species.scientificName,
                style = MaterialTheme.typography.titleMedium,
                color = colors.parchment,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (species.commonName != null) {
                Text(
                    text = species.scientificName,
                    style = ScientificNameStyle,
                    color = colors.parchmentDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.size(WildlifeSpacing.Small))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = species.observationCount.toString(),
                style = FieldTallyStyle,
                color = colors.oliveStrong,
            )
            Text("REPORTS", style = FieldStampStyle, color = colors.parchmentFaint)
        }
    }
}

/** The hairline between two printed rows. Kept here so every table of rows rules alike. */
@Composable
fun RowRule(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(start = 50.dp)
            .height(1.dp)
            .background(WildlifeOutlineSubtle),
    )
}
