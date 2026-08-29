package com.wildlife.feasibility.ui.screens.observations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.wildlife.feasibility.MarkerState
import com.wildlife.feasibility.MatchBand
import com.wildlife.feasibility.MatchProposal
import com.wildlife.feasibility.MatchReason
import com.wildlife.feasibility.ObservationCandidate
import com.wildlife.feasibility.ui.components.FieldGuidePage
import com.wildlife.feasibility.ui.components.JournalButton
import com.wildlife.feasibility.ui.components.LedgerHeader
import com.wildlife.feasibility.ui.components.MatchComparisonPlate
import com.wildlife.feasibility.ui.components.ObservationRecordLine
import com.wildlife.feasibility.ui.components.SectionRule
import com.wildlife.feasibility.ui.components.WildlifeScaffold
import com.wildlife.feasibility.ui.components.WildlifeLoadingState
import com.wildlife.feasibility.ui.theme.FieldStampStyle
import com.wildlife.feasibility.ui.theme.WildlifeOutlineSubtle
import com.wildlife.feasibility.ui.theme.WildlifeSurface
import com.wildlife.feasibility.ui.theme.WildlifeSpacing
import com.wildlife.feasibility.ui.theme.WildlifeTheme
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

/**
 * Observations: the ranger's desk.
 *
 * The page is ordered by what it asks of the reader, not by the lifecycle of a record.
 * Matches Wildlife filed on its own lead, because they are the only thing on the page that
 * happened without the user — they get the photographs at full width and a way out.
 * Everything the user must answer follows. Drafts, waiting records and settled ones fall
 * to typed ledger lines, and the public iNaturalist history closes the page as archive.
 *
 * The deciding surface is deliberately a *comparison*: the capture beside the public
 * photograph. The previous screen argued a match entirely in prose — minutes and
 * kilometres — which is the evidence for a judgement the reader makes by looking.
 */
