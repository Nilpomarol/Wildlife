package com.wildlife.feasibility

import android.content.Context

data class PublishedContentGeneration(
    val generationId: String,
    val generationSequence: Long,
    val schemaVersion: Int,
    val sourceDigest: String,
    val releaseStatus: String,
    val generatedAt: String,
    val minimumAppVersion: Int,
)

data class PublishedTaxonDescription(
    val locale: String,
    val summary: String,
    val sourceUrl: String,
    val attribution: String,
    val licenceCode: String,
    val licenceUrl: String,
)

data class PublishedTaxonConservation(
    val status: String,
    val authority: String,
    val sourceUrl: String,
    val retrievedAt: String,
)

data class PublishedMediaVariant(
    val variant: String,
    val directUrl: String,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val expectedBytes: Long?,
    val contentSha256: String?,
)

data class PublishedMediaAsset(
    val assetId: String,
    val mediaType: String,
    val provider: String,
    val providerAssetId: String,
    val sourceUrl: String,
    val creator: String?,
    val licenceCode: String,
    val licenceUrl: String?,
    val matchRank: String,
    val matchedTaxonName: String,
    val variants: List<PublishedMediaVariant>,
) {
    fun variant(name: String): PublishedMediaVariant? = variants.firstOrNull { it.variant == name }
}

data class PublishedTaxonContent(
    val taxonId: Long,
    val scientificName: String,
    val commonName: String,
    val rank: String,
    val taxonClass: String,
    val description: PublishedTaxonDescription?,
    val conservation: PublishedTaxonConservation?,
    val media: List<PublishedMediaAsset>,
    val familyName: String? = null,
) {
    fun preferredPhoto(): PublishedMediaAsset? = media.firstOrNull { it.mediaType == "photo" }

    fun specificSilhouette(): PublishedMediaAsset? = media
        .filter { it.mediaType == "silhouette" && it.matchRank in setOf("species", "genus") }
        .minByOrNull { if (it.matchRank == "species") 0 else 1 }

    fun familySilhouette(): PublishedMediaAsset? = media
        .filter { it.mediaType == "silhouette" && it.matchRank in setOf("family", "order") }
        .minByOrNull { if (it.matchRank == "family") 0 else 1 }

    /** Specific and family roles are separate frozen assignments; group art is bundled. */
    fun silhouetteHierarchy(): List<PublishedMediaAsset> =
        listOfNotNull(specificSilhouette(), familySilhouette()).distinctBy { it.assetId }

    fun preferredSilhouette(): PublishedMediaAsset? = silhouetteHierarchy().firstOrNull()
}

internal data class PublishedMediaRepairNeeds(
    val photo: Boolean,
    val silhouette: Boolean,
) {
    val required: Boolean get() = photo || silhouette
}

internal fun PublishedTaxonContent.mediaRepairNeeds() = PublishedMediaRepairNeeds(
    photo = preferredPhoto() == null,
    // Published catalogue silhouettes are one frozen projection shared by grids and detail.
    // Name-based PhyloPic discovery remains available only to off-catalogue taxa.
    silhouette = false,
)

interface PublishedContentRepository {
    fun generation(): PublishedContentGeneration

    fun catalogues(): List<InstalledRegionalCatalogue>

    fun taxa(regionKey: String): List<InstalledRegionalTaxon>

    fun achievements(regionKey: String): List<InstalledRegionalAchievement>

    fun taxon(taxonId: Long, locale: String = "en"): PublishedTaxonContent?

    fun taxaContent(regionKey: String, locale: String = "en"): Map<Long, PublishedTaxonContent>

    fun mediaAssetIds(): Set<String>

    fun mediaAssets(regionKey: String): List<PublishedMediaAsset>

    fun mediaByTaxon(regionKey: String): Map<Long, List<PublishedMediaAsset>>

    fun mediaAsset(assetId: String): PublishedMediaAsset?

    fun publishedTaxonCount(): Int
}

/**
 * Process-wide owner of the immutable published-content database.
 *
 * The generated pack is one application resource, so opening and validating a new database for
 * every screen/repository call only adds contention and cannot provide fresher data. Content-pack
 * activation is handled by the same instance and atomically replaces its generation-bound handle.
 */
object PublishedContentRepositories {
    @Volatile
    private var applicationRepository: RegionalCatalogueAssetStore? = null

    fun application(context: Context): RegionalCatalogueAssetStore =
        applicationRepository ?: synchronized(this) {
            applicationRepository ?: RegionalCatalogueAssetStore(context.applicationContext)
                .also { applicationRepository = it }
        }

    internal fun resetForTesting() = synchronized(this) {
        applicationRepository?.close()
        applicationRepository = null
    }
}

