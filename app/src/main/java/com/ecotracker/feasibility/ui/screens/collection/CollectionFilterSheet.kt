package com.wildlife.feasibility.ui.screens.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wildlife.feasibility.ui.art.FieldMark
import com.wildlife.feasibility.ui.art.StatGlyph
import com.wildlife.feasibility.ui.art.StatMark
import com.wildlife.feasibility.ui.art.TaxonSilhouette
import com.wildlife.feasibility.ui.components.SectionRule
import com.wildlife.feasibility.ui.theme.DisplayFontFamily
import com.wildlife.feasibility.ui.theme.FieldLabelStyle
import com.wildlife.feasibility.ui.theme.WildlifeBackground
import com.wildlife.feasibility.ui.theme.WildlifeSpacing
import com.wildlife.feasibility.ui.theme.WildlifeSurfaceWarm
import com.wildlife.feasibility.ui.theme.WildlifeTheme

/**
 * Every filter axis on one sheet.
 *
 * The axes are shown together rather than as a scrolling row of pills because they are now
 * meant to be combined — "missing Essentials" is two axes, and a control the user has to
 * scroll sideways to discover does not invite that.
 *
 * Selections apply immediately and the match count updates live, so the sheet needs no
 * apply button: the user can see what a combination yields before dismissing it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionFilterSheet(
    filters: CollectionFilters,
    onFilters: (CollectionFilters) -> Unit,
    presentGroups: List<SpeciesGroup>,
    matchCount: Int,
    onDismiss: () -> Unit,
) {
    val colors = WildlifeTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = WildlifeBackground,
        contentColor = colors.parchment,
        // Material's default scrim is a light grey, which over this palette reads as
        // fog rather than as the page dimming behind the sheet.
        scrimColor = Color(0xCC050703),
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), Alignment.Center) {
                Box(
                    Modifier
                        .size(width = 34.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(colors.oliveDark)
                )
            }
        },
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = WildlifeSpacing.Screen)
                .padding(bottom = WildlifeSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Card),
        ) {
            SheetHeading(
                matchCount = matchCount,
                showReset = filters.isActive,
                onReset = { onFilters(CollectionFilters.None) },
            )

            // Each axis keeps the accent it carries in the row above, so a chip and the
            // selector it belongs to are recognisably the same control.
            FilterSection("Status", colors.axisStatus) {
                StatusFilter.entries.forEach { option ->
                    FilterChip(
                        label = option.label,
                        selected = filters.status == option,
                        accent = colors.axisStatus,
                        onClick = { onFilters(filters.copy(status = option)) },
                        mark = { tint -> StatusMark(option, tint) },
                    )
                }
            }

            FilterSection("Standing", colors.axisStanding) {
                StandingFilter.entries.forEach { option ->
                    FilterChip(
                        label = option.label,
                        selected = filters.standing == option,
                        accent = colors.axisStanding,
                        onClick = { onFilters(filters.copy(standing = option)) },
                        mark = { tint -> StandingMark(option, tint) },
                    )
                }
            }

            FilterSection("Rarity", colors.axisRarity) {
                RarityFilter.entries.forEach { option ->
                    FilterChip(
                        label = option.label,
                        selected = filters.rarity == option,
                        accent = colors.axisRarity,
                        onClick = { onFilters(filters.copy(rarity = option)) },
                        mark = { tint -> RarityMark(option, tint) },
                    )
                }
            }

            // Regions ship with different taxa; a region with no groups gets no section
            // rather than a section offering only "Any group".
            if (presentGroups.isNotEmpty()) {
                FilterSection("Group", colors.axisGroup) {
                    FilterChip(
                        label = "Any group",
                        selected = filters.group == null,
                        accent = colors.axisGroup,
                        onClick = { onFilters(filters.copy(group = null)) },
                        mark = null,
                    )
                    presentGroups.forEach { group ->
                        FilterChip(
                            label = group.label,
                            selected = filters.group == group,
                            accent = colors.axisGroup,
                            onClick = { onFilters(filters.copy(group = group)) },
                            mark = { tint ->
                                TaxonSilhouette(group.key, tint, Modifier.size(15.dp))
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetHeading(matchCount: Int, showReset: Boolean, onReset: () -> Unit) {
    val colors = WildlifeTheme.colors
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (matchCount == 1) "1 species" else "$matchCount species",
            fontFamily = DisplayFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            color = colors.parchment,
        )
        if (showReset) {
            Text(
                text = "RESET",
                style = FieldLabelStyle,
                color = colors.parchment,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onReset)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSection(title: String, accent: Color, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small)) {
        SectionRule(title, accent)
        // Wrapping rather than scrolling: an axis whose options run off the edge hides the
        // very combinations the split was made to enable.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small),
            verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small),
        ) {
            content()
        }
    }
}

/**
 * One option. Selected takes the olive fill and border of an active control; unselected is
 * an outline only, so a glance at the sheet shows which axes are doing something.
 */
