package com.wildlife.feasibility.ui.screens.observations

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Biotech
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.wildlife.feasibility.MarkerState
import com.wildlife.feasibility.MatchProposal
import com.wildlife.feasibility.WildlifeNetworkIdentity
import com.wildlife.feasibility.ui.components.WildlifeScaffold
import com.wildlife.feasibility.ui.components.WildlifeLoadingState
import com.wildlife.feasibility.ui.theme.DisplayFontFamily
import com.wildlife.feasibility.ui.theme.WildlifeSpacing
import com.wildlife.feasibility.ui.theme.WildlifeTheme
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

@Composable
fun ObservationsScreen(
    state: ObservationsUiState,
    onBack: (() -> Unit)?,
    onSync: () -> Unit,
    onSubmitted: (String) -> Unit,
    onNotSubmitted: (String) -> Unit,
    onConfirm: (MatchProposal) -> Unit,
    onOpenObservation: (String) -> Unit,
    onOpenINaturalist: () -> Unit,
    onDeleteLocal: (String) -> Unit,
    title: String = "Observations",
    header: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
) {
    val needsAction = state.managed.filter { it.proposals.isNotEmpty() || it.state == MarkerState.HANDED_OFF }
    val awaiting = state.managed.filter { it.state == MarkerState.PENDING && it.proposals.isEmpty() }
    val drafts = state.managed.filter { it.state == MarkerState.CAPTURED }
    val collected = state.managed.filter { it.state == MarkerState.CONFIRMED && it.proposals.isEmpty() }

    // Semantic accents are read here (composable scope); the LazyListScope builder below is not.
    val goldAccent = WildlifeTheme.colors.gold
    val mutedAccent = WildlifeTheme.colors.mutedText
    val confirmedAccent = WildlifeTheme.colors.confirmed
    val oliveAccent = WildlifeTheme.colors.oliveStrong

    WildlifeScaffold(
        title = title,
        onBack = onBack,
        actions = {
            IconButton(onClick = onSync, enabled = state.account != null && !state.syncing) {
                Icon(Icons.Outlined.Refresh, "Sync observations")
            }
        },
        bottomBar = bottomBar,
    ) { padding ->
        if (state.isLoading && state.managed.isEmpty() && state.publicObservations.isEmpty()) {
            WildlifeLoadingState(
                label = "Opening your observation records…",
                modifier = Modifier.padding(padding),
            )
            return@WildlifeScaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = WildlifeSpacing.Screen,
                end = WildlifeSpacing.Screen,
                bottom = WildlifeSpacing.Section,
            ),
            verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small),
        ) {
            item(key = "destination-header") { header() }
            item {
                Text(
                    text = "Your Wildlife handoffs and public iNaturalist history. Wildlife never edits or deletes iNaturalist records.",
                    style = MaterialTheme.typography.bodySmall,
                    color = WildlifeTheme.colors.mutedText,
                    modifier = Modifier.padding(bottom = WildlifeSpacing.Micro),
                )
            }
            state.message?.let { message -> item { MessageBanner(message) } }
            if (state.account == null) {
                item {
                    Text(
                        text = "Link iNaturalist to check submitted handoffs and load your public observations.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (needsAction.isNotEmpty()) {
                item(key = "header-needs-action") {
                    SectionHeader(
                        title = "Needs your action",
                        count = needsAction.size,
                        accent = goldAccent,
                        hint = "Confirm a public match, or record whether you submitted these",
                    )
                }
                items(needsAction, key = { "needs-${it.groupId}" }) { observation ->
                    ActionObservationRow(observation, state.syncing, onSubmitted, onNotSubmitted, onConfirm, onOpenObservation)
                }
            }

            managedSection(
                title = "Awaiting confirmation",
                records = awaiting,
                accent = mutedAccent,
                hint = "Submitted to iNaturalist — waiting for the public record to appear",
                onDeleteLocal = onDeleteLocal,
            ) { observation ->
                IconButton(onClick = onOpenINaturalist) {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, "Open iNaturalist", tint = MaterialTheme.colorScheme.secondary)
                }
            }

            managedSection(
                title = "Drafts",
                records = drafts,
                accent = mutedAccent,
                hint = "Saved in Wildlife, not yet submitted to iNaturalist",
                onDeleteLocal = onDeleteLocal,
            )

            managedSection(
                title = "In your collection",
                records = collected,
                accent = confirmedAccent,
                hint = "Confirmed sightings linked to your iNaturalist record",
                onDeleteLocal = onDeleteLocal,
            ) { observation ->
                observation.matchedObservationUuid?.let { uuid ->
                    IconButton(onClick = { onOpenObservation(uuid) }) {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, "Open public record", tint = MaterialTheme.colorScheme.secondary)
                    }
                }
            }

            item {
                SectionHeader(
                    title = if (state.taxonFilter == null) "Public observations" else "This species",
                    count = state.publicObservations.size,
                    accent = oliveAccent,
                    hint = if (state.taxonFilter == null) {
                        "Your iNaturalist history that wasn't recorded through Wildlife"
                    } else {
                        "Your public iNaturalist sightings of this species"
                    },
                )
            }
            if (state.publicObservations.isEmpty()) {
                item {
                    Text(
                        text = "No stored public observations yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(state.publicObservations, key = { it.uuid }) { observation ->
                    PublicObservationRow(observation, onOpenObservation)
                }
            }
        }
    }
}

