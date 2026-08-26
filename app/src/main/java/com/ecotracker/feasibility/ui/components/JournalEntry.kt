package com.wildlife.feasibility.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.wildlife.feasibility.WildlifeNetworkIdentity
import com.wildlife.feasibility.ui.art.ContourField
import com.wildlife.feasibility.ui.art.RankBadgeArt
import com.wildlife.feasibility.ui.art.RankPatch
import com.wildlife.feasibility.ui.art.StampRing
import com.wildlife.feasibility.ui.art.drawSlipEdges
import com.wildlife.feasibility.ui.theme.DisplayFontFamily
import com.wildlife.feasibility.ui.theme.FieldLabelStyle
import com.wildlife.feasibility.ui.theme.FieldStampStyle
import com.wildlife.feasibility.ui.theme.FieldTallyStyle
import com.wildlife.feasibility.ui.theme.ScientificNameStyle
import com.wildlife.feasibility.ui.theme.WildlifeBackground
import com.wildlife.feasibility.ui.theme.WildlifeOutlineSubtle
import com.wildlife.feasibility.ui.theme.WildlifePlate
import com.wildlife.feasibility.ui.theme.WildlifePlateContour
import com.wildlife.feasibility.ui.theme.WildlifeSurface
import com.wildlife.feasibility.ui.theme.WildlifeTheme
import com.wildlife.feasibility.ui.theme.levelAccent

/**
 * The surfaces of a *dated journal entry*, as opposed to the reference surfaces in
 * `FieldGuide.kt`.
 *
 * The distinction is the point of the design: Collection is the printed reference section
 * of the guide — plates in a grid, browsed by narrowing. Home is the loose dated page at
 * the front, where a ranger records what happened today. These components carry that
 * second voice: mounted photographs, typed datelines, stamped tallies, stacked slips.
 *
 * They share the page, the palette, the type and the header ground with the reference
 * surfaces. Nothing here re-decides those.
 */

// ---------------------------------------------------------------- masthead

/**
 * Home's header: the *ranger*, where [RangerHeader] carries the *region*.
 *
 * The division is deliberate and load-bearing. Collection's header measures one region and
 * explicitly refuses lifetime XP, because a bar counting a region and a rank counting a
 * lifetime read as one number when stacked. Home is where that lifetime measure belongs, so
 * the rank patch runs large here and the progress bar counts XP toward the next rank.
 *
 * [dateline] is typed rather than lettered — it is the one line on the screen that says
 * *when*, and the mono is the only face in the app that can say so without reading as a
 * heading.
 */
