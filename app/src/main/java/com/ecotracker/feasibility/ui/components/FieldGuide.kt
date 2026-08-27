package com.wildlife.feasibility.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.wildlife.feasibility.R
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.wildlife.feasibility.ui.art.ContourField
import com.wildlife.feasibility.ui.art.FieldMark
import com.wildlife.feasibility.ui.art.Grain
import com.wildlife.feasibility.ui.art.RankBadgeArt
import com.wildlife.feasibility.ui.art.RankPatch
import com.wildlife.feasibility.ui.art.RegionMark
import com.wildlife.feasibility.ui.art.Sprig
import com.wildlife.feasibility.ui.art.StatGlyph
import com.wildlife.feasibility.ui.art.StatMark
import com.wildlife.feasibility.ui.art.Vignette
import com.wildlife.feasibility.ui.theme.DisplayFontFamily
import com.wildlife.feasibility.ui.theme.FieldLabelStyle
import com.wildlife.feasibility.ui.theme.FieldStampStyle
import com.wildlife.feasibility.ui.theme.levelAccent
import com.wildlife.feasibility.ui.theme.WildlifeBackground
import com.wildlife.feasibility.ui.theme.WildlifeSpacing
import com.wildlife.feasibility.ui.theme.WildlifeSurface
import com.wildlife.feasibility.ui.theme.WildlifeSurfaceWarm
import com.wildlife.feasibility.ui.theme.WildlifeOutline
import com.wildlife.feasibility.ui.theme.WildlifeOutlineSubtle
import com.wildlife.feasibility.ui.theme.WildlifeTheme

/**
 * The shared surfaces of the Ranger's Journal design.
 *
 * Screens are assembled from these rather than restyling themselves, so a change to the
 * look lands in one place. See `docs/style.md` for the direction these express.
 */

/**
 * Lets a child escape its parent's horizontal padding, so a full-bleed header can sit
 * inside a padded list without the list losing its margins.
 */
fun Modifier.bleedHorizontally(amount: Dp): Modifier = layout { measurable, constraints ->
    val extra = amount.roundToPx() * 2
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = 0,
            maxWidth = if (constraints.maxWidth == Constraints.Infinity) {
                constraints.maxWidth
            } else {
                constraints.maxWidth + extra
            },
        )
    )
    layout((placeable.width - extra).coerceAtLeast(0), placeable.height) {
        placeable.place(-amount.roundToPx(), 0)
    }
}

/** The product identity, sourced from the same artwork as the installed app icon. */
@Composable
fun WildlifeAppIcon(
    size: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Image(
        painter = painterResource(R.drawable.wildlife_app_icon),
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier = modifier.size(size),
    )
}

/**
 * The journal page: contoured, grained ground with a lamplight falloff over the top.
 *
 * Content is drawn between the texture and the vignette, so the falloff reads as light
 * on the page rather than a scrim floating above the UI.
 */
@Composable
fun FieldGuidePage(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.fillMaxSize().background(WildlifeBackground)) {
        // A painted moonlit canopy, fixed behind the content rather than scrolling with
        // it, so the page reads as a clearing you are standing in. It replaces the drawn
        // contours: two competing depictions of terrain would fight each other.
        Image(
            painter = painterResource(R.drawable.field_guide_background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        // The painting is busiest at its edges; this keeps content legible over it
        // without flattening the moon and canopy at the top.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to WildlifeBackground.copy(alpha = 0.10f),
                        0.30f to WildlifeBackground.copy(alpha = 0.45f),
                        1f to WildlifeBackground.copy(alpha = 0.72f),
                    )
                )
        )
        Grain(WildlifeTheme.colors.parchment, Modifier.fillMaxSize(), alpha = 0.030f)
        content()
        Vignette(Color(0xFF050703), Modifier.fillMaxSize(), strength = 0.52f)
    }
}

/**
 * The ground a full-bleed header sits on: transparent at the top so the page painting's
 * moon and canopy stay open, settling to near-solid where the type begins.
 *
 * Shared by every header in the app. A header that mixes its own gradient here is the
 * fastest way for two screens to stop looking like the same product.
 */
fun headerScrim(bottomAlpha: Float = 0.94f): Brush = Brush.verticalGradient(
    0f to Color(0x00000000),
    0.42f to WildlifeBackground.copy(alpha = 0.30f),
    0.78f to WildlifeBackground.copy(alpha = 0.80f),
    1f to WildlifeBackground.copy(alpha = bottomAlpha),
)

