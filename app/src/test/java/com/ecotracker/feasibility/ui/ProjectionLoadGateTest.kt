package com.wildlife.feasibility.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectionLoadGateTest {
    @Test fun `only the newest projection token can publish state`() {
        val gate = ProjectionLoadGate()
        val first = gate.next()
        val second = gate.next()

        assertFalse(gate.isLatest(first))
        assertTrue(gate.isLatest(second))
    }
}