@Composable
fun Masthead(
    dateline: String,
    levelKey: String,
    levelName: String,
    /** Sits under the rank name; the ranger's handle, or the invitation to make one. */
    subtitle: String,
    progressLabel: String,
    progressTrailing: String?,
    progressSupporting: String,
    progressDescription: String,
    progressFraction: Float,
    modifier: Modifier = Modifier,
) {
    val colors = WildlifeTheme.colors
    val accent = levelAccent(levelKey)
    Box(modifier.fillMaxWidth()) {
        Box(Modifier.matchParentSize().background(headerScrim()))
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 18.dp)) {
            // Use the product's actual launcher artwork here. A second, header-only emblem
            // makes the masthead look like a related field guide rather than Wildlife.
            Row(verticalAlignment = Alignment.CenterVertically) {
                WildlifeAppIcon(
                    size = 40.dp,
                    contentDescription = "Wildlife",
                )
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("WILDLIFE FIELD JOURNAL", style = FieldLabelStyle, color = colors.parchment)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        dateline.uppercase(),
                        style = FieldStampStyle,
                        color = colors.oliveStrong,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(15.dp))
            Row(verticalAlignment = Alignment.Top) {
                // Larger than Collection's badge: on Home the rank *is* the identity, not
                // an annotation beside a region name.
                val badgeSize = 64.dp
                Box(contentAlignment = Alignment.Center) {
                    val drawn = RankBadgeArt(
                        levelKey = levelKey,
                        color = accent,
                        modifier = Modifier.size(badgeSize),
                    )
                    if (!drawn) {
                        RankPatch(
                            levelKey = levelKey,
                            border = accent,
                            field = colors.oliveDark,
                            art = colors.parchment,
                            modifier = Modifier.size(width = badgeSize * 0.82f, height = badgeSize),
                        )
                    }
                }
                Spacer(Modifier.size(14.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy((-10).dp),
                ) {
                    Text(
                        "RANGER RANK",
                        style = FieldLabelStyle,
                        color = accent,
                    )
                    Text(
                        levelName,
                        fontFamily = DisplayFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 30.sp,
                        lineHeight = 30.sp,
                        color = colors.parchment,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        subtitle,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        lineHeight = 16.sp,
                        color = colors.parchment,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Column(
                Modifier.semantics(mergeDescendants = true) {
                    contentDescription = progressDescription
                },
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        progressLabel.uppercase(),
                        style = FieldStampStyle,
                        color = colors.parchmentDim,
                        modifier = Modifier.padding(bottom = 1.dp),
                    )
                    // Stamped, not lettered: this is a count read off a form, so it takes the
                    // mono tally rather than Eczar. Eczar numerals stay with achievement.
                    if (progressTrailing != null) {
                        Text(progressTrailing, style = FieldTallyStyle, color = accent)
                    }
                }
                Spacer(Modifier.height(6.dp))
                JournalProgressBar(progressFraction, accent = accent)
                Spacer(Modifier.height(5.dp))
                Text(
                    progressSupporting.uppercase(),
                    style = FieldStampStyle.copy(fontSize = 9.sp, letterSpacing = 0.7.sp),
                    color = colors.parchmentFaint,
                    modifier = Modifier.align(Alignment.End),
                )
            }
        }
        HeaderHairline(Modifier.align(Alignment.BottomCenter))
    }
}

// ---------------------------------------------------------------- mounted specimen

/**
 * Home's latest record as a compact horizontal field note.
 *
 * The identity and record metadata sit beside the photograph instead of obscuring it.
 * Keeping the image square also prevents a missing personal photo from becoming a large
 * empty hero.
 *
 * [stampLabel] strikes a rubber stamp over the top corner — reserved for a state the
 * *record* has reached, such as Research Grade, never for rarity or standing.
 */
