package com.wildlife.feasibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RegionalBoundaryAssignerTest {
    private val square = listOf(
        listOf(
            BoundaryPoint(0.0, 0.0), BoundaryPoint(2.0, 0.0),
            BoundaryPoint(2.0, 2.0), BoundaryPoint(0.0, 2.0), BoundaryPoint(0.0, 0.0),
        ),
    )

    @Test fun `single land polygon assigns its region`() {
        val result = assigner(RegionalBoundaryFeature("east_africa", null, listOf(square)))
            .assign("one", 1.0, 1.0, false)
        assertEquals("east_africa", result.regionKey)
        assertEquals(ObservationRegionAssignment.LAND_POLYGON, result.assignment)
    }

    @Test fun `obscured coordinate remains uncertain`() {
        val result = assigner(RegionalBoundaryFeature("east_africa", null, listOf(square)))
            .assign("one", 1.0, 1.0, true)
        assertEquals(ObservationRegionAssignment.REGION_UNCERTAIN, result.assignment)
        assertFalse(result.earnsRegionalProgress)
    }

    @Test fun `overlapping polygons remain uncertain`() {
        val result = assigner(
            RegionalBoundaryFeature("east_africa", null, listOf(square)),
            RegionalBoundaryFeature("southern_africa", null, listOf(square)),
        ).assign("one", 1.0, 1.0, false)
        assertEquals(ObservationRegionAssignment.REGION_UNCERTAIN, result.assignment)
    }

    @Test fun `unmatched coordinate is marine worldwide`() {
        val result = assigner(RegionalBoundaryFeature("east_africa", null, listOf(square)))
            .assign("one", 20.0, 20.0, false)
        assertEquals(ObservationRegionAssignment.MARINE_WORLDWIDE, result.assignment)
    }

    private fun assigner(vararg features: RegionalBoundaryFeature) =
        RegionalBoundaryAssigner("regional-boundaries-v1", features.toList())
}
