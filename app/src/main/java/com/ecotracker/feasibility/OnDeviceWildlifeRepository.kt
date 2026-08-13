package com.wildlife.feasibility

import android.content.Context
import android.text.Html
import org.json.JSONObject
import java.time.LocalDate
import java.util.Locale

class OnDeviceWildlifeRepository(context: Context) {
    private val appContext = context.applicationContext
    private val observations = ObservationStore(appContext)
    private val catalogue = CatalogueStore(appContext)
    private val iNaturalist = INaturalistClient()
    private val phyloPic = PhyloPicClient()
    private val wikimedia = WikimediaCommonsClient()

    fun syncObservations(account: VerifiedAccount): ObservationSyncResult {
        val cachedSummary = observations.summary(account.userId)
        if (
            cachedSummary.lastSyncedAtMs != null &&
            System.currentTimeMillis() - cachedSummary.lastSyncedAtMs < MIN_SYNC_INTERVAL_MS
        ) {
            return ObservationSyncResult(
                observations = observations.observations(account.userId),
                summary = cachedSummary,
                cached = true,
            )
        }
        val remote = iNaturalist.syncedObservations(account.userId)
        return observations.replaceSnapshot(account.userId, remote)
    }

    fun confirmObservation(
        account: VerifiedAccount,
        observationUuid: String,
    ): ObservationConfirmationResult = observations.confirmObservation(
        account.userId,
        observationUuid,
    )

    fun syncCataloniaCatalogue(force: Boolean = false): CatalogueSnapshot {
        val cached = catalogue.load()
        val fresh = cached?.updatedAtMs?.let {
            System.currentTimeMillis() - it < CATALOGUE_REFRESH_MS
        } == true
        if (!force && fresh && cached?.species?.isNotEmpty() == true) return cached

        val merged = linkedMapOf<Long, Pair<JSONObject, String>>()
        CATALOGUE_SCOPES.forEach { scope ->
            iNaturalist.regionalSpecies(CATALONIA_PLACE_ID, scope.limit, scope.parameters)
                .forEach { raw ->
                    val id = raw.optJSONObject("taxon")?.optLong("id", -1L) ?: -1L
                    if (id <= 0) return@forEach
                    val existing = merged[id]
                    if (existing == null || raw.optInt("count") > existing.first.optInt("count")) {
                        merged[id] = raw to scope.key
                    }
                }
        }
        val selected = merged.values.sortedByDescending { it.first.optInt("count") }
            .take(CATALOGUE_LIMIT)
        val taxaById = selected.mapNotNull { it.first.optJSONObject("taxon")?.optLong("id") }
            .chunked(30)
            .flatMap { iNaturalist.taxa(it, locale = "ca") }
            .associateBy { it.optLong("id") }
        val broadSilhouettes = CATALOGUE_SCOPES.associate { scope ->
            scope.key to scope.silhouetteSearchNames.firstNotNullOfOrNull { name ->
                runCatching { phyloPic.resolve(name, "group") }.getOrNull()
            }
        }
        val now = System.currentTimeMillis()
        val species = selected.mapIndexedNotNull { index, (raw, scopeKey) ->
            val taxon = raw.optJSONObject("taxon") ?: return@mapIndexedNotNull null
            val id = taxon.optLong("id", -1L)
            val scientificName = taxon.optString("name")
            if (id <= 0 || scientificName.isBlank()) return@mapIndexedNotNull null
            val detailedTaxon = taxaById[id] ?: taxon
            val details = normalizeTaxonDetails(detailedTaxon, scopeKey, now)
            val broadSilhouette = broadSilhouettes[scopeKey]
            CatalogueSpecies(
                taxonId = id,
                scientificName = scientificName,
                commonName = taxon.optString("preferred_common_name").valueOrNull(),
                taxonGroup = scopeKey,
                observationCount = raw.optInt("count"),
                position = index + 1,
                photoUrl = details?.photoUrl,
                photoAttribution = details?.photoAttribution,
                photoLicenseCode = details?.photoLicenseCode,
                familyName = details?.familyName,
                wikipediaSummary = details?.wikipediaSummary,
                wikipediaUrl = details?.wikipediaUrl,
                conservationStatus = details?.conservationStatus,
                conservationAuthority = details?.conservationAuthority,
                conservationUrl = details?.conservationUrl,
                silhouetteUrl = broadSilhouette?.url,
                silhouetteSourceUrl = broadSilhouette?.sourceUrl,
                silhouetteAttribution = broadSilhouette?.attribution,
                silhouetteLicenseCode = broadSilhouette?.licenceCode,
                silhouetteLicenseUrl = broadSilhouette?.licenceUrl,
                silhouetteTaxonName = broadSilhouette?.taxonName,
                silhouetteMatchRank = broadSilhouette?.matchRank,
            )
        }
        val snapshot = CatalogueSnapshot(
            regionKey = CatalogueStore.CATALONIA,
            placeId = CATALONIA_PLACE_ID,
            version = "scope-v3-device-inat-$CATALONIA_PLACE_ID-${LocalDate.now()}",
            updatedAtMs = now,
            provisional = true,
            species = species,
            cached = false,
        )
        catalogue.replace(snapshot)
        val details = selected.mapNotNull { (raw, scopeKey) ->
            val id = raw.optJSONObject("taxon")?.optLong("id") ?: return@mapNotNull null
            taxaById[id]?.let { normalizeTaxonDetails(it, scopeKey, now) }
        }
        catalogue.upsertTaxonDetails(details, preserveEnrichedMedia = true)
        return snapshot
    }

