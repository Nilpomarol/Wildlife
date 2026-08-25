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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import com.wildlife.feasibility.ui.theme.WildlifeSpacing
import kotlinx.coroutines.flow.distinctUntilChanged

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
    /** Overrides the proportion derived from the column count. Lower ratio = taller card. */
    cardAspectRatio: Float? = null,
    header: (@Composable () -> Unit)? = null,
    /** Reports only cards in the current viewport. No work is performed when omitted. */
    onVisibleEntriesChanged: ((List<T>) -> Unit)? = null,
) {
    val fontScale = LocalDensity.current.fontScale
    val gridState = rememberLazyGridState()
    val currentEntries by rememberUpdatedState(entries)
    val currentKey by rememberUpdatedState(key)
    val currentVisibleCallback by rememberUpdatedState(onVisibleEntriesChanged)

    if (onVisibleEntriesChanged != null) {
        LaunchedEffect(gridState, entries) {
            snapshotFlow { gridState.layoutInfo.visibleItemsInfo.map { it.key } }
                .distinctUntilChanged()
                .collect { visibleKeys ->
                    val callback = currentVisibleCallback ?: return@collect
                    val visibleKeySet = visibleKeys.toHashSet()
                    callback(currentEntries.filter { currentKey(it) in visibleKeySet })
                }
        }
    }
    BoxWithConstraints(modifier) {
        val columns = responsiveSpeciesGridColumns(maxWidth.value, fontScale)
        val ratio = cardAspectRatio ?: speciesCardAspectRatio(columns)
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
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
                    modifier = Modifier.fillMaxWidth().aspectRatio(ratio).animateItem(),
                )
            }
        }
    }
}
