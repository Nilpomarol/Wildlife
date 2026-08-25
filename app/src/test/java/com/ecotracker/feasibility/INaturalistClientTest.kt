package com.wildlife.feasibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
