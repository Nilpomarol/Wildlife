package com.wildlife.feasibility.ui.screenshot

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.wildlife.feasibility.MarkerState
import com.wildlife.feasibility.MatchBand
import com.wildlife.feasibility.MatchProposal
import com.wildlife.feasibility.MatchReason
import com.wildlife.feasibility.ObservationCandidate
import com.wildlife.feasibility.VerifiedAccount
import com.wildlife.feasibility.ui.screens.observations.ManagedObservationUi
import com.wildlife.feasibility.ui.screens.observations.ManagedPhotoUi
import com.wildlife.feasibility.ui.screens.observations.MatchProposalUi
import com.wildlife.feasibility.ui.screens.observations.ObservationsScreen
import com.wildlife.feasibility.ui.screens.observations.ObservationsUiState
import com.wildlife.feasibility.ui.screens.observations.PublicObservationUi
import com.wildlife.feasibility.ui.screens.observations.RegionalContextUi
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the observation ledger across the states that change its shape.
 *
 * Output lands in `app/build/screenshots/observations-*.png`.
 *
 * Photo URLs are deliberately absent: Coil cannot reach the network under Robolectric, so
 * every plate renders its empty-specimen fallback — which is the branch worth reviewing
 * anyway, since a capture without a stored thumbnail is common and a public record without
 * a photograph is what makes the comparison plate hardest to read.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ObservationsScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
    fun phoneViewport() = captureAll(suffix = "")

    /** Tall viewport, so every pile of the ledger is visible in one image. */
    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h1800dp-xhdpi")
    fun fullPage() = captureAll(suffix = "-full")

    /** The largest supported type, where the two-photograph plate is under most pressure. */
    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h1800dp-xhdpi")
    fun largeFont() {
        composeRule.setContent {
            StillTheme {
                ScaledFont(1.5f) { Ledger(ObservationsScenarios.busy) }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("build/screenshots/observations-busy-large-font.png")
    }

    private fun captureAll(suffix: String) {
        var scenario by mutableStateOf(ObservationsScenarios.all.first())
        composeRule.setContent {
            StillTheme { Ledger(scenario.second) }
        }
        ObservationsScenarios.all.forEach { next ->
            scenario = next
            composeRule.waitForIdle()
            composeRule.onRoot()
                .captureRoboImage("build/screenshots/observations-${next.first}$suffix.png")
        }
    }
}

/**
 * The screen with every callback inert. The captures are about what the ledger *says*, so
 * nothing here needs to do anything when pressed.
 */
@Composable
private fun Ledger(state: ObservationsUiState) {
    ObservationsScreen(
        state = state,
        onBack = null,
        onSync = {},
        onSubmitted = {},
        onNotSubmitted = {},
        onConfirm = {},
        onKeepAutomatic = {},
        onUndoAutomatic = {},
        onOpenObservation = {},
        onOpenINaturalist = {},
        onDeleteLocal = {},
    )
}

/**
 * Renders at a larger type scale without changing the layout density.
 *
 * Robolectric's qualifiers cannot express a font scale, so it is provided directly. The
 * device density is left where the qualifier put it: scaling both would shrink the whole
 * page rather than testing what large type does to it.
 */
@Composable
private fun ScaledFont(scale: Float, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, scale),
        content = content,
    )
}

/** Saturday 23 August 2025, so nothing in the ledger drifts with the calendar. */
private const val FIXED_MS = 1_755_907_200_000L

private object ObservationsScenarios {

    private val account = VerifiedAccount(1L, "ranger", FIXED_MS)

    private val mediterranean = RegionalContextUi("Region · Mediterranean Europe", true)

    /** Wildlife linked this one itself; the user has not answered yet. */
    private val autoFiled = ManagedObservationUi(
        groupId = "auto",
        state = MarkerState.CONFIRMED,
        label = "Common kingfisher",
        capturedAtMs = FIXED_MS - 3 * 60 * 60 * 1000,
        location = null,
        place = "Delta de l'Ebre",
        researchGrade = true,
        photos = listOf(ManagedPhotoUi("p0", "")),
        proposals = emptyList(),
        matchedObservationUuid = "kingfisher",
        regionalContext = mediterranean,
        autoFiled = true,
    )

