package com.wildlife.feasibility.ui.screenshot

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.wildlife.feasibility.MarkerState
import com.wildlife.feasibility.VerifiedAccount
import com.wildlife.feasibility.ui.screens.capture.CaptureObservationUi
import com.wildlife.feasibility.ui.screens.capture.CapturePhotoUi
import com.wildlife.feasibility.ui.screens.capture.CaptureProjection
import com.wildlife.feasibility.ui.screens.capture.CaptureScreen
import com.wildlife.feasibility.ui.screens.capture.CaptureUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Visual coverage for the empty, ready and metadata-repair shapes of one draft. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CaptureScreenshotTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
    fun empty() = capture("empty", CaptureUiState(account = account))

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
    fun ready() = capture("ready", draftState(metadataReady = true, photoCount = 2))

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h1100dp-xhdpi")
    fun metadataRepairLargeFont() {
        composeRule.setContent {
            StillTheme { ScaledFont(1.5f) { Capture(draftState(metadataReady = false, photoCount = 1)) } }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("build/screenshots/capture-metadata-large-font.png")
    }

    private fun capture(name: String, state: CaptureUiState) {
        composeRule.setContent { StillTheme { Capture(state) } }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("build/screenshots/capture-$name.png")
    }

    private companion object {
        const val FIXED_MS = 1_786_550_400_000L
        val account = VerifiedAccount(1L, "ranger", FIXED_MS)

        fun draftState(metadataReady: Boolean, photoCount: Int) = CaptureUiState(
            account = account,
            observations = listOf(
                CaptureObservationUi(
                    groupId = CaptureProjection.DRAFT_GROUP_ID,
                    state = MarkerState.CAPTURED,
                    photos = List(photoCount) { index ->
                        CapturePhotoUi(
                            markerId = "photo-$index",
                            imageUri = "",
                            capturedAtMs = FIXED_MS + index * 60_000L,
                            latitude = if (metadataReady) 41.38 else null,
                            longitude = if (metadataReady) 2.17 else null,
                            capturedAtReliable = metadataReady,
                            locationReliable = metadataReady,
                            selected = true,
                        )
                    },
                    proposals = emptyList(),
                    matchedObservationUuid = null,
                ),
            ),
            statusMessage = if (metadataReady) {
                "$photoCount photos added as one observation."
            } else {
                "Confirm the original date and time before continuing."
            },
        )
    }
}

@Composable
private fun Capture(state: CaptureUiState) {
    CaptureScreen(
        state = state,
        onBack = {}, onTakePhoto = {}, onChoosePhotos = {}, onContinueInINaturalist = {},
        onRemovePhoto = {}, onDiscardDraft = {}, onSaveMetadata = { _, _, _, _ -> },
        onLinkAccount = {}, onDismissReward = {}, onOpenCollection = {},
        onDismissHandoffUnavailable = {}, onOpenINaturalistWeb = {}, onOpenINaturalistStore = {},
    )
}