@Composable
fun ObservationsScreen(
    state: ObservationsUiState,
    onBack: (() -> Unit)?,
    onSync: () -> Unit,
    onSubmitted: (String) -> Unit,
    onNotSubmitted: (String) -> Unit,
    onConfirm: (MatchProposal) -> Unit,
    onKeepAutomatic: (String) -> Unit,
    onUndoAutomatic: (String) -> Unit,
    onOpenObservation: (String) -> Unit,
    onOpenINaturalist: () -> Unit,
    onDeleteLocal: (String) -> Unit,
    title: String = "Observations",
    /** Pinned under the app bar, above the ledger: Collection's index strip goes here. */
    header: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
) {
    // Semantic accents are read in composable scope; the LazyListScope builder below is not.
    val colors = WildlifeTheme.colors

    WildlifeScaffold(
        title = title,
        onBack = onBack,
        actions = {
            IconButton(onClick = onSync, enabled = state.account != null && !state.syncing) {
                Icon(Icons.Outlined.Refresh, "Check iNaturalist for matches")
            }
        },
        bottomBar = bottomBar,
    ) { padding ->
        FieldGuidePage {
            Column(Modifier.fillMaxSize().padding(padding)) {
                // Outside the list's padding and above its scroll, so the strip keeps the
                // same full-bleed position it holds in Collection's other sections. Drawn
                // before the loading branch, so switching section does not blank the strip.
                header()
                if (state.isLoading && state.managed.isEmpty() && state.publicObservations.isEmpty()) {
                    WildlifeLoadingState(
                        label = "Opening your observation records…",
                        modifier = Modifier.weight(1f),
                    )
                    return@Column
                }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(
                        top = WildlifeSpacing.Card,
                        start = WildlifeSpacing.Screen,
                        end = WildlifeSpacing.Screen,
                        bottom = WildlifeSpacing.Section,
                    ),
                    verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small),
                ) {
                    if (state.hasLedger) item("ledger-head") {
                        LedgerHeader(
                            tallies = listOf(
                                Triple(state.outstandingCount, "To answer", colors.gold),
                                Triple(state.awaiting.size, "Awaiting", colors.parchmentDim),
                                Triple(
                                    state.filed.size + state.autoFiled.size,
                                    "Filed",
                                    colors.confirmed,
                                ),
                            ),
                            note = "Wildlife never edits or deletes an iNaturalist record",
                        )
                    }
                    state.message?.let { message -> item("message") { MessageBanner(message) } }
                    if (state.account == null) {
                        item("unlinked") {
                            Note(
                                "Link iNaturalist to check submitted handoffs and load your " +
                                    "public observations.",
                            )
                        }
                    }

                    if (state.autoFiled.isNotEmpty()) {
                        item("rule-auto") { Rule("Filed automatically", colors.confirmed) }
                        items(state.autoFiled, key = { "auto-${it.groupId}" }) { observation ->
                            AutoFiledPlate(
                                observation = observation,
                                onKeep = onKeepAutomatic,
                                onUndo = onUndoAutomatic,
                            )
                        }
                    }

                    if (state.needsAction.isNotEmpty()) {
                        item("rule-action") { Rule("Needs your eye", colors.gold) }
                        items(state.needsAction, key = { "action-${it.groupId}" }) { observation ->
                            ActionRecord(
                                observation = observation,
                                syncing = state.syncing,
                                onSubmitted = onSubmitted,
                                onNotSubmitted = onNotSubmitted,
                                onConfirm = onConfirm,
                                onOpenObservation = onOpenObservation,
                            )
                        }
                    }

                    managedSection(
                        title = "Awaiting a public record",
                        records = state.awaiting,
                        accent = colors.parchmentDim,
                        onDeleteLocal = onDeleteLocal,
                    ) {
                        IconButton(onClick = onOpenINaturalist) {
                            Icon(
                                Icons.AutoMirrored.Outlined.OpenInNew,
                                "Open iNaturalist",
                                tint = colors.oliveStrong,
                            )
                        }
                    }

                    managedSection(
                        title = "Drafts",
                        records = state.drafts,
                        accent = colors.parchmentDim,
                        onDeleteLocal = onDeleteLocal,
                    )

                    managedSection(
                        title = "Filed",
                        records = state.filed,
                        accent = colors.confirmed,
                        onDeleteLocal = onDeleteLocal,
                    ) { observation ->
                        observation.matchedObservationUuid?.let { uuid ->
                            IconButton(onClick = { onOpenObservation(uuid) }) {
                                Icon(
                                    Icons.AutoMirrored.Outlined.OpenInNew,
                                    "Open public record",
                                    tint = colors.oliveStrong,
                                )
                            }
                        }
                    }

                    item("rule-public") {
                        Rule(
                            if (state.taxonFilter == null) "Public history" else "This species",
                            colors.parchmentFaint,
                        )
                    }
                    if (state.publicObservations.isEmpty()) {
                        item("public-empty") {
                            Note(
                                if (state.taxonFilter == null) {
                                    "No stored public observations yet."
                                } else {
                                    "No public sightings of this species yet."
                                },
                            )
                        }
                    } else {
                        items(state.publicObservations, key = { it.uuid }) { observation ->
                            PublicRecordLine(observation, onOpenObservation)
                        }
                    }
                }
            }
        }
    }
}

/**
 * A match Wildlife made on its own, presented as the comparison that justifies it.
 *
 * Keeping and undoing are given equal weight rather than making "keep" the loud one: the
 * user is being asked to check work they did not commission, and a page that visually
 * pushes them to agree is not really asking.
 */
