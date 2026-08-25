package com.wildlife.feasibility

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReferenceMediaRejectionStoreTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(ReferenceMediaRejectionStore.DATABASE)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(ReferenceMediaRejectionStore.DATABASE)
    }

    @Test
    fun `rejection history is durable idempotent and scoped by taxon`() {
        val source = "https://commons.wikimedia.org/wiki/File:Example.jpg/"
        ReferenceMediaRejectionStore(context) { 1234L }.use { store ->
            assertTrue(store.reject(10, source, "generation-7"))
            assertFalse(store.reject(10, source.removeSuffix("/"), "generation-8"))
            assertTrue(store.reject(11, source, "generation-7"))
        }

        ReferenceMediaRejectionStore(context).use { store ->
            assertEquals(setOf(source), store.sourceUrls(10))
            assertEquals(2, store.records().size)
            val record = store.records().first { it.taxonId == 10L }
            assertEquals("wikimedia_commons", record.provider)
            assertEquals("generation-7", record.catalogueGenerationId)
            assertEquals(1234L, record.rejectedAtMs)
        }
    }

    @Test
    fun `invalid and insecure source urls are never persisted`() {
        ReferenceMediaRejectionStore(context).use { store ->
            assertFalse(store.reject(10, null, null))
            assertFalse(store.reject(10, "http://commons.wikimedia.org/wiki/File:Unsafe.jpg", null))
            assertFalse(store.reject(0, "https://commons.wikimedia.org/wiki/File:Invalid.jpg", null))
            assertTrue(store.records().isEmpty())
        }
        assertNull("not a url".normalizedReferenceSource())
    }

    @Test
    fun `portable local export contains authoring ready rejection records`() {
        ReferenceMediaRejectionStore(context) { 5678L }.use {
            it.reject(
                42,
                "https://www.inaturalist.org/photos/99",
                "generation-9",
            )
        }

        val exported = StructuredLocalDataExporter(context).export()
        val records = JSONObject(exported.readText()).getJSONArray("reference_media_rejections")
        assertEquals(1, records.length())
        assertEquals(42L, records.getJSONObject(0).getLong("taxon_id"))
        assertEquals("inaturalist", records.getJSONObject(0).getString("provider"))
        assertEquals("user_requested_replacement", records.getJSONObject(0).getString("reason"))
    }
}