    fun syncTaxonDetail(taxonId: Long, force: Boolean = false): TaxonDetails {
        val cached = catalogue.loadTaxonDetail(taxonId)
        val fresh = cached?.updatedAtMs?.let {
            System.currentTimeMillis() - it < TAXON_REFRESH_MS
        } == true
        if (!force && fresh && cached?.mediaPipelineVersion == MEDIA_PIPELINE_VERSION) return cached

        val raw = iNaturalist.taxa(listOf(taxonId)).firstOrNull()
            ?: error("Unknown iNaturalist taxon $taxonId")
        var detail = normalizeTaxonDetails(raw, null, System.currentTimeMillis())
            ?: error("Incomplete iNaturalist taxon $taxonId")
        val curatedPhoto = detail.wikipediaUrl?.let { url ->
            runCatching { wikimedia.curatedPhoto(url) }
                .getOrNull()
        }
        if (curatedPhoto != null) {
            detail = detail.copy(
                photoUrl = curatedPhoto.url,
                photoAttribution = curatedPhoto.attribution,
                photoLicenseCode = curatedPhoto.licenceCode,
                photoSourceUrl = curatedPhoto.sourceUrl,
                photoRecoveryStatus = when (curatedPhoto.quality) {
                    CuratedPhotoQuality.FEATURED -> "wikimedia_featured"
                    CuratedPhotoQuality.QUALITY -> "wikimedia_quality"
                },
            )
        }
        if (detail.photoUrl == null) {
            detail = try {
                iNaturalist.referencePhoto(taxonId)?.let { photo ->
                    detail.copy(
                        photoUrl = photo.url,
                        photoAttribution = photo.attribution,
                        photoLicenseCode = photo.licenceCode,
                        photoSourceUrl = photo.sourceUrl,
                        photoRecoveryStatus = "recovered_observation",
                    )
                } ?: detail.copy(photoRecoveryStatus = "no_compatible_photo")
            } catch (_: Exception) {
                detail.copy(photoRecoveryStatus = "recovery_failed")
            }
        }
        val silhouette = silhouetteCandidates(raw).firstNotNullOfOrNull { (name, rank) ->
            runCatching { phyloPic.resolve(name, rank) }.getOrNull()
        }
        if (silhouette != null) detail = detail.withSilhouette(silhouette)
        detail = detail.copy(mediaPipelineVersion = MEDIA_PIPELINE_VERSION)
        catalogue.upsertTaxonDetail(detail)
        return detail
    }

