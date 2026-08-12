package com.wildlife.feasibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class INaturalistClientTest {
    private val client = INaturalistClient()

    @Test
    fun resolvesOnlyAnExactUsernameToImmutableId() {
        val users = listOf(
            INaturalistUser(10, "wildlife-fan"),
            INaturalistUser(42, "WildLife"),
        )

        val user = client.exactUser(users, "wildlife")

        assertEquals(42L, user?.id)
        assertEquals("WildLife", user?.login)
    }

    @Test
    fun rejectsPartialUsernameMatch() {
        assertNull(client.exactUser(listOf(INaturalistUser(10, "wildlife-fan")), "wildlife"))
    }
}
