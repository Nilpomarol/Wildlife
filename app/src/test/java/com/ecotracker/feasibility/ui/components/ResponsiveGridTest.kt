package com.wildlife.feasibility.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class ResponsiveGridTest {
    @Test
    fun `normal phones use three columns`() {
        assertEquals(3, responsiveSpeciesGridColumns(390f, 1f))
    }

    @Test
    fun `large fonts reduce card density`() {
        assertEquals(2, responsiveSpeciesGridColumns(390f, 1.4f))
        assertEquals(1, responsiveSpeciesGridColumns(390f, 1.8f))
    }

    @Test
    fun `narrow layouts never force dense cards`() {
        assertEquals(2, responsiveSpeciesGridColumns(320f, 1f))
        assertEquals(1, responsiveSpeciesGridColumns(270f, 1f))
    }
}
