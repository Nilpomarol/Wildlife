package com.wildlife.feasibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WikimediaCommonsClientTest {
    private val client = WikimediaCommonsClient()

    @Test
    fun `accepts featured and quality assessments only`() {
        assertEquals(
            CuratedPhotoQuality.FEATURED,
            client.qualityAssessment("featured|quality"),
        )
        assertEquals(CuratedPhotoQuality.QUALITY, client.qualityAssessment("quality"))
        assertNull(client.qualityAssessment("valued"))
        assertNull(client.qualityAssessment(null))
    }

    @Test
    fun `normalizes only reusable Wikimedia licences`() {
        assertEquals("cc-by-sa", client.normalizedLicence("CC BY-SA 4.0"))
        assertEquals("cc-by", client.normalizedLicence("CC BY 3.0"))
        assertEquals("cc0", client.normalizedLicence("CC0 1.0"))
        assertEquals("pdm", client.normalizedLicence("Public domain"))
        assertNull(client.normalizedLicence("GFDL"))
        assertNull(client.normalizedLicence("CC BY-NC 4.0"))
    }

    @Test
    fun `accepts the literal spaces returned in iNaturalist Wikipedia URLs`() {
        val uri = client.normalizedArticleUri(
            "https://en.wikipedia.org/wiki/Erithacus rubecula",
        )

        assertEquals(
            "/wiki/Erithacus%20rubecula",
            uri?.rawPath,
        )
    }
}
