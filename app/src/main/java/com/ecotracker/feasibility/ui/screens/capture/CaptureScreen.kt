package com.wildlife.feasibility.ui.screens.capture

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EditLocationAlt
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.wildlife.feasibility.MarkerState
import com.wildlife.feasibility.ui.components.FieldGuidePage
import com.wildlife.feasibility.ui.components.HeaderHairline
import com.wildlife.feasibility.ui.components.JournalSurface
import com.wildlife.feasibility.ui.theme.DisplayFontFamily
import com.wildlife.feasibility.ui.theme.FieldLabelStyle
import com.wildlife.feasibility.ui.theme.FieldStampStyle
import com.wildlife.feasibility.ui.theme.WildlifeBackground
import com.wildlife.feasibility.ui.theme.WildlifeOutlineSubtle
import com.wildlife.feasibility.ui.theme.WildlifeSpacing
import com.wildlife.feasibility.ui.theme.WildlifeSurfaceWarm
import com.wildlife.feasibility.ui.theme.WildlifeTheme
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The focused workspace for exactly one new observation.
 *
 * Every photo in the draft belongs to the same sighting and is included unless the user
 * explicitly removes it. Submitted and matched records belong to the Observations ledger.
 */
@Composable
fun CaptureScreen(
    state: CaptureUiState,
    onBack: () -> Unit,
    onTakePhoto: () -> Unit,
    onChoosePhotos: () -> Unit,
    onContinueInINaturalist: () -> Unit,
    onRemovePhoto: (String) -> Unit,
    onDiscardDraft: () -> Unit,
    onSaveMetadata: (String, String, String, String) -> Unit,
    onLinkAccount: () -> Unit,
    onDismissReward: () -> Unit,
    onOpenCollection: () -> Unit,
    onDismissHandoffUnavailable: () -> Unit,
    onOpenINaturalistWeb: () -> Unit,
    onOpenINaturalistStore: () -> Unit,
) {
    val photos = state.draft?.photos.orEmpty()
    var activePhotoId by rememberSaveable(photos.map(CapturePhotoUi::markerId)) {
        mutableStateOf(photos.firstOrNull()?.markerId)
    }
    val activePhoto = photos.firstOrNull { it.markerId == activePhotoId } ?: photos.firstOrNull()
    var editing by remember { mutableStateOf<CapturePhotoUi?>(null) }
    var removing by remember { mutableStateOf<CapturePhotoUi?>(null) }
    var discardRequested by remember { mutableStateOf(false) }
    val ready = photos.isNotEmpty() && photos.all(CapturePhotoUi::capturedAtReliable) && !state.busy

    FieldGuidePage {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (photos.isNotEmpty()) {
                    CaptureFooter(photos.size, ready, onContinueInINaturalist)
                }
            },
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(bottom = WildlifeSpacing.Section),
                verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Screen),
            ) {
                item { CaptureHeader(onBack, photos.isNotEmpty()) }
                if (state.busy) {
                    item {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = WildlifeSpacing.Screen),
                            color = WildlifeTheme.colors.oliveStrong,
                            trackColor = WildlifeOutlineSubtle,
                        )
                    }
                }
                if (activePhoto == null) {
                    item { EmptyPhotoStage(onTakePhoto, onChoosePhotos) }
                } else {
                    item {
                        DraftPhotoStage(
                            photos = photos,
                            activePhoto = activePhoto,
                            onSelectPhoto = { activePhotoId = it.markerId },
                            onTakePhoto = onTakePhoto,
                            onChoosePhotos = onChoosePhotos,
                            onEditMetadata = { editing = activePhoto },
                            onRemovePhoto = { removing = activePhoto },
                        )
                    }
                }
                if (state.account == null) item { AccountNote(onLinkAccount) }
                if (state.statusMessage.isNotBlank() &&
                    state.statusMessage != "Add photos to start one observation."
                ) {
                    item { StatusLine(state.statusMessage) }
                }
                if (photos.isNotEmpty()) {
                    item {
                        TextButton(
                            onClick = { discardRequested = true },
                            modifier = Modifier.padding(horizontal = WildlifeSpacing.Small),
                        ) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = null)
                            Text("Discard this draft", Modifier.padding(start = WildlifeSpacing.Micro))
                        }
                    }
                }
            }
        }
    }

    editing?.let { photo ->
        MetadataDialog(photo, { editing = null }) { date, latitude, longitude ->
            onSaveMetadata(photo.markerId, date, latitude, longitude)
            editing = null
        }
    }
    removing?.let { photo ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove this photo?") },
            text = { Text("It will be removed from this Wildlife draft. Imported originals stay untouched.") },
            confirmButton = {
                TextButton(onClick = {
                    onRemovePhoto(photo.markerId)
                    removing = null
                }) { Text("Remove photo") }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Keep") } },
        )
    }
    if (discardRequested) {
        AlertDialog(
            onDismissRequest = { discardRequested = false },
            title = { Text("Discard this observation draft?") },
            text = {
                Text("Wildlife removes its draft and private camera copies. Gallery originals stay untouched.")
            },
            confirmButton = {
                TextButton(onClick = {
                    onDiscardDraft()
                    discardRequested = false
                }) { Text("Discard draft") }
            },
            dismissButton = { TextButton(onClick = { discardRequested = false }) { Text("Cancel") } },
        )
    }
    state.reward?.let { RewardDialog(it, onDismissReward, onOpenCollection) }
    if (state.handoffUnavailable) {
        AlertDialog(
            onDismissRequest = onDismissHandoffUnavailable,
            title = { Text("Continue with iNaturalist") },
            text = {
                Text("The official iNaturalist app provides the smoothest handoff. Install it or use the web uploader.")
            },
            confirmButton = { TextButton(onClick = onOpenINaturalistStore) { Text("Install app") } },
            dismissButton = {
                Row {
                    TextButton(onClick = onOpenINaturalistWeb) { Text("Use web") }
                    TextButton(onClick = onDismissHandoffUnavailable) { Text("Cancel") }
                }
            },
        )
    }
}

