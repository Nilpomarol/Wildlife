package com.wildlife.feasibility

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExtraDiscoveryRewardTest {
    private lateinit var context: Context
    private lateinit var store: ObservationStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("wildlife_observations.db")
        store = ObservationStore(context)
    }

    @After
    fun tearDown() {
        store.close()
        context.deleteDatabase("wildlife_observations.db")
    }

    @Test
    fun `off-catalogue confirmation earns global first species once and no regional reward`() {
        store.replaceSnapshot(
            userId = 7,
            observations = listOf(observation("first"), observation("repeat")),
            regionalAssignments = listOf(region("first"), region("repeat")),
        )

        store.confirmObservation(7, "first", regionalReward = null)
        store.confirmObservation(7, "repeat", regionalReward = null)

        val events = store.xpEvents(7, limit = 20)
        assertEquals(1, events.count { it.type == XpEventType.FIRST_SPECIES })
        assertFalse(events.any { it.type == XpEventType.REGIONAL_DISCOVERY })
        assertFalse(events.any { it.type == XpEventType.REGIONAL_RARITY })
        assertFalse(events.any { it.type == XpEventType.REGIONAL_ICON_DISCOVERY })
        assertEquals(ProgressionRules.FIRST_SPECIES_XP, events.single {
            it.type == XpEventType.FIRST_SPECIES
        }.points)
    }

    private fun region(uuid: String) = ObservationRegion(
        uuid, "mediterranean", "boundaries-v1", ObservationRegionAssignment.LAND_POLYGON,
    )

    private fun observation(uuid: String) = SyncedObservation(
        id = if (uuid == "first") 1 else 2,
        uuid = uuid,
        taxonId = 42,
        taxonRank = "species",
        collectionTaxonId = 42,
        collectionTaxonRank = "species",
        label = "Test species",
        observedAtMs = if (uuid == "first") 100 else 200,
        latitude = 1.0,
        longitude = 1.0,
        obscured = false,
        createdAtMs = 100,
        qualityGrade = "needs_id",
        photoUrl = null,
        confirmed = false,
    )
}
