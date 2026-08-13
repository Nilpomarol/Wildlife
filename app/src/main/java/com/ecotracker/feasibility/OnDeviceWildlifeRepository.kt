package com.wildlife.feasibility

import android.content.Context
import android.text.Html
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale

class OnDeviceWildlifeRepository(context: Context) {
    private val appContext = context.applicationContext
    private val observations = ObservationStore(appContext)
    private val catalogue = CatalogueStore(appContext)
    private val iNaturalist = INaturalistClient()
    private val phyloPic = PhyloPicClient()
    private val wikimedia = WikimediaCommonsClient()
    private val localMedia = LocalMediaStore(appContext)

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
        if (!force && cached?.species?.isNotEmpty() == true) return cached

        val attemptedAtMs = System.currentTimeMillis()
        catalogue.recordRefreshStarted(CatalogueStore.CATALONIA, attemptedAtMs)
        return try {
            downloadCataloniaCatalogue(cached)
        } catch (error: Exception) {
            catalogue.recordRefreshFailed(
                CatalogueStore.CATALONIA,
                catalogueRefreshErrorCode(error),
            )
            throw error
        }
    }

    private fun downloadCataloniaCatalogue(cached: CatalogueSnapshot?): CatalogueSnapshot {
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
                runCatching {
                    phyloPic.resolve(name, "group")?.toDurableSilhouette()
                }.getOrNull()
            }
        }
        val existingByTaxon = cached?.species.orEmpty().associateBy(CatalogueSpecies::taxonId)
        val existingByFamily = cached?.species.orEmpty().mapNotNull { species ->
            val family = species.familyName?.lowercase(Locale.ROOT) ?: return@mapNotNull null
            species.silhouetteAsset()?.takeIf {
                it.matchRank == "family" &&
                    it.resolverVersion == SILHOUETTE_PIPELINE_VERSION &&
                    localMedia.isStored(it.localUri)
            }
                ?.let { family to it }
        }.toMap()
        val now = System.currentTimeMillis()
        val species = selected.mapIndexedNotNull { index, (raw, scopeKey) ->
            val taxon = raw.optJSONObject("taxon") ?: return@mapIndexedNotNull null
            val id = taxon.optLong("id", -1L)
            val scientificName = taxon.optString("name")
            if (id <= 0 || scientificName.isBlank()) return@mapIndexedNotNull null
            val detailedTaxon = taxaById[id] ?: taxon
            val details = normalizeTaxonDetails(detailedTaxon, scopeKey, now)
            val broadSilhouette = broadSilhouettes[scopeKey]
            val preservedSilhouette = existingByTaxon[id]?.silhouetteAsset()
                ?.takeIf {
                    it.resolverVersion == SILHOUETTE_PIPELINE_VERSION &&
                        localMedia.isStored(it.localUri)
                }
                ?: details?.familyName?.lowercase(Locale.ROOT)?.let(existingByFamily::get)
            val silhouette = closerSilhouette(preservedSilhouette, broadSilhouette)
            val existing = existingByTaxon[id]
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
                silhouetteUrl = silhouette?.url,
                silhouetteSourceUrl = silhouette?.sourceUrl,
                silhouetteAttribution = silhouette?.attribution,
                silhouetteLicenseCode = silhouette?.licenceCode,
                silhouetteLicenseUrl = silhouette?.licenceUrl,
                silhouetteTaxonName = silhouette?.taxonName,
                silhouetteMatchRank = silhouette?.matchRank,
                photoLocalUri = existing?.photoLocalUri
                    ?.takeIf { existing.photoUrl == details?.photoUrl },
                silhouetteLocalUri = silhouette?.localUri,
                silhouetteResolverVersion = silhouette?.resolverVersion,
            )
        }
        val snapshot = CatalogueSnapshot(
            regionKey = CatalogueStore.CATALONIA,
            placeId = CATALONIA_PLACE_ID,
            version = catalogueRevision(
                selected.map { (raw, scope) ->
                    (raw.optJSONObject("taxon")?.optLong("id", -1L) ?: -1L) to scope
                },
            ),
            updatedAtMs = now,
            provisional = true,
            species = species,
            cached = false,
        )
        val details = selected.mapNotNull { (raw, scopeKey) ->
            val id = raw.optJSONObject("taxon")?.optLong("id") ?: return@mapNotNull null
            taxaById[id]?.let { normalizeTaxonDetails(it, scopeKey, now) }
        }
        catalogue.replace(
            snapshot = snapshot,
            details = details,
            preserveEnrichedMedia = true,
            refreshSucceededAtMs = now,
        )
        return snapshot
    }

    fun enrichCatalogueSilhouettes(): CatalogueSnapshot? {
        val snapshot = catalogue.load() ?: return null
        if (snapshot.silhouettePipelineVersion == SILHOUETTE_PIPELINE_VERSION) return snapshot

        val knownFamilies = snapshot.species.mapNotNull { species ->
            val family = species.familyName?.lowercase(Locale.ROOT) ?: return@mapNotNull null
            species.silhouetteAsset()?.takeIf {
                it.matchRank == "family" &&
                    it.resolverVersion == SILHOUETTE_PIPELINE_VERSION &&
                    localMedia.isStored(it.localUri)
            }
                ?.let { family to it }
        }.toMap().toMutableMap()
        var completedWithoutNetworkErrors = true
        snapshot.species.mapNotNull(CatalogueSpecies::familyName)
            .distinctBy { it.lowercase(Locale.ROOT) }
            .forEach { familyName ->
                val key = familyName.lowercase(Locale.ROOT)
                val asset = knownFamilies[key] ?: try {
                    phyloPic.resolve(familyName, "family")?.toDurableSilhouette()
                } catch (_: Exception) {
                    completedWithoutNetworkErrors = false
                    null
                }
                if (asset != null) {
                    knownFamilies[key] = asset
                    catalogue.updateFamilySilhouette(
                        snapshot.regionKey,
                        familyName,
                        asset,
                        SILHOUETTE_PIPELINE_VERSION,
                    )
                    if (asset.localUri == null) completedWithoutNetworkErrors = false
                }
            }
        if (completedWithoutNetworkErrors) {
            catalogue.markSilhouettePipeline(snapshot.regionKey, SILHOUETTE_PIPELINE_VERSION)
        }
        return catalogue.load()
    }

    fun syncTaxonDetail(taxonId: Long, force: Boolean = false): TaxonDetails {
        val cached = catalogue.loadTaxonDetail(taxonId)
        val fresh = cached?.updatedAtMs?.let {
            System.currentTimeMillis() - it < TAXON_REFRESH_MS
        } == true
        if (
            !force && fresh && cached?.photoPipelineVersion == MEDIA_PIPELINE_VERSION &&
            cached.silhouetteResolverVersion == SILHOUETTE_PIPELINE_VERSION &&
            (cached.silhouetteUrl == null || localMedia.isStored(cached.silhouetteLocalUri))
        ) return cached

        val raw = iNaturalist.taxa(listOf(taxonId)).firstOrNull()
            ?: error("Unknown iNaturalist taxon $taxonId")
        var detail = normalizeTaxonDetails(raw, null, System.currentTimeMillis())
            ?: error("Incomplete iNaturalist taxon $taxonId")
        val curatedPhotoResolution = resolveMedia(detail.wikipediaUrl.orEmpty()) {
            wikimedia.curatedPhoto(
                wikipediaUrl = detail.wikipediaUrl,
                scientificName = detail.scientificName,
                iNaturalistTaxonId = taxonId,
            )
        }
        val curatedPhoto = curatedPhotoResolution.asset
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
        } else if (curatedPhotoResolution.hadTemporaryFailure && cached?.photoUrl != null) {
            detail = detail.withPhotoFrom(cached)
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
        val storedPhoto = detail.photoUrl?.let { localMedia.storePhoto(it, taxonId) }
        detail = detail.copy(
            photoLocalUri = storedPhoto ?: detail.photoLocalUri,
            photoPipelineVersion = MEDIA_PIPELINE_VERSION.takeUnless {
                curatedPhotoResolution.hadTemporaryFailure ||
                    detail.photoRecoveryStatus == "recovery_failed" ||
                    (detail.photoUrl != null && storedPhoto == null)
            },
        )
        val silhouetteResolution = resolveFirstMedia(silhouetteCandidates(raw)) { (name, rank) ->
            phyloPic.resolve(name, rank)
        }
        val resolvedSilhouette = silhouetteResolution.asset?.toDurableSilhouette()
        val cachedSilhouette = cached?.silhouetteAsset()
        val silhouette = when {
            silhouetteResolution.hadTemporaryFailure && cachedSilhouette != null -> cachedSilhouette
            cachedSilhouette?.resolverVersion == SILHOUETTE_PIPELINE_VERSION ->
                closerSilhouette(cachedSilhouette, resolvedSilhouette)
            else -> resolvedSilhouette
        }
        detail = if (silhouette != null) detail.withSilhouette(silhouette) else detail
        val silhouetteStageComplete = !silhouetteResolution.hadTemporaryFailure &&
            (silhouette == null || localMedia.isStored(silhouette.localUri))
        detail = detail.copy(
            silhouetteResolverVersion = SILHOUETTE_PIPELINE_VERSION
                .takeIf { silhouetteStageComplete },
            mediaPipelineVersion = MEDIA_PIPELINE_VERSION
                .takeUnless {
                    detail.photoPipelineVersion != MEDIA_PIPELINE_VERSION ||
                        !silhouetteStageComplete
                },
        )
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
            licence != null && attribution.isNotBlank()
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
        silhouetteLocalUri = value.localUri,
        silhouetteResolverVersion = value.resolverVersion,
    )

    private fun TaxonDetails.withSilhouetteFrom(value: TaxonDetails) = copy(
        silhouetteUrl = value.silhouetteUrl,
        silhouetteSourceUrl = value.silhouetteSourceUrl,
        silhouetteAttribution = value.silhouetteAttribution,
        silhouetteLicenseCode = value.silhouetteLicenseCode,
        silhouetteLicenseUrl = value.silhouetteLicenseUrl,
        silhouetteTaxonName = value.silhouetteTaxonName,
        silhouetteMatchRank = value.silhouetteMatchRank,
        silhouetteLocalUri = value.silhouetteLocalUri,
        silhouetteResolverVersion = value.silhouetteResolverVersion,
    )

    private fun TaxonDetails.withPhotoFrom(value: TaxonDetails) = copy(
        photoUrl = value.photoUrl,
        photoAttribution = value.photoAttribution,
        photoLicenseCode = value.photoLicenseCode,
        photoSourceUrl = value.photoSourceUrl,
        photoRecoveryStatus = value.photoRecoveryStatus,
        photoLocalUri = value.photoLocalUri,
        photoPipelineVersion = value.photoPipelineVersion,
    )

    private fun CatalogueSpecies.silhouetteAsset(): SilhouetteAsset? {
        val url = silhouetteUrl ?: return null
        val sourceUrl = silhouetteSourceUrl ?: return null
        val licenceCode = silhouetteLicenseCode ?: return null
        val licenceUrl = silhouetteLicenseUrl ?: return null
        val taxonName = silhouetteTaxonName ?: return null
        val matchRank = silhouetteMatchRank ?: return null
        return SilhouetteAsset(
            url = url,
            sourceUrl = sourceUrl,
            attribution = silhouetteAttribution,
            licenceCode = licenceCode,
            licenceUrl = licenceUrl,
            taxonName = taxonName,
            matchRank = matchRank,
            localUri = silhouetteLocalUri,
            resolverVersion = silhouetteResolverVersion,
        )
    }

    private fun TaxonDetails.silhouetteAsset(): SilhouetteAsset? {
        val url = silhouetteUrl ?: return null
        val sourceUrl = silhouetteSourceUrl ?: return null
        val licenceCode = silhouetteLicenseCode ?: return null
        val licenceUrl = silhouetteLicenseUrl ?: return null
        val taxonName = silhouetteTaxonName ?: return null
        val matchRank = silhouetteMatchRank ?: return null
        return SilhouetteAsset(
            url = url,
            sourceUrl = sourceUrl,
            attribution = silhouetteAttribution,
            licenceCode = licenceCode,
            licenceUrl = licenceUrl,
            taxonName = taxonName,
            matchRank = matchRank,
            localUri = silhouetteLocalUri,
            resolverVersion = silhouetteResolverVersion,
        )
    }

    private fun SilhouetteAsset.toDurableSilhouette(): SilhouetteAsset =
        localMedia.storeSilhouette(this).copy(resolverVersion = SILHOUETTE_PIPELINE_VERSION)

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
        private const val MEDIA_PIPELINE_VERSION = 9
        internal const val SILHOUETTE_PIPELINE_VERSION = 3
        private const val MIN_SYNC_INTERVAL_MS = 30_000L
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

