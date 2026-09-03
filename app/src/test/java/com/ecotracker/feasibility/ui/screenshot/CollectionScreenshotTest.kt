package com.wildlife.feasibility.ui.screenshot

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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class CollectionScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun currentCollection() {
        composeRule.setContent {
            StillTheme {
                CollectionScreen(
                    state = SampleCollection.state,
                    onBack = null,
                    onOpenSpecies = {},
                    onLinkAccount = {},
                    onRetry = {},
                )
            }
        }
        composeRule.onRoot().captureRoboImage("build/screenshots/collection-before.png")
    }

    /**
     * The largest supported type, where the record head is under most pressure: four
     * readouts cannot share one line at this scale, so this is the capture that proves
     * they wrap rather than clip.
     */
    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h1500dp-xhdpi")
    fun largeFont() {
        composeRule.setContent {
            StillTheme {
                ScaledFont(1.5f) {
                    CollectionScreen(
                        state = SampleCollection.state,
                        onBack = null,
                        onOpenSpecies = {},
                        onLinkAccount = {},
                        onRetry = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("build/screenshots/collection-large-font.png")
    }
}
