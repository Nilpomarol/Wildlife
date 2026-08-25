package com.wildlife.feasibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
            "cc-by",
            client.licenceCode("https://creativecommons.org/licenses/by/4.0/"),
        )
        assertNull(client.licenceCode("https://creativecommons.org/licenses/by-sa/4.0/"))
    }

    @Test
    fun selectsCompatibleAlternativeWhenFirstImageIsUnsupported() {
        val images = listOf(
            SilhouetteImageCandidate(
                uuid = "unsupported",
                attribution = null,
                licenceUrl = "https://creativecommons.org/licenses/by-sa/4.0/",
                rasterUrls = listOf("https://example.test/unsupported.png"),
            ),
            SilhouetteImageCandidate(
                uuid = "compatible",
                attribution = "Open artist",
                licenceUrl = "https://creativecommons.org/publicdomain/zero/1.0/",
                rasterUrls = listOf("https://example.test/compatible.png"),
            ),
        )

        val selected = client.firstCompatibleSilhouette(images, "Apis", "genus")

        assertEquals("https://example.test/compatible.png", selected?.url)
        assertEquals("https://www.phylopic.org/images/compatible", selected?.sourceUrl)
        assertEquals("Apis", selected?.taxonName)
        assertEquals("genus", selected?.matchRank)
    }

    @Test
    fun recordsTransientFailureEvenWhenBroaderCandidateSucceeds() {
        val fallback = SilhouetteAsset(
            url = "https://example.test/family.png",
            sourceUrl = "https://example.test/source",
            attribution = "Artist",
            licenceCode = "cc0",
            licenceUrl = "https://example.test/licence",
            taxonName = "Muscicapidae",
            matchRank = "family",
        )
        val result = resolveFirstMedia(
            listOf("Erithacus rubecula" to "species", "Muscicapidae" to "family"),
        ) { (_, rank) ->
            if (rank == "species") error("Temporary network failure") else fallback
        }

        assertSame(fallback, result.asset)
        assertTrue(result.hadTemporaryFailure)
    }

    @Test
    fun `permanent earlier failure does not keep a completed fallback retryable`() {
        val fallback = SilhouetteAsset(
            url = "https://example.test/family.png",
            sourceUrl = "https://example.test/source",
            attribution = "Artist",
            licenceCode = "cc0",
            licenceUrl = "https://example.test/licence",
            taxonName = "Muscicapidae",
            matchRank = "family",
        )
        val result = resolveFirstMedia(
            listOf("Erithacus rubecula" to "species", "Muscicapidae" to "family"),
        ) { (_, rank) ->
            if (rank == "species") {
                throw RemoteDataException.HttpFailure("PhyloPic", 403, retryable = false)
            }
            fallback
        }

        assertSame(fallback, result.asset)
        assertTrue(result.hadFailure)
        assertFalse(result.hadTemporaryFailure)
    }
}
