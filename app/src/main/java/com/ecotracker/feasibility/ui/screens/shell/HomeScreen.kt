package com.wildlife.feasibility.ui.screens.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wildlife.feasibility.NearbySpecies
import com.wildlife.feasibility.EncounterRarity
import com.wildlife.feasibility.ProgressionProjection
import com.wildlife.feasibility.ProgressionState
import com.wildlife.feasibility.VerifiedAccount
import com.wildlife.feasibility.ui.components.FieldGuidePage
import com.wildlife.feasibility.ui.components.JournalButton
import com.wildlife.feasibility.ui.components.JournalProgressBar
import com.wildlife.feasibility.ui.components.Masthead
import com.wildlife.feasibility.ui.components.NearbyHeaderRow
import com.wildlife.feasibility.ui.components.NearbySpeciesCarousel
import com.wildlife.feasibility.ui.components.RecordLine
import com.wildlife.feasibility.ui.components.RegionEmblem
import com.wildlife.feasibility.ui.components.SectionRule
import com.wildlife.feasibility.ui.components.SlipStack
import com.wildlife.feasibility.ui.components.SpecimenPlate
import com.wildlife.feasibility.ui.components.WildlifeScaffold
import com.wildlife.feasibility.ui.components.WildlifeLoadingState
import com.wildlife.feasibility.ui.components.bleedHorizontally
import com.wildlife.feasibility.ui.art.FieldMark
import com.wildlife.feasibility.ui.screens.explore.NearbyDiscoveryState
import com.wildlife.feasibility.ui.theme.DisplayFontFamily
import com.wildlife.feasibility.ui.theme.FieldLabelStyle
import com.wildlife.feasibility.ui.theme.FieldStampStyle
import com.wildlife.feasibility.ui.theme.FieldTallyStyle
import com.wildlife.feasibility.ui.theme.WildlifeSpacing
import com.wildlife.feasibility.ui.theme.WildlifeTheme
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Home: the journal's dated front page.
 *
 * It is deliberately **not** a menu. Every card this screen used to carry linked to a
 * destination already one tap away in the index strip, which left Home with no content of
 * its own — a portal stacked on top of a portal. What it carries now exists nowhere else:
 *
 * - The **ranger**, where Collection carries the region. Lifetime XP and the rank ladder
 *   live here, which is the measure Collection's regional header explicitly refuses.
 * - **Current-region progress** — a prominent summary that opens the full regional guide.
 * - **Today's record** — the most recent confirmed sighting, mounted as a photograph.
 * - **What is about** — the nearby extract, ruled as a table so the ranking is legible.
 * - **The desk** — outstanding drafts and handoffs, as a pile of slips.
 *
 * Collection is the reference section of the guide; this is the loose dated page at the
 * front of it. See `ui/components/JournalEntry.kt` for the surfaces that carry that voice.
 */
