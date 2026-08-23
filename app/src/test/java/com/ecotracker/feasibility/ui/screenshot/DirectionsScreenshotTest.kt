package com.wildlife.feasibility.ui.screenshot

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders the redesign candidates so an art direction can be chosen from real pixels. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class DirectionsScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun directionA() {
        composeRule.setContent { DirectionAScreen() }
        composeRule.onRoot().captureRoboImage("build/screenshots/direction-a.png")
    }

    @Test
    fun directionB() {
        composeRule.setContent { DirectionBScreen() }
        composeRule.onRoot().captureRoboImage("build/screenshots/direction-b.png")
    }

    @Test
    fun directionD() {
        composeRule.setContent { DirectionDScreen() }
        composeRule.onRoot().captureRoboImage("build/screenshots/direction-d.png")
    }

    /** Tall viewport so the whole page, including unfound species, is visible at once. */
    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h1700dp-xhdpi")
    fun directionDFullPage() {
        composeRule.setContent { DirectionDScreen() }
        composeRule.onRoot().captureRoboImage("build/screenshots/direction-d-full.png")
    }

    @Test
    fun directionC() {
        composeRule.setContent { DirectionCScreen() }
        composeRule.onRoot().captureRoboImage("build/screenshots/direction-c.png")
    }
}
