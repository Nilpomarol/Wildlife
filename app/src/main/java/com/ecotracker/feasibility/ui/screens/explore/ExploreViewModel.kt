package com.wildlife.feasibility.ui.screens.explore

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.Observer
import androidx.work.WorkInfo
import com.wildlife.feasibility.AccountStore
import com.wildlife.feasibility.CollectionProjection
import com.wildlife.feasibility.CollectionSpecies
import com.wildlife.feasibility.ExtraDiscoveryContext
import com.wildlife.feasibility.ExtraDiscoveryProjection
import com.wildlife.feasibility.RegionContextStore
import com.wildlife.feasibility.InstalledRegionalCatalogue
import com.wildlife.feasibility.InstalledRegionalTaxon
import com.wildlife.feasibility.NearbySpecies
import java.time.LocalDate
import com.wildlife.feasibility.NearbyDiscoveryStore
import com.wildlife.feasibility.NearbyCachePolicy
import com.wildlife.feasibility.ObservationStore
import com.wildlife.feasibility.OnDeviceWildlifeRepository
import com.wildlife.feasibility.OffCatalogueTaxonEnricher
import com.wildlife.feasibility.RemoteTaxonStore
import com.wildlife.feasibility.PublishedContentRepositories
import com.wildlife.feasibility.SyncedObservation
import com.wildlife.feasibility.TaxonDetails
import com.wildlife.feasibility.toLocalTaxonDetails
import com.wildlife.feasibility.LocalMediaStore
import com.wildlife.feasibility.MediaPrefetchScheduler
import com.wildlife.feasibility.InstalledRegionalAchievement
import com.wildlife.feasibility.ProgressionProjection
import com.wildlife.feasibility.ProgressionState
import com.wildlife.feasibility.ProgressionStore
import com.wildlife.feasibility.EncounterRarity
import com.wildlife.feasibility.ui.components.SpeciesCardRarity
import com.wildlife.feasibility.MediaPrefetchStore
import com.wildlife.feasibility.MediaPrefetchSummary
import com.wildlife.feasibility.ui.components.SpeciesCardModel
import com.wildlife.feasibility.ui.components.SpeciesCardPhotoKind
import com.wildlife.feasibility.ui.components.SpeciesCardStatus
import com.wildlife.feasibility.ui.ProjectionLoadGate
import com.wildlife.feasibility.ui.screens.collection.taxonGroupFor
import com.wildlife.feasibility.ui.screens.map.PersonalObservationMap
import com.wildlife.feasibility.ui.screens.map.PersonalObservationMapProjection
import com.wildlife.feasibility.ui.screens.map.RegionalMapProgress
import com.wildlife.feasibility.ui.screens.map.RegionalMapProgressProjection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ExploreSpecies(
    val taxonId: Long,
    val taxonGroup: String?,
    val commonName: String?,
    val scientificName: String,
    val observed: Boolean,
    val encounterRarity: EncounterRarity? = null,
    val card: SpeciesCardModel,
    /** Validated local catalogue photo reserved for Home Near me and source-bearing Detail. */
    val localReferencePhotoUrl: String? = null,
    val localReferencePhotoAttribution: String? = null,
    val localReferencePhotoLicenseCode: String? = null,
)

/** The frozen regional content pack currently used by Explore. */
data class RegionalExploreCatalogue(
    val regionKey: String,
    val displayName: String,
    val version: String,
    val speciesCount: Int,
)

