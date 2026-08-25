package com.wildlife.feasibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CatalogueLifecycleTest {
    @Test
    fun `closer silhouette always wins regardless of candidate order`() {
        val family = silhouette("family")
        val species = silhouette("species")

        assertSame(species, closerSilhouette(family, species))
        assertSame(species, closerSilhouette(species, family))
    }

    @Test
    fun `published hierarchy retains specific then family regardless of manifest order`() {
        val family = publishedSilhouette("family", "family")
        val species = publishedSilhouette("species", "species")
        val content = publishedContent(listOf(family, species))

        assertEquals(listOf(species, family), content.silhouetteHierarchy())
        assertSame(species, content.preferredSilhouette())
    }

    @Test
    fun `local projection uses family while specific downloads and upgrades when available`() {
        val family = publishedSilhouette("family", "family")
        val species = publishedSilhouette("species", "species")
        val content = publishedContent(listOf(family, species))

        val familyOnly = content.toLocalTaxonDetails(FakeMediaRepository(mapOf(
            family.assetId to "file:///family.png",
        )))
        assertEquals("file:///family.png", familyOnly.silhouetteUrl)
        assertEquals("family", familyOnly.silhouetteMatchRank)
        assertNull(familyOnly.silhouetteFallbackUrl)

        val complete = content.toLocalTaxonDetails(FakeMediaRepository(mapOf(
            family.assetId to "file:///family.png",
            species.assetId to "file:///species.png",
        )))
        assertEquals("file:///species.png", complete.silhouetteUrl)
        assertEquals("species", complete.silhouetteMatchRank)
        assertEquals("file:///family.png", complete.silhouetteFallbackUrl)
        assertEquals("family", complete.silhouetteFallbackMatchRank)
    }

    @Test
    fun `content pack manifest accepts only versioned sha256 metadata`() {
        val digest = "a".repeat(64)
        val databaseChecksum = "b".repeat(64)
        val reportChecksum = "c".repeat(64)

        assertEquals(
            RegionalContentPackManifest(
                "generation-1", 1, 4, digest, "release", "2026-08-24T00:00:00Z", 1,
                databaseChecksum, reportChecksum,
            ),
            RegionalContentPackManifest.fromJson(
                """{"schema_version":2,"content_schema_version":4,"generation_id":"generation-1","generation_sequence":1,"generated_at":"2026-08-24T00:00:00Z","minimum_app_version":1,"release_status":"release","source_digest":"$digest","files":{"catalogue.sqlite":"$databaseChecksum","catalogue-report.json":"$reportChecksum"}}""",
            ),
        )
        assertNull(
            RegionalContentPackManifest.fromJson(
                """{"schema_version":2,"source_digest":"short","files":{"catalogue.sqlite":"bad"}}""",
            ),
        )
    }

    @Test
    fun `install policy rejects incompatible draft conflicting and older generations`() {
        val current = generation("current", 2, "a".repeat(64), "2026-08-24T00:00:00Z")
        assertEquals("draft_not_allowed", PublishedContentInstallPolicy.rejectionCode(
            current.copy(releaseStatus = "draft"), null, 1, false,
        ))
        assertEquals("incompatible_app", PublishedContentInstallPolicy.rejectionCode(
            current.copy(minimumAppVersion = 2), null, 1, true,
        ))
        assertEquals("generation_conflict", PublishedContentInstallPolicy.rejectionCode(
            current.copy(sourceDigest = "b".repeat(64)), current, 1, true,
        ))
        assertEquals("downgrade", PublishedContentInstallPolicy.rejectionCode(
            generation("older", 1, "b".repeat(64), "2026-08-25T00:00:00Z"), current, 1, true,
        ))
        assertNull(PublishedContentInstallPolicy.rejectionCode(
            generation("newer", 3, "b".repeat(64), "2026-08-23T00:00:00Z"), current, 1, true,
        ))
    }

    private fun generation(id: String, sequence: Long, digest: String, generatedAt: String) = PublishedContentGeneration(
        id, sequence, 4, digest, "release", generatedAt, 1,
    )

    private fun silhouette(rank: String) = SilhouetteAsset(
        url = "https://example.test/$rank.png",
        sourceUrl = "https://example.test/source/$rank",
        attribution = "Artist",
        licenceCode = "cc0",
        licenceUrl = "https://example.test/licence",
        taxonName = rank,
        matchRank = rank,
    )

    private fun publishedSilhouette(id: String, rank: String) = PublishedMediaAsset(
        assetId = id,
        mediaType = "silhouette",
        provider = "phylopic",
        providerAssetId = id,
        sourceUrl = "https://example.test/source/$id",
        creator = "Artist",
        licenceCode = "cc0",
        licenceUrl = "https://example.test/licence",
        matchRank = rank,
        matchedTaxonName = id,
        variants = listOf(PublishedMediaVariant(
            variant = "detail",
            directUrl = "https://example.test/$id.png",
            mimeType = "image/png",
            width = 1024,
            height = 768,
            expectedBytes = 1234,
            contentSha256 = "a".repeat(64),
        )),
    )

    private fun publishedContent(media: List<PublishedMediaAsset>) = PublishedTaxonContent(
        taxonId = 10,
        scientificName = "Example species",
        commonName = "Example",
        rank = "species",
        taxonClass = "Mammalia",
        description = null,
        conservation = null,
        media = media,
        familyName = "Exampleidae",
    )

    private class FakeMediaRepository(
        private val localUris: Map<String, String>,
    ) : MediaRepository {
        override fun bestLocalVariant(asset: PublishedMediaAsset, variant: String): String? =
            localUris[asset.assetId]

        override fun downloadNow(
            generationId: String,
            asset: PublishedMediaAsset,
            variant: String,
            pinned: Boolean,
        ) = MediaDownloadResult(localUris[asset.assetId], fromCache = true)

        override fun inventory() = StoredMediaSummary(0, 0, 1)

        override fun evict(bytesNeeded: Long) = 0L
    }
}
