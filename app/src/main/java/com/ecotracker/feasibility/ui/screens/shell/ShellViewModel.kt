package com.wildlife.feasibility.ui.screens.shell

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wildlife.feasibility.AccountStore
import com.wildlife.feasibility.RegionContextStore
import com.wildlife.feasibility.CollectionProjection
import com.wildlife.feasibility.CollectionSpecies
import com.wildlife.feasibility.EncounterRarity
import com.wildlife.feasibility.MarkerState
import com.wildlife.feasibility.MarkerStore
import com.wildlife.feasibility.LocalDataInventory
import com.wildlife.feasibility.LocalDataManager
import com.wildlife.feasibility.ObservationStore
import com.wildlife.feasibility.ObservationLifecyclePolicy
import com.wildlife.feasibility.ObservationQualityTransition
import com.wildlife.feasibility.ObservationRegionAssignment
import com.wildlife.feasibility.ProgressionLevel
import com.wildlife.feasibility.ProgressionProjection
import com.wildlife.feasibility.ProgressionRules
import com.wildlife.feasibility.ProgressionState
import com.wildlife.feasibility.ProgressionStore
import com.wildlife.feasibility.PublishedContentRepositories
import com.wildlife.feasibility.VerifiedAccount
import com.wildlife.feasibility.ui.ProjectionLoadGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HomeHighlight(
    val taxonId: Long?,
    val label: String,
    val photoUrl: String?,
    val observedAtMs: Long,
    val researchGrade: Boolean,
    val observationCount: Int,
    val awaitingSpeciesIdentification: Boolean,
    val scientificName: String? = null,
    val encounterRarity: EncounterRarity? = null,
    val regionSeenIn: String? = null,
)

data class RegionalHomeProgress(
    val regionKey: String = "",
    val displayName: String,
    val observedSpecies: Int,
    val totalSpecies: Int,
    val essentialsObserved: Int,
    val essentialsTotal: Int,
    val iconsObserved: Int,
    val iconsTotal: Int,
)

data class ShellUiState(
    val isLoading: Boolean = false,
    val account: VerifiedAccount? = null,
    val collectionEntries: Int = 0,
    val identifiedSpecies: Int = 0,
    val observations: Int = 0,
    val totalXp: Int = 0,
    val latestDiscovery: HomeHighlight? = null,
    val progression: ProgressionState? = null,
    val observationSyncing: Boolean = false,
    val observationSyncError: String? = null,
    val lastObservationSyncAtMs: Long? = null,
    val observationDataStale: Boolean = true,
    val pendingMatchesReady: Int = 0,
    val recentQualityTransitions: List<ObservationQualityTransition> = emptyList(),
    val catalogueSpecies: Int = 0,
    val pendingHandoffs: Int = 0,
    val draftObservations: Int = 0,
    val localData: LocalDataInventory = LocalDataInventory(),
    val regionalProgress: RegionalHomeProgress? = null,
    /**
     * Every rank in the scheme, earned or not, so Profile can print the whole ladder.
     *
     * It is state rather than something the composable reads off the rules: the screen
     * shows the ladder, it does not decide what is on it.
     */
    val rankLadder: List<ProgressionLevel> = ProgressionRules.levels,
    val errorMessage: String? = null,
)

class ShellViewModel(application: Application) : AndroidViewModel(application) {
    private var observationSyncing = false
    private var observationSyncError: String? = null
    private var loadJob: Job? = null
    private val loadGate = ProjectionLoadGate()

    var uiState by mutableStateOf(ShellUiState(isLoading = true))
        private set

    init { refresh() }