@Composable
fun SpecimenPlate(
    label: String,
    scientificName: String?,
    caption: String,
    actionLabel: String,
    rarityLabel: String?,
    rarityTint: Color,
    regionLabel: String?,
    awaitingSpeciesIdentification: Boolean,
    photoUrl: String?,
    photoDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    stampLabel: String? = null,
    stampTint: Color? = null,
) {
    val colors = WildlifeTheme.colors
    val context = LocalContext.current
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(WildlifeSurface.copy(alpha = 0.94f))
            .border(1.dp, WildlifeOutlineSubtle, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(132.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(WildlifePlate)
                .border(1.dp, WildlifeOutlineSubtle, RoundedCornerShape(4.dp)),
        ) {
            if (photoUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(photoUrl)
                        .setHeader("User-Agent", WildlifeNetworkIdentity.REFERENCE_MEDIA_USER_AGENT)
                        .build(),
                    contentDescription = photoDescription,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                // The unfound-plate language, borrowed: contours mean "no specimen image",
                // and they are the one place the drawn terrain still lives.
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ContourField(WildlifePlateContour, Modifier.fillMaxSize(), lines = 7, seed = 23)
                    Text(
                        text = label.firstOrNull()?.uppercase() ?: "?",
                        fontFamily = DisplayFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 56.sp,
                        color = colors.silhouette,
                    )
                }
            }
            if (stampLabel != null) {
                FieldStamp(
                    label = stampLabel,
                    tint = stampTint ?: colors.confirmed,
                    modifier = Modifier.align(Alignment.TopEnd).padding(7.dp),
                    size = 48.dp,
                )
            }
        }
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f).height(132.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("FIELD NOTE", style = FieldLabelStyle, color = colors.oliveStrong)
                Text(
                    "${actionLabel.uppercase()}  ›",
                    style = FieldStampStyle,
                    color = colors.parchment,
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                label,
                fontFamily = DisplayFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 23.sp,
                lineHeight = 25.sp,
                color = colors.parchment,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Column(Modifier.offset(y = (-3).dp)) {
                if (scientificName != null) {
                    Text(scientificName, style = ScientificNameStyle, color = colors.parchmentDim)
                }
                if (rarityLabel != null) {
                    Text(rarityLabel.uppercase(), style = FieldStampStyle, color = rarityTint)
                }
                if (awaitingSpeciesIdentification) {
                    Text(
                        "AWAITING SPECIES ID",
                        style = FieldStampStyle,
                        color = colors.gold,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            if (regionLabel != null) {
                Text(
                    regionLabel.uppercase(),
                    style = FieldStampStyle,
                    color = colors.parchmentFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(caption.uppercase(), style = FieldStampStyle, color = colors.parchmentFaint)
        }
    }
}

/**
 * A struck rubber stamp: the ring with its word across the middle.
 *
 * The label is set a little tighter than [FieldStampStyle] elsewhere, because the inner
 * ring is roughly 0.74 of [size] and a word has to clear it — at the default that is about
 * 47dp of room, which holds eight mono characters.
 */
@Composable
fun FieldStamp(
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    /** Spoken instead of [label] where the stamped word is an abbreviation. */
    description: String = label,
) {
    Box(
        modifier.semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        StampRing(tint.copy(alpha = 0.85f), Modifier.size(size))
        Text(
            label.uppercase(),
            style = FieldStampStyle.copy(fontSize = 9.sp, letterSpacing = 0.5.sp),
            color = tint,
            maxLines = 1,
        )
    }
}

/**
 * The journal's action: a stamped word in an olive pill.
 *
 * Deliberately not a Material button. A filled Material button is the single loudest way
 * for this page to stop reading as paper, and its label would be the only sentence-case
 * sans on a screen where every other control is a stamped word.
 */
@Composable
fun JournalButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Draws the pill filled rather than outlined, for the one primary action on a page. */
    primary: Boolean = false,
) {
    val colors = WildlifeTheme.colors
    val accent = colors.oliveStrong
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (primary) colors.oliveDark else Color.Transparent)
            .border(
                width = if (primary) 1.5.dp else 1.dp,
                color = accent.copy(alpha = if (primary) 0.85f else 0.45f),
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label.uppercase(),
            style = FieldStampStyle,
            color = if (primary) colors.parchment else accent,
            maxLines = 1,
        )
    }
}

// ---------------------------------------------------------------- stacked slips

/**
 * Content presented as the top of a pile of loose slips.
 *
 * Used for the unfiled queue, where the *quantity* of outstanding paperwork should be felt
 * before any number is read. [depth] is a visual weight, not the count — it saturates at
 * three, because a deeper drawn pile stops reading as paper and starts reading as noise.
 */
@Composable
fun SlipStack(
    depth: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val slips = depth.coerceIn(1, 3)
    val rise = 7.dp
    val peek = rise * (slips - 1)
    Box(modifier.fillMaxWidth()) {
        if (slips > 1) {
            Canvas(Modifier.matchParentSize()) {
                drawSlipEdges(
                    fill = WildlifeSurface,
                    stroke = WildlifeOutlineSubtle,
                    shadow = WildlifeBackground,
                    count = slips,
                    rise = rise.toPx(),
                    inset = 7.dp.toPx(),
                    corner = 10.dp.toPx(),
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = peek)
                .clip(RoundedCornerShape(10.dp))
                .background(WildlifeSurface)
                .border(1.dp, WildlifeOutlineSubtle, RoundedCornerShape(10.dp)),
        ) {
            content()
        }
    }
}

/**
 * One typed line of a record: a stamped label, a leader of dots, and its value.
 *
 * The leader is what makes it a form rather than a list row — the eye is carried across
 * the gap instead of jumping it.
 */
@Composable
fun RecordLine(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueTint: Color? = null,
) {
    val colors = WildlifeTheme.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label.uppercase(), style = FieldStampStyle, color = colors.parchmentDim)
        Box(
            Modifier
                .weight(1f)
                .padding(horizontal = 7.dp)
                .height(1.dp)
                .background(WildlifeOutlineSubtle),
        )
        Text(value, style = FieldTallyStyle, color = valueTint ?: colors.parchment)
    }
}