data class ExploreUiState(
    val isLoading: Boolean = false,
    val browsedCatalogue: RegionalExploreCatalogue? = null,
    val installedCatalogues: List<InstalledRegionalCatalogue> = emptyList(),
    val entries: List<ExploreSpecies> = emptyList(),
    /** Confirmed species outside the browsed frozen catalogue; excluded from all guide totals. */
    val extraDiscoveries: List<ExploreSpecies> = emptyList(),
    val achievements: List<InstalledRegionalAchievement> = emptyList(),
    val observedRegionalTaxa: Set<Long> = emptySet(),
    val totalXp: Int = 0,
    val progression: ProgressionState? = null,
    /** Current-region entries used only to scope and decorate Home/Explore Near me results. */
    val nearbyCatalogueEntries: List<ExploreSpecies> = emptyList(),
    val accountLinked: Boolean = false,
    val personalMap: PersonalObservationMap = PersonalObservationMapProjection.build(emptyList()),
    val regionalMapProgress: List<RegionalMapProgress> = emptyList(),
    val nearby: NearbyDiscoveryState = NearbyDiscoveryState(),
    val errorMessage: String? = null,
    val mediaPrefetch: MediaPrefetchSummary = MediaPrefetchSummary(0, 0, 0, 0),
) {
    val observedCount: Int get() = entries.count(ExploreSpecies::observed)
}

data class NearbyDiscoveryState(
    val requested: Boolean = false,
    val loading: Boolean = false,
    val species: List<NearbySpecies> = emptyList(),
    val errorMessage: String? = null,
    val radiusKm: Int = 25,
    /** When this answer was fetched, so the UI can say how old it is. Null when never searched. */
    val fetchedAtMs: Long? = null,
    /** True while showing a restored answer that has not been re-checked this session. */
    val fromCache: Boolean = false,
)

internal object ExploreProjection {
    fun regionalEntries(
        taxa: List<InstalledRegionalTaxon>,
        observations: List<SyncedObservation>,
        detailsByTaxon: Map<Long, TaxonDetails> = emptyMap(),
        achievements: List<InstalledRegionalAchievement> = emptyList(),
    ): List<ExploreSpecies> {
        val collectionByTaxon = CollectionProjection.species(observations)
            .mapNotNull { entry -> entry.taxonId?.let { it to entry } }
            .toMap()
        val achievementTypesByTaxon = achievements
            .flatMap { achievement -> achievement.taxonIds.map { it to achievement.label } }
            .groupBy({ it.first }, { it.second })
        return taxa.map { taxon ->
            val collected = collectionByTaxon[taxon.taxonId]
            val details = detailsByTaxon[taxon.taxonId]
            val personalPhoto = collected?.photoUrl
            // Reference photography belongs to detail. The guide stays silhouette-first and
            // only shows a photograph when it is the user's own observation.
            val displayedPhoto = personalPhoto.takeIf { collected != null }
            val status = when {
                collected?.bestQualityGrade == "research" -> SpeciesCardStatus.RESEARCH_GRADE
                collected != null -> SpeciesCardStatus.OBSERVED
                else -> SpeciesCardStatus.NONE
            }
            val silhouetteUrl = details?.silhouetteLocalUri ?: details?.silhouetteUrl
            ExploreSpecies(
                taxonId = taxon.taxonId,
                taxonGroup = taxonGroupFor(taxon.taxonClass),
                commonName = taxon.commonName,
                scientificName = taxon.scientificName,
                observed = collected != null,
                encounterRarity = taxon.rarity,
                card = SpeciesCardModel(
                    key = "taxon:${taxon.taxonId}",
                    collected = collected != null,
                    label = taxon.commonName,
                    supportingText = taxon.scientificName,
                    photoUrl = displayedPhoto,
                    photoKind = displayedPhoto?.let { SpeciesCardPhotoKind.PERSONAL },
                    photoAttribution = null,
                    silhouetteUrl = silhouetteUrl,
                    silhouetteFallbackUrl = details?.silhouetteFallbackUrl,
                    silhouetteMatchRank = details?.silhouetteMatchRank,
                    fallbackSilhouetteGroup = taxonGroupFor(taxon.taxonClass),
                    supportingTextItalic = true,
                    status = status,
                    rarity = when (taxon.rarity) {
                        EncounterRarity.COMMON -> SpeciesCardRarity.COMMON
                        EncounterRarity.UNCOMMON -> SpeciesCardRarity.UNCOMMON
                        EncounterRarity.RARE -> SpeciesCardRarity.RARE
                        EncounterRarity.VERY_RARE -> SpeciesCardRarity.VERY_RARE
                        EncounterRarity.UNKNOWN -> null
                    },
                    regionalEssential = "essentials" in achievementTypesByTaxon[taxon.taxonId].orEmpty(),
                    regionalIcon = "icons" in achievementTypesByTaxon[taxon.taxonId].orEmpty(),
                ),
                localReferencePhotoUrl = details?.photoUrl,
                localReferencePhotoAttribution = details?.photoAttribution,
                localReferencePhotoLicenseCode = details?.photoLicenseCode,
            )
        }
    }

