package com.wildlife.feasibility.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.wildlife.feasibility.WildlifeNetworkIdentity
import com.wildlife.feasibility.ui.theme.DisplayFontFamily
import com.wildlife.feasibility.ui.theme.FieldLabelStyle
import com.wildlife.feasibility.ui.theme.FieldStampStyle
import com.wildlife.feasibility.ui.theme.WildlifeOutlineSubtle
import com.wildlife.feasibility.ui.theme.WildlifePlate
import com.wildlife.feasibility.ui.theme.WildlifeSurface
import com.wildlife.feasibility.ui.theme.WildlifeSpacing
import com.wildlife.feasibility.ui.theme.WildlifeTheme

/**
 * The ledger surfaces: one typed line of the observation record, and the plate used to
 * decide whether a public record is the same sighting as a capture.
 *
 * Both live here rather than inside Observations because two screens already draw them.
 * Capture shows the same proposals immediately after a handoff, and the record line is the
 * shape any list of sightings takes. They take plain values rather than a screen's state
 * type: presentation and its accessibility contract belong to the component, while which
 * record is in which pile belongs to the projection that fed it.
 */

/** Photograph size in a record line. Large enough to recognise a species, not a hero. */
private val RecordThumbnail = 54.dp

/**
 * One sighting as a line in the journal's ledger.
 *
 * [titleIsSpecies] switches the identity between the display serif — a named species, the
 * top of the visual hierarchy — and the body sans used for a record that has no species
 * yet. A draft is not a species, and setting "Draft sighting" in the identity face makes
 * the page claim it is one.
 *
 * [footer] extends the line downward inside the same border, for a record that asks the
 * reader something. A question drawn beneath the card rather than within it reads as a
 * remark about the list, not about the record immediately above it.
 */
@Composable
fun ObservationRecordLine(
    photoModel: String?,
    title: String,
    titleIsSpecies: Boolean,
    meta: String,
    modifier: Modifier = Modifier,
    /**
     * A state the *record* has reached, such as research grade. Never rarity or standing.
     *
     * Drawn as a pill on the identity's own line rather than as a rubber stamp beside it.
     * The stamp is a plate device: its ring holds about eight mono characters at full size,
     * and shrunk to fit a row the word breaks out of the ring and over the title.
     */
    markLabel: String? = null,
    markTint: Color? = null,
    contextLabel: String? = null,
    contextTint: Color? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
    footer: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val colors = WildlifeTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(WildlifeSurface.copy(alpha = 0.94f))
            .border(1.dp, WildlifeOutlineSubtle, RoundedCornerShape(10.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(WildlifeSpacing.Small),
        verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(WildlifeSpacing.Card),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RecordPhoto(
                model = photoModel,
                fallbackInitial = title.firstOrNull()?.uppercase() ?: "?",
                size = RecordThumbnail,
                description = if (titleIsSpecies) "$title, your photograph" else null,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = if (titleIsSpecies) {
                        MaterialTheme.typography.titleMedium.copy(
                            fontFamily = DisplayFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp,
                        )
                    } else {
                        MaterialTheme.typography.titleMedium
                    },
                    color = if (titleIsSpecies) colors.parchment else colors.parchmentDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = meta.uppercase(),
                    style = FieldStampStyle,
                    color = colors.parchmentFaint,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (markLabel != null || contextLabel != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(WildlifeSpacing.Micro),
                    ) {
                        if (markLabel != null) {
                            FieldMarkPill(
                                markName = null,
                                label = markLabel,
                                tint = markTint ?: colors.confirmed,
                            )
                        }
                        if (contextLabel != null) {
                            Text(
                                text = contextLabel.uppercase(),
                                style = FieldStampStyle,
                                color = contextTint ?: colors.parchmentFaint,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            trailing()
        }
        footer?.invoke(this)
    }
}

/**
 * The two photographs of a proposed match, side by side, with the evidence beneath them.
 *
 * Comparing the images is how a person actually answers "is this mine?" — the distance and
 * the minutes are how they check an answer they have already formed by looking. So the
 * photographs get the width and the numbers get a stamped line underneath, rather than the
 * other way round.
 *
 * [confidence] is drawn as a meter as well as named, because the score is a judgement
 * about *how much* rather than a category, and a bar carries that where a word cannot. It
 * is omitted for a match that has already been made: once a record is filed there is no
 * live score to report, and redrawing the one that filed it would imply the question is
 * still open when what remains is only whether to undo it.
 */
@Composable
fun MatchComparisonPlate(
    capturePhoto: String?,
    recordPhoto: String?,
    recordLabel: String?,
    headline: String,
    evidence: String,
    accent: Color,
    modifier: Modifier = Modifier,
    confidence: Float? = null,
    confidenceLabel: String? = null,
    /** Rendered under the evidence, typically a pair of [JournalButton]s. */
    actions: @Composable () -> Unit = {},
) {
    val colors = WildlifeTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(WildlifeSurface.copy(alpha = 0.94f))
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .padding(WildlifeSpacing.Card),
        verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small),
    ) {
        Text(headline.uppercase(), style = FieldLabelStyle, color = accent)

        Row(horizontalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small)) {
            ComparisonPane(
                caption = "Your capture",
                photoModel = capturePhoto,
                description = "The photograph you recorded in Wildlife",
                modifier = Modifier.weight(1f),
            )
            ComparisonPane(
                caption = "On iNaturalist",
                photoModel = recordPhoto,
                description = recordLabel?.let { "$it, the public iNaturalist photograph" }
                    ?: "The public iNaturalist photograph",
                modifier = Modifier.weight(1f),
            )
        }

        if (recordLabel != null) {
            Text(
                recordLabel,
                fontFamily = DisplayFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 19.sp,
                color = colors.parchment,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (confidence != null && confidenceLabel != null) {
            ConfidenceMeter(confidence, confidenceLabel, accent)
        }

        Text(
            evidence.uppercase(),
            style = FieldStampStyle,
            color = colors.parchmentFaint,
        )
        actions()
    }
}

/**
 * The matcher's confidence as a filled measure with its band named beside it.
 *
 * The percentage is deliberately absent. It would read as a precision the score does not
 * have — it is an ordering of explanations, not a probability — so the bar shows the
 * magnitude and the word carries what to do about it.
 */
@Composable
fun ConfidenceMeter(
    confidence: Float,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Match confidence: $label" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small),
    ) {
        JournalProgressBar(
            fraction = confidence,
            modifier = Modifier.weight(1f),
            height = 7.dp,
            accent = tint,
        )
        Text(
            label.uppercase(),
            style = FieldStampStyle,
            color = tint,
            maxLines = 1,
        )
    }
}

/** One captioned photograph in a [MatchComparisonPlate]. */
@Composable
private fun ComparisonPane(
    caption: String,
    photoModel: String?,
    description: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            caption.uppercase(),
            style = FieldLabelStyle,
            color = WildlifeTheme.colors.parchmentFaint,
        )
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            RecordPhoto(
                model = photoModel,
                fallbackInitial = "?",
                size = null,
                description = description,
                corner = 5.dp,
            )
        }
    }
}

