package com.wildlife.feasibility.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wildlife.feasibility.InstalledRegionalCatalogue
import com.wildlife.feasibility.ui.theme.CaribbeanTeal
import com.wildlife.feasibility.ui.theme.GameFontFamily
import com.wildlife.feasibility.ui.theme.WildlifeGold
import com.wildlife.feasibility.ui.theme.WildlifeOliveStrong
import com.wildlife.feasibility.ui.theme.WildlifeSpacing
import com.wildlife.feasibility.ui.theme.WildlifeTheme

/** Icon and accent used to give each installed region a stable, recognisable identity. */
data class RegionVisual(val icon: ImageVector, val accent: Color)

fun regionVisual(regionKey: String): RegionVisual = when (regionKey) {
    "mediterranean_europe" -> RegionVisual(Icons.Filled.Forest, WildlifeOliveStrong)
    "east_africa" -> RegionVisual(Icons.Filled.Landscape, WildlifeGold)
    "caribbean" -> RegionVisual(Icons.Filled.Waves, CaribbeanTeal)
    else -> RegionVisual(Icons.Filled.Public, WildlifeOliveStrong)
}

@Composable
fun RegionGlyph(visual: RegionVisual, size: Int = 26) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(visual.accent.copy(alpha = 0.22f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = visual.icon,
            contentDescription = null,
            tint = visual.accent,
            modifier = Modifier.size((size * 0.62f).dp),
        )
    }
}

/**
 * Chooses the active regional catalogue. This lives on Explore rather than Collection because
 * Explore is the surface that works without a linked account, so the choice stays reachable for
 * every user. Never performs continuous location tracking; the choice is always manual.
 */
@Composable
fun RegionSelector(
    selected: InstalledRegionalCatalogue?,
    catalogues: List<InstalledRegionalCatalogue>,
    onSelectRegion: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selected == null) return
    val visual = regionVisual(selected.regionKey)
    if (catalogues.size <= 1) {
        RegionPill(selected = selected, modifier = modifier)
        return
    }
    WildlifeDropdown(
        selected = selected,
        options = catalogues,
        label = InstalledRegionalCatalogue::displayName,
        onSelected = { onSelectRegion(it.regionKey) },
        accent = visual.accent,
        leading = { RegionGlyph(regionVisual(it.regionKey)) },
        modifier = modifier,
    )
}

/** Read-only counterpart for screens that show the active region but do not own the choice. */
@Composable
fun RegionPill(
    selected: InstalledRegionalCatalogue,
    modifier: Modifier = Modifier,
) {
    val visual = regionVisual(selected.regionKey)
    Surface(
        modifier = modifier.clearAndSetSemantics {
            contentDescription = "Active region: ${selected.displayName}"
        },
        shape = CircleShape,
        color = visual.accent.copy(alpha = 0.14f),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 44.dp)
                .padding(horizontal = WildlifeSpacing.Card),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small),
        ) {
            RegionGlyph(visual)
            Text(
                text = selected.displayName,
                style = MaterialTheme.typography.labelLarge,
                fontFamily = GameFontFamily,
                fontWeight = FontWeight.SemiBold,
                color = WildlifeTheme.colors.parchment,
            )
        }
    }
}
