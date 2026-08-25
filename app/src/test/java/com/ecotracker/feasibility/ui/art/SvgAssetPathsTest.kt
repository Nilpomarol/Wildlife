package com.wildlife.feasibility.ui.art

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SvgAssetPathsTest {
    @Test
    fun `every supported taxon group has drawable bundled silhouette art`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        listOf("mammals", "birds", "reptiles", "amphibians", "fish").forEach { group ->
            val path = SvgAssetPaths.path(context, "taxon-glyphs/$group.svg")
            assertNotNull("Missing silhouette for $group", path)
            assertTrue("Empty silhouette for $group", path!!.getBounds().width > 0f)
            assertTrue("Empty silhouette for $group", path.getBounds().height > 0f)
        }
    }
}
