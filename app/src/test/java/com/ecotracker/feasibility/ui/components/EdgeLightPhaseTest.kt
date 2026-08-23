package com.wildlife.feasibility.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The per-card bead offset has to be stable and well spread.
 *
 * Stable, because it is read during composition: a fresh random would jump on every
 * recomposition and make screenshots differ between runs. Well spread, because collection
 * keys are near-sequential (`taxon:1000`, `taxon:1001`, …) and a raw hash of those differs
 * only in the low bits, which would leave a whole row of cards pulsing nearly in step.
 */
class EdgeLightPhaseTest {

    private val keys = (1000..1011).map { "taxon:$it" }

    @Test
    fun `offset is stable for a key`() {
        keys.forEach { key ->
            assertEquals(speciesBeadPhaseOffset(key), speciesBeadPhaseOffset(key), 0f)
        }
    }

    @Test
    fun `offset stays inside the loop`() {
        keys.forEach { key ->
            val v = speciesBeadPhaseOffset(key)
            assertTrue("$key produced $v", v >= 0f && v < 1f)
        }
    }

    /** Adjacent keys must not land adjacent, or a row of cards moves as one. */
    @Test
    fun `sequential keys spread across the loop`() {
        val offsets = keys.map(::speciesBeadPhaseOffset)
        offsets.zipWithNext().forEach { (a, b) ->
            assertTrue("consecutive keys too close: $a and $b", kotlin.math.abs(a - b) > 0.02f)
        }
        // And the set as a whole should cover the loop rather than clustering.
        assertTrue("offsets clustered: ${offsets.min()}..${offsets.max()}", offsets.max() - offsets.min() > 0.5f)
    }

    @Test
    fun `period varies between species but stays in range`() {
        keys.map(::speciesBeadPeriodMillis).forEach {
            assertTrue("period out of range: $it", it in 3200..5000)
        }
        assertTrue(keys.map(::speciesBeadPeriodMillis).distinct().size > 1)
    }
}
