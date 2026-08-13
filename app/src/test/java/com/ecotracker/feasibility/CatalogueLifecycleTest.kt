package com.wildlife.feasibility

import java.io.IOException
import java.net.SocketTimeoutException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Test

class CatalogueLifecycleTest {
    @Test
    fun `catalogue revision is stable for the same ordered denominator`() {
        val entries = listOf(10L to "Aves", 20L to "Mammalia")

        assertEquals(catalogueRevision(entries), catalogueRevision(entries))
        assertEquals(catalogueRevision(entries), catalogueRevision(entries.reversed()))
        assertNotEquals(catalogueRevision(entries), catalogueRevision(entries + (30L to "Aves")))
    }

    @Test
    fun `refresh errors are reduced to stable non-sensitive codes`() {
        assertEquals("network_timeout", catalogueRefreshErrorCode(SocketTimeoutException()))
        assertEquals("network_unavailable", catalogueRefreshErrorCode(IOException()))
        assertEquals("IllegalStateException", catalogueRefreshErrorCode(IllegalStateException()))
        assertEquals(
            "rate_limited",
            catalogueRefreshErrorCode(RemoteDataException.HttpFailure("Wikimedia", 429, true)),
        )
        assertEquals(
            "remote_http_403",
            catalogueRefreshErrorCode(RemoteDataException.HttpFailure("iNaturalist", 403, false)),
        )
    }

    @Test
    fun `closer silhouette always wins regardless of candidate order`() {
        val family = silhouette("family")
        val species = silhouette("species")

        assertSame(species, closerSilhouette(family, species))
        assertSame(species, closerSilhouette(species, family))
    }

    private fun silhouette(rank: String) = SilhouetteAsset(
        url = "https://example.test/$rank.png",
        sourceUrl = "https://example.test/source/$rank",
        attribution = "Artist",
        licenceCode = "cc0",
        licenceUrl = "https://example.test/licence",
        taxonName = rank,
        matchRank = rank,
    )
}