@Composable
fun HomeScreen(
    state: ShellUiState,
    onCapture: () -> Unit,
    onCollection: () -> Unit,
    onExplore: () -> Unit,
    onMyMap: () -> Unit,
    onObservations: () -> Unit,
    onOpenSpecies: (Long) -> Unit,
    mappedObservationCount: Int,
    nearby: NearbyDiscoveryState,
    onDiscoverNearby: () -> Unit,
    onSeeAllNearby: () -> Unit,
    onLinkAccount: () -> Unit,
    bottomBar: @Composable () -> Unit,
    /** Overridden by screenshot tests so the dateline does not change under them. */
    nowMs: Long = System.currentTimeMillis(),
) {
    // The masthead carries the title, so the app bar would print the name twice.
    WildlifeScaffold(title = "Wildlife", bottomBar = bottomBar, showTopBar = false) { innerPadding ->
        FieldGuidePage {
            if (state.isLoading && state.latestDiscovery == null && state.account == null &&
                state.catalogueSpecies == 0 && state.regionalProgress == null
            ) {
                WildlifeLoadingState(
                    label = "Opening your field journal…",
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
                item("masthead") {
                    HomeMasthead(
                        state = state,
                        nowMs = nowMs,
                        modifier = Modifier.bleedHorizontally(WildlifeSpacing.Screen),
                    )
                }

                state.regionalProgress?.let { progress ->
                    item("current-region") {
                        CurrentRegionCard(
                            progress = progress,
                            onOpenCollection = onCollection,
                        )
                    }
                }

                val discovery = state.latestDiscovery
                when {
                    state.account == null -> item("unlinked") {
                        UnlinkedInvitation(onExplore = onExplore, onLinkAccount = onLinkAccount)
                    }

                    discovery != null -> item("specimen") {
                        Column {
                            SectionRule("Latest record")
                            Spacer(Modifier.height(WildlifeSpacing.Card))
                            SpecimenPlate(
                                label = discovery.label,
                                scientificName = discovery.scientificName,
                                caption = discoveryCaption(discovery),
                                actionLabel = if (discovery.taxonId != null) {
                                    "View species"
                                } else {
                                    "Open collection"
                                },
                                rarityLabel = rarityLabel(discovery.encounterRarity),
                                rarityTint = rarityTint(discovery.encounterRarity),
                                regionLabel = discovery.regionSeenIn,
                                awaitingSpeciesIdentification =
                                    discovery.awaitingSpeciesIdentification,
                                photoUrl = discovery.photoUrl,
                                photoDescription =
                                    "${discovery.label}, your most recent observation photo",
                                onClick = { discovery.taxonId?.let(onOpenSpecies) ?: onCollection() },
                                stampLabel = if (discovery.researchGrade) "RESEARCH" else null,
                                stampTint = WildlifeTheme.colors.confirmed,
                            )
                        }
                    }

                    else -> item("first-entry") { FirstEntryNote(onCapture = onCapture) }
                }

                item("nearby") {
                    Column {
                        SectionRule("Reported nearby")
                        Spacer(Modifier.height(WildlifeSpacing.Small))
                        NearbySection(
                            nearby = nearby,
                            onDiscover = onDiscoverNearby,
                            onOpenTaxon = onOpenSpecies,
                            onSeeAll = onSeeAllNearby,
                            nowMs = nowMs,
                        )
                    }
                }

                // Hidden when there is genuinely nothing on it. A visitor who has never
                // recorded anything was being shown a pile of slips reading 0, 0, 0, 0 —
                // the emptiest possible thing the page could say about them.
                if (state.hasDeskContents(mappedObservationCount)) {
                    item("desk") {
                        Column {
                            SectionRule("The desk")
                            Spacer(Modifier.height(WildlifeSpacing.Card))
                            DeskSlips(
                                state = state,
                                mappedObservationCount = mappedObservationCount,
                                onReview = onObservations,
                                onMyMap = onMyMap,
                            )
                        }
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
            }
        }
    }
}

// region — masthead

@Composable
private fun HomeMasthead(
    state: ShellUiState,
    nowMs: Long,
    modifier: Modifier = Modifier,
) {
    // Unlinked users have no XP account yet, so fall back to the projected entry level —
    // the masthead is never blank, it just opens at the bottom of the ladder.
    val levels: ProgressionState = state.progression ?: ProgressionProjection.project(state.totalXp)
    val title = levels.selectedTitle
    val next = levels.nextLevel
    val number = NumberFormat.getIntegerInstance(Locale.getDefault())
    Masthead(
        dateline = dateline(nowMs),
        levelKey = title.key,
        levelName = title.displayName,
        subtitle = state.account?.login?.let { "@$it" } ?: "No account linked",
        // The bar counts the ladder, not a region. Naming the next rank on it is what keeps
        // it from being read as regional completion, which is Collection's measure.
        progressLabel = next?.let { "Progress to ${it.displayName}" } ?: "Ladder complete",
        progressTrailing = next?.let {
            "${number.format(levels.totalXp)} / ${number.format(it.thresholdXp)} XP"
        } ?: "${number.format(levels.totalXp)} XP",
        progressSupporting = levels.xpToNextLevel?.let {
            "${number.format(it)} XP remaining"
        } ?: "Highest ranger rank reached",
        progressDescription = next?.let {
            "${number.format(levels.totalXp)} of ${number.format(it.thresholdXp)} lifetime XP earned toward ${it.displayName}; " +
                "${number.format(levels.xpToNextLevel)} XP remaining"
        } ?: "Highest ranger rank reached with ${number.format(levels.totalXp)} lifetime XP",
        progressFraction = next?.let {
            levels.totalXp.toFloat() / it.thresholdXp.toFloat()
        } ?: 1f,
        modifier = modifier,
    )
}

@Composable
private fun CurrentRegionCard(
    progress: RegionalHomeProgress,
    onOpenCollection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WildlifeTheme.colors
    val fraction = if (progress.totalSpecies > 0) {
        progress.observedSpecies.toFloat() / progress.totalSpecies.toFloat()
    } else {
        0f
    }
    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
            .border(
                width = 1.dp,
                color = colors.oliveStrong.copy(alpha = 0.62f),
                shape = MaterialTheme.shapes.medium,
            )
            .clickable(onClick = onOpenCollection)
            .semantics(mergeDescendants = true) {
                contentDescription =
                    "Current region, ${progress.displayName}. " +
                    "${progress.observedSpecies} of ${progress.totalSpecies} species. " +
                    "${progress.essentialsObserved} of ${progress.essentialsTotal} Essentials. " +
                    "${progress.iconsObserved} of ${progress.iconsTotal} Icons. Open guide."
            }
            .padding(WildlifeSpacing.Screen),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("CURRENT REGION", style = FieldLabelStyle, color = colors.oliveStrong)
            Text("OPEN REGIONAL GUIDE  ›", style = FieldStampStyle, color = colors.parchment)
        }
        Spacer(Modifier.height(WildlifeSpacing.Small))
        Row(verticalAlignment = Alignment.CenterVertically) {
            RegionEmblem(
                regionKey = progress.regionKey.ifBlank { null },
                size = 54.dp,
            )
            Spacer(Modifier.size(WildlifeSpacing.Card))
            Column(Modifier.weight(1f)) {
                Text(
                    text = progress.displayName,
                    fontFamily = DisplayFontFamily,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    fontSize = 23.sp,
                    lineHeight = 26.sp,
                    color = colors.parchment,
                    maxLines = 2,
                )
            }
            Spacer(Modifier.size(WildlifeSpacing.Small))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${progress.observedSpecies} / ${progress.totalSpecies}",
                    style = FieldTallyStyle.copy(fontSize = 17.sp, lineHeight = 21.sp),
                    color = colors.oliveStrong,
                )
                Text("SPECIES", style = FieldStampStyle, color = colors.parchmentDim)
            }
        }
        Spacer(Modifier.height(WildlifeSpacing.Card))
        JournalProgressBar(fraction = fraction, height = 7.dp)
        Spacer(Modifier.height(WildlifeSpacing.Card))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(WildlifeSpacing.Screen),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RegionalStandingProgress(
                markName = "regional_essential",
                label = "Essentials",
                observed = progress.essentialsObserved,
                total = progress.essentialsTotal,
                tint = colors.essential,
                modifier = Modifier.weight(1f),
            )
            RegionalStandingProgress(
                markName = "regional_icon",
                label = "Icons",
                observed = progress.iconsObserved,
                total = progress.iconsTotal,
                tint = colors.icon,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun RegionalStandingProgress(
    markName: String,
    label: String,
    observed: Int,
    total: Int,
    tint: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    val colors = WildlifeTheme.colors
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small),
    ) {
        FieldMark(markName, tint, Modifier.size(25.dp))
        Column {
            Text(label.uppercase(), style = FieldStampStyle, color = tint)
            Text(
                "$observed / $total",
                style = FieldTallyStyle.copy(fontSize = 15.sp, lineHeight = 18.sp),
                color = colors.parchment,
            )
        }
    }
}

