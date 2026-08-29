package com.wildlife.feasibility

import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

class INaturalistClientTest {
    private val client = INaturalistClient()

    @Test
    fun resolvesOnlyAnExactUsernameToImmutableId() {
        val users = listOf(
            INaturalistUser(10, "wildlife-fan"),
            INaturalistUser(42, "WildLife"),
        )

        val user = client.exactUser(users, "wildlife")

        assertEquals(42L, user?.id)
        assertEquals("WildLife", user?.login)
    }

    @Test
    fun rejectsPartialUsernameMatch() {
        assertNull(client.exactUser(listOf(INaturalistUser(10, "wildlife-fan")), "wildlife"))
    }

    @Test
    fun acceptsOnlyExplicitReusablePhotoLicences() {
        assertEquals("cc-by-sa", INaturalistClient.normalizedPhotoLicence(" CC-BY-SA "))
        assertNull(INaturalistClient.normalizedPhotoLicence("cc-by-nc"))
        assertNull(INaturalistClient.normalizedPhotoLicence(null))
    }
}

/**
 * Parsing of the nearby species_counts response.
 *
 * These guard two things that fail *silently* rather than loudly: an un-normalized taxon
 * group draws no silhouette and reports no error, and a photo whose licence was never
 * checked would be displayed with no credit anywhere on the card.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NearbySpeciesParsingTest {
    private val client = INaturalistClient()

    private fun response(taxon: String) = org.json.JSONObject(
        """{"results":[{"count":412,"taxon":$taxon}]}""",
    )

    @Test
    fun normalizesIconicTaxonNameToTheGroupKeyUsedByTheSilhouetteAssets() {
        val parsed = client.parseNearbySpecies(
            response(
                """{"id":1,"name":"Erithacus rubecula","iconic_taxon_name":"Aves"}""",
            ),
        )

        // "Aves" would find no asset in taxon-glyphs/ and render an empty plate.
        assertEquals("birds", parsed.single().taxonGroup)
    }

    @Test
    fun leavesAnUnknownIconicTaxonAsNoGroupRatherThanGuessing() {
        val parsed = client.parseNearbySpecies(
            response("""{"id":1,"name":"Bombus terrestris","iconic_taxon_name":"Insecta"}"""),
        )

        assertNull(parsed.single().taxonGroup)
    }

    @Test
    fun readsTheDefaultPhotoSoATileCostsNoExtraRequest() {
        val parsed = client.parseNearbySpecies(
            response(
                """{"id":1,"name":"Erithacus rubecula","iconic_taxon_name":"Aves",
                   "default_photo":{"medium_url":"https://example.test/robin.jpg",
                   "attribution":"(c) someone","license_code":"cc0"}}""",
            ),
        ).single()

        assertEquals("https://example.test/robin.jpg", parsed.photoUrl)
        assertEquals("cc0", parsed.photoLicenseCode)
        assertEquals("https://example.test/robin.jpg", parsed.cardPhotoUrl())
    }

    @Test
    fun withholdsACardPhotoWhoseLicenceIsNotPublicDomain() {
        val parsed = client.parseNearbySpecies(
            response(
                """{"id":1,"name":"Erithacus rubecula","iconic_taxon_name":"Aves",
                   "default_photo":{"medium_url":"https://example.test/robin.jpg",
                   "attribution":"(c) someone","license_code":"cc-by-nc"}}""",
            ),
        ).single()

        // A card has nowhere to put a credit line, so anything short of CC0 falls back to
        // the silhouette. The URL is still carried for surfaces that can credit it.
        assertEquals("https://example.test/robin.jpg", parsed.photoUrl)
        assertNull(parsed.cardPhotoUrl())
    }

    @Test
    fun withholdsACardPhotoThatArrivedWithoutAnAttribution() {
        val parsed = client.parseNearbySpecies(
            response(
                """{"id":1,"name":"Erithacus rubecula","iconic_taxon_name":"Aves",
                   "default_photo":{"medium_url":"https://example.test/robin.jpg",
                   "license_code":"cc0"}}""",
            ),
        ).single()

        assertNull(parsed.cardPhotoUrl())
    }

    @Test
    fun toleratesATaxonWithNoPhotoAtAll() {
        val parsed = client.parseNearbySpecies(
            response("""{"id":1,"name":"Erithacus rubecula","iconic_taxon_name":"Aves"}"""),
        ).single()

        assertNull(parsed.photoUrl)
        assertNull(parsed.cardPhotoUrl())
    }
}

/**
 * The two photo bars, which differ only by whether the surface can show a credit.
 *
 * Kept separate from the parsing tests because the distinction is a licensing rule, and a
 * regression here means either a wall of silhouettes or an uncredited image.
 */
