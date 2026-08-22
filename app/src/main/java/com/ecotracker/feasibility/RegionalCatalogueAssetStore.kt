package com.wildlife.feasibility

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File

data class InstalledRegionalCatalogue(
    val regionKey: String,
    val displayName: String,
    val version: String,
)

data class InstalledRegionalTaxon(
    val taxonId: Long,
    val commonName: String,
    val scientificName: String,
    val rarity: EncounterRarity,
    val prestige: RegionalPrestige,
    /** Linnaean class from the frozen taxonomy, e.g. "Mammalia", "Aves". Blank if unknown. */
    val taxonClass: String,
)

data class InstalledRegionalAchievement(val label: String, val taxonIds: Set<Long>)

/** Metadata for the immutable regional-content bundle currently installed on the device. */
data class RegionalContentPackInventory(
    val sourceDigest: String,
    val fileBytes: Long,
    val installedAtMs: Long,
    val catalogues: List<InstalledRegionalCatalogue>,
) {
    val isInstalled: Boolean
        get() = fileBytes > 0
}

/** Reads the generated, versioned content pack bundled with the app; it never contacts a service. */
class RegionalCatalogueAssetStore(private val context: Context) {
    /**
     * The base bundle can be safely re-created from the APK. This method removes only that copied
     * content and its metadata; observations, assignments and progression live in other stores.
     */
    fun removeInstalledBasePack(): Boolean {
        val destination = destinationFile()
        val staged = stagedFile()
        val backup = backupFile()
        val removed = listOf(destination, staged, backup).all { file -> !file.exists() || file.delete() }
        preferences().edit().clear().commit()
        return removed
    }

    fun inventory(): RegionalContentPackInventory {
        val destination = installedDatabase()
        return RegionalContentPackInventory(
            sourceDigest = sourceDigest(),
            fileBytes = destination.length(),
            installedAtMs = preferences().getLong(KEY_INSTALLED_AT_MS, 0L),
            catalogues = catalogues(),
        )
    }

    fun catalogues(): List<InstalledRegionalCatalogue> = database().use { database ->
        database.rawQuery(
            "SELECT DISTINCT region_key, catalogue_version FROM regional_taxon ORDER BY region_key",
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val key = cursor.getString(0)
                    add(InstalledRegionalCatalogue(key, DISPLAY_NAMES.getValue(key), cursor.getString(1)))
                }
            }
        }
    }

    fun taxa(regionKey: String): List<InstalledRegionalTaxon> = database().use { database ->
        database.rawQuery(
            """SELECT rt.taxon_id, t.scientific_name, t.common_names_json, rt.encounter_rarity, rt.prestige, t.taxonomy_json
                FROM regional_taxon rt JOIN taxon t ON t.taxon_id = rt.taxon_id
                WHERE rt.region_key = ? ORDER BY rt.sort_order""".trimIndent(),
            arrayOf(regionKey),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val names = org.json.JSONObject(cursor.getString(2))
                    val taxonClass = runCatching {
                        org.json.JSONObject(cursor.getString(5)).optString("class", "")
                    }.getOrDefault("")
                    add(
                        InstalledRegionalTaxon(
                            taxonId = cursor.getLong(0),
                            commonName = names.optString("en", cursor.getString(1)),
                            scientificName = cursor.getString(1),
                            rarity = EncounterRarity.valueOf(cursor.getString(3).uppercase()),
                            prestige = RegionalPrestige.valueOf(cursor.getString(4).uppercase()),
                            taxonClass = taxonClass,
                        ),
                    )
                }
            }
        }
    }

    fun achievements(regionKey: String): List<InstalledRegionalAchievement> = database().use { database ->
        database.rawQuery(
            "SELECT achievement_type, taxon_ids_json FROM regional_achievement WHERE region_key = ? ORDER BY achievement_key",
            arrayOf(regionKey),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val ids = org.json.JSONArray(cursor.getString(1)).let { array ->
                        buildSet { for (index in 0 until array.length()) add(array.getLong(index)) }
                    }
                    add(InstalledRegionalAchievement(cursor.getString(0), ids))
                }
            }
        }
    }

    private fun database(): SQLiteDatabase = SQLiteDatabase.openDatabase(
        installedDatabase().path,
        null,
        SQLiteDatabase.OPEN_READONLY,
    )

    private fun installedDatabase(): File {
        val destination = destinationFile()
        val digest = sourceDigest()
        if (destination.exists() && preferences().getString(KEY_DIGEST, null) == digest) {
            return destination
        }
        installBasePack(destination, digest)
        return destination
    }

    /** Stages the asset before replacing the prior readable bundle to survive interrupted updates. */
    private fun installBasePack(destination: File, digest: String) {
        destination.parentFile?.mkdirs()
        val staged = stagedFile()
        val backup = backupFile()
        staged.delete()
        backup.delete()
        try {
            context.assets.open(ASSET).use { input -> staged.outputStream().use(input::copyTo) }
            check(staged.length() > 0L) { "Regional content bundle is empty." }
            if (destination.exists()) check(destination.renameTo(backup)) {
                "Could not stage the previous regional content bundle."
            }
            check(staged.renameTo(destination)) { "Could not install the regional content bundle." }
            backup.delete()
            preferences().edit()
                .putString(KEY_DIGEST, digest)
                .putLong(KEY_INSTALLED_AT_MS, System.currentTimeMillis())
                .apply()
        } catch (error: Exception) {
            staged.delete()
            if (!destination.exists() && backup.exists()) backup.renameTo(destination)
            throw error
        }
    }

    private fun sourceDigest(): String = context.assets.open(REPORT).bufferedReader().use { reader ->
        org.json.JSONObject(reader.readText()).getString("source_digest")
    }

    private fun destinationFile() = File(context.noBackupFilesDir, DATABASE)

    private fun stagedFile() = File(context.noBackupFilesDir, "$DATABASE.staged")

    private fun backupFile() = File(context.noBackupFilesDir, "$DATABASE.backup")

    private fun preferences() = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    private companion object {
        const val ASSET = "catalogues/catalogue.sqlite"
        const val REPORT = "catalogues/catalogue-report.json"
        const val DATABASE = "regional-catalogue.sqlite"
        const val PREFERENCES = "regional_catalogue_content"
        const val KEY_DIGEST = "source_digest"
        const val KEY_INSTALLED_AT_MS = "installed_at_ms"
        val DISPLAY_NAMES = mapOf(
            "mediterranean_europe" to "Mediterranean Europe",
            "east_africa" to "East Africa",
            "caribbean" to "Caribbean",
        )
    }
}