    /** Two records fit it, so neither was filed and both are offered. */
    private val contested = ManagedObservationUi(
        groupId = "contested",
        state = MarkerState.PENDING,
        label = null,
        capturedAtMs = FIXED_MS - 5 * 60 * 60 * 1000,
        location = null,
        place = "Montseny",
        researchGrade = false,
        photos = listOf(ManagedPhotoUi("p1", ""), ManagedPhotoUi("p2", "")),
        proposals = listOf(
            proposal(
                uuid = "jay", label = "Eurasian jay", confidence = 0.74,
                band = MatchBand.LIKELY, minutes = 7, distanceKm = 0.4,
                reasons = listOf(MatchReason.COMPETING_CANDIDATES),
            ),
            proposal(
                uuid = "magpie", label = "Eurasian magpie", confidence = 0.66,
                band = MatchBand.POSSIBLE, minutes = 22, distanceKm = null,
                obscured = true,
                reasons = listOf(
                    MatchReason.LOCATION_OBSCURED, MatchReason.COMPETING_CANDIDATES,
                ),
            ),
        ),
        matchedObservationUuid = null,
    )

    /** Handed off to iNaturalist, and Wildlife cannot see whether it was submitted. */
    private val handedOff = ManagedObservationUi(
        groupId = "handoff",
        state = MarkerState.HANDED_OFF,
        label = null,
        capturedAtMs = FIXED_MS - 26 * 60 * 60 * 1000,
        location = null,
        place = "Garrotxa",
        researchGrade = false,
        photos = listOf(ManagedPhotoUi("p3", "")),
        proposals = emptyList(),
        matchedObservationUuid = null,
    )

    private val awaiting = handedOff.copy(
        groupId = "awaiting",
        state = MarkerState.PENDING,
        place = "Cap de Creus",
    )

    private val draft = handedOff.copy(
        groupId = "draft",
        state = MarkerState.CAPTURED,
        place = "Girona",
    )

    private val settled = ManagedObservationUi(
        groupId = "settled",
        state = MarkerState.CONFIRMED,
        label = "European robin",
        capturedAtMs = FIXED_MS - 8 * 24 * 60 * 60 * 1000,
        location = null,
        place = "Girona",
        researchGrade = true,
        photos = listOf(ManagedPhotoUi("p4", "")),
        proposals = emptyList(),
        matchedObservationUuid = "robin",
        regionalContext = mediterranean,
    )

    private val history = listOf(
        PublicObservationUi(
            uuid = "boar", taxonId = 3L, label = "Wild boar",
            observedAtMs = FIXED_MS - 30 * 24 * 60 * 60 * 1000,
            qualityGrade = "research", photoUrl = null, location = null, place = "Collserola",
            regionalContext = mediterranean,
        ),
        PublicObservationUi(
            uuid = "gull", taxonId = 4L, label = "Yellow-legged gull",
            observedAtMs = FIXED_MS - 44 * 24 * 60 * 60 * 1000,
            qualityGrade = "needs_id", photoUrl = null, location = null, place = "Barcelona",
            regionalContext = RegionalContextUi("Not counted · Marine worldwide", false),
        ),
    )

    /** The state the redesign exists for: something filed, something contested, a backlog. */
    val busy = ObservationsUiState(
        account = account,
        message = "Filed 1 automatically. 2 still need your eye.",
        managed = listOf(autoFiled, contested, handedOff, awaiting, draft, settled),
        publicObservations = history,
    )

    val all = listOf(
        // Never linked: the ledger explains itself and asks for nothing.
        "1-unlinked" to ObservationsUiState(),
        // Linked with nothing outstanding — only history.
        "2-history-only" to ObservationsUiState(
            account = account,
            managed = listOf(settled),
            publicObservations = history,
        ),
        // One automatic match, waiting to be checked.
        "3-auto-filed" to ObservationsUiState(
            account = account,
            message = "One sighting matched and filed. Undo it below if it is not yours.",
            managed = listOf(autoFiled, settled),
            publicObservations = history,
        ),
        "4-busy" to busy,
        // Checking iNaturalist, with the sync control disabled.
        "5-syncing" to busy.copy(syncing = true, message = "Checking the verified iNaturalist account…"),
    )

    private fun proposal(
        uuid: String,
        label: String,
        confidence: Double,
        band: MatchBand,
        minutes: Long,
        distanceKm: Double?,
        obscured: Boolean = false,
        reasons: List<MatchReason>,
    ) = MatchProposalUi(
        proposal = MatchProposal(
            markerId = "p1",
            candidate = ObservationCandidate(
                uuid = uuid,
                observedAtMs = FIXED_MS - 5 * 60 * 60 * 1000 + minutes * 60_000,
                latitude = null,
                longitude = null,
                obscured = obscured,
                label = label,
            ),
            confidence = confidence,
            band = band,
            timeDeltaMinutes = minutes,
            distanceKm = distanceKm,
            reasons = reasons,
        ),
        observedAtMs = FIXED_MS - 5 * 60 * 60 * 1000 + minutes * 60_000,
        label = label,
        photoUrl = null,
        place = "Montseny",
        obscured = obscured,
        distanceKm = distanceKm,
        timeDeltaMinutes = minutes,
        confidence = confidence,
        band = band,
        reasons = reasons,
    )
}
