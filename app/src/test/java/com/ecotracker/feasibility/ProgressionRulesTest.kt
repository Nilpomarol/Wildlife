package com.wildlife.feasibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressionRulesTest {
    @Test
    fun levelThresholdsUseLifetimeXpBoundaries() {
        assertEquals("tourist", ProgressionProjection.project(499).currentLevel.key)
        assertEquals("explorer", ProgressionProjection.project(500).currentLevel.key)
        assertEquals("naturalist", ProgressionProjection.project(2_500).currentLevel.key)
        assertEquals("legendary_ranger", ProgressionProjection.project(100_000).currentLevel.key)
    }

    @Test
    fun progressAndRemainingXpUseTheCurrentThresholdSpan() {
        val state = ProgressionProjection.project(1_500)

        assertEquals("explorer", state.currentLevel.key)
        assertEquals("naturalist", state.nextLevel?.key)
        assertEquals(1_000, state.xpToNextLevel)
        assertEquals(0.5f, state.progressFraction, 0.0001f)
    }

    @Test
    fun highestLevelHasNoFabricatedNextTarget() {
        val state = ProgressionProjection.project(150_000)

        assertEquals("legendary_ranger", state.currentLevel.key)
        assertNull(state.nextLevel)
        assertNull(state.xpToNextLevel)
        assertEquals(1f, state.progressFraction)
    }

    @Test
    fun selectedTitleMustAlreadyBeEarned() {
        val selected = ProgressionProjection.project(2_500, selectedLevelKey = "explorer")
        val unavailable = ProgressionProjection.project(500, selectedLevelKey = "naturalist")

        assertEquals("explorer", selected.selectedTitle.key)
        assertEquals("explorer", unavailable.selectedTitle.key)
    }

    @Test
    fun previouslyEarnedTitleRemainsAvailableAfterThresholdsRise() {
        val state = ProgressionProjection.project(
            totalXp = 500,
            selectedLevelKey = "naturalist",
            highestLevelKey = "naturalist",
        )

        assertEquals("explorer", state.currentLevel.key)
        assertEquals("naturalist", state.selectedTitle.key)
        assertEquals(listOf("tourist", "explorer", "naturalist"), state.earnedLevels.map(ProgressionLevel::key))
    }

    @Test
    fun highestLevelNeverMovesBackwards() {
        assertEquals(
            "naturalist",
            HighestLevelProjection.highestLevelKey(
                currentLevelKey = "explorer",
                previouslyReachedKey = "naturalist",
                levels = ProgressionRules.levels,
            ),
        )
    }

    @Test
    fun repeatObservationScheduleStopsAfterThreeRewards() {
        assertEquals(10, ProgressionRules.confirmedObservationXp(0))
        assertEquals(5, ProgressionRules.confirmedObservationXp(1))
        assertEquals(5, ProgressionRules.confirmedObservationXp(2))
        assertEquals(0, ProgressionRules.confirmedObservationXp(3))
        assertEquals(0, ProgressionRules.confirmedObservationXp(20))
    }

    @Test
    fun recentEventsArePositiveNewestFirstAndLimited() {
        val events = listOf(
            event("older", 10, 100),
            event("zero", 0, 300),
            event("newer", 500, 200),
        )

        val state = ProgressionProjection.project(510, events, recentLimit = 1)

        assertEquals(listOf("newer"), state.recentEvents.map(XpEventRecord::eventKey))
    }

    private fun event(key: String, points: Int, createdAtMs: Long) = XpEventRecord(
        eventKey = key,
        type = XpEventType.LEGACY,
        observationUuid = null,
        subjectTaxonId = null,
        label = null,
        points = points,
        createdAtMs = createdAtMs,
    )
}