internal fun catalogueRefreshErrorCode(error: Exception): String = when (error) {
    is RemoteDataException.HttpFailure -> when (error.statusCode) {
        429 -> "rate_limited"
        else -> "remote_http_${error.statusCode}"
    }
    is RemoteDataException.InvalidResponse -> "invalid_response"
    is RemoteDataException.NetworkFailure -> "network_unavailable"
    is java.net.SocketTimeoutException -> "network_timeout"
    is java.io.IOException -> "network_unavailable"
    else -> error.javaClass.simpleName.takeIf(String::isNotBlank) ?: "unknown_error"
}

internal fun catalogueRevision(entries: List<Pair<Long, String>>): String {
    val denominator = entries.sortedWith(compareBy<Pair<Long, String>>({ it.second }, { it.first }))
        .joinToString("|") { (taxonId, scope) -> "$scope:$taxonId" }
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(denominator.toByteArray(Charsets.UTF_8))
        .take(6)
        .joinToString("") { "%02x".format(it) }
    return "catalonia-provisional-v4-$digest"
}

internal fun closerSilhouette(
    first: SilhouetteAsset?,
    second: SilhouetteAsset?,
): SilhouetteAsset? = listOfNotNull(first, second).maxByOrNull {
    when (it.matchRank) {
        "species" -> 5
        "genus" -> 4
        "family" -> 3
        "order" -> 2
        "group" -> 1
        else -> 0
    }
}
