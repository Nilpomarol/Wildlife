package com.wildlife.feasibility.ui.screens.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wildlife.feasibility.ObservationQualityTransition
import com.wildlife.feasibility.ProgressionLevel
import com.wildlife.feasibility.ProgressionProjection
import com.wildlife.feasibility.ProgressionState
import com.wildlife.feasibility.VerifiedAccount
import com.wildlife.feasibility.XpEventRecord
import com.wildlife.feasibility.XpEventType
import com.wildlife.feasibility.formatBytes
import com.wildlife.feasibility.ui.art.RankBadgeArt
import com.wildlife.feasibility.ui.art.RankPatch
import com.wildlife.feasibility.ui.components.FieldGuidePage
import com.wildlife.feasibility.ui.components.FieldStamp
import com.wildlife.feasibility.ui.components.HeaderHairline
import com.wildlife.feasibility.ui.components.JournalButton
import com.wildlife.feasibility.ui.components.JournalProgressBar
import com.wildlife.feasibility.ui.components.JournalSurface
import com.wildlife.feasibility.ui.components.RecordHead
import com.wildlife.feasibility.ui.components.RecordLine
import com.wildlife.feasibility.ui.components.RecordTally
import com.wildlife.feasibility.ui.components.SectionRule
import com.wildlife.feasibility.ui.components.WildlifeLoadingState
import com.wildlife.feasibility.ui.components.WildlifeScaffold
import com.wildlife.feasibility.ui.components.bleedHorizontally
import com.wildlife.feasibility.ui.components.headerScrim
import com.wildlife.feasibility.ui.theme.DisplayFontFamily
import com.wildlife.feasibility.ui.theme.FieldLabelStyle
import com.wildlife.feasibility.ui.theme.FieldStampStyle
import com.wildlife.feasibility.ui.theme.FieldTallyStyle
import com.wildlife.feasibility.ui.theme.WildlifeSpacing
import com.wildlife.feasibility.ui.theme.WildlifeSurface
import com.wildlife.feasibility.ui.theme.WildlifeTheme
import com.wildlife.feasibility.ui.theme.levelAccent
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

/**
 * Profile: the ranger's service record.
 *
 * The existing voices divide by *what is being measured*: Explore's guide is the printed
 * reference section, Collection is the personal ledger, Home is the loose dated page at
 * the front. This screen is the page a field pamphlet carries at the back — the
 * **credential page**, where the ranger's own standing is recorded rather than the
 * wildlife's.
 *
 * It divides from Home's masthead deliberately. Home prints the *next rung*: today's rank
 * and the bar toward the one after it. Profile prints the **whole ladder** — every rank in
 * the scheme, which of them have been earned, which title is being worn, and the rewards
 * that moved the ranger up it. Choosing a title happens on that ladder rather than in a
 * dialog floating over the page, because a dialog hid the very thing being chosen between.
 *
 * Below the ladder the page turns into paperwork, which is what the diagnostics are: ruled
 * form lines with leaders, not a settings list.
 */