/** The hairline that closes a header against the page below it. */
@Composable
fun HeaderHairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(WildlifeOutlineSubtle))
}

/**
 * One readout in the ranger header's stat row.
 *
 * A readout carries either a drawn [mark] or a bundled [fieldMark] asset name, so rarity
 * readouts can use the same artwork as the rarity pills on the plates below them.
 */
data class RangerStat(
    val value: String,
    val label: String,
    val tint: Color,
    val mark: StatMark? = null,
    val fieldMark: String? = null,
)

/**
 * The full-bleed header: the region's emblem and name, the ranger's progression level and
 * a progress measure, sitting on a ground of their own and closed by a hairline rule.
 *
 * [compact] drops the stat row and shortens the scene, for screens where identity should
 * not cost a third of the viewport.
 */
@Composable
fun RangerHeader(
    regionName: String,
    regionKey: String?,
    levelKey: String,
    levelName: String,
    progressLabel: String,
    progressTrailing: String,
    progressFraction: Float,
    stats: List<RangerStat>,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    eyebrow: String = "FIELD GUIDE",
    showBackgroundScrim: Boolean = true,
    scrimBottomAlpha: Float = 0.94f,
    showBottomEdge: Boolean = true,
    /** Optional content under the stat row, for things that belong to the region itself. */
    belowStats: (@Composable () -> Unit)? = null,
) {
    val colors = WildlifeTheme.colors
    // Height follows the content. The header used to reserve a tall block of sky for the
    // landscape it drew itself; now that the painting is the page background and is
    // visible everywhere, reserving that space just pushed the content down.
    Box(modifier.fillMaxWidth()) {
        // The page painting supplies the sky, moon and canopy behind this header, so it
        // draws no scene of its own — only the scrim that keeps type readable on it.
        if (showBackgroundScrim) {
            Box(Modifier.matchParentSize().background(headerScrim(scrimBottomAlpha)))
        }

        Column(
            Modifier.padding(
                start = 16.dp,
                end = 16.dp,
                top = if (compact) 10.dp else 16.dp,
                bottom = if (compact) 14.dp else 18.dp,
            ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RegionEmblem(
                    regionKey = regionKey,
                    size = if (compact) 42.dp else 58.dp,
                )
                Spacer(Modifier.size(13.dp))
                Column(Modifier.weight(1f)) {
                    Text(eyebrow, style = FieldLabelStyle, color = colors.oliveStrong)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        regionName,
                        fontFamily = DisplayFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = if (compact) 18.sp else 21.sp,
                        lineHeight = if (compact) 21.sp else 24.sp,
                        color = colors.parchment,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.size(10.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val accent = levelAccent(levelKey)
                    val badgeSize = if (compact) 40.dp else 52.dp
                    // Owner-drawn badge where one exists; the generated patch is the
                    // fallback so a new level can ship before its artwork does.
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
                            modifier = Modifier.size(
                                width = badgeSize * 0.82f,
                                height = badgeSize,
                            ),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        levelName.uppercase(),
                        style = FieldLabelStyle,
                        color = accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.height(if (compact) 14.dp else 18.dp))
            // Completion is the region's one headline measure, so the percentage carries
            // the weight here and everything below it annotates the same bar.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    progressLabel,
                    style = FieldLabelStyle,
                    color = colors.parchmentDim,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
                Text(
                    progressTrailing,
                    fontFamily = DisplayFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                    color = colors.gold,
                )
            }
            Spacer(Modifier.height(6.dp))
            JournalProgressBar(progressFraction)

            if (!compact && stats.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                    stats.forEach { stat -> StatReadout(stat) }
                }
            }
            if (!compact && belowStats != null) {
                Spacer(Modifier.height(16.dp))
                belowStats()
            }
        }

        if (showBottomEdge) {
            // A plain hairline. The header is separated by its ground and this rule, the
            // way an app bar is — the decorative torn edge that used to sit here drew
            // attention to the seam rather than to the header.
            HeaderHairline(Modifier.align(Alignment.BottomCenter))
        }
    }
}

/**
 * The header's identity disc: the region's own emblem in the region's accent.
 *
 * Falls back to the app icon when a region has no artwork yet, or when no region is
 * selected at all — the badge is never empty and no second product emblem is introduced.
 */
