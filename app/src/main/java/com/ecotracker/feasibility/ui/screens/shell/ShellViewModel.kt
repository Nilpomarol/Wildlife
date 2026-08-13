package com.wildlife.feasibility.ui.screens.shell

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.wildlife.feasibility.AccountStore
import com.wildlife.feasibility.CatalogueStore
import com.wildlife.feasibility.CollectionProjection
import com.wildlife.feasibility.MarkerState
import com.wildlife.feasibility.MarkerStore
import com.wildlife.feasibility.ObservationStore
import com.wildlife.feasibility.ProgressionProjection
import com.wildlife.feasibility.ProgressionState
import com.wildlife.feasibility.ProgressionStore
import com.wildlife.feasibility.VerifiedAccount

data class ShellUiState(
    val account: VerifiedAccount? = null,
    val collectionEntries: Int = 0,
    val identifiedSpecies: Int = 0,
    val observations: Int = 0,
    val totalXp: Int = 0,
    val progression: ProgressionState? = null,
    val catalogueSpecies: Int = 0,
    val pendingHandoffs: Int = 0,
    val draftObservations: Int = 0,
    val errorMessage: String? = null,
)

class ShellViewModel(application: Application) : AndroidViewModel(application) {
    var uiState by mutableStateOf(load())
        private set

    fun refresh() {
        uiState = load()
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
        val summary = account?.let { observationStore?.summary(it.userId) }
        val progression = account?.let {
            ProgressionProjection.project(
                totalXp = summary?.totalXp ?: 0,
                events = observationStore?.xpEvents(it.userId).orEmpty(),
                selectedLevelKey = ProgressionStore(context).selectedLevelKey(it.userId),
            )
        }
        val markers = MarkerStore(context).load()
        ShellUiState(
            account = account,
            collectionEntries = collection.size,
            identifiedSpecies = collection.count { !it.awaitingSpeciesIdentification },
            observations = observations.size,
            totalXp = summary?.totalXp ?: 0,
            progression = progression,
            catalogueSpecies = CatalogueStore(context).load()?.species?.size ?: 0,
            pendingHandoffs = markers.count {
                it.state == MarkerState.HANDED_OFF || it.state == MarkerState.PENDING
            },
            draftObservations = markers.count { it.state == MarkerState.CAPTURED },
        )
    }.getOrElse { error ->
        ShellUiState(errorMessage = error.message ?: "Local Wildlife data could not be read.")
    }
}