/**
 * A photograph on a plate, or the plate alone when there is none.
 *
 * A missing image is drawn as an empty specimen plate with the record's initial rather than
 * a broken-image glyph: the plate is the field guide's way of saying a specimen is not
 * mounted, and the same treatment is already used for unfound species.
 */
@Composable
private fun RecordPhoto(
    model: String?,
    fallbackInitial: String,
    size: Dp?,
    description: String?,
    corner: Dp = 6.dp,
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(corner)
    val base = (if (size != null) Modifier.size(size) else Modifier.fillMaxSize())
        .clip(shape)
        .background(WildlifePlate)
        .border(1.dp, WildlifeOutlineSubtle, shape)
    if (model.isNullOrBlank()) {
        Box(
            // One label for the plate, so a screen reader is not handed a bare letter.
            base.clearAndSetSemantics {
                contentDescription = description?.let { "$it: no photograph" } ?: "No photograph"
            },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                fallbackInitial,
                fontFamily = DisplayFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = if (size != null) 22.sp else 34.sp,
                color = WildlifeTheme.colors.silhouette,
            )
        }
        return
    }
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(model)
            .crossfade(true)
            .setHeader("User-Agent", WildlifeNetworkIdentity.REFERENCE_MEDIA_USER_AGENT)
            .build(),
        contentDescription = description,
        contentScale = ContentScale.Crop,
        modifier = base,
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0E1209, widthDp = 390)
@Composable
private fun ObservationLedgerPreview() = WildlifeTheme {
    val colors = WildlifeTheme.colors
    Column(
        Modifier.padding(WildlifeSpacing.Screen),
        verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Card),
    ) {
        RecordHead(
            tallies = listOf(
                RecordTally.of(2, "To answer", colors.gold),
                RecordTally.of(1, "Awaiting", colors.parchmentDim),
                RecordTally.of(14, "Filed", colors.confirmed),
            ),
            note = "Wildlife never edits or deletes an iNaturalist record",
        )
        MatchComparisonPlate(
            capturePhoto = null,
            recordPhoto = null,
            recordLabel = "Common kingfisher",
            headline = "Filed automatically",
            evidence = "2 min apart · 40 m away · Delta de l'Ebre",
            accent = colors.confirmed,
            confidence = 0.94f,
            confidenceLabel = "Certain",
            actions = {
                Row(horizontalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small)) {
                    JournalButton("Undo", onClick = {}, modifier = Modifier.weight(1f))
                    JournalButton("Keep", onClick = {}, modifier = Modifier.weight(1f), primary = true)
                }
            },
        )
        ObservationRecordLine(
            photoModel = null,
            title = "European robin",
            titleIsSpecies = true,
            meta = "12 Aug 2026 · Girona · 2 photos",
            markLabel = "Research",
            markTint = colors.confirmed,
            contextLabel = "Region · Mediterranean Europe",
            contextTint = colors.oliveStrong,
        )
        Spacer(Modifier.height(WildlifeSpacing.Micro))
        ObservationRecordLine(
            photoModel = null,
            title = "Draft sighting",
            titleIsSpecies = false,
            meta = "13 Aug 2026 · Montseny · 1 photo · In Capture",
        )
    }
}
