package com.wildlife.feasibility.ui.screens.shell

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.wildlife.feasibility.AccountStore
import com.wildlife.feasibility.ActiveCatalogueStore
import com.wildlife.feasibility.CatalogueStore
import com.wildlife.feasibility.CollectionProjection
import com.wildlife.feasibility.CollectionSpecies
import com.wildlife.feasibility.MarkerState
import com.wildlife.feasibility.MarkerStore
import com.wildlife.feasibility.LocalDataInventory
import com.wildlife.feasibility.LocalDataManager
import com.wildlife.feasibility.ObservationStore
import com.wildlife.feasibility.ObservationLifecyclePolicy
import com.wildlife.feasibility.ObservationQualityTransition
import com.wildlife.feasibility.ProgressionProjection
import com.wildlife.feasibility.ProgressionState
import com.wildlife.feasibility.ProgressionStore
import com.wildlife.feasibility.RegionalCatalogueAssetStore
import com.wildlife.feasibility.VerifiedAccount

data class HomeHighlight(
    val taxonId: Long?,
    val label: String,
    val photoUrl: String?,
    val observedAtMs: Long,
    val researchGrade: Boolean,
    val observationCount: Int,
    val awaitingSpeciesIdentification: Boolean,
)

data class RegionalHomeProgress(
    val displayName: String,
    val observedSpecies: Int,
    val totalSpecies: Int,
    val essentialsObserved: Int,
    val essentialsTotal: Int,
    val iconsObserved: Int,
    val iconsTotal: Int,
)

data class ShellUiState(
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
    val errorMessage: String? = null,
)

class ShellViewModel(application: Application) : AndroidViewModel(application) {
    private var observationSyncing = false
    private var observationSyncError: String? = null

    var uiState by mutableStateOf(load())
        private set

    fun refresh() {
        uiState = load()
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
        ProgressionStore(getApplication()).selectLevel(account.userId, levelKey)
        refresh()
    }

    private fun load(): ShellUiState = runCatching {
        val context = getApplication<Application>()
        val account = AccountStore(context).verified()
        val observationStore = account?.let { ObservationStore(context) }
        val observations = account?.let { observationStore?.observations(it.userId) }.orEmpty()
        val collection = CollectionProjection.species(observations)
        val latestDiscovery = (
            collection.filter { it.photoUrl != null }.maxByOrNull(CollectionSpecies::latestObservedAtMs)
                ?: collection.maxByOrNull(CollectionSpecies::latestObservedAtMs)
            )?.let { species ->
            HomeHighlight(
                taxonId = species.taxonId,
                label = species.label,
                photoUrl = species.photoUrl,
                observedAtMs = species.latestObservedAtMs,
                researchGrade = species.bestQualityGrade == "research",
                observationCount = species.observationCount,
                awaitingSpeciesIdentification = species.awaitingSpeciesIdentification,
            )
        }
        val summary = account?.let { observationStore?.summary(it.userId) }
        val regionalProgress = account?.let { verified ->
            val content = RegionalCatalogueAssetStore(context)
            val catalogues = content.catalogues()
            val selected = ActiveCatalogueStore(context).selectedRegionKey()
                .takeIf { key -> catalogues.any { it.regionKey == key } }
                ?.let { key -> catalogues.first { it.regionKey == key } }
                ?: catalogues.firstOrNull()
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
            observationSyncing = observationSyncing,
            observationSyncError = observationSyncError,
            lastObservationSyncAtMs = summary?.lastSyncedAtMs,
            observationDataStale = ObservationLifecyclePolicy.isStale(
                summary?.lastSyncedAtMs,
                now,
            ),
            pendingMatchesReady = pendingStatus?.readyToReview ?: 0,
            recentQualityTransitions = account?.let {
                observationStore?.qualityTransitions(it.userId).orEmpty()
            }.orEmpty(),
            catalogueSpecies = CatalogueStore(context).use {
                it.load()?.species?.size ?: 0
            },
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
