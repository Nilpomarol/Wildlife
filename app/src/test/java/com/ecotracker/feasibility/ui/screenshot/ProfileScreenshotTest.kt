package com.wildlife.feasibility.ui.screenshot

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.wildlife.feasibility.LocalDataInventory
import com.wildlife.feasibility.ObservationQualityTransition
import com.wildlife.feasibility.ProgressionProjection
import com.wildlife.feasibility.VerifiedAccount
import com.wildlife.feasibility.XpEventRecord
import com.wildlife.feasibility.XpEventType
import com.wildlife.feasibility.ui.screens.shell.ProfileScreen
import com.wildlife.feasibility.ui.screens.shell.ShellUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders Profile across the states that change its shape, so the credential page and the
 * rank ladder can be reviewed without grinding an account to each rank.
 *
 * Output lands in `app/build/screenshots/profile-*.png`.
 *
 * The initial-loading state is deliberately not captured: `WildlifeLoadingState` holds a
 * `LinearProgressIndicator`, whose infinite transition keeps the Compose test clock busy so
 * `waitForIdle` never returns.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProfileScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
    fun phoneViewport() = captureAll(suffix = "")

    /** Tall viewport, so the credential, ladder, rewards and drawer are visible at once. */
    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h2400dp-xhdpi")
    fun fullPage() = captureAll(suffix = "-full")

    /**
     * The largest supported type, where the rank ladder is under most pressure: a rung has
     * to hold a badge, a two-word rank name, its standing and a five-digit threshold on one
     * line, and the drawer's form lines have to keep their leaders.
     */
    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h3000dp-xhdpi")
    fun largeFont() {
        composeRule.setContent {
            StillTheme {
                ScaledFont(1.5f) { Record(ProfileScenarios.all[2].state) }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("build/screenshots/profile-titled-large-font.png")
    }

    @Composable
    private fun Record(state: ShellUiState) {
        ProfileScreen(
            state = state,
            onManageAccount = {},
            onOpenPublicProfile = {},
            onSelectProgressionTitle = {},
            onOpenObservations = {},
            onCopyTestReport = {},
            onDeleteLocalData = {},
            bottomBar = {},
        )
    }

    private fun captureAll(suffix: String) {
        var scenario by mutableStateOf(ProfileScenarios.all.first())
        composeRule.setContent {
            StillTheme { Record(scenario.state) }
        }
        ProfileScenarios.all.forEach { next ->
            scenario = next
            composeRule.waitForIdle()
            composeRule.onRoot().captureRoboImage("build/screenshots/profile-${next.key}$suffix.png")
        }
    }
}

/** 7 August 2024, so the reward dates do not drift with the calendar. */
private const val FIXED_EVENT_MS = 1_723_000_000_000L

private data class ProfileScenario(val key: String, val state: ShellUiState)

private object ProfileScenarios {

    private val inventory = LocalDataInventory(
        accountLinked = true,
        cachedObservations = 89,
        catalogueSpecies = 568,
        draftCaptures = 2,
        pendingHandoffs = 1,
        readyToReview = 1,
        hiddenMapObservations = 3,
        privateCaptureFiles = 12,
        privateCaptureBytes = 42_000_000,
        referenceMediaFiles = 430,
        referenceMediaBytes = 180_000_000,
        referenceMediaCapacityBytes = 512_000_000,
        referenceMediaPinnedFiles = 24,
        mediaPrefetchQueued = 6,
        mediaPrefetchRunning = 1,
        mediaPrefetchFailed = 2,
        mediaDetailQueued = 1,
    )

    private val events = listOf(
        XpEventRecord(
            eventKey = "first-species:42",
            type = XpEventType.FIRST_SPECIES,
            observationUuid = "robin",
            subjectTaxonId = 42,
            label = "European robin",
            points = 500,
            createdAtMs = FIXED_EVENT_MS,
        ),
        XpEventRecord(
            eventKey = "research:robin",
            type = XpEventType.RESEARCH_GRADE,
            observationUuid = "robin",
            subjectTaxonId = 42,
            label = null,
            points = 25,
            createdAtMs = FIXED_EVENT_MS - 86_400_000,
        ),
        XpEventRecord(
            eventKey = "observation:robin",
            type = XpEventType.CONFIRMED_OBSERVATION,
            observationUuid = "robin",
            subjectTaxonId = 42,
            label = "European robin",
            points = 10,
            createdAtMs = FIXED_EVENT_MS - 172_800_000,
        ),
    )

    val all = listOf(
        // 1 — never linked: the ladder is legible, the record does not exist yet.
        ProfileScenario(
            key = "1-unlinked",
            state = ShellUiState(
                localData = LocalDataInventory(catalogueSpecies = 568),
            ),
        ),
        // 2 — the working state: one rank earned, a title with nothing to choose between,
        // and a shallow rewards ledger.
        ProfileScenario(
            key = "2-linked",
            state = ShellUiState(
                account = VerifiedAccount(42, "naturalist", 1),
                collectionEntries = 57,
                observations = 89,
                totalXp = 510,
                progression = ProgressionProjection.project(510, events),
                lastObservationSyncAtMs = FIXED_EVENT_MS,
                observationDataStale = false,
                pendingMatchesReady = 1,
                localData = inventory,
                recentQualityTransitions = listOf(
                    ObservationQualityTransition(
                        observationUuid = "robin",
                        label = "European robin",
                        fromQualityGrade = "needs_id",
                        toQualityGrade = "research",
                        detectedAtMs = FIXED_EVENT_MS,
                    ),
                ),
            ),
        ),
        // 3 — several ranks earned with a lower title worn: the ladder as a control, and
        // the one case where the header's title and the current rank differ.
        ProfileScenario(
            key = "3-titled",
            state = ProfileTitled.state,
        ),
        // 4 — a failed check: the status line is the only thing on the page that changes,
        // and it must not be mistaken for a reward.
        ProfileScenario(
            key = "4-sync-error",
            state = ShellUiState(
                account = VerifiedAccount(42, "naturalist", 1),
                collectionEntries = 57,
                observations = 89,
                totalXp = 510,
                progression = ProgressionProjection.project(510, events),
                observationSyncError = "iNaturalist could not be reached",
                lastObservationSyncAtMs = FIXED_EVENT_MS,
                localData = inventory,
            ),
        ),
    )

    private object ProfileTitled {
        val state = ShellUiState(
            account = VerifiedAccount(42, "trackerofthings", 1),
            collectionEntries = 210,
            observations = 640,
            totalXp = 9_400,
            progression = ProgressionProjection.project(
                totalXp = 9_400,
                events = events,
                selectedLevelKey = "naturalist",
            ),
            lastObservationSyncAtMs = FIXED_EVENT_MS,
            observationDataStale = true,
            localData = inventory,
        )
    }
}