@Composable
private fun CaptureHeader(onBack: () -> Unit, hasDraft: Boolean) {
    val colors = WildlifeTheme.colors
    Box(
        Modifier.fillMaxWidth().background(
            Brush.verticalGradient(
                0f to WildlifeBackground.copy(alpha = 0.12f),
                1f to WildlifeBackground.copy(alpha = 0.88f),
            ),
        ),
    ) {
        Column(
            Modifier.padding(
                start = WildlifeSpacing.Small,
                end = WildlifeSpacing.Screen,
                top = WildlifeSpacing.Small,
                bottom = WildlifeSpacing.Screen,
            ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.parchment,
                    )
                }
                Column(Modifier.padding(start = WildlifeSpacing.Micro)) {
                    Text(
                        if (hasDraft) "FIELD RECORD · IN PROGRESS" else "FIELD RECORD · NEW",
                        style = FieldStampStyle,
                        color = colors.oliveStrong,
                    )
                    Text(
                        "New observation",
                        fontFamily = DisplayFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 30.sp,
                        lineHeight = 34.sp,
                        color = colors.parchment,
                    )
                }
            }
            Text(
                "One sighting. One record. Add more photos only when they show the same animal at the same place and time.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.parchmentDim,
                modifier = Modifier.padding(start = 52.dp, top = WildlifeSpacing.Small),
            )
        }
        HeaderHairline(Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun EmptyPhotoStage(onTakePhoto: () -> Unit, onChoosePhotos: () -> Unit) {
    val colors = WildlifeTheme.colors
    Column(
        modifier = Modifier.padding(horizontal = WildlifeSpacing.Screen),
        verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Card),
    ) {
        Text("START THE SIGHTING", style = FieldLabelStyle, color = colors.parchmentDim)
        JournalSurface(
            Modifier.fillMaxWidth().height(352.dp).semantics(mergeDescendants = true) {
                contentDescription = "No photo added. Take a photo to start one observation."
            },
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(WildlifeSpacing.Section),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Surface(
                    onClick = onTakePhoto,
                    shape = CircleShape,
                    color = colors.oliveDark,
                    border = BorderStroke(1.5.dp, colors.oliveStrong),
                    modifier = Modifier.size(88.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.AddAPhoto,
                            contentDescription = "Take a photo",
                            tint = colors.parchment,
                            modifier = Modifier.size(38.dp),
                        )
                    }
                }
                Spacer(Modifier.height(WildlifeSpacing.Screen))
                Text("Take a photo", style = MaterialTheme.typography.headlineMedium, color = colors.parchment)
                Text(
                    "The first photo starts this observation",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.parchmentDim,
                )
                Spacer(Modifier.height(WildlifeSpacing.Section))
                TextButton(onClick = onChoosePhotos) {
                    Icon(Icons.Outlined.PhotoLibrary, contentDescription = null)
                    Text("Choose from gallery", Modifier.padding(start = WildlifeSpacing.Small))
                }
            }
        }
    }
}

