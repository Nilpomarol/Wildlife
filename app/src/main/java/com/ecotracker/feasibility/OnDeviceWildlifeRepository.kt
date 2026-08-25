package com.wildlife.feasibility

import android.content.Context
import android.text.Html
import org.json.JSONObject
import java.util.Locale
import java.time.LocalDate
import java.io.Closeable

class OnDeviceWildlifeRepository(context: Context) : Closeable {
    private val appContext = context.applicationContext
    private val observations = ObservationStore(appContext)
    private val regionalBoundaries by lazy { RegionalBoundaryAssetLoader.load(appContext) }
    private val remoteTaxa = RemoteTaxonStore(appContext)
    private val iNaturalist = INaturalistClient()
    private val phyloPic = PhyloPicClient()
    private val wikimedia = WikimediaCommonsClient()
    private val localMedia = LocalMediaStore(appContext)
    private val rejectedReferenceMedia = ReferenceMediaRejectionStore(appContext)

    override fun close() {
        observations.close()
        remoteTaxa.close()
        rejectedReferenceMedia.close()
    }

    fun syncObservations(account: VerifiedAccount, force: Boolean = false): ObservationSyncResult {
        val cachedSummary = observations.summary(account.userId)
        if (
            !force &&
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
        val assignments = remote.map { observation ->
            regionalBoundaries.assign(
                observationUuid = observation.uuid,
                latitude = observation.latitude,
                longitude = observation.longitude,
                obscured = observation.obscured,
            )
        }
        return observations.replaceSnapshot(account.userId, remote, assignments)
    }

    fun confirmObservation(
        account: VerifiedAccount,
        observationUuid: String,
    ): ObservationConfirmationResult {
        val observation = observations.observations(account.userId).firstOrNull { it.uuid == observationUuid }
        val assignment = observations.observationRegion(account.userId, observationUuid)
        val reward = observation?.let { item ->
            assignment?.takeIf { it.earnsRegionalProgress }?.regionKey?.let { regionKey ->
                val content = PublishedContentRepositories.application(appContext)
                val catalogue = content.catalogues().firstOrNull { it.regionKey == regionKey } ?: return@let null
                val taxonId = item.collectionTaxonId ?: item.taxonId ?: return@let null
                val regionalTaxon = content.taxa(regionKey).firstOrNull { it.taxonId == taxonId } ?: return@let null
                val achievements = content.achievements(regionKey)
                RegionalRewardContext(
                    regionKey, catalogue.version, taxonId, regionalTaxon.rarity,
                    achievements.firstOrNull { it.label == "essentials" }?.taxonIds.orEmpty(),
                    achievements.firstOrNull { it.label == "icons" }?.taxonIds.orEmpty(),
                )
            }
        }
        return observations.confirmObservation(account.userId, observationUuid, reward)
    }

    fun setObservationMapVisible(
        account: VerifiedAccount,
        observationUuid: String,
        visible: Boolean,
    ) = observations.setObservationMapVisible(account.userId, observationUuid, visible)

    fun discoverNearbySpecies(
        latitude: Double,
        longitude: Double,
        radiusKm: Int = 25,
    ): List<NearbySpecies> = iNaturalist.nearbySpecies(
        latitude = latitude,
        longitude = longitude,
        radiusKm = radiusKm,
        month = LocalDate.now().monthValue,
    )

    fun syncTaxonDetail(taxonId: Long, force: Boolean = false): TaxonDetails {
        check(PublishedContentRepositories.application(appContext).taxon(taxonId) == null) {
            "Published taxa must use targeted media repair."
        }
        return syncTaxonMedia(taxonId, force, recoverPhoto = true, recoverSilhouette = true)
    }

    /**
     * Repairs only media absent from the immutable published manifest. The recovered metadata
     * remains an overlay in [RemoteTaxonStore]; it never mutates catalogue identity or content.
     */
    fun repairPublishedTaxonMedia(taxonId: Long, force: Boolean = false): TaxonDetails {
        val published = PublishedContentRepositories.application(appContext).taxon(taxonId)
            ?: return syncTaxonDetail(taxonId, force)
        val needs = published.mediaRepairNeeds()
        return syncTaxonMedia(
            taxonId = taxonId,
            force = force,
            recoverPhoto = needs.photo,
            recoverSilhouette = needs.silhouette,
        )
    }

    /**
     * Finds a different, licence-compatible Commons photo for one published taxon. The frozen
     * catalogue is never rewritten on device; a successful choice is persisted as a media-only
     * overlay and therefore survives restarts and content reads without changing biological data.
     */
    fun requestAlternativePublishedPhoto(taxonId: Long): AlternativePhotoResult {
        val published = PublishedContentRepositories.application(appContext).taxon(taxonId)
            ?: return AlternativePhotoResult.NotAvailable
        val cached = remoteTaxa.cached(taxonId)
        val publishedSource = published.preferredPhoto()?.sourceUrl
        val currentSource = cached?.photoSourceUrl?.takeIf {
            cached.photoRecoveryStatus == USER_REQUESTED_ALTERNATIVE || publishedSource == null
        } ?: publishedSource
        rejectedReferenceMedia.reject(
            taxonId = taxonId,
            sourceUrl = currentSource,
            catalogueGenerationId = PublishedContentRepositories.application(appContext)
                .generation().generationId,
        )
        val excluded = buildSet {
            addAll(rejectedReferenceMedia.sourceUrls(taxonId))
            publishedSource?.let(::add)
            cached?.photoSourceUrl?.let(::add)
        }
        val raw = iNaturalist.taxa(listOf(taxonId)).firstOrNull()
            ?: return AlternativePhotoResult.NotAvailable
        val local = normalizeTaxonDetails(raw, null, System.currentTimeMillis())
            ?: return AlternativePhotoResult.NotAvailable
        val resolution = resolveMedia(local.wikipediaUrl.orEmpty()) {
            wikimedia.curatedPhoto(
                wikipediaUrl = local.wikipediaUrl,
                scientificName = local.scientificName,
                iNaturalistTaxonId = taxonId,
                excludedSourceUrls = excluded,
            )
        }
        val photo = resolution.asset ?: return if (resolution.hadTemporaryFailure) {
            AlternativePhotoResult.TemporaryFailure
        } else {
            AlternativePhotoResult.NotAvailable
        }
        val stored = localMedia.storePhoto(photo.url, taxonId)
            ?: return AlternativePhotoResult.TemporaryFailure
        val replacement = local.copy(
            photoUrl = photo.url,
            photoLocalUri = stored,
            photoAttribution = photo.attribution,
            photoLicenseCode = photo.licenceCode,
            photoSourceUrl = photo.sourceUrl,
            photoRecoveryStatus = USER_REQUESTED_ALTERNATIVE,
            photoPipelineVersion = MEDIA_PIPELINE_VERSION,
            mediaPipelineVersion = MEDIA_PIPELINE_VERSION,
        ).let { value ->
            cached?.silhouetteAsset()?.let { silhouette -> value.withSilhouette(silhouette) }
                ?: value
        }
        remoteTaxa.store(replacement)
        return AlternativePhotoResult.Found
    }

    private fun syncTaxonMedia(
        taxonId: Long,
        force: Boolean,
        recoverPhoto: Boolean,
        recoverSilhouette: Boolean,
    ): TaxonDetails {
        val cached = remoteTaxa.cached(taxonId)
        val fresh = cached?.updatedAtMs?.let {
            System.currentTimeMillis() - it < TAXON_REFRESH_MS
        } == true
        val photoStageFresh = !recoverPhoto || (
            cached?.photoPipelineVersion == MEDIA_PIPELINE_VERSION &&
                (cached.photoUrl == null || localMedia.isStored(cached.photoLocalUri))
            )
        val silhouetteStageFresh = !recoverSilhouette || (
            cached?.silhouetteResolverVersion == SILHOUETTE_PIPELINE_VERSION &&
                (cached.silhouetteUrl == null || localMedia.isStored(cached.silhouetteLocalUri))
            )
        if (
            !force && fresh && photoStageFresh && silhouetteStageFresh
        ) return checkNotNull(cached)

        val raw = iNaturalist.taxa(listOf(taxonId)).firstOrNull()
            ?: error("Unknown iNaturalist taxon $taxonId")
        var detail = normalizeTaxonDetails(raw, null, System.currentTimeMillis())
            ?: error("Incomplete iNaturalist taxon $taxonId")
        var curatedPhotoResolution = MediaResolution<CuratedReferencePhoto>(null)
        if (recoverPhoto) {
            curatedPhotoResolution = resolveMedia(detail.wikipediaUrl.orEmpty()) {
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
        } else {
            detail = cached?.let { detail.withPhotoFrom(it) } ?: detail.withoutPhoto()
        }
        val silhouetteResolution = if (recoverSilhouette) {
            resolveFirstMedia(silhouetteCandidates(raw)) { (name, rank) ->
                phyloPic.resolve(name, rank)
            }
        } else {
            MediaResolution(cached?.silhouetteAsset())
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
        val silhouetteStageComplete = !recoverSilhouette || (
            !silhouetteResolution.hadTemporaryFailure &&
                (silhouette == null || localMedia.isStored(silhouette.localUri))
            )
        detail = detail.copy(
            silhouetteResolverVersion = if (recoverSilhouette) {
                SILHOUETTE_PIPELINE_VERSION.takeIf { silhouetteStageComplete }
            } else {
                cached?.silhouetteResolverVersion
            },
            mediaPipelineVersion = MEDIA_PIPELINE_VERSION
                .takeUnless {
                    (recoverPhoto && detail.photoPipelineVersion != MEDIA_PIPELINE_VERSION) ||
                        (recoverSilhouette && !silhouetteStageComplete)
                },
        )
        remoteTaxa.store(detail)
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

    private fun TaxonDetails.withPhotoFrom(value: TaxonDetails) = copy(
        photoUrl = value.photoUrl,
        photoAttribution = value.photoAttribution,
        photoLicenseCode = value.photoLicenseCode,
        photoSourceUrl = value.photoSourceUrl,
        photoRecoveryStatus = value.photoRecoveryStatus,
        photoLocalUri = value.photoLocalUri,
        photoPipelineVersion = value.photoPipelineVersion,
    )

    private fun TaxonDetails.withoutPhoto() = copy(
        photoUrl = null,
        photoAttribution = null,
        photoLicenseCode = null,
        photoSourceUrl = null,
        photoRecoveryStatus = null,
        photoLocalUri = null,
        photoPipelineVersion = null,
    )

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

    companion object {
        private const val BUTTERFLIES_TAXON_ID = 47224L
        private const val ODONATA_TAXON_ID = 47792L
        internal const val MEDIA_PIPELINE_VERSION = 9
        internal const val SILHOUETTE_PIPELINE_VERSION = 3
        internal const val USER_REQUESTED_ALTERNATIVE = "user_requested_wikimedia"
        private const val MIN_SYNC_INTERVAL_MS = 30_000L
        private const val TAXON_REFRESH_MS = 30L * 24L * 60L * 60L * 1_000L
    }
}

sealed interface AlternativePhotoResult {
    data object Found : AlternativePhotoResult
    data object NotAvailable : AlternativePhotoResult
    data object TemporaryFailure : AlternativePhotoResult
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