/**
 * "SUNDAY · 23 AUGUST".
 */
private fun dateline(nowMs: Long): String {
    val format = SimpleDateFormat("EEEE · d MMMM", Locale.getDefault())
    return format.format(Date(nowMs))
}

private fun discoveryCaption(highlight: HomeHighlight): String {
    val date = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
        .format(Date(highlight.observedAtMs))
    val observations = if (highlight.observationCount == 1) {
        "1 observation"
    } else {
        "${highlight.observationCount} observations"
    }
    return "$date · $observations"
}

@Composable
private fun rarityTint(rarity: EncounterRarity?): androidx.compose.ui.graphics.Color {
    val colors = WildlifeTheme.colors
    return when (rarity) {
        EncounterRarity.COMMON -> colors.rarityCommon
        EncounterRarity.UNCOMMON -> colors.rarityUncommon
        EncounterRarity.RARE -> colors.rarityRare
        EncounterRarity.VERY_RARE -> colors.rarityVeryRare
        EncounterRarity.UNKNOWN, null -> colors.parchmentFaint
    }
}

private fun rarityLabel(rarity: EncounterRarity?): String? = when (rarity) {
    EncounterRarity.COMMON -> "Common"
    EncounterRarity.UNCOMMON -> "Uncommon"
    EncounterRarity.RARE -> "Rare"
    EncounterRarity.VERY_RARE -> "Very rare"
    EncounterRarity.UNKNOWN, null -> null
}