@Composable
fun RegionEmblem(regionKey: String?, size: Dp, modifier: Modifier = Modifier) {
    val colors = WildlifeTheme.colors
    val accent = regionKey?.let { regionVisual(it).accent } ?: colors.oliveStrong
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        // Drawn at full size with no disc behind it: the region artwork is already a
        // self-contained round emblem, and a ring around it just doubled the circle.
        val drawn = regionKey != null && RegionMark(
            regionKey = regionKey,
            color = accent,
            modifier = Modifier.size(size),
        )
        if (!drawn) {
            WildlifeAppIcon(
                size = size,
                contentDescription = "Wildlife",
            )
        }
    }
}

@Composable
fun JournalProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 9.dp,
    accent: Color? = null,
) {
    val colors = WildlifeTheme.colors
    val end = accent ?: colors.oliveStrong
    val start = accent?.copy(alpha = 0.55f) ?: colors.oliveDark
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(WildlifeSurfaceWarm.copy(alpha = 0.85f))
            .border(1.dp, end.copy(alpha = 0.40f), CircleShape),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxSize()
                .clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(start, end)))
        )
    }
}

/**
 * A single inline readout: mark, value, label on one line.
 *
 * These are annotations on the region's progress, not achievements in their own right, so
 * they are deliberately quiet — stacked discs with large numerals competed with the region
 * name for the top of the hierarchy.
 */
@Composable
fun StatReadout(stat: RangerStat, modifier: Modifier = Modifier) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        when {
            stat.fieldMark != null -> FieldMark(stat.fieldMark, stat.tint, Modifier.size(13.dp))
            stat.mark != null -> StatGlyph(stat.mark, stat.tint, Modifier.size(13.dp))
        }
        Text(
            stat.value,
            fontFamily = DisplayFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            color = WildlifeTheme.colors.parchment,
        )
        Text(stat.label, style = FieldLabelStyle, color = WildlifeTheme.colors.parchmentFaint)
    }
}

/** A section rule: a label between hairlines, flanked by mirrored botanical sprigs. */
@Composable
fun SectionRule(
    label: String,
    /** Tints the sprigs, so a section can be recognised by the colour of the thing it holds. */
    accent: Color? = null,
    modifier: Modifier = Modifier,
) {
    val colors = WildlifeTheme.colors
    val sprig = accent ?: colors.oliveStrong
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(1.dp).background(WildlifeOutlineSubtle))
        Sprig(sprig, Modifier.size(width = 58.dp, height = 22.dp))
        Text("  ${label.uppercase()}  ", style = FieldLabelStyle, color = colors.parchment)
        Sprig(sprig, Modifier.size(width = 58.dp, height = 22.dp), mirrored = true)
        Box(Modifier.weight(1f).height(1.dp).background(WildlifeOutlineSubtle))
    }
}

/**
 * The journal's numbered chapter rail.
 *
 * Collection and Explore each hold several views of one destination. The selector stays
 * flat on the page: a quiet rule, mono chapter numbers and small-caps names separated by
 * hairlines. The active chapter gains a physical marker as well as brighter ink, so the
 * state is not communicated by colour alone.
 *
 * Chapters span the full rail with space between their natural-width targets: the first
 * chapter sits at the left content edge and the last at the right. The treatment remains
 * flat page furniture rather than a segmented control because the chapters carry no shared
 * container, fill or rounded outline. Labels ellipsize when constrained, and every chapter
 * retains a 48dp-high target.
 */
@Composable
fun JournalChapterRail(
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = WildlifeSpacing.Screen),
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(WildlifeOutline))
        Row(
            Modifier
                .fillMaxWidth()
                .height(ChapterRailHeight)
                .selectableGroup(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            labels.forEachIndexed { index, label ->
                if (index > 0) {
                    Box(
                        Modifier
                            .size(width = 1.dp, height = 20.dp)
                            .background(WildlifeOutlineSubtle),
                    )
                }
                JournalChapter(
                    number = index + 1,
                    label = label,
                    selected = index == selectedIndex,
                    onClick = { onSelected(index) },
                )
            }
        }
    }
}

private val ChapterRailHeight = 48.dp

