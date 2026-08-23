package com.wildlife.feasibility.ui.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.wildlife.feasibility.ui.components.CollectionSearchBar
import com.wildlife.feasibility.ui.theme.FieldLabelStyle
import com.wildlife.feasibility.ui.theme.WildlifeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The search pill in the three states that differ visually.
 *
 * Focus and content are what change its chrome, and neither shows up in a capture of the
 * Collection screen at rest — so the pill gets its own sheet.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class SearchBarTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun states() {
        var typed by mutableStateOf("")
        composeRule.setContent {
            StillTheme {
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0E1209))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Label("AT REST")
                    CollectionSearchBar(query = "", onQueryChange = {})
                    Label("TYPED")
                    CollectionSearchBar(query = "vulture", onQueryChange = {})
                    Label("FOCUSED")
                    CollectionSearchBar(query = typed, onQueryChange = { typed = it })
                }
            }
        }
        // Only the first and third fields show the placeholder — the typed one has content
        // — so the third field is the second placeholder node.
        composeRule.onAllNodesWithText("Search your collection")[1].performClick()
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("build/screenshots/search-bar.png")
    }

    /**
     * The clear control only exists once there is something to clear. A field that always
     * offers it advertises an action that does nothing most of the time.
     */
    @Test
    fun clearAppearsOnlyWhenTyped() {
        var query by mutableStateOf("")
        composeRule.setContent {
            StillTheme {
                CollectionSearchBar(query = query, onQueryChange = { query = it })
            }
        }
        assertEquals(0, composeRule.clearControlCount())

        composeRule.onNodeWithText("Search your collection").performTextInput("otter")
        composeRule.waitForIdle()
        assertEquals(1, composeRule.clearControlCount())
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = FieldLabelStyle, color = WildlifeTheme.colors.parchmentDim)
}

private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.clearControlCount(): Int =
    onAllNodesWithContentDescription("Clear search").fetchSemanticsNodes().size
