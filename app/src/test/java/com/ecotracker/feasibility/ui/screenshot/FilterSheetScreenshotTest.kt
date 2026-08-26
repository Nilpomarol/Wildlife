package com.wildlife.feasibility.ui.screenshot

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.wildlife.feasibility.ui.screens.collection.CollectionFilterSheet
import com.wildlife.feasibility.ui.screens.collection.CollectionScreen
import com.wildlife.feasibility.ui.screens.collection.CollectionFilters
import com.wildlife.feasibility.ui.screens.collection.SpeciesGroup
import com.wildlife.feasibility.ui.screens.collection.StandingFilter
import com.wildlife.feasibility.ui.screens.collection.StatusFilter
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The filter sheet, open, in the two states worth reviewing: nothing selected, and the
 * combination the split exists to enable ("missing Essentials").
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class FilterSheetScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val groups = listOf(
        SpeciesGroup.MAMMALS,
        SpeciesGroup.BIRDS,
        SpeciesGroup.REPTILES,
        SpeciesGroup.AMPHIBIANS,
    )

    // The sheet renders in its own window, so a root-scoped capture finds two roots;
    // a screen capture takes every window, which is what a sheet review needs anyway.
    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun sheet() {
        var filters by mutableStateOf(CollectionFilters.None)
        composeRule.setContent {
            StillTheme {
                CollectionFilterSheet(
                    filters = filters,
                    onFilters = { filters = it },
                    presentGroups = groups,
                    matchCount = 12,
                    onDismiss = {},
                )
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage("build/screenshots/filter-sheet-clear.png")

        // The query the old flat enum could not express, with the reset action now shown.
        filters = CollectionFilters(
            status = StatusFilter.MISSING,
            standing = StandingFilter.ESSENTIALS,
        )
        composeRule.waitForIdle()
        captureScreenRoboImage("build/screenshots/filter-sheet-active.png")
    }
}

/**
 * The sort menu open, where the three options' leading marks sit side by side.
 *
 * Rarity used to render the matted `EncounterTrace` disc at 30dp while A–Z and Recent used
 * 18dp glyphs, so one option in the menu was visibly larger and heavier than its peers.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class SortMenuScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun sortMenu() {
        composeRule.setContent {
            StillTheme {
                CollectionScreen(
                    state = CollectionScenarios.all[4].state,
                    onBack = null,
                    onOpenSpecies = {},
                    onLinkAccount = {},
                    onRetry = {},
                )
            }
        }
        composeRule.onNodeWithText("A–Z").performClick()
        composeRule.waitForIdle()
        captureScreenRoboImage("build/screenshots/sort-menu.png")
    }
}

/**
 * The row once an axis is set: the pill takes the option's name and colour, and the clear
 * action appears beside the count.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class ActiveFiltersScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun host() {
        composeRule.setContent {
            StillTheme {
                CollectionScreen(
                    state = CollectionScenarios.all[4].state,
                    onBack = null,
                    onOpenSpecies = {},
                    onLinkAccount = {},
                    onRetry = {},
                )
            }
        }
    }

    /** Drives the personal Status selector to Confirmed, the way a user would. */
    private fun selectConfirmed() {
        composeRule.onNodeWithText("Status").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Confirmed").performClick()
        composeRule.waitForIdle()
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun activeRow() {
        host()
        selectConfirmed()
        captureScreenRoboImage("build/screenshots/filters-active-row.png")
    }

    /**
     * Clearing has to be reachable without opening the sheet, and it has to actually
     * reset the axis rather than only hide its own button.
     */
    @Test
    fun clearFiltersResetsEveryAxis() {
        host()
        selectConfirmed()
        composeRule.onNodeWithContentDescription("Clear all filters").performClick()
        composeRule.waitForIdle()

        // The selector is back to its neutral label, and the action has removed itself.
        composeRule.onNodeWithText("Status").assertExists()
        composeRule.onAllNodesWithContentDescription("Clear all filters").assertCountEquals(0)
    }
}