// endregion

// region — sections

/**
 * The nearby extract, ruled as a table.
 *
 * Every state states the same caveat in the same words as Explore's full section: these are
 * *reports*, ordered by reporting frequency, and the location is sampled once.
 */
@Composable
private fun NearbySection(
    nearby: NearbyDiscoveryState,
    onDiscover: () -> Unit,
    onOpenTaxon: (Long) -> Unit,
    onSeeAll: () -> Unit,
    nowMs: Long,
) {
    val shelf = nearby.species.take(NEARBY_SHELF_LIMIT)
    Column(verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small)) {
        when {
            // A refresh over a restored answer keeps the shelf on screen and says so, rather
            // than replacing content the user was already reading with a spinner.
            nearby.loading && shelf.isNotEmpty() -> {
                NearbyHeader(shelf, nearby, checkedLabel = "again now", onSeeAll = onSeeAll)
                NearbySpeciesCarousel(species = shelf, onOpenTaxon = onOpenTaxon)
            }

            nearby.loading -> {
                PrintedNote("Checking what has been reported within ${nearby.radiusKm} km…")
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            // Likewise for a failed refresh: the previous answer is still the best one there
            // is, so the error annotates it instead of replacing it.
            nearby.errorMessage != null && shelf.isNotEmpty() -> {
                Text(
                    text = nearby.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                NearbyHeader(shelf, nearby, checkedAgo(nearby.fetchedAtMs, nowMs), onSeeAll)
                NearbySpeciesCarousel(species = shelf, onOpenTaxon = onOpenTaxon)
            }

            nearby.errorMessage != null -> {
                Text(
                    text = nearby.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                JournalButton("Try again", onDiscover)
            }

            !nearby.requested -> {
                PrintedNote(
                    "Check which species from your regional guide have been reported within " +
                        "${nearby.radiusKm} km this month.",
                )
                Text(
                    "Your location is sampled once. Only a rounded position is kept, so the " +
                        "app can tell when you have moved far enough to look again.",
                    style = FieldStampStyle,
                    color = WildlifeTheme.colors.parchmentFaint,
                )
                JournalButton("Check near me", onDiscover, primary = true)
            }

            shelf.isEmpty() -> {
                PrintedNote(
                    "No species from your regional guide were reported in this area for this " +
                        "calendar month.",
                )
                JournalButton("Check again", onDiscover)
            }

            else -> {
                NearbyHeader(shelf, nearby, checkedAgo(nearby.fetchedAtMs, nowMs), onSeeAll)
                NearbySpeciesCarousel(species = shelf, onOpenTaxon = onOpenTaxon)
            }
        }
    }
}

/**
 * The footnote and, beside it, "See all" when the shelf is not showing every result.
 *
 * A row rather than the button sitting under the full-width carousel: the footnote is short
 * enough to leave room beside it, and stacking them cost a line for no reason.
 */
@Composable
private fun NearbyHeader(
    shelf: List<NearbySpecies>,
    nearby: NearbyDiscoveryState,
    checkedLabel: String?,
    onSeeAll: () -> Unit,
) {
    NearbyHeaderRow(
        total = nearby.species.size,
        checkedLabel = checkedLabel,
        seeAllLabel = "See all ${nearby.species.size} in Explore".takeIf {
            nearby.species.size > shelf.size
        },
        onSeeAll = onSeeAll,
    )
}

/**
 * "2 hours ago" as a stamped phrase, or null when the answer has no timestamp.
 *
 * Coarse on purpose: the exact minute of a search is neither interesting nor something the
 * page should imply it is tracking.
 */
private fun checkedAgo(fetchedAtMs: Long?, nowMs: Long): String? {
    if (fetchedAtMs == null || fetchedAtMs <= 0L) return null
    val elapsed = nowMs - fetchedAtMs
    if (elapsed < 0) return null
    val minutes = elapsed / 60_000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 2 -> "just now"
        minutes < 60 -> "$minutes min ago"
        hours < 24 -> if (hours == 1L) "1 hour ago" else "$hours hours ago"
        days == 1L -> "yesterday"
        else -> "$days days ago"
    }
}

/** Whether the desk has anything on it worth printing a section for. */
private fun ShellUiState.hasDeskContents(mapped: Int): Boolean =
    account != null || draftObservations > 0 || pendingHandoffs > 0 ||
        pendingMatchesReady > 0 || mapped > 0

/**
 * The ranger's desk: what is outstanding, and what has been filed.
 *
 * Presented as the top of a pile of slips, and the pile's depth is felt before any number
 * is read — which is the point: a draft is a thing you have to come back to, and a flat
 * card said that far too politely. Only *outstanding* items add depth; a mapped observation
 * is filed, so it is listed but does not deepen the pile.
 *
 * The section is called the desk rather than "Unfiled" because it carries both. A mapped
 * observation is the opposite of unfiled, and listing it under that heading was the one
 * line on the page that contradicted its own label.
 */
@Composable
private fun DeskSlips(
    state: ShellUiState,
    mappedObservationCount: Int,
    onReview: () -> Unit,
    onMyMap: () -> Unit,
) {
    val colors = WildlifeTheme.colors
    // One slip per kind of outstanding thing, so the pile grows with the actual backlog.
    val outstanding = listOf(
        state.draftObservations,
        state.pendingHandoffs,
        state.pendingMatchesReady,
    ).count { it > 0 }
    SlipStack(depth = outstanding.coerceAtLeast(1)) {
        Column(
            Modifier.padding(WildlifeSpacing.Card),
            verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small),
        ) {
            RecordLine("Drafts", state.draftObservations.toString())
            RecordLine("Awaiting iNaturalist", state.pendingHandoffs.toString())
            RecordLine(
                label = "Ready to confirm",
                value = state.pendingMatchesReady.toString(),
                // The one actionable number in the pile, so it is the one that takes colour.
                valueTint = if (state.pendingMatchesReady > 0) colors.gold else null,
            )
            RecordLine("Mapped", mappedObservationCount.toString())
            Spacer(Modifier.height(WildlifeSpacing.Micro))
            Row(horizontalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small)) {
                JournalButton(
                    label = "Open observations",
                    onClick = onReview,
                    primary = state.pendingMatchesReady > 0,
                )
                if (state.account != null) {
                    JournalButton("My map", onMyMap)
                }
            }
        }
    }
}