/** Adds a header + rows for a managed group with a uniform compact ledger row + trailing actions. */
private fun LazyListScope.managedSection(
    title: String,
    records: List<ManagedObservationUi>,
    accent: Color,
    hint: String,
    onDeleteLocal: (String) -> Unit,
    trailing: @Composable (RowScope.(ManagedObservationUi) -> Unit) = {},
) {
    if (records.isEmpty()) return
    item(key = "header-$title") { SectionHeader(title, records.size, accent, hint) }
    items(records, key = { "$title-${it.groupId}" }) { observation ->
        ObservationCard {
            IdentityRow(
                photoModel = observation.photos.firstOrNull()?.imageUri,
                title = observation.label ?: managedTitle(observation.state),
                titleIsSpecies = observation.label != null,
                meta = managedMeta(observation),
                confirmedBadge = observation.researchGrade,
                regionalContext = observation.regionalContext,
            ) {
                trailing(observation)
                IconButton(onClick = { onDeleteLocal(observation.groupId) }) {
                    Icon(Icons.Outlined.DeleteOutline, "Remove from Wildlife", tint = WildlifeTheme.colors.mutedText)
                }
            }
        }
    }
}

@Composable
private fun ActionObservationRow(
    observation: ManagedObservationUi,
    syncing: Boolean,
    onSubmitted: (String) -> Unit,
    onNotSubmitted: (String) -> Unit,
    onConfirm: (MatchProposal) -> Unit,
    onOpenObservation: (String) -> Unit,
) {
    ObservationCard {
        Column(
            modifier = Modifier.padding(WildlifeSpacing.Card),
            verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Card),
        ) {
            IdentityRow(
                photoModel = observation.photos.firstOrNull()?.imageUri,
                title = observation.label ?: managedTitle(observation.state),
                titleIsSpecies = observation.label != null,
                meta = managedMeta(observation),
                padded = false,
            )
            if (observation.proposals.isNotEmpty()) {
                observation.proposals.forEach { match ->
                    MatchDetail(match)
                    ActionButtons(
                        primaryLabel = "Confirm match",
                        onPrimary = { onConfirm(match.proposal) },
                        primaryEnabled = !syncing,
                        secondaryLabel = "Inspect",
                        onSecondary = { onOpenObservation(match.proposal.candidate.uuid) },
                    )
                }
            } else {
                Text(
                    text = "Did you submit this to iNaturalist?",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ActionButtons(
                    primaryLabel = "Submitted",
                    onPrimary = { onSubmitted(observation.groupId) },
                    primaryEnabled = true,
                    secondaryLabel = "Not submitted",
                    onSecondary = { onNotSubmitted(observation.groupId) },
                )
            }
        }
    }
}

