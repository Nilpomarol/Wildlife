package com.wildlife.feasibility.ui.screenshot

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.wildlife.feasibility.ui.navigation.WildlifeBottomBar
import com.wildlife.feasibility.ui.navigation.WildlifeDestination
import com.wildlife.feasibility.ui.screens.collection.CollectionScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The navigation bar in place, under the screen it was designed against.
 *
 * Captured over Collection rather than in isolation because the bar's ground is
 * semi-transparent: whether it sits on the page correctly can only be judged with the
 * page behind it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class NavigationBarScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun barInEverySelectedState() {
        // `setContent` may only be called once per test, so the selection is driven from
        // state and swapped between captures.
        var selected by mutableStateOf(WildlifeDestination.HOME)
        composeRule.setContent {
            StillTheme {
                CollectionScreen(
                    state = SampleCollection.state,
                    onBack = null,
                    onOpenSpecies = {},
                    onLinkAccount = {},
                    onRetry = {},
                    bottomBar = { WildlifeBottomBar(selected = selected) { selected = it } },
                )
            }
        }

        listOf(
            WildlifeDestination.HOME,
            WildlifeDestination.COLLECTION,
            WildlifeDestination.EXPLORE,
            WildlifeDestination.PROFILE,
        ).forEach { destination ->
            selected = destination
            composeRule.waitForIdle()
            composeRule.onRoot()
                .captureRoboImage("build/screenshots/nav-${destination.route}.png")
        }
    }
}