    fun extraEntries(
        entries: List<CollectionSpecies>,
        detailsByTaxon: Map<Long, TaxonDetails> = emptyMap(),
        context: ExtraDiscoveryContext,
    ): List<ExploreSpecies> = entries.mapNotNull { collected ->
        val taxonId = collected.taxonId ?: return@mapNotNull null
        val details = detailsByTaxon[taxonId]
        val commonName = details?.commonName ?: collected.label
        val scientificName = details?.scientificName ?: collected.scientificName
            ?: "Scientific name unavailable"
        val group = taxonGroupFor(details?.taxonGroup.orEmpty()) ?: collected.taxonGroup
        val photo = collected.photoUrl
        ExploreSpecies(
            taxonId = taxonId,
            taxonGroup = group,
            commonName = commonName,
            scientificName = scientificName,
            observed = true,
            encounterRarity = null,
            card = SpeciesCardModel(
                key = "extra:${context.regionKey}:${context.catalogueVersion}:$taxonId",
                label = commonName,
                supportingText = scientificName,
                photoUrl = photo,
                photoKind = photo?.let { SpeciesCardPhotoKind.PERSONAL },
                silhouetteUrl = details?.silhouetteLocalUri,
                silhouetteFallbackUrl = details?.silhouetteFallbackUrl ?: details?.silhouetteUrl,
                silhouetteMatchRank = details?.silhouetteMatchRank,
                fallbackSilhouetteGroup = group,
                supportingTextItalic = scientificName != "Scientific name unavailable",
                collected = true,
                status = if (collected.bestQualityGrade == "research") {
                    SpeciesCardStatus.RESEARCH_GRADE
                } else {
                    SpeciesCardStatus.OBSERVED
                },
                extraDiscoveryLabel = "Extra",
                extraDiscoveryDescription =
                    "Extra discovery in ${context.regionName}, catalogue ${context.catalogueVersion}",
            ),
        )
    }.sortedBy { (it.commonName ?: it.scientificName).lowercase() }
}

internal object NearbyDiscoveryProjection {
    fun withinCatalogue(
        nearby: List<NearbySpecies>,
        catalogue: List<ExploreSpecies>,
    ): List<NearbySpecies> {
        val catalogueTaxa = catalogue.mapTo(hashSetOf(), ExploreSpecies::taxonId)
        return nearby.filter { it.taxonId in catalogueTaxa }
    }

    fun withLocalMedia(
        nearby: List<NearbySpecies>,
        catalogue: List<ExploreSpecies>,
    ): List<NearbySpecies> {
        val cards = catalogue.associateBy(ExploreSpecies::taxonId)
        return nearby.map { species ->
            val entry = cards[species.taxonId]
            val card = entry?.card
            val localReferencePhotoUrl = entry?.localReferencePhotoUrl
            species.copy(
                // The API default remains discovery evidence. Home gets the validated local
                // catalogue asset whenever one is available, avoiding the wall of silhouettes
                // caused by the mostly non-commercial default-photo licences.
                photoUrl = localReferencePhotoUrl ?: species.photoUrl,
                photoAttribution = if (localReferencePhotoUrl != null) {
                    entry?.localReferencePhotoAttribution
                } else {
                    species.photoAttribution
                },
                photoLicenseCode = if (localReferencePhotoUrl != null) {
                    entry?.localReferencePhotoLicenseCode
                } else {
                    species.photoLicenseCode
                },
                personalPhotoUrl = card?.photoUrl
                    .takeIf { card?.photoKind == SpeciesCardPhotoKind.PERSONAL },
                silhouetteUrl = card?.silhouetteUrl ?: card?.silhouetteFallbackUrl,
                silhouetteMatchRank = card?.silhouetteMatchRank,
            )
        }
    }
}

