package com.wildlife.feasibility

import android.content.Context
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PublishedContentStoreTest {
    private lateinit var context: Context
    private lateinit var store: RegionalCatalogueAssetStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PublishedContentRepositories.resetForTesting()
        store = RegionalCatalogueAssetStore(context, allowDraftContent = true, appVersionCode = 1)
        store.removeInstalledBasePack()
    }

    @After
    fun tearDown() {
        PublishedContentRepositories.resetForTesting()
        store.removeInstalledBasePack()
        ObservationStore(context).use { it.clearAllLocalData() }
        ProgressionStore(context).clear()
    }

    @Test
    fun `clean install exposes generation regions taxa and prepublished media variants`() {
        assertEquals(4, store.generation().schemaVersion)
        assertTrue(store.generation().generationSequence > 0)
        assertEquals(3, store.catalogues().size)
        assertEquals(2419, store.publishedTaxonCount())

        val taxon = store.taxon(53L)
        assertNotNull(taxon)
        assertEquals("Gruidae", taxon!!.familyName)
        assertNotNull(taxon.description)
        val taxonWithPhoto = publishedTaxonWithPhoto()
        assertNotNull(taxonWithPhoto.preferredPhoto()?.variant("thumbnail"))
        assertNotNull(taxonWithPhoto.preferredPhoto()?.variant("detail"))
    }

    @Test
    fun `every generated photo uses the runtime licence contract`() {
        val photos = store.catalogues()
            .flatMap { store.mediaAssets(it.regionKey) }
            .filter { it.mediaType == "photo" }
            .distinctBy { it.assetId }

        assertTrue(photos.isNotEmpty())
        photos.forEach { photo ->
            val detail = requireNotNull(photo.variant("detail") ?: photo.variant("thumbnail"))
            assertNotNull(CataloguePhotoPolicy.creditedUrl(detail.directUrl, photo.creator, photo.licenceCode))
            assertNotNull(CataloguePhotoPolicy.creditedAttribution(detail.directUrl, photo.creator, photo.licenceCode))
        }
    }

    @Test
    fun `legacy migration leaves unrelated user preferences intact`() {
        val userState = context.getSharedPreferences("user-state-test", Context.MODE_PRIVATE)
        userState.edit().putInt("xp", 42).commit()

        assertTrue(store.migrateLegacyCacheIfNeeded())
        assertEquals(42, userState.getInt("xp", 0))
        assertTrue(store.generation().generationId.isNotBlank())
    }

    @Test
    fun `invalid replacement leaves active generation readable`() {
        val before = store.generation()
        val invalidPack = java.io.File(context.cacheDir, "invalid-content-pack.zip")
        invalidPack.writeText("not a content pack")

        assertFalse(store.installContentPack(invalidPack).installed)
        assertEquals(before, store.generation())
    }

    @Test
    fun `release pack requires frozen catalogues and normalized achievement counts`() {
        val invalid = releasePack("invalid-release", sequence = 100, freezeCatalogues = false)
        assertEquals("invalid_pack", store.installContentPack(invalid).errorCode)

        val valid = releasePack("valid-release", sequence = 101, freezeCatalogues = true)
        assertTrue(store.installContentPack(valid).installed)
        assertEquals("release", store.generation().releaseStatus)
        assertEquals(101L, store.generation().generationSequence)
        assertEquals(10, store.achievements("mediterranean_europe").first { it.label == "essentials" }.taxonIds.size)
        assertEquals(5, store.achievements("mediterranean_europe").first { it.label == "icons" }.taxonIds.size)
    }

    @Test
    fun `interrupted replacement restores the prior verified generation`() {
        val before = store.generation()
        val files = store.swapFilesForTesting()
        // A process interruption closes SQLite before startup recovery inspects the files.
        store.close()
        files.active.copyTo(files.backup, overwrite = true)
        files.active.writeText("truncated replacement")
        files.staged.writeText("abandoned staging data")

        assertEquals(before, store.generation())
        assertTrue(files.active.length() > "truncated replacement".length)
        assertFalse(files.backup.exists())
        assertFalse(files.staged.exists())
    }

    @Test
    fun `application repository has one generation bound instance`() {
        val first = PublishedContentRepositories.application(context)
        val second = PublishedContentRepositories.application(context)

        assertSame(first, second)
        assertEquals(first.generation(), second.generation())
    }

    @Test
    fun `regional content uses one cached four query projection without revalidation`() {
        val before = store.diagnostics()
        val first = store.taxaContent("mediterranean_europe")
        val afterFirst = store.diagnostics()
        val second = store.taxaContent("mediterranean_europe")
        val afterSecond = store.diagnostics()

        assertEquals(800, first.size)
        assertSame(first, second)
        assertEquals(4, afterFirst.regionalProjectionQueryCount - before.regionalProjectionQueryCount)
        assertEquals(afterFirst.regionalProjectionQueryCount, afterSecond.regionalProjectionQueryCount)
        assertEquals(afterFirst.fullValidationCount, afterSecond.fullValidationCount)
        assertEquals(afterFirst.activeDatabaseOpenCount, afterSecond.activeDatabaseOpenCount)
    }

    @Test
    fun `content migration and failed install preserve observation and progression stores`() {
        val userId = 73L
        val observation = SyncedObservation(
            id = 1, uuid = "preserved", taxonId = 53, taxonRank = "species",
            collectionTaxonId = 53, collectionTaxonRank = "species", label = "Robin",
            observedAtMs = 100, latitude = null, longitude = null, obscured = false,
            createdAtMs = 100, qualityGrade = "research", photoUrl = null, confirmed = false,
        )
        ObservationStore(context).use { it.replaceSnapshot(userId, listOf(observation)) }
        ProgressionStore(context).selectLevel(userId, "naturalist")
        val invalidPack = java.io.File(context.cacheDir, "invalid-preservation-pack.zip")
        invalidPack.writeText("not a content pack")

        assertTrue(store.migrateLegacyCacheIfNeeded())
        assertFalse(store.installContentPack(invalidPack).installed)

        ObservationStore(context).use {
            assertEquals("preserved", it.observations(userId).single().uuid)
        }
        assertEquals("naturalist", ProgressionStore(context).selectedLevelKey(userId))
    }

    @Test
    fun `published projection never exposes an unvalidated direct image url`() {
        val taxon = publishedTaxonWithPhoto()
        val unavailable = taxon.toLocalTaxonDetails(FakeMediaRepository(null))
        assertNull(unavailable.photoUrl)
        assertTrue(unavailable.photoSourceUrl!!.startsWith("https://"))

        val local = taxon.toLocalTaxonDetails(FakeMediaRepository("file:///validated/reference.jpg"))
        assertEquals("file:///validated/reference.jpg", local.photoUrl)
        assertFalse(local.photoUrl!!.startsWith("https://"))
    }

    @Test
    fun `published media repair is requested only for missing stages`() {
        assertEquals(
            PublishedMediaRepairNeeds(photo = false, silhouette = false),
            publishedTaxonWithPhoto().mediaRepairNeeds(),
        )
        assertEquals(
            PublishedMediaRepairNeeds(photo = true, silhouette = false),
            publishedTaxonWithoutPhoto().mediaRepairNeeds(),
        )
    }

    @Test
    fun `validated runtime repair fills published media gaps without replacing content`() {
        val published = publishedTaxonWithoutPhoto()
        val static = published.toLocalTaxonDetails(FakeMediaRepository(null))
        val recovered = static.copy(
            commonName = "Wrong runtime name",
            wikipediaSummary = "Wrong runtime summary",
            photoUrl = "https://example.test/agile-frog.jpg",
            photoLocalUri = "file:///validated/agile-frog.jpg",
            photoAttribution = "Open photographer",
            photoLicenseCode = "cc-by",
            photoSourceUrl = "https://commons.wikimedia.org/wiki/File:Agile_frog.jpg",
            photoRecoveryStatus = "wikimedia_quality",
            silhouetteUrl = "https://example.test/agile-frog.png",
            silhouetteLocalUri = "file:///validated/agile-frog.png",
            silhouetteSourceUrl = "https://www.phylopic.org/images/frog",
            silhouetteAttribution = "Open illustrator",
            silhouetteLicenseCode = "cc0",
            silhouetteLicenseUrl = "https://creativecommons.org/publicdomain/zero/1.0/",
            silhouetteTaxonName = "Rana dalmatina",
            silhouetteMatchRank = "species",
        )

        val merged = published.toLocalTaxonDetails(FakeMediaRepository(null), recovered)

        assertEquals(published.commonName, merged.commonName)
        assertEquals(published.description?.summary, merged.wikipediaSummary)
        assertEquals("file:///validated/agile-frog.jpg", merged.photoUrl)
        assertNull(merged.silhouetteUrl)
        assertNull(merged.silhouetteMatchRank)
    }

    @Test
    fun `user requested photo overlay replaces the bundled photo without changing content`() {
        val published = publishedTaxonWithPhoto()
        val recovered = published.toLocalTaxonDetails(FakeMediaRepository(null)).copy(
            commonName = "Wrong runtime name",
            photoUrl = "https://upload.wikimedia.org/alternative.jpg",
            photoLocalUri = "file:///validated/alternative.jpg",
            photoAttribution = "Alternative photographer",
            photoLicenseCode = "cc-by",
            photoSourceUrl = "https://commons.wikimedia.org/wiki/File:Alternative.jpg",
            photoRecoveryStatus = OnDeviceWildlifeRepository.USER_REQUESTED_ALTERNATIVE,
        )

        val merged = published.toLocalTaxonDetails(
            FakeMediaRepository("file:///validated/bundled.jpg"),
            recovered,
        )

        assertEquals(published.commonName, merged.commonName)
        assertEquals("file:///validated/alternative.jpg", merged.photoUrl)
        assertEquals("Alternative photographer", merged.photoAttribution)
        assertEquals("https://commons.wikimedia.org/wiki/File:Alternative.jpg", merged.photoSourceUrl)
    }

    @Test
    fun `published taxon is guarded from legacy discovery even if called accidentally`() {
        OnDeviceWildlifeRepository(context).use { repository ->
            assertThrows(IllegalStateException::class.java) { repository.syncTaxonDetail(53L) }
        }
    }

    private fun publishedTaxonWithoutPhoto(): PublishedTaxonContent = store.catalogues()
        .asSequence()
        .flatMap { catalogue -> store.taxaContent(catalogue.regionKey).values.asSequence() }
        .distinctBy(PublishedTaxonContent::taxonId)
        .first { it.preferredPhoto() == null }

    private fun publishedTaxonWithPhoto(): PublishedTaxonContent = store.catalogues()
        .asSequence()
        .flatMap { catalogue -> store.taxaContent(catalogue.regionKey).values.asSequence() }
        .distinctBy(PublishedTaxonContent::taxonId)
        .first { it.preferredPhoto() != null }

    private fun releasePack(name: String, sequence: Long, freezeCatalogues: Boolean): File {
        store.generation()
        store.close()
        val databaseFile = File(context.cacheDir, "$name.sqlite")
        store.swapFilesForTesting().active.copyTo(databaseFile, overwrite = true)
        SQLiteDatabase.openDatabase(databaseFile.path, null, SQLiteDatabase.OPEN_READWRITE).use { database ->
            database.update(
                "content_generation",
                ContentValues().apply {
                    put("generation_id", name)
                    put("generation_sequence", sequence)
                    put("source_digest", "d".repeat(64))
                    put("release_status", "release")
                    put("generated_at", "2026-08-24T14:00:00Z")
                },
                null,
                null,
            )
            if (freezeCatalogues) {
                database.update("catalogue_version", ContentValues().apply { put("status", "frozen") }, null, null)
            }
        }
        val databaseBytes = databaseFile.readBytes()
        val databaseHash = sha256(databaseBytes)
        val reportBytes = JSONObject()
            .put("schema_version", 4)
            .put("generation_id", name)
            .put("generation_sequence", sequence)
            .put("generated_at", "2026-08-24T14:00:00Z")
            .put("minimum_app_version", 1)
            .put("release_status", "release")
            .put("source_digest", "d".repeat(64))
            .put("database_sha256", databaseHash)
            .toString().toByteArray()
        val manifestBytes = JSONObject()
            .put("schema_version", 2)
            .put("content_schema_version", 4)
            .put("generation_id", name)
            .put("generation_sequence", sequence)
            .put("generated_at", "2026-08-24T14:00:00Z")
            .put("minimum_app_version", 1)
            .put("release_status", "release")
            .put("source_digest", "d".repeat(64))
            .put("files", JSONObject().put("catalogue.sqlite", databaseHash).put("catalogue-report.json", sha256(reportBytes)))
            .toString().toByteArray()
        return File(context.cacheDir, "$name.zip").also { archive ->
            ZipOutputStream(FileOutputStream(archive)).use { output ->
                listOf(
                    "catalogue.sqlite" to databaseBytes,
                    "catalogue-report.json" to reportBytes,
                    "manifest.json" to manifestBytes,
                ).forEach { (entryName, bytes) ->
                    output.putNextEntry(ZipEntry(entryName))
                    output.write(bytes)
                    output.closeEntry()
                }
            }
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private class FakeMediaRepository(private val localUri: String?) : MediaRepository {
        override fun bestLocalVariant(asset: PublishedMediaAsset, variant: String) = localUri
        override fun downloadNow(generationId: String, asset: PublishedMediaAsset, variant: String, pinned: Boolean) =
            MediaDownloadResult(localUri, fromCache = localUri != null)
        override fun inventory() = StoredMediaSummary(0, 0, 1)
        override fun evict(bytesNeeded: Long) = 0L
    }
}
