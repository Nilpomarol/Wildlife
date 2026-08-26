package com.wildlife.feasibility.ui.screens.collection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.wildlife.feasibility.ui.components.JournalTabStrip

enum class CollectionSection(val label: String) {
    SPECIES("Species"),
    OBSERVATIONS("Observations"),
    MAP("Map"),
}

/**
 * Collection's index strip.
 *
 * The three sections are three views of one personal record, so the strip is pinned
 * directly under the destination's title on every one of them and never scrolls away with
 * a section's content: a switch that is in a different place — or gone — depending on
 * which view is open is a switch the user has to hunt for.
 */
@Composable
fun CollectionSectionSelector(
    selected: CollectionSection,
    onSelected: (CollectionSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val labels = remember { CollectionSection.entries.map { it.label } }
    JournalTabStrip(
        labels = labels,
        selectedIndex = selected.ordinal,
        onSelected = { onSelected(CollectionSection.entries[it]) },
        modifier = modifier,
    )
}