@Composable
private fun JournalChapter(
    number: Int,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WildlifeTheme.colors
    Row(
        modifier
            .fillMaxHeight()
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
            )
            .defaultMinSize(minWidth = 48.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 3.dp, height = 16.dp)
                .background(if (selected) colors.oliveStrong else Color.Transparent),
        )
        Spacer(Modifier.size(WildlifeSpacing.Micro))
        Text(
            text = number.toString().padStart(2, '0'),
            style = FieldStampStyle,
            color = if (selected) colors.oliveStrong else colors.parchmentFaint,
            maxLines = 1,
        )
        Spacer(Modifier.size(WildlifeSpacing.Micro))
        Text(
            text = label.uppercase(),
            style = ChapterLabelStyle.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            ),
            color = if (selected) colors.parchment else colors.parchmentDim,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Chapter names are a step larger and less widely tracked than metadata labels so the
 * longest destination name remains legible in Collection's three-chapter rail.
 */
private val ChapterLabelStyle = FieldLabelStyle.copy(fontSize = 11.sp, letterSpacing = 0.7.sp)

@Preview(showBackground = true, backgroundColor = 0xFF0E1209, widthDp = 411)
@Preview(
    name = "Chapter rail large text",
    showBackground = true,
    backgroundColor = 0xFF0E1209,
    widthDp = 411,
    fontScale = 2f,
)
@Composable
private fun JournalChapterRailPreview() {
    com.wildlife.feasibility.ui.theme.WildlifeTheme {
        Column(
            Modifier
                .background(WildlifeBackground)
                .padding(vertical = WildlifeSpacing.Screen),
        ) {
            JournalChapterRail(
                labels = listOf("Species", "Observations", "Map"),
                selectedIndex = 0,
                onSelected = {},
            )
        }
    }
}

/** A filter tab in the journal's tab row. */
@Composable
fun JournalTab(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = WildlifeTheme.colors
    Box(
        modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) colors.oliveDark else WildlifeSurfaceWarm)
            .border(
                width = 1.dp,
                color = if (selected) colors.oliveStrong else WildlifeOutlineSubtle,
                shape = RoundedCornerShape(9.dp),
            )
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label.uppercase(),
            style = FieldLabelStyle,
            color = if (selected) colors.parchment else colors.parchmentDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A small pill carrying a field mark and its word. Mark plus word makes the emblem
 * teachable: the reader learns the mark, then stops needing the label.
 */
@Composable
fun FieldMarkPill(
    markName: String?,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(5.dp))
            .background(tint.copy(alpha = 0.16f))
            .padding(start = if (markName != null) 5.dp else 7.dp, end = 7.dp, top = 3.dp, bottom = 3.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (markName != null) {
                FieldMark(markName, tint, Modifier.size(15.dp))
                Spacer(Modifier.size(5.dp))
            }
            Text(label.uppercase(), style = FieldLabelStyle, color = tint)
        }
    }
}

/** A regional standing mark, matted so it stays readable over photography. */
@Composable
fun RegionalStandingChip(markName: String, tint: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(29.dp)
            .clip(CircleShape)
            .background(WildlifeBackground.copy(alpha = 0.82f))
            .border(1.2.dp, tint.copy(alpha = 0.72f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        FieldMark(markName, tint, Modifier.size(18.dp))
    }
}

/**
 * The stacked regional standing marks for a species. Order is deliberate: the rarer
 * standing sits topmost so an Icon reads first.
 */
@Composable
fun RegionalStandingColumn(
    icon: Boolean,
    essential: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!icon && !essential) return
    val colors = WildlifeTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        if (icon) RegionalStandingChip("regional_icon", colors.icon)
        if (essential) RegionalStandingChip("regional_essential", colors.essential)
    }
}

/** Surface used for the journal's inset controls, such as the search field. */
@Composable
fun JournalSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(WildlifeSurfaceWarm)
            .border(1.dp, WildlifeOutlineSubtle, RoundedCornerShape(12.dp)),
    ) {
        content()
    }
}

/** The card surface for a species plate, framed by its regional standing. */
@Composable
fun PlateSurface(
    standingBorder: Color?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(WildlifeSurface)
            .border(
                width = if (standingBorder != null) 1.5.dp else 1.dp,
                color = standingBorder ?: WildlifeOutlineSubtle,
                shape = RoundedCornerShape(14.dp),
            ),
    ) {
        content()
    }
}
