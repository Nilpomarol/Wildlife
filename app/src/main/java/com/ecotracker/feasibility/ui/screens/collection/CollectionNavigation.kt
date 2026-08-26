package com.wildlife.feasibility.ui.screens.collection

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow
import com.wildlife.feasibility.ui.theme.WildlifeTheme

enum class CollectionSection(val label: String) {
    SPECIES("Species"),
    OBSERVATIONS("Observations"),
    MAP("Map"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionSectionSelector(
    selected: CollectionSection,
    onSelected: (CollectionSection) -> Unit,
) {
    SecondaryTabRow(
        selectedTabIndex = selected.ordinal,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = WildlifeTheme.colors.oliveStrong,
    ) {
        CollectionSection.entries.forEach { section ->
            Tab(
                selected = section == selected,
                onClick = { onSelected(section) },
                text = {
                    Text(
                        text = section.label,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                selectedContentColor = WildlifeTheme.colors.oliveStrong,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