class ExploreViewModel(application: Application) : AndroidViewModel(application) {
    private val mediaWork = MediaPrefetchScheduler.workInfos(application)
    private val mediaObserver = Observer<List<WorkInfo>> { refreshMediaProgress() }
    private var mediaSummaryJob: Job? = null
    private var loadJob: Job? = null
    private val loadGate = ProjectionLoadGate()
    private var visiblePrefetchJob: Job? = null
    private var visibleDemand: Pair<String, Set<Long>>? = null
    /**
     * Seeded from the cache, so Home and Explore open with the last answer rather than a
     * button. A restored answer stays marked [NearbyDiscoveryState.fromCache] until something
     * re-checks it.
     */
    private var nearbyState = NearbyDiscoveryState()
    private var nearbyInitialized = false
    private var pendingNearbyStaleCheck = false
    private var pendingNearbyLatitude: Double? = null
    private var pendingNearbyLongitude: Double? = null
    var uiState by mutableStateOf(ExploreUiState(isLoading = true))
        private set

    init {
        mediaWork.observeForever(mediaObserver)
        refreshLocal()
    }

    private fun restoreNearby(): NearbyDiscoveryState {
        val cached = NearbyDiscoveryStore(getApplication()).load() ?: return NearbyDiscoveryState()
        return NearbyDiscoveryState(
            requested = true,
            loading = false,
            species = cached.species,
            radiusKm = cached.radiusKm,
            fetchedAtMs = cached.fetchedAtMs,
            fromCache = true,
        )
    }

    /**
     * Re-runs the search only if the cached answer no longer applies here and now.
     *
     * [latitude]/[longitude] are null when the caller could not read a location without
     * prompting. The cache is then judged on age and month alone, so someone who has not
     * granted location keeps their last answer instead of being shown an empty section.
     *
     * Deliberately does nothing while a search is already in flight. Cache I/O and policy
     * evaluation run away from the UI thread.
     */
    fun refreshNearbyIfStale(latitude: Double?, longitude: Double?) {
        if (uiState.nearby.loading) return
        if (uiState.isLoading) {
            pendingNearbyStaleCheck = true
            pendingNearbyLatitude = latitude
            pendingNearbyLongitude = longitude
            return
        }
        if (uiState.browsedCatalogue == null) return
        val radiusKm = uiState.nearby.radiusKm
        viewModelScope.launch {
            val shouldRefresh = withContext(Dispatchers.IO) {
                val cached = NearbyDiscoveryStore(getApplication()).load()
                    ?: return@withContext false
                !NearbyCachePolicy.isFresh(
                    cached = cached,
                    nowMs = System.currentTimeMillis(),
                    currentMonth = LocalDate.now().monthValue,
                    currentLatitude = latitude,
                    currentLongitude = longitude,
                    radiusKm = radiusKm,
                )
            }
            // Without a coordinate there is nothing to search with, so the stale answer stays up
            // rather than being replaced by an error the user cannot act on.
            if (shouldRefresh && latitude != null && longitude != null) {
                discoverNearby(latitude, longitude)
            }
        }
    }

