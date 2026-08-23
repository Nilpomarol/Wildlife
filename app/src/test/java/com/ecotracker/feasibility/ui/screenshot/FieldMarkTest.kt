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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The project's existing rarity and regional field marks, rendered at usable sizes. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class FieldMarkTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun marks() {
        val marks = listOf(
            "rarity_uncommon" to Color(0xFF5FB6AA),
            "rarity_rare" to Color(0xFFA484DC),
            "rarity_very_rare" to Color(0xFFCFA53E),
            "regional_essential" to Color(0xFF8FB059),
            "regional_icon" to Color(0xFFEFE7D2),
            "regional_legend" to Color(0xFFCFA53E),
        )
        composeRule.setContent {
            Column(
                Modifier.fillMaxSize().background(Color(0xFF0E1209)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                listOf(56.dp, 24.dp, 15.dp).forEach { size ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        marks.forEach { (name, tint) ->
                            FieldMark(name, tint, Modifier.size(size))
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    marks.forEach { (name, _) ->
                        Text(
                            name.removePrefix("rarity_").removePrefix("regional_"),
                            color = Color(0xFFADAB90),
                            fontSize = 9.sp,
                        )
                    }
                }
            }
        }
        composeRule.onRoot().captureRoboImage("build/screenshots/field-marks.png")
    }
}
