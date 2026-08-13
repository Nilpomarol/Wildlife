package com.wildlife.feasibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhyloPicClientTest {
    private val client = PhyloPicClient()

    @Test
    fun acceptsOnlySupportedSilhouetteLicences() {
        assertEquals(
            "pdm",
            client.licenceCode("https://creativecommons.org/publicdomain/mark/1.0/"),
        )
        assertEquals(
            "cc0",
            client.licenceCode("https://creativecommons.org/publicdomain/zero/1.0/"),
        )
        assertEquals(
            "cc-by-4.0",
            client.licenceCode("https://creativecommons.org/licenses/by/4.0/"),
        )
        assertNull(client.licenceCode("https://creativecommons.org/licenses/by-sa/4.0/"))
    }
}