/** Shared projection used by published content and the isolated off-catalogue cache. */
private fun PublishedTaxonContent.toTaxonDetails(): TaxonDetails {
    val photo = preferredPhoto()
    val detail = photo?.variant("detail") ?: photo?.variant("thumbnail")
    val silhouettes = silhouetteHierarchy()
    val silhouette = silhouettes.firstOrNull()
    val silhouetteFallback = silhouettes.drop(1).firstOrNull()
    val silhouetteVariant = silhouette?.variant("detail") ?: silhouette?.variant("thumbnail")
    val silhouetteFallbackVariant = silhouetteFallback?.variant("detail")
        ?: silhouetteFallback?.variant("thumbnail")
    return TaxonDetails(
        taxonId = taxonId,
        scientificName = scientificName,
        commonName = commonName,
        taxonGroup = taxonClass,
        familyName = familyName,
        wikipediaSummary = description?.summary,
        wikipediaUrl = description?.sourceUrl,
        conservationStatus = conservation?.status,
        conservationAuthority = conservation?.authority,
        conservationUrl = conservation?.sourceUrl,
        photoUrl = detail?.directUrl,
        photoAttribution = photo?.creator,
        photoLicenseCode = photo?.licenceCode,
        silhouetteUrl = silhouetteVariant?.directUrl,
        silhouetteSourceUrl = silhouette?.sourceUrl,
        silhouetteAttribution = silhouette?.creator,
        silhouetteLicenseCode = silhouette?.licenceCode,
        silhouetteLicenseUrl = silhouette?.licenceUrl,
        silhouetteTaxonName = silhouette?.matchedTaxonName,
        silhouetteMatchRank = silhouette?.matchRank,
        updatedAtMs = null,
        photoSourceUrl = photo?.sourceUrl,
        photoRecoveryStatus = if (photo == null) "no_compatible_photo" else photo.provider,
        mediaPipelineVersion = 2,
        photoPipelineVersion = 2,
        silhouetteResolverVersion = if (silhouette == null) null else 2,
        silhouetteFallbackUrl = silhouetteFallbackVariant?.directUrl,
        silhouetteFallbackSourceUrl = silhouetteFallback?.sourceUrl,
        silhouetteFallbackAttribution = silhouetteFallback?.creator,
        silhouetteFallbackLicenseCode = silhouetteFallback?.licenceCode,
        silhouetteFallbackLicenseUrl = silhouetteFallback?.licenceUrl,
        silhouetteFallbackTaxonName = silhouetteFallback?.matchedTaxonName,
        silhouetteFallbackMatchRank = silhouetteFallback?.matchRank,
    )
}

/** Product projection: published reference media is displayed only after store validation. */
fun PublishedTaxonContent.toLocalTaxonDetails(
    mediaStore: MediaRepository,
    recovered: TaxonDetails? = null,
): TaxonDetails {
    val base = toTaxonDetails()
    val photo = preferredPhoto()
    val silhouettes = silhouetteHierarchy()
    val publishedPhoto = photo?.let {
        mediaStore.bestLocalVariant(it, "detail") ?: mediaStore.bestLocalVariant(it, "thumbnail")
    }
    val recoveredPhoto = recovered?.takeIf {
        it.photoLocalUri != null && CataloguePhotoPolicy.detailUrl(it) != null
    }
    val userSelectedReplacement = recoveredPhoto?.takeIf {
        it.photoRecoveryStatus == OnDeviceWildlifeRepository.USER_REQUESTED_ALTERNATIVE
    }
    val localPhoto = userSelectedReplacement?.photoLocalUri ?: publishedPhoto ?: recoveredPhoto?.photoLocalUri
    val localSilhouettes = silhouettes.mapNotNull { asset ->
        val uri = mediaStore.bestLocalVariant(asset, "detail")
            ?: mediaStore.bestLocalVariant(asset, "thumbnail")
        uri?.let { asset to it }
    }
    val (primarySilhouette, localSilhouette) = localSilhouettes.firstOrNull()
        ?: (null to null)
    val (fallbackSilhouette, localSilhouetteFallback) = localSilhouettes.drop(1).firstOrNull()
        ?: (null to null)
    val usingRecoveredPhoto = userSelectedReplacement != null ||
        (publishedPhoto == null && recoveredPhoto != null)
    return base.copy(
        photoUrl = localPhoto,
        photoLocalUri = localPhoto,
        photoAttribution = if (usingRecoveredPhoto) recoveredPhoto?.photoAttribution else base.photoAttribution,
        photoLicenseCode = if (usingRecoveredPhoto) recoveredPhoto?.photoLicenseCode else base.photoLicenseCode,
        photoSourceUrl = if (usingRecoveredPhoto) recoveredPhoto?.photoSourceUrl else base.photoSourceUrl,
        photoRecoveryStatus = if (usingRecoveredPhoto || photo == null) {
            recovered?.photoRecoveryStatus ?: base.photoRecoveryStatus
        } else {
            base.photoRecoveryStatus
        },
        silhouetteUrl = localSilhouette,
        silhouetteLocalUri = localSilhouette,
        silhouetteSourceUrl = primarySilhouette?.sourceUrl,
        silhouetteAttribution = primarySilhouette?.creator,
        silhouetteLicenseCode = primarySilhouette?.licenceCode,
        silhouetteLicenseUrl = primarySilhouette?.licenceUrl,
        silhouetteTaxonName = primarySilhouette?.matchedTaxonName,
        silhouetteMatchRank = primarySilhouette?.matchRank,
        updatedAtMs = recovered?.updatedAtMs,
        mediaPipelineVersion = recovered?.mediaPipelineVersion,
        photoPipelineVersion = recovered?.photoPipelineVersion,
        silhouetteResolverVersion = recovered?.silhouetteResolverVersion,
        silhouetteFallbackUrl = localSilhouetteFallback,
        silhouetteFallbackSourceUrl = fallbackSilhouette?.sourceUrl,
        silhouetteFallbackAttribution = fallbackSilhouette?.creator,
        silhouetteFallbackLicenseCode = fallbackSilhouette?.licenceCode,
        silhouetteFallbackLicenseUrl = fallbackSilhouette?.licenceUrl,
        silhouetteFallbackTaxonName = fallbackSilhouette?.matchedTaxonName,
        silhouetteFallbackMatchRank = fallbackSilhouette?.matchRank,
    )
}