@Composable
private fun AutoFiledPlate(
    observation: ManagedObservationUi,
    onKeep: (String) -> Unit,
    onUndo: (String) -> Unit,
) {
    val colors = WildlifeTheme.colors
    MatchComparisonPlate(
        capturePhoto = observation.photos.firstOrNull()?.imageUri,
        recordPhoto = observation.matchedPhotoUrl,
        recordLabel = observation.label,
        // Not "Filed automatically": the rule directly above already says so, and a plate
        // that repeats its own heading reads as two separate claims about one record.
        headline = "Matched by Wildlife",
        evidence = managedMeta(observation),
        accent = colors.confirmed,
        actions = {
            Row(horizontalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small)) {
                JournalButton(
                    label = "Not mine",
                    onClick = { onUndo(observation.groupId) },
                    modifier = Modifier.weight(1f),
                )
                JournalButton(
                    label = "Keep",
                    onClick = { onKeep(observation.groupId) },
                    modifier = Modifier.weight(1f),
                    primary = true,
                )
            }
        },
    )
}

/**
 * A record the user has to answer: either which public observation it is, or — for a
 * handoff Wildlife cannot see the outcome of — whether it was submitted at all.
 */
@Composable
private fun ActionRecord(
    observation: ManagedObservationUi,
    syncing: Boolean,
    onSubmitted: (String) -> Unit,
    onNotSubmitted: (String) -> Unit,
    onConfirm: (MatchProposal) -> Unit,
    onOpenObservation: (String) -> Unit,
) {
    val colors = WildlifeTheme.colors
    if (observation.proposals.isEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small)) {
            ObservationRecordLine(
                photoModel = observation.photos.firstOrNull()?.imageUri,
                title = managedTitle(observation.state),
                titleIsSpecies = false,
                meta = managedMeta(observation),
            ) {
                Note("Did you submit this to iNaturalist?")
                Row(horizontalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small)) {
                    JournalButton(
                        label = "Not submitted",
                        onClick = { onNotSubmitted(observation.groupId) },
                        modifier = Modifier.weight(1f),
                    )
                    JournalButton(
                        label = "Submitted",
                        onClick = { onSubmitted(observation.groupId) },
                        modifier = Modifier.weight(1f),
                        primary = true,
                    )
                }
            }
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small)) {
        observation.proposals.forEach { match ->
            MatchComparisonPlate(
                capturePhoto = observation.photos.firstOrNull()?.imageUri,
                recordPhoto = match.photoUrl,
                recordLabel = match.label,
                headline = "Possible match",
                evidence = matchEvidence(match),
                accent = bandTint(match.band, colors.confirmed, colors.gold, colors.parchmentFaint),
                confidence = match.confidence.toFloat(),
                confidenceLabel = bandLabel(match.band),
                actions = {
                    Row(horizontalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small)) {
                        JournalButton(
                            label = "Inspect",
                            onClick = { onOpenObservation(match.proposal.candidate.uuid) },
                            modifier = Modifier.weight(1f),
                        )
                        JournalButton(
                            label = "Confirm",
                            onClick = { onConfirm(match.proposal) },
                            modifier = Modifier.weight(1f),
                            primary = !syncing,
                        )
                    }
                },
            )
        }
    }
}

/** Adds a rule plus one ledger line per record, with section-specific trailing actions. */
private fun LazyListScope.managedSection(
    title: String,
    records: List<ManagedObservationUi>,
    accent: Color,
    onDeleteLocal: (String) -> Unit,
    trailing: @Composable (RowScope.(ManagedObservationUi) -> Unit) = {},
) {
    if (records.isEmpty()) return
    item("rule-$title") { Rule(title, accent) }
    items(records, key = { "$title-${it.groupId}" }) { observation ->
        val colors = WildlifeTheme.colors
        ObservationRecordLine(
            photoModel = observation.photos.firstOrNull()?.imageUri,
            title = observation.label ?: managedTitle(observation.state),
            titleIsSpecies = observation.label != null,
            meta = managedMeta(observation),
            markLabel = if (observation.researchGrade) "Research" else null,
            markTint = colors.confirmed,
            contextLabel = observation.regionalContext?.label,
            contextTint = observation.regionalContext?.let {
                if (it.countsTowardRegion) colors.oliveStrong else colors.parchmentFaint
            },
            // Named, because the record line's trailing lambda is no longer its last
            // parameter: an unnamed block here would land in the footer slot instead.
            trailing = {
                trailing(observation)
                IconButton(onClick = { onDeleteLocal(observation.groupId) }) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        "Remove from Wildlife",
                        tint = colors.parchmentFaint,
                    )
                }
            },
        )
    }
}

