package com.wildlife.feasibility.ui.screens.capture

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EditLocationAlt
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.wildlife.feasibility.MarkerState
import com.wildlife.feasibility.MatchBand
import com.wildlife.feasibility.MatchProposal
import com.wildlife.feasibility.ui.components.WildlifeScaffold
import com.wildlife.feasibility.ui.theme.WildlifeSpacing
import com.wildlife.feasibility.ui.theme.WildlifeTheme
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun CaptureScreen(
    state: CaptureUiState,
    onBack: () -> Unit,
    onTakePhoto: () -> Unit,
    onChoosePhotos: () -> Unit,
    onTogglePhoto: (String, Boolean) -> Unit,
    onContinueInINaturalist: () -> Unit,
    onSubmitted: (String) -> Unit,
    onNotSubmitted: (String) -> Unit,
    onCheckNow: () -> Unit,
    onConfirmMatch: (MatchProposal) -> Unit,
    onOpenObservation: (String) -> Unit,
    onDelete: (String) -> Unit,
    onSaveMetadata: (String, String, String, String) -> Unit,
    onLinkAccount: () -> Unit,
    onDismissReward: () -> Unit,
    onOpenCollection: () -> Unit,
    onDismissHandoffUnavailable: () -> Unit,
    onOpenINaturalistWeb: () -> Unit,
    onOpenINaturalistStore: () -> Unit,
) {
    var deleting by remember { mutableStateOf<CaptureObservationUi?>(null) }
    var editing by remember { mutableStateOf<CapturePhotoUi?>(null) }
    WildlifeScaffold(title = "New observation", onBack = onBack) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = WildlifeSpacing.Screen,
                end = WildlifeSpacing.Screen,
                bottom = WildlifeSpacing.Section,
            ),
            verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Screen),
        ) {
            if (state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            item { CaptureGuideCard() }
            if (state.account == null) {
                item { UnlinkedCard(onLinkAccount) }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small),
                ) {
                    OutlinedButton(
                        onClick = onTakePhoto,
                        modifier = Modifier.weight(1f),
                        enabled = !state.busy,
                    ) {
                        Icon(Icons.Outlined.CameraAlt, contentDescription = null)
                        Text("Camera", Modifier.padding(start = WildlifeSpacing.Micro))
                    }
                    OutlinedButton(
                        onClick = onChoosePhotos,
                        modifier = Modifier.weight(1f),
                        enabled = !state.busy,
                    ) {
                        Icon(Icons.Outlined.PhotoLibrary, contentDescription = null)
                        Text("Gallery", Modifier.padding(start = WildlifeSpacing.Micro))
                    }
                }
            }
            item {
                Button(
                    onClick = onContinueInINaturalist,
                    enabled = state.selectedDraftPhotos > 0 && !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
                    Text(
                        if (state.selectedDraftPhotos == 0) {
                            "Select photos to continue"
                        } else {
                            "Continue one observation in iNaturalist"
                        },
                        Modifier.padding(start = WildlifeSpacing.Small),
                    )
                }
            }
            item {
                StatusMessage(state.statusMessage)
            }
            if (state.observations.isEmpty()) {
                item { EmptyCaptureState() }
            } else {
                items(state.observations, key = CaptureObservationUi::groupId) { observation ->
                    ObservationCard(
                        observation = observation,
                        busy = state.busy,
                        onTogglePhoto = onTogglePhoto,
                        onSubmitted = { onSubmitted(observation.groupId) },
                        onNotSubmitted = { onNotSubmitted(observation.groupId) },
                        onCheckNow = onCheckNow,
                        onConfirmMatch = onConfirmMatch,
                        onOpenObservation = onOpenObservation,
                        onDelete = { deleting = observation },
                        onEditMetadata = { editing = it },
                    )
                }
            }
        }
    }

    deleting?.let { observation ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete from Wildlife?") },
            text = {
                Text(
                    if (observation.state == MarkerState.CONFIRMED) {
                        "The iNaturalist observation remains online. Wildlife removes only its local record and private camera copies."
                    } else {
                        "Matching checks stop. Imported originals remain untouched; Wildlife removes only its local record and private camera copies."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(observation.groupId)
                    deleting = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("Cancel") }
            },
        )
    }
    editing?.let { photo ->
        MetadataDialog(
            photo = photo,
            onDismiss = { editing = null },
            onSave = { date, latitude, longitude ->
                onSaveMetadata(photo.markerId, date, latitude, longitude)
                editing = null
            },
        )
    }
    state.reward?.let { reward ->
        RewardDialog(reward, onDismissReward, onOpenCollection)
    }
    if (state.handoffUnavailable) {
        AlertDialog(
            onDismissRequest = onDismissHandoffUnavailable,
            title = { Text("iNaturalist app needed") },
            text = {
                Text(
                    "Wildlife can only transfer photos directly to the official iNaturalist app. " +
                        "You can install it, or open the web uploader and add the photos manually.",
                )
            },
            confirmButton = {
                TextButton(onClick = onOpenINaturalistStore) { Text("Install app") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = onOpenINaturalistWeb) { Text("Open web") }
                    TextButton(onClick = onDismissHandoffUnavailable) { Text("Cancel") }
                }
            },
        )
    }
}

