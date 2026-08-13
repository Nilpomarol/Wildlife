package com.wildlife.feasibility.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Biotech
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.wildlife.feasibility.ui.theme.WildlifeSpacing
import com.wildlife.feasibility.ui.theme.WildlifeTheme

data class SpeciesCardModel(
    val key: String,
    val label: String,
    val supportingText: String,
    val photoUrl: String?,
    val silhouetteUrl: String? = null,
    val supportingTextItalic: Boolean = false,
    val status: SpeciesCardStatus = SpeciesCardStatus.NONE,
    val noPhotoLabel: String = "Photo unavailable",
)

enum class SpeciesCardStatus {
    NONE,
    OBSERVED,
    RESEARCH_GRADE,
}

@Composable
fun SpeciesCard(
    species: SpeciesCardModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var imageFailed by remember(species.photoUrl) { mutableStateOf(species.photoUrl == null) }
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(Modifier.fillMaxSize()) {
            if (!imageFailed) {
                AsyncImage(
                    model = species.photoUrl,
                    contentDescription = species.label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    onError = { imageFailed = true },
                )
            } else {
                NoPhotoState(
                    label = species.label,
                    message = species.noPhotoLabel,
                    silhouetteUrl = species.silhouetteUrl,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.38f to Color.Transparent,
                            1f to Color(0xE6080B09),
                        ),
                    ),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(WildlifeSpacing.Small),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = species.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = species.supportingText,
                    style = MaterialTheme.typography.labelSmall,
                    fontStyle = if (species.supportingTextItalic) {
                        FontStyle.Italic
                    } else {
                        FontStyle.Normal
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (species.status != SpeciesCardStatus.NONE) {
                SpeciesStatusBadge(
                    status = species.status,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(WildlifeSpacing.Small),
                )
            }
        }
    }
}

@Composable
private fun SpeciesStatusBadge(
    status: SpeciesCardStatus,
    modifier: Modifier = Modifier,
) {
    if (status == SpeciesCardStatus.NONE) return
    Box(
        modifier = modifier
            .size(30.dp)
            .background(
                color = WildlifeTheme.colors.confirmed.copy(alpha = 0.94f),
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = when (status) {
                SpeciesCardStatus.OBSERVED -> Icons.Outlined.Check
                SpeciesCardStatus.RESEARCH_GRADE -> Icons.Outlined.Biotech
                SpeciesCardStatus.NONE -> Icons.Outlined.Check
            },
            contentDescription = when (status) {
                SpeciesCardStatus.OBSERVED -> "Observed"
                SpeciesCardStatus.RESEARCH_GRADE -> "Research grade"
                SpeciesCardStatus.NONE -> null
            },
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(19.dp),
        )
    }
}

@Composable
private fun NoPhotoState(
    label: String,
    message: String,
    silhouetteUrl: String?,
    modifier: Modifier = Modifier,
) {
    var silhouetteFailed by remember(silhouetteUrl) { mutableStateOf(silhouetteUrl == null) }
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (!silhouetteFailed) {
            AsyncImage(
                model = silhouetteUrl,
                contentDescription = "Representative silhouette for $label",
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(WildlifeTheme.colors.silhouette),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(WildlifeSpacing.Card),
                onError = { silhouetteFailed = true },
            )
        } else {
            Text(
                text = label.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.displayLarge,
                color = WildlifeTheme.colors.silhouette,
            )
        }
        Text(
            text = message,
            style = MaterialTheme.typography.labelSmall,
            color = WildlifeTheme.colors.mutedText,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(WildlifeSpacing.Small),
        )
    }
}