/** Shown in place of the specimen plate when a linked account has recorded nothing yet. */
@Composable
private fun FirstEntryNote(onCapture: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Card)) {
        SectionRule("Latest record")
        PrintedNote(
            "This page fills in from the top. Record a sighting and it is mounted here once " +
                "iNaturalist confirms it.",
        )
        JournalButton("Record a sighting", onCapture, primary = true)
    }
}

/** Shown to an unlinked visitor: the guide works, the personal record does not exist yet. */
@Composable
private fun UnlinkedInvitation(onExplore: () -> Unit, onLinkAccount: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Card)) {
        SectionRule("Unregistered")
        PrintedNote(
            "The regional guide and \"what is reported here\" work right away. Link an " +
                "iNaturalist account when you want this page to start keeping your record.",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small)) {
            JournalButton("Open the guide", onExplore, primary = true)
            JournalButton("Link iNaturalist", onLinkAccount)
        }
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

/**
 * How many plates the shelf carries.
 *
 * A carousel costs one screen width whatever its length, so the old three-item cap existed
 * only because a vertical list of them cost real page. Twelve is roughly five screens of
 * swiping, past which "See all in Explore" is the better affordance than more swiping.
 */
private const val NEARBY_SHELF_LIMIT = 12

// endregion

// region — previews

