package com.wildlife.feasibility.ui.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.takahirom.roborazzi.captureRoboImage
import com.wildlife.feasibility.ui.components.RegionGlyph
import com.wildlife.feasibility.ui.components.regionVisual
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The bundled region emblems, plus a region with no artwork to confirm the generic
 * fallback still renders — regions ship before their marks do.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class RegionMarkTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun regionMarks() {
        val keys = listOf("mediterranean_europe", "east_africa", "caribbean", "not_yet_drawn")
        composeRule.setContent {
            StillTheme {
                Column(
                    Modifier.fillMaxSize().background(Color(0xFF0E1209)).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    listOf(96, 44, 26).forEach { size ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            keys.forEach { key ->
                                RegionGlyph(regionVisual(key), size = size, regionKey = key)
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        keys.forEach {
                            Text(it, color = Color(0xFFADAB90), fontSize = 9.sp)
                        }
                    }
                }
            }
        }
        composeRule.onRoot().captureRoboImage("build/screenshots/region-marks.png")
    }
}