@Composable
private fun CaptureGuideCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(
            modifier = Modifier.padding(WildlifeSpacing.Card),
            verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Micro),
        ) {
            Text("One sighting, one observation", style = MaterialTheme.typography.titleMedium)
            Text(
                "Add one or more photos of the same animal at the same place and time. iNaturalist handles identification and submission.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun UnlinkedCard(onLinkAccount: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(WildlifeSpacing.Card),
            verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small),
        ) {
            Text("Link required for confirmation", style = MaterialTheme.typography.titleMedium)
            Text(
                "You may prepare and submit a sighting, but Wildlife needs your verified public iNaturalist user ID to match it and award XP.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onLinkAccount) {
                Icon(Icons.Outlined.Link, contentDescription = null)
                Text("Link iNaturalist", Modifier.padding(start = WildlifeSpacing.Small))
            }
        }
    }
}

@Composable
private fun StatusMessage(message: String) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(WildlifeSpacing.Card),
        )
    }
}

@Composable
private fun EmptyCaptureState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = WildlifeSpacing.Large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small),
    ) {
        Icon(
            Icons.Outlined.CameraAlt,
            contentDescription = null,
            tint = WildlifeTheme.colors.silhouette,
            modifier = Modifier.size(52.dp),
        )
        Text("No observations yet", style = MaterialTheme.typography.titleMedium)
        Text(
            "Take a photo or choose existing photos to create a draft.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ObservationCard(
    observation: CaptureObservationUi,
    busy: Boolean,
    onTogglePhoto: (String, Boolean) -> Unit,
    onSubmitted: () -> Unit,
    onNotSubmitted: () -> Unit,
    onCheckNow: () -> Unit,
    onConfirmMatch: (MatchProposal) -> Unit,
    onOpenObservation: (String) -> Unit,
    onDelete: () -> Unit,
    onEditMetadata: (CapturePhotoUi) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(
            modifier = Modifier.padding(WildlifeSpacing.Card),
            verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small),
        ) {
            ObservationHeader(observation.state, observation.photos.size)
            Text(
                text = stateExplanation(observation.state),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small)) {
                items(observation.photos, key = CapturePhotoUi::markerId) { photo ->
                    CapturePhotoTile(
                        photo = photo,
                        selectable = observation.state == MarkerState.CAPTURED,
                        onToggle = { onTogglePhoto(photo.markerId, it) },
                        onEditMetadata = { onEditMetadata(photo) },
                    )
                }
            }
            when (observation.state) {
                MarkerState.HANDED_OFF -> {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        "Did you submit this observation in iNaturalist?",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Button(onClick = onSubmitted, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text("Yes, I submitted it")
                    }
                    OutlinedButton(
                        onClick = onNotSubmitted,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("No, return it to draft") }
                }
                MarkerState.PENDING -> {
                    OutlinedButton(
                        onClick = onCheckNow,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.CloudSync, contentDescription = null)
                        Text("Check iNaturalist now", Modifier.padding(start = WildlifeSpacing.Small))
                    }
                }
                else -> Unit
            }
            observation.proposals.forEach { proposal ->
                MatchCandidateCard(proposal, busy, onConfirmMatch, onOpenObservation)
            }
            observation.matchedObservationUuid?.let { uuid ->
                OutlinedButton(onClick = { onOpenObservation(uuid) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
                    Text("Open confirmed observation", Modifier.padding(start = WildlifeSpacing.Small))
                }
            }
            TextButton(onClick = onDelete, enabled = !busy) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = null)
                Text("Delete from Wildlife", Modifier.padding(start = WildlifeSpacing.Micro))
            }
        }
    }
}