@Composable
fun ProfileScreen(
    state: ShellUiState,
    onManageAccount: () -> Unit,
    onOpenPublicProfile: (String) -> Unit,
    onSelectProgressionTitle: (String) -> Unit,
    onOpenObservations: () -> Unit,
    onCopyTestReport: (String) -> Unit,
    onDeleteLocalData: () -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    var confirmingLocalDeletion by remember { mutableStateOf(false) }
    val colors = WildlifeTheme.colors
    // An unlinked visitor has no XP account yet, so the page opens at the bottom of the
    // ladder rather than blank — the same fallback the masthead makes on Home.
    val progression = state.progression ?: ProgressionProjection.project(state.totalXp)

    // The credential header carries the identity, so the app bar would print it twice.
    WildlifeScaffold(title = "Profile", bottomBar = bottomBar, showTopBar = false) { innerPadding ->
        FieldGuidePage {
            if (state.isLoading && state.account == null && state.progression == null) {
                WildlifeLoadingState(
                    label = "Opening your ranger record…",
                    modifier = Modifier.padding(innerPadding),
                )
                return@FieldGuidePage
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(
                    start = WildlifeSpacing.Screen,
                    end = WildlifeSpacing.Screen,
                    bottom = WildlifeSpacing.Section,
                ),
                verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Section),
            ) {
                item("credential") {
                    CredentialHeader(
                        account = state.account,
                        title = progression.selectedTitle,
                        modifier = Modifier.bleedHorizontally(WildlifeSpacing.Screen),
                    )
                }

                if (state.account == null) {
                    item("unregistered") {
                        Section("Unregistered") {
                            PrintedNote(
                                "This page keeps your record: lifetime XP, the ranks you have " +
                                    "earned and the rewards behind them. Link a public " +
                                    "iNaturalist identity and it starts filling in.",
                            )
                            JournalButton("Link iNaturalist", onManageAccount, primary = true)
                        }
                    }
                } else {
                    item("lifetime") { LifetimeRecord(state = state, progression = progression) }
                }

                item("ladder") {
                    Section("Rank ladder") {
                        RankLadder(
                            ladder = state.rankLadder,
                            progression = progression,
                            choosable = state.account != null &&
                                progression.earnedLevels.size > 1,
                            onSelectTitle = onSelectProgressionTitle,
                        )
                    }
                }

                if (state.account != null) {
                    item("rewards") {
                        Section("Recent rewards") { RewardsLedger(progression.recentEvents) }
                    }
                    item("inaturalist") {
                        Section("iNaturalist") {
                            INaturalistRecord(
                                state = state,
                                onManageAccount = onManageAccount,
                                onOpenPublicProfile = onOpenPublicProfile,
                                onOpenObservations = onOpenObservations,
                            )
                        }
                    }
                }

                item("local-data") {
                    Section("Local data") {
                        LocalDataForm(
                            state = state,
                            onCopyTestReport = onCopyTestReport,
                            onDeleteLocalData = { confirmingLocalDeletion = true },
                        )
                    }
                }

                state.errorMessage?.let { message ->
                    item("error") {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                item("footnote") {
                    Text(
                        text = "Wildlife uses only public iNaturalist data. It cannot create, " +
                            "edit, or delete observations.",
                        style = FieldStampStyle,
                        color = colors.parchmentFaint,
                    )
                }
            }
        }
    }

    if (confirmingLocalDeletion) {
        AlertDialog(
            onDismissRequest = { confirmingLocalDeletion = false },
            containerColor = WildlifeSurface,
            title = { Text("Delete Wildlife local data?") },
            text = {
                Text(
                    "This deletes the linked-account record, private capture copies, cached " +
                        "observations and catalogue, XP history, map settings, reference media " +
                        "and temporary cache. It cannot be undone. Your iNaturalist account and " +
                        "observations are not changed.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingLocalDeletion = false
                        onDeleteLocalData()
                    },
                ) {
                    Text("Delete local data", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingLocalDeletion = false }) { Text("Cancel") }
            },
        )
    }
}

// region — the credential

/**
 * The header: the ranger's credential, where Home's masthead is the day's status line.
 *
 * It carries no progress bar. The measure this page makes is the *ladder*, printed in full
 * below; a bar here would answer the same question twice and would leave the two screens'
 * headers reading as one duplicated component.
 */
