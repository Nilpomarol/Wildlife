package com.wildlife.feasibility.ui.screenshot

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.wildlife.feasibility.ui.navigation.WildlifeDestination
import com.wildlife.feasibility.ui.screens.collection.CollectionScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders each navigation study under the Collection page it has to live with. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class NavSketchScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun sketches() {
        val sketches: List<Pair<String, @Composable () -> Unit>> = listOf(
            "a-thumb-index" to { NavSketchThumbIndex(WildlifeDestination.COLLECTION) },
            "b-parchment-strip" to { NavSketchParchmentStrip(WildlifeDestination.COLLECTION) },
            "c-field-desk" to { NavSketchFieldDesk(WildlifeDestination.COLLECTION) },
            "d-stitched-binding" to { NavSketchStitchedBinding(WildlifeDestination.COLLECTION) },
        )

        // One `setContent` for the whole test; the bar under the page is swapped by state.
        var current by mutableStateOf(0)
        composeRule.setContent {
            StillTheme {
                CollectionScreen(
                    state = SampleCollection.state,
                    onBack = null,
                    onOpenSpecies = {},
                    onLinkAccount = {},
                    onRetry = {},
                    bottomBar = { sketches[current].second() },
                )
            }
        }

        sketches.forEachIndexed { index, (name, _) ->
            current = index
            composeRule.waitForIdle()
            composeRule.onRoot().captureRoboImage("build/screenshots/navsketch-$name.png")
        }
    }
}
