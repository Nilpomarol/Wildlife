package com.wildlife.feasibility

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RemoteTaxonStoreTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(RemoteTaxonStore.DATABASE)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(RemoteTaxonStore.DATABASE)
    }

    @Test
    fun `off catalogue detail survives store restart and can be cleared independently`() {
        val expected = detail(9_999_999L)
        RemoteTaxonStore(context).use { it.store(expected) }

        RemoteTaxonStore(context).use { store ->
            assertEquals(expected, store.cached(expected.taxonId))
            store.clear()
            assertNull(store.cached(expected.taxonId))
        }
    }

    private fun detail(taxonId: Long) = TaxonDetails(
        taxonId = taxonId,
        scientificName = "Testus localis",
        commonName = "Test species",
        taxonGroup = "Aves",
        familyName = "Testidae",
        wikipediaSummary = "Stored fallback detail.",
        wikipediaUrl = "https://example.test/taxon",
        conservationStatus = null,
        conservationAuthority = null,
        conservationUrl = null,
        photoUrl = "https://example.test/photo.jpg",
        photoAttribution = "Test author, CC0",
        photoLicenseCode = "cc0",
        silhouetteUrl = null,
        silhouetteSourceUrl = null,
        silhouetteAttribution = null,
        silhouetteLicenseCode = null,
        silhouetteLicenseUrl = null,
        silhouetteTaxonName = null,
        silhouetteMatchRank = null,
        updatedAtMs = 1234L,
        photoSourceUrl = "https://example.test/source",
        photoRecoveryStatus = "default_licensed",
        mediaPipelineVersion = 9,
        photoLocalUri = "file:///stored/photo.jpg",
        photoPipelineVersion = 9,
    )
}