@Composable
private fun CredentialHeader(
    account: VerifiedAccount?,
    title: ProgressionLevel,
    modifier: Modifier = Modifier,
) {
    val colors = WildlifeTheme.colors
    val accent = levelAccent(title.key)
    Box(modifier.fillMaxWidth()) {
        Box(Modifier.matchParentSize().background(headerScrim()))
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 18.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("RANGER RECORD", style = FieldLabelStyle, color = colors.oliveStrong)
                if (account != null) {
                    // The immutable public user number: the part of the credential that
                    // identifies the record rather than the name the person chose.
                    Text(
                        "USER #${account.userId}",
                        style = FieldStampStyle,
                        color = colors.parchmentFaint,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val badgeSize = 64.dp
                Box(contentAlignment = Alignment.Center) {
                    val drawn = RankBadgeArt(
                        levelKey = title.key,
                        color = accent,
                        modifier = Modifier.size(badgeSize),
                    )
                    if (!drawn) {
                        RankPatch(
                            levelKey = title.key,
                            border = accent,
                            field = colors.oliveDark,
                            art = colors.parchment,
                            modifier = Modifier.size(width = badgeSize * 0.82f, height = badgeSize),
                        )
                    }
                }
                Spacer(Modifier.size(14.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy((-10).dp),
                ) {
                    Text("FIELD TITLE", style = FieldLabelStyle, color = accent)
                    Text(
                        title.displayName,
                        fontFamily = DisplayFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 30.sp,
                        lineHeight = 30.sp,
                        color = colors.parchment,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        account?.login?.let { "@$it" } ?: "No account linked",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        lineHeight = 16.sp,
                        color = colors.parchment,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (account != null) {
                    // A struck stamp is reserved for a state the *record* has reached. A
                    // verified public identity is exactly that, and it is the one thing on
                    // this header the ranger did not choose for themselves.
                    Spacer(Modifier.size(WildlifeSpacing.Small))
                    FieldStamp(
                        label = "LINKED",
                        tint = colors.confirmed,
                        size = 56.dp,
                        description = "iNaturalist account linked and verified",
                    )
                }
            }
        }
        HeaderHairline(Modifier.align(Alignment.BottomCenter))
    }
}

/** Lifetime XP with the counts that produced it, in the ledger's summary box. */
@Composable
private fun LifetimeRecord(state: ShellUiState, progression: ProgressionState) {
    val colors = WildlifeTheme.colors
    val number = NumberFormat.getIntegerInstance(Locale.getDefault())
    RecordHead(
        eyebrow = "Service record",
        headlineValue = number.format(progression.totalXp),
        headlineLabel = "lifetime XP",
        tallies = listOf(
            RecordTally.of(state.collectionEntries, "Species", colors.confirmed),
            RecordTally.of(state.observations, "Observations", colors.parchmentDim),
            RecordTally(
                value = "${progression.earnedLevels.size} / ${state.rankLadder.size}",
                label = "Ranks",
                tint = colors.gold,
                description = "${progression.earnedLevels.size} of ${state.rankLadder.size} " +
                    "ranks earned",
            ),
        ),
        note = "XP is earned from confirmed public iNaturalist records only",
    )
}

// endregion

// region — the ladder

/**
 * Every rank in the scheme, printed as a page of the guide rather than a settings list.
 *
 * Earned rungs are also the control for choosing which title is worn: the choice belongs
 * on the ladder the titles come from. Locked rungs keep a silhouette badge — the same "not
 * yet found" language the unfilled species plates use.
 */
@Composable
private fun RankLadder(
    ladder: List<ProgressionLevel>,
    progression: ProgressionState,
    choosable: Boolean,
    onSelectTitle: (String) -> Unit,
) {
    val colors = WildlifeTheme.colors
    val earnedKeys = progression.earnedLevels.mapTo(mutableSetOf()) { it.key }
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (choosable) Modifier.selectableGroup() else Modifier),
    ) {
        ladder.forEachIndexed { index, level ->
            if (index > 0) HeaderHairline()
            RankRung(
                level = level,
                earned = level.key in earnedKeys,
                current = level.key == progression.currentLevel.key,
                worn = level.key == progression.selectedTitle.key,
                choosable = choosable,
                onSelect = { onSelectTitle(level.key) },
            )
            // The bar sits against the rung it is climbing out of. Printed under the whole
            // ladder it would read as progress through the scheme rather than to one rank.
            if (level.key == progression.currentLevel.key) {
                progression.nextLevel?.let { next -> RungProgress(progression, next) }
            }
        }
        if (choosable) {
            Spacer(Modifier.height(WildlifeSpacing.Card))
            Text(
                "TAP AN EARNED RANK TO WEAR ITS TITLE",
                style = FieldStampStyle,
                color = colors.parchmentFaint,
            )
        }
    }
}

@Composable
private fun RankRung(
    level: ProgressionLevel,
    earned: Boolean,
    current: Boolean,
    worn: Boolean,
    choosable: Boolean,
    onSelect: () -> Unit,
) {
    val colors = WildlifeTheme.colors
    val accent = levelAccent(level.key)
    val number = NumberFormat.getIntegerInstance(Locale.getDefault())
    // A worn title is only worth stating where the ranger could have worn another one.
    // With a single rank earned it is the current rank and nothing else, and calling it a
    // title implies a choice the page is not offering.
    val standing = when {
        worn && choosable && current -> "Current rank · title worn"
        worn && choosable -> "Title worn"
        current -> "Current rank"
        earned -> null
        else -> "Locked"
    }
    val standingTint = when {
        worn && choosable -> accent
        current -> colors.oliveStrong
        else -> colors.parchmentFaint
    }
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (choosable && earned) {
                    Modifier.selectable(
                        selected = worn,
                        onClick = onSelect,
                        role = Role.RadioButton,
                    )
                } else {
                    Modifier
                },
            )
            .semantics(mergeDescendants = true) {
                contentDescription = "${level.displayName}. ${standing ?: "Earned"}. " +
                    "${number.format(level.thresholdXp)} XP."
            }
            .heightIn(min = 48.dp)
            .padding(vertical = WildlifeSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The worn title is marked physically as well as in ink, the way the chapter rail
        // marks its selection: the state must not be carried by colour alone.
        Box(
            Modifier
                .size(width = 3.dp, height = 20.dp)
                .background(if (worn) accent else Color.Transparent),
        )
        Spacer(Modifier.size(WildlifeSpacing.Card))
        val badgeTint = if (earned) accent else colors.silhouette
        Box(contentAlignment = Alignment.Center) {
            val drawn = RankBadgeArt(
                levelKey = level.key,
                color = badgeTint,
                modifier = Modifier.size(RungBadgeSize),
            )
            if (!drawn) {
                RankPatch(
                    levelKey = level.key,
                    border = badgeTint,
                    field = colors.oliveDark,
                    art = if (earned) colors.parchment else colors.silhouette,
                    modifier = Modifier.size(
                        width = RungBadgeSize * 0.82f,
                        height = RungBadgeSize,
                    ),
                )
            }
        }
        Spacer(Modifier.size(WildlifeSpacing.Card))
        Column(Modifier.weight(1f)) {
            Text(
                level.displayName,
                fontFamily = DisplayFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                lineHeight = 21.sp,
                color = if (earned) colors.parchment else colors.parchmentFaint,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (standing != null) {
                Text(standing.uppercase(), style = FieldStampStyle, color = standingTint)
            }
        }
        Spacer(Modifier.size(WildlifeSpacing.Small))
        Text(
            "${number.format(level.thresholdXp)} XP",
            style = FieldTallyStyle,
            color = if (earned) colors.parchmentDim else colors.parchmentFaint,
        )
    }
}

/** The climb out of the current rung: a bar, and what is left to pay for the next one. */
@Composable
private fun RungProgress(progression: ProgressionState, next: ProgressionLevel) {
    val colors = WildlifeTheme.colors
    val number = NumberFormat.getIntegerInstance(Locale.getDefault())
    val remaining = progression.xpToNextLevel
    Column(
        Modifier
            .fillMaxWidth()
            // Indented to the rung's name column, so the bar reads as belonging to the
            // rank above it rather than to the ladder as a whole.
            .padding(start = RungTextInset, bottom = WildlifeSpacing.Small)
            .semantics(mergeDescendants = true) {
                contentDescription = remaining?.let {
                    "${number.format(it)} XP remaining until ${next.displayName}"
                } ?: "Progress toward ${next.displayName}"
            },
    ) {
        JournalProgressBar(
            fraction = progression.progressFraction,
            height = 7.dp,
            accent = levelAccent(next.key),
        )
        Spacer(Modifier.height(5.dp))
        Text(
            remaining?.let { "${number.format(it)} XP TO ${next.displayName.uppercase()}" }
                ?: "PROGRESS TO ${next.displayName.uppercase()}",
            style = FieldStampStyle,
            color = colors.parchmentFaint,
        )
    }
}

/** The rung's badge, and the indent that lines the progress bar up with the rank name. */
private val RungBadgeSize = 32.dp
private val RungTextInset = 3.dp + WildlifeSpacing.Card + RungBadgeSize + WildlifeSpacing.Card

// endregion

// region — the ledger

/**
 * What the XP was paid for, most recent first.
 *
 * The reward *kind* is stamped and its subject printed under it, so a species name stays a
 * name rather than becoming part of a typed form line.
 */
@Composable
private fun RewardsLedger(events: List<XpEventRecord>) {
    val colors = WildlifeTheme.colors
    if (events.isEmpty()) {
        PrintedNote("Confirm a new public observation to begin earning XP.")
        return
    }
    Column(Modifier.fillMaxWidth()) {
        events.forEachIndexed { index, event ->
            if (index > 0) HeaderHairline()
            Row(
                Modifier.fillMaxWidth().padding(vertical = WildlifeSpacing.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        rewardKind(event).uppercase(),
                        style = FieldStampStyle,
                        color = colors.oliveStrong,
                    )
                    rewardSubject(event)?.let { subject ->
                        Text(
                            subject,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.parchment,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        dateMedium(event.createdAtMs).uppercase(),
                        style = FieldStampStyle,
                        color = colors.parchmentFaint,
                    )
                }
                Spacer(Modifier.size(WildlifeSpacing.Small))
                Text("+${event.points} XP", style = FieldTallyStyle, color = colors.gold)
            }
        }
    }
}

/** The account's standing with iNaturalist, and the ways out to it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun INaturalistRecord(
    state: ShellUiState,
    onManageAccount: () -> Unit,
    onOpenPublicProfile: (String) -> Unit,
    onOpenObservations: () -> Unit,
) {
    val colors = WildlifeTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small)) {
        Text(
            observationSyncStatus(state).uppercase(),
            style = FieldStampStyle,
            color = if (state.observationSyncError != null) {
                MaterialTheme.colorScheme.error
            } else {
                colors.parchmentDim
            },
        )
        if (state.pendingMatchesReady > 0) {
            Text(
                "${state.pendingMatchesReady} PENDING HANDOFF READY TO REVIEW",
                style = FieldStampStyle,
                color = colors.gold,
            )
        }
        if (state.recentQualityTransitions.isNotEmpty()) {
            Spacer(Modifier.height(WildlifeSpacing.Micro))
            Text("RECENT QUALITY CHANGES", style = FieldLabelStyle, color = colors.parchmentDim)
            state.recentQualityTransitions.take(RECENT_TRANSITIONS).forEach { transition ->
                QualityTransitionLine(transition)
            }
        }
        Spacer(Modifier.height(WildlifeSpacing.Micro))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small),
            verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small),
        ) {
            JournalButton("Manage account", onManageAccount)
            state.account?.let { account ->
                JournalButton(
                    label = "Public profile",
                    onClick = { onOpenPublicProfile(account.login) },
                )
            }
            JournalButton("Open observations", onOpenObservations)
        }
    }
}

@Composable
private fun QualityTransitionLine(transition: ObservationQualityTransition) {
    val colors = WildlifeTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(top = WildlifeSpacing.Micro),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            transition.label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.parchment,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.size(WildlifeSpacing.Small))
        Text(
            text = "${qualityLabel(transition.fromQualityGrade)} → " +
                qualityLabel(transition.toQualityGrade),
            style = FieldStampStyle,
            color = if (transition.toQualityGrade == "research") {
                colors.confirmed
            } else {
                colors.parchmentDim
            },
        )
    }
}

/**
 * Diagnostics as the paperwork they are: ruled form lines with leaders.
 *
 * Every line is a count read off a record, which is what [RecordLine] exists for, so the
 * section needs no list rows, dividers or secondary type of its own.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LocalDataForm(
    state: ShellUiState,
    onCopyTestReport: (String) -> Unit,
    onDeleteLocalData: () -> Unit,
) {
    val colors = WildlifeTheme.colors
    val data = state.localData
    JournalSurface {
        Column(
            Modifier.padding(WildlifeSpacing.Card),
            verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small),
        ) {
            RecordLine("Cached observations", data.cachedObservations.toString())
            RecordLine("Catalogue species", data.catalogueSpecies.toString())
            RecordLine("Drafts", data.draftCaptures.toString())
            RecordLine("Pending handoffs", data.pendingHandoffs.toString())
            RecordLine(
                label = "Ready to review",
                value = data.readyToReview.toString(),
                // The one line in the drawer that is an outstanding task rather than a
                // measurement, so it is the one that takes colour.
                valueTint = if (data.readyToReview > 0) colors.gold else null,
            )
            RecordLine("Hidden from My map", data.hiddenMapObservations.toString())
            RecordLine(
                "Capture files",
                "${data.privateCaptureFiles} · ${formatBytes(data.privateCaptureBytes)}",
            )
            RecordLine(
                "Reference media",
                "${data.referenceMediaFiles} · ${formatBytes(data.referenceMediaBytes)}",
            )
            RecordLine("Media capacity", formatBytes(data.referenceMediaCapacityBytes))
            RecordLine("Pinned media", data.referenceMediaPinnedFiles.toString())
            // One measurement per line. Three counts on one line wrapped mid-value at large
            // type, which is the one thing a ruled form line cannot survive: the leader
            // stops carrying the eye to a value and starts pointing at a paragraph.
            RecordLine("Media queued", data.mediaPrefetchQueued.toString())
            RecordLine("Media downloading", data.mediaPrefetchRunning.toString())
            RecordLine("Media failed", data.mediaPrefetchFailed.toString())
            RecordLine("Detail media queued", data.mediaDetailQueued.toString())
            RecordLine("Detail media loading", data.mediaDetailRunning.toString())
            data.mediaNextRetryAtMs?.let { retryAt ->
                RecordLine("Next media retry", dateTimeShort(retryAt))
            }
            data.mediaFailureCodes.forEach { (code, count) ->
                RecordLine("Failure $code", count.toString())
            }
            HeaderHairline()
            Text(
                "COUNTS ONLY · THE COPIED REPORT EXCLUDES IDENTITY, COORDINATES, SPECIES AND " +
                    "FILE PATHS",
                style = FieldStampStyle,
                color = colors.parchmentFaint,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small),
                verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small),
            ) {
                JournalButton(
                    label = "Copy test report",
                    onClick = {
                        onCopyTestReport(
                            data.privacySafeTestReport(state.observationSyncError != null),
                        )
                    },
                )
                // The one destructive control in the app, and so the one control that
                // leaves the olive family. Outlined rather than filled: the page offers it,
                // it does not recommend it.
                JournalButton(
                    label = "Delete local data",
                    onClick = onDeleteLocalData,
                    accent = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

// endregion

// region — page furniture

/** A ruled section heading with its contents beneath, spaced as one block. */
@Composable
private fun Section(label: String, content: @Composable () -> Unit) {
    Column {
        SectionRule(label)
        Spacer(Modifier.height(WildlifeSpacing.Card))
        Column(verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Card)) { content() }
    }
}

