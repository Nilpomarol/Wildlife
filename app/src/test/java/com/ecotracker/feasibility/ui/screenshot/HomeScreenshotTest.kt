package com.wildlife.feasibility.ui.screenshot

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.wildlife.feasibility.NearbySpecies
import com.wildlife.feasibility.VerifiedAccount
import com.wildlife.feasibility.ui.screens.explore.NearbyDiscoveryState
import com.wildlife.feasibility.ui.screens.shell.HomeHighlight
import com.wildlife.feasibility.ui.screens.shell.HomeScreen
import com.wildlife.feasibility.ui.screens.shell.RegionalHomeProgress
import com.wildlife.feasibility.ui.screens.shell.ShellUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders Home across the states that change its shape, so the dated-entry design can be
 * reviewed without grinding an account to each one.
 *
 * Output lands in `app/build/screenshots/home-*.png`.
 *
 * The nearby *loading* state is deliberately not captured: it holds a
 * `LinearProgressIndicator`, whose infinite transition keeps the Compose test clock busy
 * so `waitForIdle` never returns.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
    fun phoneViewport() = captureAll(suffix = "")

    /** Tall viewport, so the masthead, record, table and slips are visible at once. */
    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h1500dp-xhdpi")
    fun fullPage() = captureAll(suffix = "-full")

    private fun captureAll(suffix: String) {
        var scenario by mutableStateOf(HomeScenarios.all.first())
        composeRule.setContent {
            StillTheme {
                HomeScreen(
                    state = scenario.state,
                    onCapture = {}, onCollection = {}, onExplore = {}, onMyMap = {},
                    onObservations = {}, onOpenSpecies = {},
                    mappedObservationCount = scenario.mapped,
                    nearby = scenario.nearby,
                    onDiscoverNearby = {}, onSeeAllNearby = {}, onLinkAccount = {},
                    bottomBar = {},
                    nowMs = FIXED_NOW_MS,
                )
            }
        }
        HomeScenarios.all.forEach { next ->
            scenario = next
            composeRule.waitForIdle()
            composeRule.onRoot().captureRoboImage("build/screenshots/home-${next.key}$suffix.png")
        }
    }
}

/** Saturday 23 August 2025, so the dateline does not drift with the calendar. */
private const val FIXED_NOW_MS = 1_755_907_200_000L

private data class HomeScenario(
    val key: String,
    val state: ShellUiState,
    val nearby: NearbyDiscoveryState,
    val mapped: Int,
)

private object HomeScenarios {

    private val region = RegionalHomeProgress(
        displayName = "Mediterranean Europe",
        observedSpecies = 57,
        totalSpecies = 240,
        essentialsObserved = 4,
        essentialsTotal = 10,
        iconsObserved = 1,
        iconsTotal = 5,
    )

    // Group keys, not iNaturalist's "Aves"/"Mammalia": the client normalizes at parse time,
    // so this is the shape the UI actually receives. Photo URLs are deliberately absent —
    // Coil cannot reach the network under Robolectric, so every plate renders the silhouette
    // fallback, which is the branch worth seeing captured anyway.
    private val reported = NearbyDiscoveryState(
        requested = true,
        fetchedAtMs = FIXED_NOW_MS - 2 * 60 * 60 * 1000,
        species = listOf(
            NearbySpecies(1, "European robin", "Erithacus rubecula", "birds", 412),
            NearbySpecies(2, "Common kingfisher", "Alcedo atthis", "birds", 96),
            NearbySpecies(3, "Red fox", "Vulpes vulpes", "mammals", 54),
            NearbySpecies(4, "Fire salamander", "Salamandra salamandra", "amphibians", 12),
            NearbySpecies(5, "Ocellated lizard", "Timon lepidus", "reptiles", 9),
            NearbySpecies(6, "Wild boar", "Sus scrofa", "mammals", 7),
            NearbySpecies(7, "Barbel", "Barbus meridionalis", "fish", 4),
        ),
    )

    val all = listOf(
        // 1 — never linked: the guide works, the record does not exist yet.
        HomeScenario(
            key = "1-unlinked",
            state = ShellUiState(catalogueSpecies = 568),
            nearby = NearbyDiscoveryState(),
            mapped = 0,
        ),
        // 2 — linked, nothing recorded: the page has to explain how it fills.
        HomeScenario(
            key = "2-first-entry",
            state = ShellUiState(
                account = VerifiedAccount(1, "newranger", 0),
                regionalProgress = region,
            ),
            nearby = NearbyDiscoveryState(),
            mapped = 0,
        ),
        // 3 — the working state: a mounted record, a ranked table, a shallow pile.
        HomeScenario(
            key = "3-recorded",
            state = ShellUiState(
                account = VerifiedAccount(1, "naturalist", 0),
                totalXp = 510,
                observations = 89,
                regionalProgress = region,
                draftObservations = 2,
                pendingHandoffs = 1,
                latestDiscovery = HomeHighlight(
                    taxonId = 42,
                    label = "European robin",
                    photoUrl = null,
                    observedAtMs = 1_755_300_000_000,
                    researchGrade = true,
                    observationCount = 3,
                    awaitingSpeciesIdentification = false,
                ),
            ),
            nearby = reported,
            mapped = 72,
        ),
        // 4 — a deep backlog with something to confirm: the pile at full depth and the
        // one gold number on the page.
        HomeScenario(
            key = "4-backlog",
            state = ShellUiState(
                account = VerifiedAccount(1, "naturalist", 0),
                totalXp = 4_200,
                observations = 310,
                regionalProgress = region,
                draftObservations = 3,
                pendingHandoffs = 2,
                pendingMatchesReady = 4,
                latestDiscovery = HomeHighlight(
                    taxonId = 77,
                    label = "Iberian lynx",
                    photoUrl = null,
                    observedAtMs = 1_755_600_000_000,
                    researchGrade = false,
                    observationCount = 1,
                    awaitingSpeciesIdentification = true,
                ),
            ),
            nearby = reported,
            mapped = 240,
        ),
    )
}