class NearbyPhotoLicenceTest {

    private fun species(licence: String?, attribution: String? = "(c) someone") = NearbySpecies(
        taxonId = 1,
        commonName = "European robin",
        scientificName = "Erithacus rubecula",
        taxonGroup = "birds",
        observationCount = 1,
        photoUrl = "https://example.test/robin.jpg",
        photoAttribution = attribution,
        photoLicenseCode = licence,
    )

    @Test
    fun ccZeroPassesBothBars() {
        assertEquals("https://example.test/robin.jpg", species("cc0").cardPhotoUrl())
        assertEquals("https://example.test/robin.jpg", species("cc0").tilePhotoUrl())
    }

    @Test
    fun ccByIsAllowedOnlyWhereACreditIsShown() {
        // The whole reason the tile bar exists: iNaturalist default photos are mostly CC-BY,
        // and a CC0-only rule would render silhouettes for nearly every species.
        assertNull(species("cc-by").cardPhotoUrl())
        assertEquals("https://example.test/robin.jpg", species("cc-by").tilePhotoUrl())
    }

    @Test
    fun aTileImageAlwaysHasACreditToShowWithIt() {
        val entry = species("cc-by")
        assertNotNull(entry.tilePhotoUrl())
        assertNotNull(entry.tileAttribution())
    }

    @Test
    fun nonCommercialAndNoDerivativesAreRejectedByBothBars() {
        listOf("cc-by-nc", "cc-by-nd", "cc-by-nc-sa", "c", null).forEach { licence ->
            assertNull("$licence must not reach a card", species(licence).cardPhotoUrl())
            assertNull("$licence must not reach a tile", species(licence).tilePhotoUrl())
        }
    }

    @Test
    fun anImageWithNoAttributionIsRejectedEvenUnderAPermissiveLicence() {
        assertNull(species("cc-by", attribution = null).tilePhotoUrl())
        assertNull(species("cc0", attribution = null).cardPhotoUrl())
    }
}

/**
 * The one-line credit used on a narrow tile.
 *
 * The point of these is that shortening must never cost the licence: ellipsising the raw
 * iNaturalist string drops it, which credits *less* completely in the same space.
 */
class CompactCreditTest {

    @Test
    fun keepsTheAuthorAndTheLicenceAndDiscardsTheBoilerplate() {
        assertEquals(
            "Jörg Hempel / CC-BY-SA",
            CataloguePhotoPolicy.compactCredit(
                "(c) Jörg Hempel, some rights reserved (CC BY-SA)",
                "cc-by-sa",
            ),
        )
    }

    @Test
    fun handlesAPublicDomainDedicationWithNoRightsReserved() {
        assertEquals(
            "Anna Roca / CC0",
            CataloguePhotoPolicy.compactCredit("(c) Anna Roca, no rights reserved", "cc0"),
        )
    }

    @Test
    fun toleratesACopyrightSymbolInsteadOfTheAsciiForm() {
        assertEquals(
            "Anna Roca / CC-BY",
            CataloguePhotoPolicy.compactCredit("© Anna Roca, some rights reserved", "cc-by"),
        )
    }

    @Test
    fun fallsBackToTheLicenceAloneWhenNoAuthorSurvives() {
        assertEquals("CC-BY", CataloguePhotoPolicy.compactCredit("(c)", "cc-by"))
    }

    @Test
    fun yieldsNothingWithoutALicenceOrWithoutAnAttribution() {
        assertNull(CataloguePhotoPolicy.compactCredit("(c) Anna Roca", null))
        assertNull(CataloguePhotoPolicy.compactCredit(null, "cc-by"))
    }
}