/** Body copy printed straight onto the page, with no card under it. */
@Composable
private fun PrintedNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = WildlifeTheme.colors.parchmentDim,
        modifier = modifier,
    )
}

/** How many recent grade changes the record prints before it stops being news. */
private const val RECENT_TRANSITIONS = 3

private fun observationSyncStatus(state: ShellUiState): String = when {
    state.observationSyncing -> "Checking public observations…"
    state.observationSyncError != null -> state.observationSyncError
    state.lastObservationSyncAtMs == null -> "Not checked yet"
    state.observationDataStale ->
        "Stored data may be stale · last checked ${dateTimeShort(state.lastObservationSyncAtMs)}"
    else -> "Last checked ${dateTimeShort(state.lastObservationSyncAtMs)}"
}

private fun qualityLabel(value: String): String = when (value) {
    "needs_id" -> "Needs ID"
    "research" -> "Research Grade"
    "casual" -> "Casual"
    else -> value.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

private fun dateTimeShort(value: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(value))

private fun dateMedium(value: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(value))

/** What the reward was for, as a stamped kind. */
private fun rewardKind(event: XpEventRecord): String = when (event.type) {
    XpEventType.CONFIRMED_OBSERVATION -> "Observation confirmed"
    XpEventType.FIRST_SPECIES -> "First species"
    XpEventType.RESEARCH_GRADE -> "Reached Research Grade"
    XpEventType.REGIONAL_DISCOVERY -> "Regional discovery"
    XpEventType.REGIONAL_RARITY -> "Regional rarity bonus"
    XpEventType.REGIONAL_ICON_DISCOVERY -> "Regional Icon discovered"
    XpEventType.REGIONAL_ESSENTIALS -> "Regional Essentials complete"
    XpEventType.REGIONAL_ICONS -> "Regional Icons complete"
    XpEventType.IDENTIFICATION_GIVEN -> "Identification contributed"
    XpEventType.ANOMALY_CONFIRMED -> "Reviewed range anomaly"
    XpEventType.LEGACY -> "Earlier Wildlife progress"
}

/** The species the reward was earned on, where the event records one. */
private fun rewardSubject(event: XpEventRecord): String? = when (event.type) {
    XpEventType.CONFIRMED_OBSERVATION, XpEventType.FIRST_SPECIES ->
        event.label?.takeIf(String::isNotBlank)
    else -> null
}

// endregion

// region — previews

@Preview(showBackground = true, backgroundColor = 0xFF0E1209, widthDp = 390, heightDp = 900)
@Composable
private fun ProfileUnlinkedPreview() {
    WildlifeTheme {
        ProfileScreen(
            state = ShellUiState(),
            onManageAccount = {},
            onOpenPublicProfile = {},
            onSelectProgressionTitle = {},
            onOpenObservations = {},
            onCopyTestReport = {},
            onDeleteLocalData = {},
            bottomBar = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0E1209, widthDp = 390, heightDp = 1_400)
@Composable
private fun ProfileProgressionPreview() {
    val events = listOf(
        XpEventRecord(
            eventKey = "first-species:42",
            type = XpEventType.FIRST_SPECIES,
            observationUuid = "robin",
            subjectTaxonId = 42,
            label = "European robin",
            points = 500,
            createdAtMs = 1_723_000_000_000,
        ),
        XpEventRecord(
            eventKey = "observation:robin",
            type = XpEventType.CONFIRMED_OBSERVATION,
            observationUuid = "robin",
            subjectTaxonId = 42,
            label = "European robin",
            points = 10,
            createdAtMs = 1_722_999_000_000,
        ),
    )
    WildlifeTheme {
        ProfileScreen(
            state = ShellUiState(
                account = VerifiedAccount(42, "naturalist", 1),
                collectionEntries = 57,
                observations = 89,
                totalXp = 510,
                progression = ProgressionProjection.project(510, events),
                lastObservationSyncAtMs = 1_723_000_000_000,
                observationDataStale = false,
                pendingMatchesReady = 1,
                recentQualityTransitions = listOf(
                    ObservationQualityTransition(
                        observationUuid = "robin",
                        label = "European robin",
                        fromQualityGrade = "needs_id",
                        toQualityGrade = "research",
                        detectedAtMs = 1_723_000_000_000,
                    ),
                ),
            ),
            onManageAccount = {},
            onOpenPublicProfile = {},
            onSelectProgressionTitle = {},
            onOpenObservations = {},
            onCopyTestReport = {},
            onDeleteLocalData = {},
            bottomBar = {},
        )
    }
}

@Preview(
    showBackground = true,
    backgroundColor = 0xFF0E1209,
    widthDp = 390,
    heightDp = 1_600,
    fontScale = 1.8f,
)
@Composable
private fun ProfileLargeTextPreview() {
    WildlifeTheme {
        ProfileScreen(
            state = ShellUiState(
                account = VerifiedAccount(42, "naturalist", 1),
                collectionEntries = 210,
                observations = 640,
                totalXp = 2_550,
                progression = ProgressionProjection.project(2_550),
                observationSyncing = true,
                lastObservationSyncAtMs = 1_723_000_000_000,
                observationDataStale = false,
            ),
            onManageAccount = {},
            onOpenPublicProfile = {},
            onSelectProgressionTitle = {},
            onOpenObservations = {},
            onCopyTestReport = {},
            onDeleteLocalData = {},
            bottomBar = {},
        )
    }
}

// endregion
