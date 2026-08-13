package com.wildlife.feasibility.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WildlifeDestinationTest {
    @Test
    fun `bottom destinations have stable unique routes and capture remains central`() {
        val destinations = WildlifeDestination.entries

        assertEquals(destinations.size, destinations.map(WildlifeDestination::route).distinct().size)
        assertEquals(WildlifeDestination.CAPTURE, destinations[destinations.size / 2])
        assertTrue(destinations.all { it.label.isNotBlank() })
    }
}
