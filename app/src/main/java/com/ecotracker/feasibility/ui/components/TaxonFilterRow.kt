package com.wildlife.feasibility.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wildlife.feasibility.ui.theme.WildlifeSpacing
import com.wildlife.feasibility.ui.theme.WildlifeTheme

/**
 * Horizontally scrolling filter chips.
 *
 * [icon] is optional; when supplied each chip shows a leading glyph so the row reads as a playful,
 * game-like filter bar. Callers that don't pass it keep the plain text chips.
 */
@Composable
fun <T> TaxonFilterRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    icon: ((T) -> ImageVector)? = null,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelected(option) },
                label = {
                    Text(
                        text = label(option),
                        fontWeight = FontWeight.Medium,
                    )
                },
                leadingIcon = icon?.let { resolve ->
                    {
                        Icon(
                            imageVector = resolve(option),
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    }
                },
                shape = androidx.compose.foundation.shape.CircleShape,
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = MaterialTheme.colorScheme.outline,
                    selectedBorderColor = WildlifeTheme.colors.olive,
                    borderWidth = 1.dp,
                    selectedBorderWidth = 1.5.dp,
                ),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    labelColor = MaterialTheme.colorScheme.secondary,
                    iconColor = MaterialTheme.colorScheme.secondary,
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    }
}