    fun refresh() {
        val revision = loadGate.next()
        val syncing = observationSyncing
        val syncError = observationSyncError
        loadJob?.cancel()
        uiState = uiState.copy(isLoading = true, errorMessage = null)
        loadJob = viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) { load(syncing, syncError) }
            if (loadGate.isLatest(revision)) {
                uiState = loaded.copy(
                    isLoading = false,
                    observationSyncing = observationSyncing,
                    observationSyncError = observationSyncError,
                )
            }
        }
    }

    fun observationSyncStarted() {
        observationSyncing = true
        observationSyncError = null
        uiState = uiState.copy(observationSyncing = true, observationSyncError = null)
    }

    fun observationSyncSucceeded() {
        observationSyncing = false
        observationSyncError = null
        refresh()
    }

    fun observationSyncFailed(message: String) {
        observationSyncing = false
        observationSyncError = message
        uiState = uiState.copy(observationSyncing = false, observationSyncError = message)
    }

    fun selectProgressionTitle(levelKey: String) {
        val account = uiState.account ?: return
        val progression = uiState.progression ?: return
        if (progression.earnedLevels.none { it.key == levelKey }) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                ProgressionStore(getApplication()).selectLevel(account.userId, levelKey)
            }
            refresh()
        }
    }

    private fun load(syncing: Boolean, syncError: String?): ShellUiState = runCatching {
        val context = getApplication<Application>()
        val account = AccountStore(context).verified()
        val observationStore = account?.let { ObservationStore(context) }
        val observations = account?.let { observationStore?.observations(it.userId) }.orEmpty()
        val collection = CollectionProjection.species(observations)
        val content = PublishedContentRepositories.application(context)
        val catalogues = content.catalogues()
        val latestDiscovery = (
            collection.filter { it.photoUrl != null }.maxByOrNull(CollectionSpecies::latestObservedAtMs)
                ?: collection.maxByOrNull(CollectionSpecies::latestObservedAtMs)
            )?.let { species ->
            val assignment = account?.let {
                observationStore?.observationRegion(it.userId, species.latestObservationUuid)
            }
            val regionKey = assignment?.regionKey
            val rarityRegionKey = assignment?.takeIf { it.earnsRegionalProgress }?.regionKey
            val regionName = when (assignment?.assignment) {
                ObservationRegionAssignment.LAND_POLYGON,
                ObservationRegionAssignment.OFFSHORE_BUFFER ->
                    catalogues.firstOrNull { it.regionKey == regionKey }?.displayName
                ObservationRegionAssignment.MARINE_WORLDWIDE -> "Marine worldwide"
                ObservationRegionAssignment.REGION_UNCERTAIN -> "Region uncertain"
                ObservationRegionAssignment.UNSUPPORTED_LAND -> "Unsupported land"
                null -> null
            }
            val regionalTaxon = if (rarityRegionKey != null && species.taxonId != null) {
                content.taxa(rarityRegionKey).firstOrNull { it.taxonId == species.taxonId }
            } else {
                null
            }
            val publishedTaxon = species.taxonId?.let { content.taxon(it) }
            HomeHighlight(
                taxonId = species.taxonId,
                label = species.label,
                photoUrl = species.photoUrl,
                observedAtMs = species.latestObservedAtMs,
                researchGrade = species.bestQualityGrade == "research",
                observationCount = species.observationCount,
                awaitingSpeciesIdentification = species.awaitingSpeciesIdentification,
                scientificName = regionalTaxon?.scientificName ?: publishedTaxon?.scientificName,
                encounterRarity = regionalTaxon?.rarity,
                regionSeenIn = regionName,
            )
        }
        val summary = account?.let { observationStore?.summary(it.userId) }
        val regionalProgress = account?.let { verified ->
            val selected = RegionContextStore(context).currentRegionKey(
                catalogues.mapTo(mutableSetOf()) { it.regionKey },
            )
                ?.let { key -> catalogues.first { it.regionKey == key } }
            selected?.let { catalogue ->
                val observedTaxa = observations.filter { observation ->
                    observationStore?.observationRegion(verified.userId, observation.uuid)?.let { assignment ->
                        assignment.earnsRegionalProgress && assignment.regionKey == catalogue.regionKey
                    } == true
                }.mapNotNull { it.collectionTaxonId ?: it.taxonId }.toSet()
                val achievements = content.achievements(catalogue.regionKey).associateBy { it.label }
                val essentials = achievements["essentials"]?.taxonIds.orEmpty()
                val icons = achievements["icons"]?.taxonIds.orEmpty()
                RegionalHomeProgress(
                    regionKey = catalogue.regionKey,
                    displayName = catalogue.displayName,
                    observedSpecies = observedTaxa.intersect(content.taxa(catalogue.regionKey).map { it.taxonId }.toSet()).size,
                    totalSpecies = content.taxa(catalogue.regionKey).size,
                    essentialsObserved = observedTaxa.intersect(essentials).size,
                    essentialsTotal = essentials.size,
                    iconsObserved = observedTaxa.intersect(icons).size,
                    iconsTotal = icons.size,
                )
            }
        }
        val progression = account?.let {
            val progressionStore = ProgressionStore(context)
            val projected = ProgressionProjection.project(
                totalXp = summary?.totalXp ?: 0,
                events = observationStore?.xpEvents(it.userId).orEmpty(),
                selectedLevelKey = progressionStore.selectedLevelKey(it.userId),
                highestLevelKey = progressionStore.highestLevelKey(it.userId),
            )
            progressionStore.recordHighestLevel(it.userId, projected.currentLevel.key)
            projected
        }
        val markers = MarkerStore(context).load()
        val pendingStatus = account?.let {
            ObservationLifecyclePolicy.pendingStatus(
                markers = markers,
                candidates = observationStore?.candidates(it.userId).orEmpty(),
            )
        }
        val now = System.currentTimeMillis()
        val state = ShellUiState(
            account = account,
            collectionEntries = collection.size,
            identifiedSpecies = collection.count { !it.awaitingSpeciesIdentification },
            observations = observations.size,
            totalXp = summary?.totalXp ?: 0,
            latestDiscovery = latestDiscovery,
            progression = progression,
            observationSyncing = syncing,
            observationSyncError = syncError,
            lastObservationSyncAtMs = summary?.lastSyncedAtMs,
            observationDataStale = ObservationLifecyclePolicy.isStale(
                summary?.lastSyncedAtMs,
                now,
            ),
            pendingMatchesReady = pendingStatus?.readyToReview ?: 0,
            recentQualityTransitions = account?.let {
                observationStore?.qualityTransitions(it.userId).orEmpty()
            }.orEmpty(),
            catalogueSpecies = PublishedContentRepositories.application(context).publishedTaxonCount(),
            pendingHandoffs = markers.count {
                it.state == MarkerState.HANDED_OFF || it.state == MarkerState.PENDING
            },
            draftObservations = markers.count { it.state == MarkerState.CAPTURED },
            localData = LocalDataManager(context).inventory(),
            regionalProgress = regionalProgress,
        )
        observationStore?.close()
        state
    }.getOrElse { error ->
        ShellUiState(errorMessage = error.message ?: "Local Wildlife data could not be read.")
    }
}