@Composable
private fun PublicRecordLine(
    observation: PublicObservationUi,
    onOpenObservation: (String) -> Unit,
) {
    val colors = WildlifeTheme.colors
    ObservationRecordLine(
        photoModel = observation.photoUrl,
        title = observation.label,
        titleIsSpecies = true,
        meta = publicMeta(observation),
        markLabel = if (observation.qualityGrade == "research") "Research" else null,
        markTint = colors.confirmed,
        contextLabel = observation.regionalContext?.label,
        contextTint = observation.regionalContext?.let {
            if (it.countsTowardRegion) colors.oliveStrong else colors.parchmentFaint
        },
        onClick = { onOpenObservation(observation.uuid) },
    )
}

@Composable
private fun Rule(label: String, accent: Color) {
    Column {
        Spacer(Modifier.height(WildlifeSpacing.Card))
        SectionRule(label, accent)
        Spacer(Modifier.height(WildlifeSpacing.Micro))
    }
}

/** A stamped aside: guidance and empty states, in the page's record voice. */
@Composable
private fun Note(text: String) {
    Text(
        text.uppercase(),
        style = FieldStampStyle,
        color = WildlifeTheme.colors.parchmentDim,
        modifier = Modifier.padding(horizontal = WildlifeSpacing.Micro),
    )
}

@Composable
private fun MessageBanner(message: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(WildlifeSurface.copy(alpha = 0.90f))
            .border(1.dp, WildlifeOutlineSubtle, RoundedCornerShape(9.dp))
            .padding(WildlifeSpacing.Card),
    ) {
        Text(
            message.uppercase(),
            style = FieldStampStyle,
            color = WildlifeTheme.colors.parchmentDim,
        )
    }
}

private fun bandLabel(band: MatchBand): String = when (band) {
    MatchBand.AUTOMATIC -> "Certain"
    MatchBand.LIKELY -> "Likely"
    MatchBand.POSSIBLE -> "Possible"
}

private fun bandTint(band: MatchBand, confirmed: Color, gold: Color, dim: Color): Color =
    when (band) {
        MatchBand.AUTOMATIC -> confirmed
        MatchBand.LIKELY -> gold
        MatchBand.POSSIBLE -> dim
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
    observation.place?.let { add(if (observation.location?.obscured == true) "$it area" else it) }
}.joinToString(" · ")

/**
 * The measurable evidence, then anything that qualifies it.
 *
 * The reasons that only restate the numbers are left out — a reader who can see "2 min
 * apart" does not also need to be told the times align. What survives is what the numbers
 * cannot say: that the location proves nothing, or that something else fits just as well.
 */
private fun matchEvidence(match: MatchProposalUi): String = buildList {
    add(timeDeltaLabel(match.timeDeltaMinutes) + " apart")
    if (!match.obscured) match.distanceKm?.let { add(distanceLabel(it)) }
    match.place?.let { add(if (match.obscured) "$it area" else it) }
    add("iNat ${dateMedium(match.observedAtMs)}")
    match.reasons.mapNotNull(::caveat).distinct().forEach(::add)
}.joinToString(" · ")

private fun caveat(reason: MatchReason): String? = when (reason) {
    MatchReason.LOCATION_OBSCURED -> "Location obscured"
    MatchReason.LOCATION_UNKNOWN -> "No location to compare"
    MatchReason.CAPTURE_TIME_UNRELIABLE -> "Capture time estimated"
    MatchReason.COMPETING_CANDIDATES -> "Another record fits too"
    MatchReason.COMPETING_CAPTURES -> "Another of your captures fits too"
    // Already carried by the minutes and metres printed beside them.
    MatchReason.TIME_ALIGNED,
    MatchReason.TIME_APPROXIMATE,
    MatchReason.LOCATION_ALIGNED,
    MatchReason.LOCATION_APPROXIMATE -> null
}

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

