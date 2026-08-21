package com.wildlife.feasibility.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import com.wildlife.feasibility.ui.theme.WildlifeSpacing

/**
 * Shared responsive field-guide grid. Cards always fall back to their silhouette state.
 *
 * [header] is an optional full-width slot pinned as the first, spanning grid item, so a screen's
 * header, stats and filters scroll together with the cards instead of staying frozen above them.
 * It sits inside the grid's horizontal content padding, matching the cards' side margins.
 */
@Composable
fun <T> SpeciesGrid(
    entries: List<T>,
    key: (T) -> Any,
    model: (T) -> SpeciesCardModel,
    onClick: (T) -> Unit,
    modifier: Modifier = Modifier,
    cardAspectRatio: Float = 0.72f,
    header: (@Composable () -> Unit)? = null,
) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(responsiveSpeciesGridColumns(maxWidth.value, fontScale)),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = WildlifeSpacing.Screen, end = WildlifeSpacing.Screen, bottom = WildlifeSpacing.Section,
            ),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(WildlifeSpacing.Grid),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(WildlifeSpacing.Grid),
        ) {
            if (header != null) {
                item(key = "grid-header", span = { GridItemSpan(maxLineSpan) }) {
                    header()
                }
            }
            items(entries, key = key) { entry ->
                SpeciesCard(
                    species = model(entry), onClick = { onClick(entry) },
                    modifier = Modifier.fillMaxWidth().aspectRatio(cardAspectRatio).animateItem(),
                )
            }
        }
    }
}