@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    mark: (@Composable (Color) -> Unit)? = null,
) {
    val colors = WildlifeTheme.colors
    val tint = if (selected) accent else colors.parchmentDim
    Row(
        modifier = Modifier
            .heightIn(min = 38.dp)
            .clip(CircleShape)
            .background(
                if (selected) accent.copy(alpha = 0.16f)
                else WildlifeSurfaceWarm.copy(alpha = 0.60f)
            )
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                // Even unselected, the outline carries a trace of the axis accent, so a
                // section reads as one family rather than as generic chips under a
                // coloured heading.
                color = if (selected) accent.copy(alpha = 0.70f) else accent.copy(alpha = 0.30f),
                shape = CircleShape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = WildlifeSpacing.Card),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small),
    ) {
        mark?.invoke(tint)
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) colors.parchment else colors.parchmentDim,
        )
    }
}

// ---------------------------------------------------------------- option marks
//
// The chips reuse the mark language of the cards, so the sheet teaches the same vocabulary
// the grid uses rather than inventing a second set of symbols for the same ideas. They are
// internal because the inline row in CollectionScreen shows the same axes and has to mark
// them identically.

@Composable
internal fun StatusMark(option: StatusFilter, tint: Color) {
    val mark = when (option) {
        StatusFilter.ANY -> return
        StatusFilter.MISSING -> StatMark.DOT
        StatusFilter.RECORDED -> StatMark.TICK
        StatusFilter.CONFIRMED -> StatMark.ROSETTE
        StatusFilter.AWAITING -> StatMark.SPARKLE
    }
    StatGlyph(mark, tint, Modifier.size(15.dp))
}

@Composable
internal fun StandingMark(option: StandingFilter, tint: Color) {
    when (option) {
        StandingFilter.ANY -> Unit
        StandingFilter.ESSENTIALS -> FieldMark("regional_essential", tint, Modifier.size(15.dp))
        StandingFilter.ICONS -> FieldMark("regional_icon", tint, Modifier.size(15.dp))
        // The bundled legend mark, not a drawn sparkle: Awaiting ID already uses the
        // sparkle, and one glyph carrying two unrelated meanings teaches neither.
        StandingFilter.LEGENDS -> FieldMark("regional_legend", tint, Modifier.size(15.dp))
    }
}

@Composable
internal fun RarityMark(option: RarityFilter, tint: Color) {
    // Common has no mark by design (style.md §30), so it takes the neutral dot here rather
    // than borrowing another tier's symbol.
    when (option) {
        RarityFilter.ANY -> Unit
        RarityFilter.COMMON -> StatGlyph(StatMark.DOT, tint, Modifier.size(15.dp))
        RarityFilter.UNCOMMON -> FieldMark("rarity_uncommon", tint, Modifier.size(15.dp))
        RarityFilter.RARE -> FieldMark("rarity_rare", tint, Modifier.size(15.dp))
        RarityFilter.VERY_RARE -> FieldMark("rarity_very_rare", tint, Modifier.size(15.dp))
    }
}
