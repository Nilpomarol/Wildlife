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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.wildlife.feasibility.WildlifeNetworkIdentity
import com.wildlife.feasibility.ui.theme.WildlifeSpacing
import com.wildlife.feasibility.ui.theme.WildlifeTheme

data class SpeciesCardModel(
    val key: String,
    val label: String,
    val supportingText: String,
    val photoUrl: String?,
    val photoKind: SpeciesCardPhotoKind? = null,
    val photoFallbackUrl: String? = null,
    val photoFallbackKind: SpeciesCardPhotoKind? = null,
    val photoFallbackAttribution: String? = null,
    val additionalPhotoFallbacks: List<SpeciesCardPhotoCandidate> = emptyList(),
    val photoAttribution: String? = null,
    val silhouetteUrl: String? = null,
    val silhouetteFallbackUrl: String? = null,
    val silhouetteMatchRank: String? = null,
    val supportingTextItalic: Boolean = false,
    val status: SpeciesCardStatus = SpeciesCardStatus.NONE,
)

enum class SpeciesCardPhotoKind {
    PERSONAL,
    REFERENCE,
}

data class SpeciesCardPhotoCandidate(
    val url: String,
    val kind: SpeciesCardPhotoKind,
    val attribution: String? = null,
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
    val context = LocalContext.current
    val photoCandidates = remember(
        species.photoUrl,
        species.photoKind,
        species.photoAttribution,
        species.photoFallbackUrl,
        species.photoFallbackKind,
        species.photoFallbackAttribution,
        species.additionalPhotoFallbacks,
    ) {
        species.orderedPhotoCandidates()
    }
    var activePhotoIndex by remember(photoCandidates) { mutableStateOf(0) }
    var imageFailed by remember(photoCandidates) {
        mutableStateOf(photoCandidates.isEmpty())
    }
    val activePhoto = photoCandidates.getOrNull(activePhotoIndex)
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
                    model = ImageRequest.Builder(context)
                        .data(activePhoto?.url)
                        .setHeader(
                            "User-Agent",
                            WildlifeNetworkIdentity.REFERENCE_MEDIA_USER_AGENT,
                        )
                        .build(),
                    contentDescription = when (activePhoto?.kind) {
                        SpeciesCardPhotoKind.PERSONAL ->
                            "${species.label}, your observation photo"
                        SpeciesCardPhotoKind.REFERENCE ->
                            "${species.label}, attributed reference photo"
                        null -> species.label
                    },
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    onError = {
                        if (activePhotoIndex < photoCandidates.lastIndex) {
                            activePhotoIndex++
                        } else {
                            imageFailed = true
                        }
                    },
                )
            } else {
                NoPhotoState(
                    label = species.label,
                    silhouetteUrl = species.silhouetteUrl,
                    silhouetteFallbackUrl = species.silhouetteFallbackUrl,
                    silhouetteMatchRank = species.silhouetteMatchRank,
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
            if (!imageFailed && activePhoto?.attribution != null) {
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(WildlifeSpacing.Small),
                ) {
                    Text(
                        text = activePhoto.attribution,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                }
            }
        }
    }
}

internal fun SpeciesCardModel.orderedPhotoCandidates(): List<SpeciesCardPhotoCandidate> =
    buildList {
        photoUrl?.let { url ->
            add(
                SpeciesCardPhotoCandidate(
                    url,
                    photoKind ?: SpeciesCardPhotoKind.REFERENCE,
                    photoAttribution,
                ),
            )
        }
        photoFallbackUrl?.let { url ->
            add(
                SpeciesCardPhotoCandidate(
                    url,
                    photoFallbackKind ?: SpeciesCardPhotoKind.REFERENCE,
                    photoFallbackAttribution,
                ),
            )
        }
        addAll(additionalPhotoFallbacks)
    }.distinctBy(SpeciesCardPhotoCandidate::url)

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
    silhouetteUrl: String?,
    silhouetteFallbackUrl: String?,
    silhouetteMatchRank: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var activeSilhouetteUrl by remember(silhouetteUrl, silhouetteFallbackUrl) {
        mutableStateOf(silhouetteUrl)
    }
    var silhouetteFailed by remember(silhouetteUrl, silhouetteFallbackUrl) {
        mutableStateOf(silhouetteUrl == null)
    }
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (!silhouetteFailed) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(activeSilhouetteUrl)
                    .setHeader(
                        "User-Agent",
                        WildlifeNetworkIdentity.REFERENCE_MEDIA_USER_AGENT,
                    )
                    .build(),
                contentDescription = if (silhouetteMatchRank == "species") {
                    "Silhouette for $label"
                } else {
                    "Representative silhouette for $label"
                },
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(WildlifeTheme.colors.silhouette),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(WildlifeSpacing.Card),
                onError = {
                    if (activeSilhouetteUrl != silhouetteFallbackUrl &&
                        silhouetteFallbackUrl != null
                    ) {
                        activeSilhouetteUrl = silhouetteFallbackUrl
                    } else {
                        silhouetteFailed = true
                    }
                },
            )
        } else {
            Text(
                text = label.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.displayLarge,
                color = WildlifeTheme.colors.silhouette,
            )
        }
    }
}