@Preview(showBackground = true, backgroundColor = 0xFF0E1209, widthDp = 390, heightDp = 1000)
@Composable
private fun ObservationsPreview() = WildlifeTheme {
    ObservationsScreen(
        state = previewState(),
        onBack = {}, onSync = {}, onSubmitted = {}, onNotSubmitted = {}, onConfirm = {},
        onKeepAutomatic = {}, onUndoAutomatic = {}, onOpenObservation = {},
        onOpenINaturalist = {}, onDeleteLocal = {},
    )
}

@Preview(
    name = "Large font",
    showBackground = true,
    backgroundColor = 0xFF0E1209,
    widthDp = 390,
    heightDp = 1000,
    fontScale = 1.5f,
)
@Composable
private fun ObservationsLargeFontPreview() = WildlifeTheme {
    ObservationsScreen(
        state = previewState(),
        onBack = {}, onSync = {}, onSubmitted = {}, onNotSubmitted = {}, onConfirm = {},
        onKeepAutomatic = {}, onUndoAutomatic = {}, onOpenObservation = {},
        onOpenINaturalist = {}, onDeleteLocal = {},
    )
}

@Preview(name = "Empty", showBackground = true, backgroundColor = 0xFF0E1209, widthDp = 390)
@Composable
private fun ObservationsEmptyPreview() = WildlifeTheme {
    ObservationsScreen(
        state = ObservationsUiState(),
        onBack = {}, onSync = {}, onSubmitted = {}, onNotSubmitted = {}, onConfirm = {},
        onKeepAutomatic = {}, onUndoAutomatic = {}, onOpenObservation = {},
        onOpenINaturalist = {}, onDeleteLocal = {},
    )
}

private fun previewState() = ObservationsUiState(
    message = "Filed 1 automatically. 1 still needs your eye.",
    managed = listOf(
        ManagedObservationUi(
            groupId = "g0",
            state = MarkerState.CONFIRMED,
            label = "Common kingfisher",
            capturedAtMs = 1_723_400_000_000,
            location = null,
            place = "Delta de l'Ebre",
            researchGrade = true,
            photos = listOf(ManagedPhotoUi("m0", "")),
            proposals = emptyList(),
            matchedObservationUuid = "abc",
            autoFiled = true,
        ),
        ManagedObservationUi(
            groupId = "g1",
            state = MarkerState.PENDING,
            label = null,
            capturedAtMs = 1_723_000_000_000,
            location = null,
            place = "Montseny",
            researchGrade = false,
            photos = listOf(ManagedPhotoUi("m1", ""), ManagedPhotoUi("m2", "")),
            proposals = listOf(
                MatchProposalUi(
                    proposal = MatchProposal(
                        markerId = "m1",
                        candidate = ObservationCandidate(
                            uuid = "cand", observedAtMs = 1_723_000_400_000,
                            latitude = null, longitude = null, obscured = false,
                        ),
                        confidence = 0.78,
                        band = MatchBand.LIKELY,
                        timeDeltaMinutes = 7,
                        distanceKm = 0.4,
                    ),
                    observedAtMs = 1_723_000_400_000,
                    label = "Eurasian jay",
                    photoUrl = null,
                    place = "Montseny",
                    obscured = false,
                    distanceKm = 0.4,
                    timeDeltaMinutes = 7,
                    confidence = 0.78,
                    band = MatchBand.LIKELY,
                    reasons = listOf(MatchReason.COMPETING_CANDIDATES),
                ),
            ),
            matchedObservationUuid = null,
        ),
        ManagedObservationUi(
            groupId = "g2",
            state = MarkerState.CAPTURED,
            label = null,
            capturedAtMs = 1_722_000_000_000,
            location = null,
            place = "Girona",
            researchGrade = false,
            photos = listOf(ManagedPhotoUi("m3", "")),
            proposals = emptyList(),
            matchedObservationUuid = null,
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
)
