package com.wildlife.feasibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

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

    @Test
    fun `distinguishes a temporary Wikimedia failure from no eligible image`() {
        val failed = resolveMedia("https://example.test/wiki") {
            error("temporary network failure")
        }
        val unavailable = resolveMedia<String, CuratedReferencePhoto>(
            "https://example.test/wiki",
        ) { null }

        assertNull(failed.asset)
        assertTrue(failed.hadTemporaryFailure)
        assertNull(unavailable.asset)
        assertFalse(unavailable.hadFailure)
    }

    @Test
    fun `typed media result distinguishes permanent policy failures`() {
        val failed = resolveMedia<String, CuratedReferencePhoto>("input") {
            throw RemoteDataException.HttpFailure("Wikimedia", 403, retryable = false)
        }

        assertTrue(failed.hadFailure)
        assertFalse(failed.hadTemporaryFailure)
    }

    @Test
    fun `scientific fallback prefers the matching iNaturalist taxon id`() {
        val wrong = candidate("Erithacus rubecula", "999")
        val correct = candidate("Erithacus rubecula", "123")

        assertEquals(
            correct.entity,
            client.verifiedTaxonEntity(listOf(wrong, correct), "Erithacus rubecula", 123),
        )
    }

    @Test
    fun `scientific fallback rejects an ambiguous name without a matching id`() {
        val candidates = listOf(
            candidate("Aus bus", "1"),
            candidate("Aus bus", "2"),
        )

        assertNull(client.verifiedTaxonEntity(candidates, "Aus bus", null))
    }

    private fun candidate(scientificName: String, taxonId: String) = WikidataTaxonCandidate(
        entity = JSONObject(),
        scientificName = scientificName,
        iNaturalistTaxonId = taxonId,
    )
}