/** A fixed instant, so previews and screenshots do not shift with the calendar. */
private const val PREVIEW_NOW_MS = 1_755_900_000_000L

@Preview(showBackground = true, backgroundColor = 0xFF080B09, widthDp = 390, heightDp = 900)
@Composable
private fun HomePreview() {
    WildlifeTheme {
        HomeScreen(
            state = ShellUiState(
                collectionEntries = 57,
                identifiedSpecies = 55,
                observations = 89,
                totalXp = 510,
                catalogueSpecies = 568,
                pendingHandoffs = 1,
                draftObservations = 2,
                pendingMatchesReady = 1,
                account = VerifiedAccount(1, "naturalist", 0),
                regionalProgress = RegionalHomeProgress(
                    displayName = "Mediterranean Europe",
                    observedSpecies = 57,
                    totalSpecies = 240,
                    essentialsObserved = 4,
                    essentialsTotal = 10,
                    iconsObserved = 1,
                    iconsTotal = 5,
                ),
                latestDiscovery = HomeHighlight(
                    taxonId = 42,
                    label = "European robin",
                    photoUrl = null,
                    observedAtMs = 1_723_000_000_000,
                    researchGrade = true,
                    observationCount = 3,
                    awaitingSpeciesIdentification = false,
                    scientificName = "Erithacus rubecula",
                    encounterRarity = EncounterRarity.COMMON,
                    regionSeenIn = "Mediterranean Europe",
                ),
            ),
            onCapture = {}, onCollection = {}, onExplore = {}, onMyMap = {}, onObservations = {},
            onOpenSpecies = {}, mappedObservationCount = 72,
            nearby = NearbyDiscoveryState(
                requested = true,
                species = listOf(
                    NearbySpecies(1, "European robin", "Erithacus rubecula", "birds", 412),
                    NearbySpecies(2, "Common kingfisher", "Alcedo atthis", "birds", 96),
                    NearbySpecies(3, "Red fox", "Vulpes vulpes", "mammals", 54),
                    NearbySpecies(4, "Fire salamander", "Salamandra salamandra", "amphibians", 12),
                ),
            ),
            onDiscoverNearby = {}, onSeeAllNearby = {}, onLinkAccount = {},
            bottomBar = {},
            nowMs = PREVIEW_NOW_MS,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF080B09, widthDp = 390, heightDp = 820)
@Composable
private fun HomeUnlinkedPreview() {
    WildlifeTheme {
        HomeScreen(
            state = ShellUiState(catalogueSpecies = 568),
            onCapture = {}, onCollection = {}, onExplore = {}, onMyMap = {}, onObservations = {},
            onOpenSpecies = {}, mappedObservationCount = 0,
            nearby = NearbyDiscoveryState(),
            onDiscoverNearby = {}, onSeeAllNearby = {}, onLinkAccount = {},
            bottomBar = {},
            nowMs = PREVIEW_NOW_MS,
        )
    }
}

// endregion