@Composable
private fun DraftPhotoStage(
    photos: List<CapturePhotoUi>,
    activePhoto: CapturePhotoUi,
    onSelectPhoto: (CapturePhotoUi) -> Unit,
    onTakePhoto: () -> Unit,
    onChoosePhotos: () -> Unit,
    onEditMetadata: () -> Unit,
    onRemovePhoto: () -> Unit,
) {
    val colors = WildlifeTheme.colors
    Column(
        modifier = Modifier.padding(horizontal = WildlifeSpacing.Screen),
        verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Card),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column {
                Text("THIS SIGHTING", style = FieldLabelStyle, color = colors.oliveStrong)
                Text("1 observation", style = MaterialTheme.typography.headlineMedium, color = colors.parchment)
            }
            Text(
                if (photos.size == 1) "1 PHOTO" else "${photos.size} PHOTOS",
                style = FieldStampStyle,
                color = colors.parchmentDim,
            )
        }
        JournalSurface(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f)) {
                AsyncImage(
                    model = activePhoto.imageUri,
                    contentDescription = "Selected photo in this observation",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier.fillMaxWidth().height(88.dp).align(Alignment.BottomCenter).background(
                        Brush.verticalGradient(
                            listOf(
                                WildlifeBackground.copy(alpha = 0f),
                                WildlifeBackground.copy(alpha = 0.88f),
                            ),
                        ),
                    ),
                )
                Text(
                    "PHOTO ${photos.indexOf(activePhoto) + 1} OF ${photos.size}",
                    style = FieldStampStyle,
                    color = colors.parchment,
                    modifier = Modifier.align(Alignment.BottomStart).padding(WildlifeSpacing.Card),
                )
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small)) {
            itemsIndexed(photos, key = { _, photo -> photo.markerId }) { index, photo ->
                PhotoThumbnail(
                    photo = photo,
                    number = index + 1,
                    selected = photo.markerId == activePhoto.markerId,
                    onClick = { onSelectPhoto(photo) },
                )
            }
            item { AddPhotoTile("Camera", Icons.Outlined.AddAPhoto, onTakePhoto) }
            item { AddPhotoTile("Gallery", Icons.Outlined.PhotoLibrary, onChoosePhotos) }
        }
        PhotoDetails(activePhoto, onEditMetadata, onRemovePhoto)
    }
}

