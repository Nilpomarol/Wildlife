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

/** Reads the generated, versioned content pack bundled with the app; it never contacts a service. */
class RegionalCatalogueAssetStore(private val context: Context) {
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

    private fun database(): SQLiteDatabase {
        val destination = File(context.noBackupFilesDir, "regional-catalogue.sqlite")
        val digest = context.assets.open(REPORT).bufferedReader().use { reader ->
            org.json.JSONObject(reader.readText()).getString("source_digest")
        }
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        if (!destination.exists() || preferences.getString(KEY_DIGEST, null) != digest) {
            destination.parentFile?.mkdirs()
            context.assets.open(ASSET).use { input -> destination.outputStream().use(input::copyTo) }
            preferences.edit().putString(KEY_DIGEST, digest).apply()
        }
        return SQLiteDatabase.openDatabase(destination.path, null, SQLiteDatabase.OPEN_READONLY)
    }

    private companion object {
        const val ASSET = "catalogues/catalogue.sqlite"
        const val REPORT = "catalogues/catalogue-report.json"
        const val PREFERENCES = "regional_catalogue_content"
        const val KEY_DIGEST = "source_digest"
        val DISPLAY_NAMES = mapOf(
            "mediterranean_europe" to "Mediterranean Europe",
            "east_africa" to "East Africa",
            "caribbean" to "Caribbean",
        )
    }
}
