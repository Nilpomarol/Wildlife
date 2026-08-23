package com.wildlife.feasibility.ui.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

/** Confirms the ornament kit renders before it is used to build a screen. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class NatureKitTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun ornaments() {
        composeRule.setContent {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xFF10130C))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("birds", "mammals", "reptiles", "amphibians", "fish").forEach {
                        TaxonSilhouette(it, Color(0xFF89A857), Modifier.size(64.dp))
                    }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .background(Color(0xFF1A1F13)),
                ) {
                    ContourField(Color(0xFF2C3620), Modifier.fillMaxSize())
                }
                Box(Modifier.fillMaxWidth().height(70.dp)) {
                    PineRidge(Color(0xFF2E4021), Modifier.fillMaxSize())
                }
                Sprig(Color(0xFFCBA23C), Modifier.fillMaxWidth().height(36.dp))
            }
        }
        composeRule.onRoot().captureRoboImage("build/screenshots/nature-kit.png")
    }
}
