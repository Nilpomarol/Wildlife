package com.wildlife.feasibility

import java.io.IOException
import java.net.SocketTimeoutException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
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

    @Test
    fun `content pack manifest accepts only versioned sha256 metadata`() {
        val digest = "a".repeat(64)
        val checksum = "b".repeat(64)

        assertEquals(
            RegionalContentPackManifest(digest, checksum),
            RegionalContentPackManifest.fromJson(
                """{"schema_version":1,"source_digest":"$digest","files":{"catalogue.sqlite":"$checksum"}}""",
            ),
        )
        assertNull(
            RegionalContentPackManifest.fromJson(
                """{"schema_version":2,"source_digest":"short","files":{"catalogue.sqlite":"bad"}}""",
            ),
        )
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