    internal fun normalizeTaxonDetails(
        raw: JSONObject,
        forcedGroup: String?,
        nowMs: Long,
    ): TaxonDetails? {
        val taxonId = raw.optLong("id", -1L)
        val scientificName = raw.optString("name")
        if (taxonId <= 0 || scientificName.isBlank()) return null
        val lineage = buildList {
            raw.optJSONArray("ancestors")?.let { ancestors ->
                for (index in 0 until ancestors.length()) ancestors.optJSONObject(index)?.let(::add)
            }
            add(raw)
        }
        val lineageIds = lineage.map { it.optLong("id") }.toSet()
        val group = forcedGroup ?: when {
            BUTTERFLIES_TAXON_ID in lineageIds -> "Papilionoidea"
            ODONATA_TAXON_ID in lineageIds -> "Odonata"
            else -> raw.optString("iconic_taxon_name").valueOrNull()
        }
        val family = lineage.firstOrNull { it.optString("rank") == "family" }
            ?.optString("name")?.valueOrNull()
        val photo = raw.optJSONObject("default_photo")
        val licence = INaturalistClient.normalizedPhotoLicence(photo?.optString("license_code"))
        val attribution = photo?.optString("attribution")?.trim().orEmpty()
        val photoUrl = photo?.optString("medium_url")?.valueOrNull()?.takeIf {
            licence != null && (licence == "cc0" || attribution.isNotBlank())
        }
        val status = when {
            photoUrl != null -> "default_licensed"
            photo == null -> "no_default_photo"
            photo.optString("license_code").isBlank() -> "default_missing_license"
            else -> "default_incompatible_license"
        }
        val conservation = raw.optJSONArray("conservation_statuses")?.let { statuses ->
            (0 until statuses.length()).asSequence().mapNotNull(statuses::optJSONObject)
                .firstOrNull {
                    it.optString("authority") == "IUCN Red List" && it.isNull("place")
                }
        }
        val summaryHtml = raw.optString("wikipedia_summary")
        val summary = summaryHtml.valueOrNull()?.let {
            Html.fromHtml(it, Html.FROM_HTML_MODE_LEGACY).toString()
                .replace(Regex("\\s+"), " ").trim().valueOrNull()
        }
        return TaxonDetails(
            taxonId = taxonId,
            scientificName = scientificName,
            commonName = raw.optString("preferred_common_name").valueOrNull(),
            taxonGroup = group,
            familyName = family,
            wikipediaSummary = summary,
            wikipediaUrl = raw.optString("wikipedia_url").valueOrNull(),
            conservationStatus = conservation?.optString("status")?.valueOrNull(),
            conservationAuthority = conservation?.optString("authority")?.valueOrNull(),
            conservationUrl = conservation?.optLong("iucn", -1L)?.takeIf { it > 0 }
                ?.let { "https://www.iucnredlist.org/species/$it" },
            photoUrl = photoUrl,
            photoAttribution = attribution.valueOrNull()?.takeIf { photoUrl != null },
            photoLicenseCode = licence?.takeIf { photoUrl != null },
            silhouetteUrl = null,
            silhouetteSourceUrl = null,
            silhouetteAttribution = null,
            silhouetteLicenseCode = null,
            silhouetteLicenseUrl = null,
            silhouetteTaxonName = null,
            silhouetteMatchRank = null,
            updatedAtMs = nowMs,
            photoSourceUrl = "https://www.inaturalist.org/taxa/$taxonId",
            photoRecoveryStatus = status,
        )
    }

    internal fun silhouetteCandidates(raw: JSONObject): List<Pair<String, String>> {
        val lineage = buildList {
            raw.optJSONArray("ancestors")?.let { ancestors ->
                for (index in 0 until ancestors.length()) ancestors.optJSONObject(index)?.let(::add)
            }
            add(raw)
        }
        val byRank = lineage.associate { it.optString("rank") to it.optString("name") }
        return listOf("species", "genus", "family", "order").mapNotNull { rank ->
            byRank[rank]?.valueOrNull()?.let { it to rank }
        }.distinctBy { it.first.lowercase(Locale.ROOT) }
    }

    private fun TaxonDetails.withSilhouette(value: SilhouetteAsset) = copy(
        silhouetteUrl = value.url,
        silhouetteSourceUrl = value.sourceUrl,
        silhouetteAttribution = value.attribution,
        silhouetteLicenseCode = value.licenceCode,
        silhouetteLicenseUrl = value.licenceUrl,
        silhouetteTaxonName = value.taxonName,
        silhouetteMatchRank = value.matchRank,
    )

    private fun String.valueOrNull() = trim().takeIf(String::isNotBlank)

    private data class CatalogueScope(
        val key: String,
        val limit: Int,
        val parameters: Map<String, String>,
        val silhouetteSearchNames: List<String>,
    )

    companion object {
        private const val CATALONIA_PLACE_ID = 12997L
        private const val CATALOGUE_LIMIT = 580
        private const val BUTTERFLIES_TAXON_ID = 47224L
        private const val ODONATA_TAXON_ID = 47792L
        private const val MEDIA_PIPELINE_VERSION = 6
        private const val MIN_SYNC_INTERVAL_MS = 30_000L
        private const val CATALOGUE_REFRESH_MS = 7L * 24L * 60L * 60L * 1_000L
        private const val TAXON_REFRESH_MS = 30L * 24L * 60L * 60L * 1_000L
        private val CATALOGUE_SCOPES = listOf(
            CatalogueScope("Aves", 250, mapOf("iconic_taxa" to "Aves"), listOf("Aves", "Erithacus rubecula")),
            CatalogueScope("Mammalia", 80, mapOf("iconic_taxa" to "Mammalia"), listOf("Mammalia")),
            CatalogueScope("Reptilia", 50, mapOf("iconic_taxa" to "Reptilia"), listOf("Reptilia")),
            CatalogueScope("Amphibia", 30, mapOf("iconic_taxa" to "Amphibia"), listOf("Amphibia", "Rana temporaria")),
            CatalogueScope("Papilionoidea", 120, mapOf("taxon_id" to BUTTERFLIES_TAXON_ID.toString()), listOf("Papilionoidea", "Papilio machaon")),
            CatalogueScope("Odonata", 50, mapOf("taxon_id" to ODONATA_TAXON_ID.toString()), listOf("Odonata")),
        )
    }
}
