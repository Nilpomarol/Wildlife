package com.wildlife.feasibility.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponsiveGridTest {
    /**
     * Phones show three plates across. A guide is browsed by scanning, so seeing more of
     * the region at once matters more than caption width — the enlarged rarity and
     * standing marks do the identifying, and the caption carries only the name.
     *
     * This reverses the earlier two-column decision, which was taken when cards still
     * carried a taxon chip and a "not yet recorded" line under the name.
     */
    @Test
    fun `phones use three columns`() {
        assertEquals(3, responsiveSpeciesGridColumns(390f, 1f))
        assertEquals(3, responsiveSpeciesGridColumns(600f, 1f))
    }

    @Test
    fun `tablet widths use four columns`() {
        assertEquals(4, responsiveSpeciesGridColumns(700f, 1f))
    }

    /** Text that grows costs a column rather than overrunning the plates. */
    @Test
    fun `large fonts reduce card density`() {
        assertEquals(3, responsiveSpeciesGridColumns(390f, 1.4f))
        assertEquals(2, responsiveSpeciesGridColumns(390f, 1.5f))
        assertEquals(1, responsiveSpeciesGridColumns(390f, 1.8f))
    }

    @Test
    fun `narrow layouts never force dense cards`() {
        assertEquals(2, responsiveSpeciesGridColumns(320f, 1f))
        assertEquals(1, responsiveSpeciesGridColumns(270f, 1f))
    }

    /**
     * Multi-column cards are square. The caption sits over the plate rather than under it,
     * so there is no fixed block of text to make room for and the tile can be the
     * artwork's own shape — which is why this no longer scales with column count.
     */
    @Test
    fun `multi column cards are square`() {
        assertEquals(1f, speciesCardAspectRatio(2), 0f)
        assertEquals(1f, speciesCardAspectRatio(3), 0f)
        assertEquals(1f, speciesCardAspectRatio(4), 0f)
    }

    /** A full-width square would be an enormous tile, so one column stays taller. */
    @Test
    fun `single column is taller than wide`() {
        assertTrue(speciesCardAspectRatio(1) > 1f)
    }
}