/**
 * Pagination of the observation sync.
 *
 * These guard a failure that was *silent and destructive* rather than loud. The walk used
 * to stop when the parsed page came back shorter than the page size, but
 * `parseSyncedObservations` drops records it cannot key, and iNaturalist permits an
 * observation with no date. One such record inside a full page ended the walk early, and
 * `ObservationStore.replaceSnapshot` deletes and rewrites the whole account — so every
 * observation past that page was removed from the collection with no error shown.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ObservationPaginationTest {
    private val client = INaturalistClient()

    private fun page(vararg records: String) = JSONObject("""{"results":[${records.joinToString(",")}]}""")

    private fun record(id: Long, dated: Boolean = true) = if (dated) {
        """{"id":$id,"uuid":"uuid-$id","time_observed_at":"2026-08-27T10:00:00+02:00"}"""
    } else {
        """{"id":$id,"uuid":"uuid-$id"}"""
    }

    @Test
    fun `a full page whose records all parse advances the cursor to the highest id`() {
        val root = page(record(7), record(9), record(8))

        assertEquals(9L, client.nextPageCursor(root, current = null, pageSize = 3))
    }

    @Test
    fun `an undated record inside a full page does not end the walk`() {
        // The regression: this page parses to two rows, so the old parsed-size check
        // stopped here and the account lost every observation after id 9.
        val root = page(record(7), record(8, dated = false), record(9))

        assertEquals(9L, client.nextPageCursor(root, current = null, pageSize = 3))
    }

    @Test
    fun `the cursor advances past an unparseable highest id rather than re-requesting it`() {
        // If the walk took its cursor from the parsed rows it would resume at 8 and be
        // handed the same page again, forever.
        val root = page(record(7), record(8), record(9, dated = false))

        assertEquals(9L, client.nextPageCursor(root, current = null, pageSize = 3))
    }

    @Test
    fun `a short page is the last page`() {
        assertNull(client.nextPageCursor(page(record(7), record(8)), current = null, pageSize = 3))
    }

    @Test
    fun `an empty final page ends the walk`() {
        assertNull(client.nextPageCursor(page(), current = 9L, pageSize = 3))
        assertNull(client.nextPageCursor(JSONObject("{}"), current = 9L, pageSize = 3))
    }

    @Test
    fun `a full page that cannot advance the cursor ends the walk rather than looping`() {
        val root = page(record(1, dated = false), record(2, dated = false))

        assertEquals(2L, client.nextPageCursor(root, current = null, pageSize = 2))
        assertNull(client.nextPageCursor(root, current = 2L, pageSize = 2))
    }
}

/**
 * The observation request itself: v2 with an explicit field selection.
 *
 * The v1 endpoint has no field selection and returns the entire object graph per record —
 * one 200-record page measured 27.2 MiB against the client's 8 MiB ceiling, which failed
 * the sync outright for any account past its first page.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ObservationRequestTest {
    private fun record(id: Long) =
        """{"id":$id,"uuid":"uuid-$id","time_observed_at":"2026-08-27T10:00:00+02:00"}"""

    private class Recorder(private val bodies: List<String>) {
        val requested = mutableListOf<URL>()
        fun factory(): (URL) -> HttpURLConnection = { url ->
            val body = bodies[requested.size]
            requested += url
            object : HttpURLConnection(url) {
                override fun connect() = Unit
                override fun disconnect() = Unit
                override fun usingProxy() = false
                override fun getResponseCode() = 200
                override fun getContentLengthLong() = -1L
                override fun getInputStream() = ByteArrayInputStream(body.toByteArray())
            }
        }
    }

    private fun clientOver(bodies: List<String>): Pair<INaturalistClient, Recorder> {
        val recorder = Recorder(bodies)
        val http = ReadOnlyHttpClient(
            connectionFactory = recorder.factory(),
            clock = { 0L },
            sleeper = {},
        )
        return INaturalistClient(http) to recorder
    }

    @Test
    fun `the sync reads v2 and names every field it parses`() {
        val (client, recorder) = clientOver(listOf("""{"results":[${record(1)}]}"""))

        client.syncedObservations(userId = 42L, pageSize = 2)

        val url = recorder.requested.single().toString()
        assertTrue(url, url.startsWith("https://api.inaturalist.org/v2/observations?"))
        val fields = URL(url).query.split("&").first { it.startsWith("fields=") }
        // Percent-decoded, because the shared URL builder encodes the separators.
        val selected = java.net.URLDecoder.decode(fields.removePrefix("fields="), "UTF-8").split(",")
        listOf(
            "id", "uuid", "observed_on", "observed_on_string", "time_observed_at",
            "created_at", "quality_grade", "species_guess", "geoprivacy", "obscured",
            "taxon.id", "taxon.name", "taxon.rank", "taxon.rank_level", "taxon.parent_id",
            "taxon.preferred_common_name", "photos.url", "geojson.coordinates",
        ).forEach { field -> assertTrue(field, field in selected) }
    }

    @Test
    fun `the walk follows id_above until a short page and keeps every record`() {
        val (client, recorder) = clientOver(
            listOf(
                """{"results":[${record(1)},${record(2)}]}""",
                """{"results":[${record(3)},${record(4)}]}""",
                """{"results":[${record(5)}]}""",
            ),
        )

        val observations = client.syncedObservations(userId = 42L, pageSize = 2)

        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), observations.map(SyncedObservation::id))
        assertEquals(3, recorder.requested.size)
        assertNull(recorder.requested[0].query.split("&").firstOrNull { it.startsWith("id_above=") })
        assertEquals("id_above=2", recorder.requested[1].query.split("&").first { it.startsWith("id_above=") })
        assertEquals("id_above=4", recorder.requested[2].query.split("&").first { it.startsWith("id_above=") })
    }

    @Test
    fun `an undated record in a full page does not truncate the synced account`() {
        val (client, recorder) = clientOver(
            listOf(
                """{"results":[${record(1)},{"id":2,"uuid":"uuid-2"}]}""",
                """{"results":[${record(3)}]}""",
            ),
        )

        val observations = client.syncedObservations(userId = 42L, pageSize = 2)

        assertEquals(listOf(1L, 3L), observations.map(SyncedObservation::id))
        assertEquals(2, recorder.requested.size)
    }
}


/**
 * The adaptive page size.
 *
 * Field selection makes a page small, but that is a measurement rather than a guarantee —
 * it bounds how many fields come back, not how long the values in them are. A single
 * unusually heavy page must narrow the request instead of failing the whole sync, which is
 * only safe because the cursor is `id_above` rather than an offset.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AdaptivePageSizeTest {
    private fun record(id: Long) =
        """{"id":$id,"uuid":"uuid-$id","time_observed_at":"2026-08-27T10:00:00+02:00"}"""

    private class Sized(
        private val bodies: ArrayDeque<String>,
        private val oversizedAbove: Int,
    ) {
        val perPages = mutableListOf<Int>()
        fun factory(): (URL) -> HttpURLConnection = { url ->
            val perPage = url.query.split("&")
                .first { it.startsWith("per_page=") }.removePrefix("per_page=").toInt()
            perPages += perPage
            val oversized = perPage > oversizedAbove
            val body = if (oversized) "{}" else bodies.removeFirst()
            object : HttpURLConnection(url) {
                override fun connect() = Unit
                override fun disconnect() = Unit
                override fun usingProxy() = false
                override fun getResponseCode() = 200
                override fun getContentLengthLong() = if (oversized) 9_000_000L else -1L
                override fun getInputStream() = ByteArrayInputStream(body.toByteArray())
            }
        }
    }

    private fun clientOver(bodies: List<String>, oversizedAbove: Int): Pair<INaturalistClient, Sized> {
        val sized = Sized(ArrayDeque(bodies), oversizedAbove)
        return INaturalistClient(
            ReadOnlyHttpClient(connectionFactory = sized.factory(), clock = { 0L }, sleeper = {}),
        ) to sized
    }

    @Test
    fun `an oversized page is halved and retried rather than failing the sync`() {
        val (client, sized) = clientOver(
            bodies = listOf("""{"results":[${record(1)}]}"""),
            oversizedAbove = 100,
        )

        val observations = client.syncedObservations(userId = 42L, pageSize = 200)

        assertEquals(listOf(1L), observations.map(SyncedObservation::id))
        // 200 was refused, 100 fit.
        assertEquals(listOf(200, 100), sized.perPages)
    }

    @Test
    fun `narrowing halves repeatedly until the page fits`() {
        val (client, sized) = clientOver(
            bodies = listOf("""{"results":[${record(1)}]}"""),
            oversizedAbove = 30,
        )

        client.syncedObservations(userId = 42L, pageSize = 200)

        assertEquals(listOf(200, 100, 50, 25), sized.perPages)
    }

    @Test
    fun `the narrowed size is kept for the rest of the walk`() {
        // A full page at the narrowed size, so the walk continues to a second request.
        val firstPage = (1L..50L).joinToString(",", prefix = """{"results":[""", postfix = "]}") {
            record(it)
        }
        val (client, sized) = clientOver(
            bodies = listOf(firstPage, """{"results":[${record(51)}]}"""),
            oversizedAbove = 50,
        )

        val observations = client.syncedObservations(userId = 42L, pageSize = 100)

        assertEquals((1L..51L).toList(), observations.map(SyncedObservation::id))
        // 100 was refused, 50 fit, and the second page asks for 50 rather than reverting
        // to 100 — which also keeps the short-page end-of-walk test honest.
        assertEquals(listOf(100, 50, 50), sized.perPages)
    }

    @Test
    fun `a page still too large at the floor reports the error instead of looping`() {
        val (client, sized) = clientOver(bodies = emptyList(), oversizedAbove = 0)

        val error = runCatching {
            client.syncedObservations(userId = 42L, pageSize = 200)
        }.exceptionOrNull()

        assertTrue(error.toString(), error is RemoteDataException.ResponseTooLarge)
        assertEquals(
            listOf(200, 100, 50, 25),
            sized.perPages,
        )
    }
}
