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
import com.github.takahirom.roborazzi.captureRoboImage
import com.wildlife.feasibility.ProgressionRules
import com.wildlife.feasibility.ui.art.RankBadgeArt
import com.wildlife.feasibility.ui.theme.FieldLabelStyle
import com.wildlife.feasibility.ui.theme.levelAccent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Every progression badge in its level accent, so the ladder can be judged as a set. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class RankBadgeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun ladder() {
        composeRule.setContent {
            StillTheme {
                Column(
                    Modifier.fillMaxSize().background(Color(0xFF0E1209)).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    ProgressionRules.levels.chunked(4).forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            row.forEach { level ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    RankBadgeArt(
                                        levelKey = level.key,
                                        color = levelAccent(level.key),
                                        modifier = Modifier.size(76.dp),
                                    )
                                    Text(
                                        level.displayName.uppercase(),
                                        style = FieldLabelStyle,
                                        color = levelAccent(level.key),
                                    )
                                }
                            }
                        }
                    }
                    // The size they actually appear at in the header.
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ProgressionRules.levels.forEach { level ->
                            RankBadgeArt(
                                levelKey = level.key,
                                color = levelAccent(level.key),
                                modifier = Modifier.size(52.dp),
                            )
                        }
                    }
                }
            }
        }
        composeRule.onRoot().captureRoboImage("build/screenshots/rank-badges.png")
    }
}
