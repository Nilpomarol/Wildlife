package com.wildlife.feasibility.ui.navigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wildlife.feasibility.ui.theme.WildlifeSpacing
import com.wildlife.feasibility.ui.theme.WildlifeTheme

enum class WildlifeDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    HOME("home", "Home", Icons.Outlined.Home),
    COLLECTION("collection", "Collection", Icons.Outlined.PhotoLibrary),
    CAPTURE("capture", "Capture", Icons.Outlined.CameraAlt),
    EXPLORE("explore", "Explore", Icons.Outlined.Explore),
    PROFILE("profile", "Profile", Icons.Outlined.Person),
}

@Composable
fun WildlifeBottomBar(
    selected: WildlifeDestination,
    onSelect: (WildlifeDestination) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 0.dp,
        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp)
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WildlifeDestination.entries.forEach { destination ->
                val isCapture = destination == WildlifeDestination.CAPTURE
                val isSelected = destination == selected
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .selectable(
                            selected = isSelected,
                            onClick = { onSelect(destination) },
                            role = Role.Tab,
                        )
                        .padding(vertical = WildlifeSpacing.Micro),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (isCapture) {
                        Surface(
                            shape = CircleShape,
                            color = WildlifeTheme.colors.olive,
                            border = BorderStroke(3.dp, MaterialTheme.colorScheme.background),
                            modifier = Modifier
                                .offset(y = (-8).dp)
                                .size(56.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = null,
                                    tint = WildlifeTheme.colors.parchment,
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                        }
                    } else {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = null,
                            tint = if (isSelected) {
                                WildlifeTheme.colors.oliveStrong
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Text(
                        text = destination.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected || isCapture) {
                            WildlifeTheme.colors.parchment
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = if (isCapture) Modifier.offset(y = (-8).dp) else Modifier,
                    )
                }
            }
        }
    }
}
