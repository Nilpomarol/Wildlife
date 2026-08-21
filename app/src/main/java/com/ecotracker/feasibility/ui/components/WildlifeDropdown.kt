package com.wildlife.feasibility.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.wildlife.feasibility.ui.theme.GameFontFamily
import com.wildlife.feasibility.ui.theme.WildlifeSpacing
import com.wildlife.feasibility.ui.theme.WildlifeTheme

/**
 * A playful field-guide selector, shared for catalogue and region choices.
 *
 * [leading] is an optional slot rendered before the label in both the closed pill and each open
 * row — pass a per-option icon to give the menu a game-like, iconographic feel. [accent] tints the
 * pill's chrome (chevron, selected highlight) so different selectors can carry their own identity.
 */
@Composable
fun <T> WildlifeDropdown(
    selected: T,
    options: List<T>,
    label: (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    accent: androidx.compose.ui.graphics.Color? = null,
    leading: (@Composable (T) -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val tint = accent ?: WildlifeTheme.colors.oliveStrong
    val chevronRotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = tint.copy(alpha = 0.14f),
        border = BorderStroke(1.5.dp, tint.copy(alpha = 0.55f)),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 44.dp)
                .clip(CircleShape)
                .clickable { expanded = true }
                .padding(horizontal = WildlifeSpacing.Card),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small),
        ) {
            if (leading != null) leading(selected)
            Text(
                text = label(selected),
                style = MaterialTheme.typography.labelLarge,
                fontFamily = GameFontFamily,
                fontWeight = FontWeight.SemiBold,
                color = WildlifeTheme.colors.parchment,
            )
            Icon(
                imageVector = Icons.Filled.ExpandMore,
                contentDescription = "Choose ${label(selected)}",
                tint = tint,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(chevronRotation),
            )
        }
    }
    if (expanded) {
        Popup(
            onDismissRequest = { expanded = false },
            properties = PopupProperties(focusable = true),
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.5.dp, tint.copy(alpha = 0.4f)),
                shadowElevation = 12.dp,
            ) {
                Column(modifier = Modifier.width(240.dp).padding(WildlifeSpacing.Micro)) {
                    options.forEach { option ->
                        val chosen = option == selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .background(
                                    if (chosen) tint.copy(alpha = 0.16f)
                                    else androidx.compose.ui.graphics.Color.Transparent,
                                )
                                .clickable { onSelected(option); expanded = false }
                                .padding(horizontal = WildlifeSpacing.Card, vertical = WildlifeSpacing.Small),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(WildlifeSpacing.Card),
                        ) {
                            if (leading != null) leading(option)
                            Text(
                                text = label(option),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                                fontFamily = GameFontFamily,
                                fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (chosen) WildlifeTheme.colors.parchment
                                else WildlifeTheme.colors.parchment.copy(alpha = 0.82f),
                            )
                            if (chosen) {
                                Box(
                                    modifier = Modifier.size(22.dp).background(tint, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            } else {
                                Spacer(Modifier.size(22.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