@Composable
private fun PhotoThumbnail(
    photo: CapturePhotoUi,
    number: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = WildlifeTheme.colors
    Box(
        modifier = Modifier.size(78.dp).clip(RoundedCornerShape(10.dp)).background(colors.plate)
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) colors.oliveStrong else WildlifeOutlineSubtle,
                RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = "Photo $number of this observation${if (selected) ", selected" else ""}"
            },
    ) {
        AsyncImage(
            model = photo.imageUri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (!photo.capturedAtReliable) {
            Surface(
                shape = CircleShape,
                color = WildlifeBackground.copy(alpha = 0.88f),
                modifier = Modifier.align(Alignment.TopEnd).padding(WildlifeSpacing.Micro),
            ) {
                Icon(
                    Icons.Outlined.WarningAmber,
                    contentDescription = "Original time needed",
                    tint = colors.gold,
                    modifier = Modifier.padding(4.dp).size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun AddPhotoTile(label: String, icon: ImageVector, onClick: () -> Unit) {
    val colors = WildlifeTheme.colors
    Column(
        modifier = Modifier.width(78.dp).height(78.dp).clip(RoundedCornerShape(10.dp))
            .background(WildlifeSurfaceWarm)
            .border(1.dp, WildlifeOutlineSubtle, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick).semantics { role = Role.Button },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = colors.oliveStrong, modifier = Modifier.size(24.dp))
        Text(label.uppercase(), style = FieldStampStyle.copy(fontSize = 8.sp), color = colors.parchmentDim)
    }
}

@Composable
private fun PhotoDetails(photo: CapturePhotoUi, onEdit: () -> Unit, onRemove: () -> Unit) {
    val colors = WildlifeTheme.colors
    JournalSurface(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(WildlifeSpacing.Card)) {
            Text("PHOTO DETAILS", style = FieldLabelStyle, color = colors.parchmentDim)
            Spacer(Modifier.height(WildlifeSpacing.Small))
            DetailRow(
                Icons.Outlined.CalendarMonth,
                "Observed",
                if (photo.capturedAtReliable) formattedDate(photo.capturedAtMs) else "Original time needed",
                photo.capturedAtReliable,
            )
            DetailRow(
                Icons.Outlined.LocationOn,
                "Location",
                if (photo.locationReliable) "Saved with photo" else "Not available · optional",
                photo.locationReliable,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = WildlifeSpacing.Small),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onEdit) {
                    Icon(Icons.Outlined.EditLocationAlt, contentDescription = null)
                    Text("Edit details", Modifier.padding(start = WildlifeSpacing.Micro))
                }
                TextButton(onClick = onRemove) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = null)
                    Text("Remove", Modifier.padding(start = WildlifeSpacing.Micro))
                }
            }
        }
    }
}

@Composable
private fun DetailRow(icon: ImageVector, label: String, value: String, ready: Boolean) {
    val colors = WildlifeTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = WildlifeSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (ready) Icons.Outlined.CheckCircle else icon,
            contentDescription = null,
            tint = if (ready) colors.confirmed else colors.gold,
            modifier = Modifier.size(22.dp),
        )
        Column(Modifier.weight(1f).padding(start = WildlifeSpacing.Card)) {
            Text(label.uppercase(), style = FieldLabelStyle, color = colors.parchmentFaint)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = colors.parchment)
        }
    }
}

