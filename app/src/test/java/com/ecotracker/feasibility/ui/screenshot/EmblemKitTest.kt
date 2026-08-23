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

/** Sheet of every drawn emblem, patch and texture, so they can be judged in isolation. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class EmblemKitTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun sheet() {
        val ink = Color(0xFF0E1209)
        val field = Color(0xFF2C4520)
        val moss = Color(0xFF8FB059)
        val brass = Color(0xFFCFA53E)
        val parchment = Color(0xFFEFE7D2)

        composeRule.setContent {
            Column(
                Modifier.fillMaxSize().background(ink).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                WildlifeEmblem(moss, field, parchment, brass, Modifier.size(110.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "tourist", "explorer", "naturalist", "tracker",
                        "field_ranger", "master_ranger", "legendary_ranger",
                    ).forEach {
                        RankPatch(it, brass, field, parchment, Modifier.size(46.dp, 54.dp))
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    StatGlyph(StatMark.TICK, moss, Modifier.size(34.dp))
                    StatGlyph(StatMark.SPARKLE, Color(0xFFA484DC), Modifier.size(34.dp))
                    StatGlyph(StatMark.ROSETTE, brass, Modifier.size(34.dp))
                    StatGlyph(StatMark.PAW, parchment, Modifier.size(34.dp))
                }

                Box(Modifier.fillMaxWidth().height(150.dp)) {
                    LandscapeScene(
                        sky = Color(0xFF16200F),
                        far = Color(0xFF243119),
                        mid = Color(0xFF1B2513),
                        near = Color(0xFF10170B),
                        moon = Color(0xFFE8E0C4),
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                Box(Modifier.fillMaxWidth().height(26.dp)) {
                    TornEdge(Color(0xFF1A2012), Modifier.fillMaxSize())
                }

                Box(Modifier.fillMaxWidth().height(120.dp).background(Color(0xFF1A2012))) {
                    Vignette(Color.Black, Modifier.fillMaxSize(), strength = 0.8f)
                }
            }
        }
        composeRule.onRoot().captureRoboImage("build/screenshots/emblem-kit.png")
    }
}