@Composable
private fun ObservationHeader(state: MarkerState, photoCount: Int) {
    val icon = when (state) {
        MarkerState.CAPTURED -> Icons.Outlined.CameraAlt
        MarkerState.HANDED_OFF -> Icons.Outlined.HourglassTop
        MarkerState.PENDING -> Icons.Outlined.CloudSync
        MarkerState.CONFIRMED -> Icons.Outlined.CheckCircle
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small),
        ) {
            Surface(
                shape = CircleShape,
                color = if (state == MarkerState.CONFIRMED) {
                    WildlifeTheme.colors.confirmed
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            ) {
                Icon(icon, contentDescription = null, Modifier.padding(WildlifeSpacing.Small))
            }
            Text(friendlyState(state), style = MaterialTheme.typography.titleMedium)
        }
        Text(
            if (photoCount == 1) "1 photo" else "$photoCount photos",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CapturePhotoTile(
    photo: CapturePhotoUi,
    selectable: Boolean,
    onToggle: (Boolean) -> Unit,
    onEditMetadata: () -> Unit,
) {
    Card(
        onClick = { if (selectable) onToggle(!photo.selected) },
        modifier = Modifier.width(164.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(
            1.dp,
            if (photo.selected && selectable) {
                WildlifeTheme.colors.oliveStrong
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(116.dp),
            ) {
                AsyncImage(
                    model = photo.imageUri,
                    contentDescription = "Observation draft photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (selectable) {
                    Checkbox(
                        checked = photo.selected,
                        onCheckedChange = onToggle,
                        modifier = Modifier.align(Alignment.TopEnd),
                    )
                }
            }
            Column(
                modifier = Modifier.padding(WildlifeSpacing.Small),
                verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Micro),
            ) {
                Text(
                    if (photo.capturedAtReliable) {
                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                            .format(Date(photo.capturedAtMs))
                    } else {
                        "Original time needed"
                    },
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    if (photo.locationReliable) "Location saved" else "Location unavailable",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (selectable && (!photo.capturedAtReliable || !photo.locationReliable)) {
                    TextButton(onClick = onEditMetadata, contentPadding = PaddingValues(0.dp)) {
                        Icon(Icons.Outlined.EditLocationAlt, contentDescription = null)
                        Text("Add metadata")
                    }
                }
            }
        }
    }
}

@Composable
private fun MatchCandidateCard(
    proposal: MatchProposal,
    busy: Boolean,
    onConfirmMatch: (MatchProposal) -> Unit,
    onOpenObservation: (String) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(WildlifeSpacing.Card),
            verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small),
        ) {
            Text("Possible public match", style = MaterialTheme.typography.titleMedium)
            val distance = proposal.distanceKm?.let { "${(it * 10).roundToInt() / 10.0} km" }
                ?: "location obscured or unavailable"
            Text(
                "${confidenceLabel(proposal.band)} · ${proposal.timeDeltaMinutes} min · $distance",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small)) {
                OutlinedButton(
                    onClick = { onOpenObservation(proposal.candidate.uuid) },
                    modifier = Modifier.weight(1f),
                ) { Text("Inspect") }
                Button(
                    onClick = { onConfirmMatch(proposal) },
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) { Text("Confirm match") }
            }
        }
    }
}

@Composable
private fun MetadataDialog(
    photo: CapturePhotoUi,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
) {
    val formatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }
    var date by remember(photo.markerId) {
        mutableStateOf(if (photo.capturedAtReliable) formatter.format(Date(photo.capturedAtMs)) else "")
    }
    var latitude by remember(photo.markerId) {
        mutableStateOf(if (photo.locationReliable) photo.latitude?.toString().orEmpty() else "")
    }
    var longitude by remember(photo.markerId) {
        mutableStateOf(if (photo.locationReliable) photo.longitude?.toString().orEmpty() else "")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Original observation metadata") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small)) {
                Text(
                    "Use where and when the photo was taken—not where or when it is uploaded.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Date and time") },
                    supportingText = { Text("YYYY-MM-DD HH:MM") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = latitude,
                    onValueChange = { latitude = it },
                    label = { Text("Latitude (optional)") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = longitude,
                    onValueChange = { longitude = it },
                    label = { Text("Longitude (optional)") },
                    singleLine = true,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(date, latitude, longitude) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun RewardDialog(
    reward: CaptureRewardUi,
    onDismiss: () -> Unit,
    onOpenCollection: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        AnimatedVisibility(visible = true, enter = fadeIn() + scaleIn(initialScale = 0.92f)) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, WildlifeTheme.colors.oliveStrong),
            ) {
                Column(
                    modifier = Modifier.padding(WildlifeSpacing.Section),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small),
                ) {
                    if (reward.photoUri != null) {
                        AsyncImage(
                            model = reward.photoUri,
                            contentDescription = "Confirmed observation photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(152.dp)
                                .clip(MaterialTheme.shapes.large),
                        )
                    } else {
                        Surface(
                            shape = CircleShape,
                            color = WildlifeTheme.colors.confirmed,
                            modifier = Modifier.size(64.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Science,
                                contentDescription = null,
                                modifier = Modifier.padding(WildlifeSpacing.Screen),
                            )
                        }
                    }
                    Text("Observation confirmed", style = MaterialTheme.typography.headlineLarge)
                    Text(
                        reward.speciesLabel,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        if (reward.xpAwarded > 0) "+${reward.xpAwarded} XP" else "Already rewarded",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = WildlifeTheme.colors.gold,
                    )
                    Button(onClick = onOpenCollection, modifier = Modifier.fillMaxWidth()) {
                        Text("View in collection")
                    }
                    TextButton(onClick = onDismiss) { Text("Keep recording") }
                }
            }
        }
    }
}