/**
 * Explains why a public iNaturalist observation was proposed as the same sighting: when it was
 * recorded, roughly where, and how close it is to the Wildlife capture in time and distance.
 */
@Composable
private fun MatchDetail(match: MatchProposalUi) {
    Column(verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Micro)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Link,
                contentDescription = null,
                tint = WildlifeTheme.colors.gold,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = if (match.highConfidence) "Strong public match" else "Possible public match",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Text(
            text = matchDetailLine(match),
            style = MaterialTheme.typography.bodySmall,
            color = WildlifeTheme.colors.mutedText,
        )
        if (!match.highConfidence) {
            Text(
                text = "Confirm only if this is your sighting.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ActionButtons(
    primaryLabel: String,
    onPrimary: () -> Unit,
    primaryEnabled: Boolean,
    secondaryLabel: String,
    onSecondary: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small)) {
        OutlinedButton(onClick = onSecondary, modifier = Modifier.weight(1f)) { Text(secondaryLabel) }
        Button(onClick = onPrimary, enabled = primaryEnabled, modifier = Modifier.weight(1f)) { Text(primaryLabel) }
    }
}

@Composable
private fun PublicObservationRow(
    observation: PublicObservationUi,
    onOpenObservation: (String) -> Unit,
) {
    ObservationCard {
        IdentityRow(
            photoModel = observation.photoUrl,
            title = observation.label,
            titleIsSpecies = true,
            meta = publicMeta(observation),
            confirmedBadge = observation.qualityGrade == "research",
            regionalContext = observation.regionalContext,
        ) {
            IconButton(onClick = { onOpenObservation(observation.uuid) }) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, "Open public record", tint = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

/** The shared bordered, softly-rounded row container. */
@Composable
private fun ObservationCard(content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(11.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
    ) {
        content()
    }
}

@Composable
private fun IdentityRow(
    photoModel: String?,
    title: String,
    titleIsSpecies: Boolean,
    meta: String,
    confirmedBadge: Boolean = false,
    regionalContext: RegionalContextUi? = null,
    padded: Boolean = true,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = if (padded) Modifier.padding(WildlifeSpacing.Small) else Modifier,
        horizontalArrangement = Arrangement.spacedBy(WildlifeSpacing.Card),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowThumbnail(photoModel, confirmedBadge)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = if (titleIsSpecies) {
                    MaterialTheme.typography.titleMedium.copy(
                        fontFamily = DisplayFontFamily,
                        fontWeight = FontWeight.SemiBold,
                    )
                } else {
                    MaterialTheme.typography.titleMedium
                },
                color = if (titleIsSpecies) {
                    MaterialTheme.colorScheme.onBackground
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = meta,
                style = MaterialTheme.typography.labelMedium,
                color = WildlifeTheme.colors.mutedText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            regionalContext?.let { context ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(WildlifeSpacing.Micro),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Public,
                        contentDescription = null,
                        tint = if (context.countsTowardRegion) WildlifeTheme.colors.oliveStrong else WildlifeTheme.colors.mutedText,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = context.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (context.countsTowardRegion) MaterialTheme.colorScheme.onSurfaceVariant else WildlifeTheme.colors.mutedText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        trailing()
    }
}

@Composable
private fun RowThumbnail(model: String?, confirmedBadge: Boolean) {
    val context = LocalContext.current
    Box {
        if (model != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(model)
                    .crossfade(true)
                    .setHeader("User-Agent", WildlifeNetworkIdentity.REFERENCE_MEDIA_USER_AGENT)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
        if (confirmedBadge) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(18.dp)
                    .background(WildlifeTheme.colors.confirmed, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Biotech,
                    contentDescription = "Research grade",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int, accent: Color, hint: String) {
    Column(
        modifier = Modifier.padding(top = WildlifeSpacing.Card, bottom = WildlifeSpacing.Micro),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(accent, CircleShape),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = WildlifeTheme.colors.mutedText,
            )
        }
        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            color = WildlifeTheme.colors.mutedText,
            modifier = Modifier.padding(start = 15.dp),
        )
    }
}

@Composable
private fun MessageBanner(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
            .padding(WildlifeSpacing.Card),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun managedTitle(state: MarkerState) = when (state) {
    MarkerState.CAPTURED -> "Draft sighting"
    MarkerState.HANDED_OFF -> "Ready to submit"
    MarkerState.PENDING -> "Awaiting confirmation"
    MarkerState.CONFIRMED -> "Confirmed sighting"
}

private fun managedMeta(observation: ManagedObservationUi): String = buildList {
    add(dateMedium(observation.capturedAtMs))
    observation.place?.let(::add)
    add(photoCount(observation.photos.size))
    if (observation.state == MarkerState.CAPTURED) add("In Capture")
}.joinToString(" · ")

private fun publicMeta(observation: PublicObservationUi): String = buildList {
    add(dateMedium(observation.observedAtMs))
    add(qualityLabel(observation.qualityGrade))
    observation.place?.let { add(if (observation.location?.obscured == true) "$it area" else it) }
}.joinToString(" · ")

private fun matchDetailLine(match: MatchProposalUi): String = buildList {
    add("iNat sighting ${dateMedium(match.observedAtMs)}")
    match.place?.let { add(if (match.obscured) "$it area" else it) }
    add("${timeDeltaLabel(match.timeDeltaMinutes)} from your capture")
    match.distanceKm?.let { add(distanceLabel(it)) }
}.joinToString(" · ")

private fun timeDeltaLabel(minutes: Long): String {
    val m = minutes.coerceAtLeast(0)
    return when {
        m < 60 -> "$m min"
        m < 1440 -> "${m / 60} h"
        else -> "${m / 1440} days"
    }
}

private fun distanceLabel(km: Double): String =
    if (km < 1.0) "${(km * 1000).roundToInt()} m away" else "%.1f km away".format(km)

private fun photoCount(count: Int) = if (count == 1) "1 photo" else "$count photos"

private fun dateMedium(ms: Long) = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ms))

private fun qualityLabel(quality: String) = if (quality == "research") "Research grade" else "Needs ID"

@Preview(showBackground = true, backgroundColor = 0xFF080B09, widthDp = 390, heightDp = 900)
@Composable
private fun ObservationsPreview() = WildlifeTheme {
    ObservationsScreen(
        state = ObservationsUiState(
            message = "Possible public match found. Inspect it, then confirm.",
            managed = listOf(
                ManagedObservationUi(
                    groupId = "g1",
                    state = MarkerState.HANDED_OFF,
                    label = null,
                    capturedAtMs = 1_723_000_000_000,
                    location = null,
                    place = "Montseny",
                    researchGrade = false,
                    photos = listOf(ManagedPhotoUi("m1", ""), ManagedPhotoUi("m2", "")),
                    proposals = emptyList(),
                    matchedObservationUuid = null,
                ),
                ManagedObservationUi(
                    groupId = "g2",
                    state = MarkerState.CONFIRMED,
                    label = "Common kingfisher",
                    capturedAtMs = 1_722_000_000_000,
                    location = null,
                    place = "Delta de l'Ebre",
                    researchGrade = true,
                    photos = listOf(ManagedPhotoUi("m3", "")),
                    proposals = emptyList(),
                    matchedObservationUuid = "abc",
                ),
            ),
            publicObservations = listOf(
                PublicObservationUi(
                    uuid = "u1",
                    taxonId = 42,
                    label = "European robin",
                    observedAtMs = 1_721_000_000_000,
                    qualityGrade = "research",
                    photoUrl = null,
                    location = null,
                    place = "Girona",
                ),
            ),
        ),
        onBack = {}, onSync = {}, onSubmitted = {}, onNotSubmitted = {}, onConfirm = {},
        onOpenObservation = {}, onOpenINaturalist = {}, onDeleteLocal = {},
    )
}
