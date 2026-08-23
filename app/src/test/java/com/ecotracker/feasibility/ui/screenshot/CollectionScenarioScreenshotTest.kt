package com.wildlife.feasibility.ui.screenshot

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.wildlife.feasibility.ui.screens.collection.CollectionScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders every scenario in [CollectionScenarios] so the design can be reviewed across
 * the whole progression arc without a device and without grinding to each state.
 *
 * Output lands in `app/build/screenshots/state-*.png`, numbered so they sort in order.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CollectionScenarioScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** Phone viewport: what the screen actually looks like in the hand. */
    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
    fun phoneViewport() {
        captureAll(suffix = "")
    }

    /** Tall viewport, so the header, quests and the whole grid are visible at once. */
    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h1500dp-xhdpi")
    fun fullPage() {
        captureAll(suffix = "-full")
    }

    private fun captureAll(suffix: String) {
        // setContent may only be called once per rule, so the host is driven by mutable
        // state and each scenario is swapped in and captured in turn.
        var current by mutableStateOf(CollectionScenarios.all.first().state)
        composeRule.setContent {
            StillTheme {
                CollectionScreen(
                    state = current,
                    onBack = null,
                    onOpenSpecies = {},
                    onLinkAccount = {},
                    onRetry = {},
                )
            }
        }
        CollectionScenarios.all.forEach { scenario ->
            current = scenario.state
            composeRule.waitForIdle()
            composeRule.onRoot()
                .captureRoboImage("build/screenshots/state-${scenario.key}$suffix.png")
        }
    }
}