private fun friendlyState(state: MarkerState): String = when (state) {
    MarkerState.CAPTURED -> "Draft observation"
    MarkerState.HANDED_OFF -> "Waiting for your answer"
    MarkerState.PENDING -> "Checking iNaturalist"
    MarkerState.CONFIRMED -> "Confirmed"
}

private fun stateExplanation(state: MarkerState): String = when (state) {
    MarkerState.CAPTURED -> "Select photos from this one sighting, then continue in iNaturalist."
    MarkerState.HANDED_OFF -> "Tell Wildlife whether you completed the iNaturalist form."
    MarkerState.PENDING -> "Submitted; waiting for the observation to appear in the public API."
    MarkerState.CONFIRMED -> "Matched to a public iNaturalist observation."
}

private fun confidenceLabel(band: MatchBand): String = when (band) {
    // Capture never files automatically, so the strongest band still reads as a proposal
    // here. Observations is where the same band becomes an action.
    MatchBand.AUTOMATIC -> "Strong time/location match"
    MatchBand.LIKELY -> "Likely time/location match"
    MatchBand.POSSIBLE -> "Needs careful confirmation"
}

@Preview(showBackground = true, backgroundColor = 0xFF080B09, widthDp = 390, heightDp = 820)
@Composable
private fun CaptureEmptyPreview() {
    WildlifeTheme {
        CaptureScreen(
            state = CaptureUiState(), onBack = {}, onTakePhoto = {}, onChoosePhotos = {},
            onTogglePhoto = { _, _ -> }, onContinueInINaturalist = {}, onSubmitted = {},
            onNotSubmitted = {}, onCheckNow = {}, onConfirmMatch = {}, onOpenObservation = {},
            onDelete = {}, onSaveMetadata = { _, _, _, _ -> }, onLinkAccount = {},
            onDismissReward = {}, onOpenCollection = {},
            onDismissHandoffUnavailable = {}, onOpenINaturalistWeb = {},
            onOpenINaturalistStore = {},
        )
    }
}

@Preview(
    showBackground = true,
    backgroundColor = 0xFF080B09,
    widthDp = 390,
    heightDp = 820,
    fontScale = 1.4f,
)
@Composable
private fun CapturePendingLargeFontPreview() {
    WildlifeTheme {
        CaptureScreen(
            state = CaptureUiState(
                observations = listOf(
                    CaptureObservationUi(
                        groupId = "handoff",
                        state = MarkerState.PENDING,
                        photos = listOf(
                            CapturePhotoUi(
                                markerId = "photo",
                                imageUri = "",
                                capturedAtMs = 1_786_550_400_000,
                                latitude = 41.38,
                                longitude = 2.17,
                                capturedAtReliable = true,
                                locationReliable = true,
                                selected = false,
                            ),
                        ),
                        proposals = emptyList(),
                        matchedObservationUuid = null,
                    ),
                ),
                statusMessage = "Submitted; waiting for the public observation.",
            ),
            onBack = {}, onTakePhoto = {}, onChoosePhotos = {},
            onTogglePhoto = { _, _ -> }, onContinueInINaturalist = {}, onSubmitted = {},
            onNotSubmitted = {}, onCheckNow = {}, onConfirmMatch = {}, onOpenObservation = {},
            onDelete = {}, onSaveMetadata = { _, _, _, _ -> }, onLinkAccount = {},
            onDismissReward = {}, onOpenCollection = {}, onDismissHandoffUnavailable = {},
            onOpenINaturalistWeb = {}, onOpenINaturalistStore = {},
        )
    }
}