@Composable
private fun CaptureFooter(photoCount: Int, ready: Boolean, onContinue: () -> Unit) {
    val colors = WildlifeTheme.colors
    Column(
        Modifier.fillMaxWidth().background(WildlifeBackground.copy(alpha = 0.97f)).navigationBarsPadding(),
    ) {
        HeaderHairline()
        Row(
            Modifier.padding(WildlifeSpacing.Screen),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WildlifeSpacing.Card),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (photoCount == 1) "1 OBSERVATION · 1 PHOTO" else "1 OBSERVATION · $photoCount PHOTOS",
                    style = FieldStampStyle,
                    color = colors.oliveStrong,
                )
                Text(
                    if (ready) "Identify and submit in the official app" else "Add the original time to continue",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.parchmentDim,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier.clip(RoundedCornerShape(9.dp))
                    .background(if (ready) colors.oliveDark else WildlifeOutlineSubtle)
                    .border(
                        1.5.dp,
                        if (ready) colors.oliveStrong else colors.parchmentFaint.copy(alpha = 0.35f),
                        RoundedCornerShape(9.dp),
                    )
                    .clickable(enabled = ready, onClick = onContinue)
                    .semantics {
                        role = Role.Button
                        contentDescription = "Continue this observation in iNaturalist"
                        if (!ready) disabled()
                    }
                    .padding(horizontal = WildlifeSpacing.Screen, vertical = 13.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "CONTINUE",
                        style = FieldStampStyle,
                        color = if (ready) colors.parchment else colors.parchmentFaint,
                    )
                    Icon(
                        Icons.AutoMirrored.Outlined.OpenInNew,
                        contentDescription = null,
                        tint = if (ready) colors.parchment else colors.parchmentFaint,
                        modifier = Modifier.padding(start = WildlifeSpacing.Small).size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountNote(onLinkAccount: () -> Unit) {
    val colors = WildlifeTheme.colors
    JournalSurface(Modifier.fillMaxWidth().padding(horizontal = WildlifeSpacing.Screen)) {
        Row(Modifier.padding(WildlifeSpacing.Card), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Link, contentDescription = null, tint = colors.gold)
            Column(Modifier.weight(1f).padding(horizontal = WildlifeSpacing.Card)) {
                Text("LINK FOR COLLECTION REWARDS", style = FieldLabelStyle, color = colors.gold)
                Text(
                    "Take the photo now. Link your public iNaturalist account so Wildlife can find it later.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.parchmentDim,
                )
            }
            TextButton(onClick = onLinkAccount) { Text("Link") }
        }
    }
}

@Composable
private fun StatusLine(message: String) {
    val colors = WildlifeTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = WildlifeSpacing.Screen),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.padding(top = 7.dp).size(5.dp).background(colors.oliveStrong, CircleShape))
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = colors.parchmentDim,
            modifier = Modifier.padding(start = WildlifeSpacing.Small),
        )
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
        title = { Text("When and where was it seen?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small)) {
                Text("Use the moment and place of the sighting—not the upload time.")
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
private fun RewardDialog(reward: CaptureRewardUi, onDismiss: () -> Unit, onOpenCollection: () -> Unit) {
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
                            modifier = Modifier.size(152.dp).clip(MaterialTheme.shapes.large),
                        )
                    } else {
                        Icon(
                            Icons.Outlined.Science,
                            contentDescription = null,
                            tint = WildlifeTheme.colors.confirmed,
                            modifier = Modifier.size(56.dp),
                        )
                    }
                    Text("Observation confirmed", style = MaterialTheme.typography.headlineLarge)
                    Text(reward.speciesLabel, textAlign = TextAlign.Center)
                    Text(
                        if (reward.xpAwarded > 0) "+${reward.xpAwarded} XP" else "Already rewarded",
                        style = MaterialTheme.typography.titleLarge,
                        color = WildlifeTheme.colors.gold,
                    )
                    TextButton(onClick = onOpenCollection) { Text("View in collection") }
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    }
}

private fun formattedDate(timeMs: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timeMs))

private val previewPhoto = CapturePhotoUi(
    markerId = "photo",
    imageUri = "",
    capturedAtMs = 1_786_550_400_000,
    latitude = 41.38,
    longitude = 2.17,
    capturedAtReliable = true,
    locationReliable = true,
    selected = true,
)

@Preview(showBackground = true, backgroundColor = 0xFF0E1209, widthDp = 390, heightDp = 820)
@Composable
private fun CaptureEmptyPreview() {
    WildlifeTheme { CapturePreview(CaptureUiState()) }
}

@Preview(showBackground = true, backgroundColor = 0xFF0E1209, widthDp = 390, heightDp = 820)
@Composable
private fun CaptureDraftPreview() {
    WildlifeTheme {
        CapturePreview(
            CaptureUiState(
                observations = listOf(
                    CaptureObservationUi(
                        CaptureProjection.DRAFT_GROUP_ID,
                        MarkerState.CAPTURED,
                        listOf(previewPhoto),
                        emptyList(),
                        null,
                    ),
                ),
                statusMessage = "Photo added. Add another view only if it shows this same sighting.",
            ),
        )
    }
}

@Composable
private fun CapturePreview(state: CaptureUiState) {
    CaptureScreen(
        state = state,
        onBack = {},
        onTakePhoto = {},
        onChoosePhotos = {},
        onContinueInINaturalist = {},
        onRemovePhoto = {},
        onDiscardDraft = {},
        onSaveMetadata = { _, _, _, _ -> },
        onLinkAccount = {},
        onDismissReward = {},
        onOpenCollection = {},
        onDismissHandoffUnavailable = {},
        onOpenINaturalistWeb = {},
        onOpenINaturalistStore = {},
    )
}
