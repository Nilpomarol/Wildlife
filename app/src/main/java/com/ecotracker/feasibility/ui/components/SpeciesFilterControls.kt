package com.wildlife.feasibility.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wildlife.feasibility.ui.art.ClearGlyph
import com.wildlife.feasibility.ui.theme.DisplayFontFamily
import com.wildlife.feasibility.ui.theme.WildlifeSpacing
import com.wildlife.feasibility.ui.theme.WildlifeTheme

/** Search and multi-axis filter controls shared by species grids. */
@Composable
fun SpeciesSearchFilterRow(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    activeCount: Int,
    onOpenFilters: () -> Unit,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CollectionSearchBar(
            query = query,
            onQueryChange = onQueryChange,
            placeholder = placeholder,
            modifier = Modifier.weight(1f),
        )
        SpeciesFiltersButton(activeCount = activeCount, onClick = onOpenFilters)
        if (activeCount > 0) {
            ClearSpeciesFiltersButton(onClear = onClearFilters)
        }
    }
}

@Composable
private fun ClearSpeciesFiltersButton(onClear: () -> Unit) {
    val colors = WildlifeTheme.colors
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .border(1.dp, colors.parchmentDim.copy(alpha = 0.40f), CircleShape)
            .clickable(onClick = onClear)
            .semantics { contentDescription = "Clear all filters" },
        contentAlignment = Alignment.Center,
    ) {
        ClearGlyph(colors.parchmentDim, Modifier.size(14.dp))
    }
}

@Composable
private fun SpeciesFiltersButton(activeCount: Int, onClick: () -> Unit) {
    val colors = WildlifeTheme.colors
    val active = activeCount > 0
    val chrome = if (active) colors.parchment else colors.parchmentDim
    Row(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(CircleShape)
            .background(if (active) chrome.copy(alpha = 0.12f) else Color.Transparent)
            .border(
                width = if (active) 1.5.dp else 1.dp,
                color = chrome.copy(alpha = if (active) 0.75f else 0.50f),
                shape = CircleShape,
            )
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = if (active) "Filters, $activeCount active" else "Filters"
            }
            .padding(horizontal = WildlifeSpacing.Card),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Filled.Tune, contentDescription = null, tint = chrome, modifier = Modifier.size(18.dp))
        if (active) {
            Text(
                text = activeCount.toString(),
                fontFamily = DisplayFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = colors.parchment,
            )
        }
    }
}
