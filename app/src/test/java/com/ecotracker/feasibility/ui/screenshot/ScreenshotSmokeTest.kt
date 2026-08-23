package com.wildlife.feasibility.ui.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Proves the JVM screenshot pipeline renders real pixels, including the bundled
 * display fonts, so the redesign can be reviewed without a device.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class ScreenshotSmokeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersThemedText() {
        composeRule.setContent {
            StillTheme {
                Surface(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Wildlife",
                            style = MaterialTheme.typography.displayLarge,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                }
            }
        }
        composeRule.onRoot().captureRoboImage("build/screenshots/smoke.png")
    }
}