    fun refreshLocal() {
        val revision = loadGate.next()
        val shouldRestoreNearby = !nearbyInitialized
        val nearbySnapshot = nearbyState
        loadJob?.cancel()
        uiState = uiState.copy(isLoading = true, errorMessage = null)
        loadJob = viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val nearby = if (shouldRestoreNearby) restoreNearby() else nearbySnapshot
                loadLocal(nearby)
            }
            if (loadGate.isLatest(revision)) {
                if (!nearbyInitialized) {
                    nearbyState = loaded.nearby
                    nearbyInitialized = true
                }
                uiState = loaded.copy(isLoading = false, nearby = nearbyState)
                val missingExtraTaxa = loaded.extraDiscoveries.mapNotNull { entry ->
                    entry.taxonId.takeIf { entry.scientificName == "Scientific name unavailable" }
                }
                val improved = withContext(Dispatchers.IO) {
                    OffCatalogueTaxonEnricher.enrich(getApplication(), missingExtraTaxa)
                }
                if (improved && loadGate.isLatest(revision)) {
                    uiState = withContext(Dispatchers.IO) { loadLocal(nearbyState) }
                        .copy(isLoading = false, nearby = nearbyState)
                }
                if (pendingNearbyStaleCheck) {
                    pendingNearbyStaleCheck = false
                    val latitude = pendingNearbyLatitude
                    val longitude = pendingNearbyLongitude
                    pendingNearbyLatitude = null
                    pendingNearbyLongitude = null
                    refreshNearbyIfStale(latitude, longitude)
                }
            }
        }
    }

    /**
     * Converts the grid's actual viewport into bounded, debounced media demand. Catalogue
     * projection stays read-only: scrolling is the only routine source of visible-row work.
     */
    fun prioritizeVisibleTaxa(taxonIds: Set<Long>) {
        val regionKey = uiState.browsedCatalogue?.regionKey ?: return
        val boundedIds = taxonIds.asSequence().take(MAX_VISIBLE_PREFETCH_COUNT).toSet()
        if (boundedIds.isEmpty()) return
        val demand = regionKey to boundedIds
        if (visibleDemand == demand) return
        visibleDemand = demand
        visiblePrefetchJob?.cancel()
        visiblePrefetchJob = viewModelScope.launch {
            delay(VISIBLE_PREFETCH_DEBOUNCE_MS)
            withContext(Dispatchers.IO) {
                MediaPrefetchScheduler.prioritizeBrowsedVisible(
                    getApplication(), regionKey, boundedIds,
                )
            }
            if (uiState.browsedCatalogue?.regionKey == regionKey) refreshMediaProgress()
        }
    }

    /**
     * Explore owns only the guide being browsed. This never changes the location-derived current
     * region used for progress and background preparation.
     */
    fun selectRegion(regionKey: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                RegionContextStore(getApplication()).selectBrowsedRegion(regionKey)
            }
            visibleDemand = null
            refreshLocal()
        }
    }

    fun setObservationMapVisible(observationUuid: String, visible: Boolean) {
        val account = AccountStore(getApplication()).verified() ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                OnDeviceWildlifeRepository(getApplication()).use {
                    it.setObservationMapVisible(account, observationUuid, visible)
                }
            }
            refreshLocal()
        }
    }

    fun nearbyLocationStarted() {
        if (!uiState.nearby.loading) {
            nearbyInitialized = true
            nearbyState = uiState.nearby.copy(
                requested = true,
                loading = true,
                errorMessage = null,
            )
            uiState = uiState.copy(
                nearby = nearbyState,
            )
        }
    }

    fun nearbyLocationFailed(message: String) {
        nearbyInitialized = true
        nearbyState = uiState.nearby.copy(
            requested = true,
            loading = false,
            errorMessage = message,
        )
        uiState = uiState.copy(
            nearby = nearbyState,
        )
    }

    fun discoverNearby(latitude: Double, longitude: Double) {
        nearbyLocationStarted()
        val catalogue = uiState.nearbyCatalogueEntries.ifEmpty { uiState.entries }
        val radiusKm = uiState.nearby.radiusKm
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val species = OnDeviceWildlifeRepository(getApplication()).use {
                        it.discoverNearbySpecies(
                            latitude = latitude,
                            longitude = longitude,
                            radiusKm = radiusKm,
                        )
                    }
                    val within = NearbyDiscoveryProjection.withinCatalogue(species, catalogue)
                    val displayed = NearbyDiscoveryProjection.withLocalMedia(within, catalogue)
                    val fetchedAtMs = System.currentTimeMillis()
                    NearbyDiscoveryStore(getApplication()).save(
                        latitude = latitude,
                        longitude = longitude,
                        radiusKm = radiusKm,
                        month = LocalDate.now().monthValue,
                        fetchedAtMs = fetchedAtMs,
                        species = within,
                    )
                    displayed to fetchedAtMs
                }
            }
            nearbyState = result.fold(
                    onSuccess = { (within, fetchedAtMs) ->
                        uiState.nearby.copy(
                            requested = true,
                            loading = false,
                            species = within,
                            errorMessage = null,
                            fetchedAtMs = fetchedAtMs,
                            fromCache = false,
                        )
                    },
                    onFailure = { error ->
                        // The previous answer is kept on screen. A failed re-check is not a
                        // reason to discard a result that was good a moment ago.
                        uiState.nearby.copy(
                            requested = true,
                            loading = false,
                            errorMessage = error.message
                                ?: "Nearby species could not be checked. Try again with coverage.",
                        )
                    },
                )
            uiState = uiState.copy(
                nearby = nearbyState,
            )
        }
    }

    private fun loadLocal(nearby: NearbyDiscoveryState): ExploreUiState = runCatching {
        val context = getApplication<Application>()
        val content = PublishedContentRepositories.application(context)
        val catalogues = content.catalogues()
        val regionStore = RegionContextStore(context)
        val installedKeys = catalogues.map { it.regionKey }
        val selectedKey = regionStore.browsedRegionKey(
            installedKeys,
        ) ?: error("No regional catalogue is installed.")
        val currentKey = regionStore.currentRegionKey(installedKeys.toSet()) ?: selectedKey
        val selected = catalogues.first { it.regionKey == selectedKey }
        val mediaStore = LocalMediaStore(context)
        fun detailsFor(regionKey: String) = content.taxaContent(regionKey)
            .mapValues { (_, taxon) -> taxon.toLocalTaxonDetails(mediaStore) }
        val detailsByTaxon = detailsFor(selectedKey)
        val account = AccountStore(context).verified()
        val observationStore = account?.let { ObservationStore(context) }
        val observations = account?.let { observationStore?.observations(it.userId) }.orEmpty()
        val observationRegionsByUuid = account?.let { linked ->
            observations.mapNotNull { observation ->
                observationStore?.observationRegion(linked.userId, observation.uuid)
                    ?.let { assignment -> observation.uuid to assignment }
            }.toMap()
        }.orEmpty()
        fun regionalObservations(regionKey: String) = account?.let {
            observations.filter { observation ->
                observationRegionsByUuid[observation.uuid]?.let { assignment ->
                    assignment.earnsRegionalProgress && assignment.regionKey == regionKey
                } == true
            }
        }.orEmpty()
        val hiddenObservationUuids = account?.let {
            observationStore?.mapHiddenObservationUuids(it.userId)
        }.orEmpty()
        val summary = account?.let { observationStore?.summary(it.userId) }
        val taxaByRegion = catalogues.associate { catalogue ->
            catalogue.regionKey to content.taxa(catalogue.regionKey)
        }
        val regionalMapProgress = RegionalMapProgressProjection.build(
            catalogues = catalogues,
            taxaByRegion = taxaByRegion,
            achievementsByRegion = catalogues.associate { catalogue ->
                catalogue.regionKey to content.achievements(catalogue.regionKey)
            },
            observations = observations,
            assignmentsByObservationUuid = observationRegionsByUuid,
        )
        observationStore?.close()
        val entries = ExploreProjection.regionalEntries(
            taxa = content.taxa(selectedKey), observations = regionalObservations(selectedKey),
            detailsByTaxon = detailsByTaxon,
            achievements = content.achievements(selectedKey),
        )
        val extraContextsByTaxon = ExtraDiscoveryProjection.contextsByTaxon(
            observations = observations,
            regionsByObservationUuid = observationRegionsByUuid,
            catalogues = catalogues,
            catalogueTaxaByRegion = taxaByRegion.mapValues { (_, taxa) ->
                taxa.mapTo(mutableSetOf()) { it.taxonId }
            },
        )
        val selectedExtraContexts = extraContextsByTaxon.mapNotNull { (taxonId, contexts) ->
            contexts.firstOrNull {
                it.regionKey == selected.regionKey && it.catalogueVersion == selected.version
            }?.let { taxonId to it }
        }.toMap()
        val selectedExtraDetails = RemoteTaxonStore(context).use { remote ->
            selectedExtraContexts.keys.mapNotNull { taxonId ->
                val detail = content.taxon(taxonId)?.toLocalTaxonDetails(mediaStore)
                    ?: remote.cached(taxonId)
                detail?.let { taxonId to it }
            }.toMap()
        }
        val extraEntries = ExploreProjection.extraEntries(
            entries = CollectionProjection.species(regionalObservations(selectedKey))
                .filter { it.taxonId in selectedExtraContexts }
                .map { entry ->
                    entry.copy(
                        extraDiscoveryContexts = entry.taxonId
                            ?.let(selectedExtraContexts::get)
                            ?.let(::listOf)
                            .orEmpty(),
                    )
                },
            detailsByTaxon = selectedExtraDetails,
            context = ExtraDiscoveryContext(
                selected.regionKey,
                selected.displayName,
                selected.version,
            ),
        )
        val nearbyEntries = if (currentKey == selectedKey) {
            entries
        } else {
            ExploreProjection.regionalEntries(
                taxa = content.taxa(currentKey),
                observations = regionalObservations(currentKey),
                detailsByTaxon = detailsFor(currentKey),
                achievements = content.achievements(currentKey),
            )
        }
        ExploreUiState(
            browsedCatalogue = RegionalExploreCatalogue(
                regionKey = selected.regionKey,
                displayName = selected.displayName,
                version = selected.version,
                speciesCount = content.taxa(selectedKey).size,
            ),
            installedCatalogues = catalogues,
            entries = entries,
            extraDiscoveries = extraEntries,
            achievements = content.achievements(selectedKey),
            observedRegionalTaxa = regionalObservations(selectedKey)
                .mapNotNull { it.collectionTaxonId ?: it.taxonId }
                .toSet(),
            totalXp = summary?.totalXp ?: 0,
            progression = account?.let {
                val store = ProgressionStore(context)
                ProgressionProjection.project(
                    totalXp = summary?.totalXp ?: 0,
                    selectedLevelKey = store.selectedLevelKey(it.userId),
                    highestLevelKey = store.highestLevelKey(it.userId),
                )
            },
            nearbyCatalogueEntries = nearbyEntries,
            accountLinked = account != null,
            personalMap = PersonalObservationMapProjection.build(
                observations = observations,
                hiddenObservationUuids = hiddenObservationUuids,
            ),
            regionalMapProgress = regionalMapProgress,
            nearby = nearby.copy(
                species = NearbyDiscoveryProjection.withLocalMedia(nearby.species, nearbyEntries),
            ),
            errorMessage = null,
            mediaPrefetch = MediaPrefetchStore(context).use {
                it.summary(content.generation().generationId, selectedKey)
            },
        )
    }.getOrElse { error ->
        ExploreUiState(
            nearby = nearby,
            errorMessage = error.message ?: "The offline catalogue could not be read.",
        )
    }

    private fun refreshMediaProgress() {
        val regionKey = uiState.browsedCatalogue?.regionKey ?: return
        mediaSummaryJob?.cancel()
        mediaSummaryJob = viewModelScope.launch {
            val summary = runCatching {
                withContext(Dispatchers.IO) {
                    val context = getApplication<Application>()
                    val generation = PublishedContentRepositories.application(context)
                        .generation().generationId
                    MediaPrefetchStore(context).use { it.summary(generation, regionKey) }
                }
            }.getOrNull() ?: return@launch
            if (uiState.browsedCatalogue?.regionKey == regionKey &&
                uiState.mediaPrefetch != summary
            ) {
                uiState = uiState.copy(mediaPrefetch = summary)
            }
        }
    }

    private companion object {
        const val MAX_VISIBLE_PREFETCH_COUNT = 40
        const val VISIBLE_PREFETCH_DEBOUNCE_MS = 150L
    }

    override fun onCleared() {
        mediaWork.removeObserver(mediaObserver)
        super.onCleared()
    }
}
