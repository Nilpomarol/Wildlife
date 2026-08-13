package com.wildlife.feasibility.ui.screens.collection

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.wildlife.feasibility.AccountStore
import com.wildlife.feasibility.CollectionProjection
import com.wildlife.feasibility.CollectionSpecies
import com.wildlife.feasibility.ObservationStore

data class CollectionUiState(
    val linked: Boolean,
    val entries: List<CollectionSpecies>,
    val observationCount: Int,
    val totalXp: Int,
    val lastSyncedAtMs: Long? = null,
    val errorMessage: String? = null,
) {
    val awaitingIdentificationCount: Int
        get() = entries.count(CollectionSpecies::awaitingSpeciesIdentification)

    val identifiedSpeciesCount: Int
        get() = entries.size - awaitingIdentificationCount
}

class CollectionViewModel(application: Application) : AndroidViewModel(application) {
    var uiState by mutableStateOf(load())
        private set

    fun refresh() {
        uiState = load()
    }

    private fun load(): CollectionUiState {
        val context = getApplication<Application>()
        val account = AccountStore(context).verified()
            ?: return CollectionUiState(
                linked = false,
                entries = emptyList(),
                observationCount = 0,
                totalXp = 0,
            )
        return runCatching {
            val store = ObservationStore(context)
            val observations = store.observations(account.userId)
            val summary = store.summary(account.userId)
            CollectionUiState(
                linked = true,
                entries = CollectionProjection.species(observations),
                observationCount = observations.size,
                totalXp = summary.totalXp,
                lastSyncedAtMs = summary.lastSyncedAtMs,
            )
        }.getOrElse { error ->
            CollectionUiState(
                linked = true,
                entries = emptyList(),
                observationCount = 0,
                totalXp = 0,
                errorMessage = error.message ?: "The local collection could not be read.",
            )
        }
    }
}
