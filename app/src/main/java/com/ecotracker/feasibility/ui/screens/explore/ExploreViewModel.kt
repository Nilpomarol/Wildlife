package com.wildlife.feasibility.ui.screens.explore

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wildlife.feasibility.AccountStore
import com.wildlife.feasibility.CatalogueSnapshot
import com.wildlife.feasibility.CataloguePhotoPolicy
import com.wildlife.feasibility.CatalogueStore
import com.wildlife.feasibility.CollectionProjection
import com.wildlife.feasibility.ObservationStore
import com.wildlife.feasibility.OnDeviceWildlifeRepository
import com.wildlife.feasibility.SyncedObservation
import com.wildlife.feasibility.TaxonDetails
import com.wildlife.feasibility.ui.components.SpeciesCardModel
import com.wildlife.feasibility.ui.components.SpeciesCardStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ExploreSpecies(
    val taxonId: Long,
    val taxonGroup: String?,
    val commonName: String?,
    val scientificName: String,
    val observed: Boolean,
    val card: SpeciesCardModel,
)

data class ExploreUiState(
    val snapshot: CatalogueSnapshot? = null,
    val entries: List<ExploreSpecies> = emptyList(),
    val syncing: Boolean = false,
    val errorMessage: String? = null,
) {
    val observedCount: Int get() = entries.count(ExploreSpecies::observed)
}

internal object ExploreProjection {
    fun entries(
        snapshot: CatalogueSnapshot?,
        observations: List<SyncedObservation>,
        detailsByTaxon: Map<Long, TaxonDetails> = emptyMap(),
    ): List<ExploreSpecies> {
        if (snapshot == null) return emptyList()
        val collectionByTaxon = CollectionProjection.species(observations)
            .mapNotNull { entry -> entry.taxonId?.let { it to entry } }
            .toMap()
        return snapshot.species.map { species ->
            val collected = collectionByTaxon[species.taxonId]
            val details = detailsByTaxon[species.taxonId]
            val label = species.commonName ?: species.scientificName
            val status = when {
                collected?.bestQualityGrade == "research" -> SpeciesCardStatus.RESEARCH_GRADE
                collected != null -> SpeciesCardStatus.OBSERVED
                else -> SpeciesCardStatus.NONE
            }
            ExploreSpecies(
                taxonId = species.taxonId,
                taxonGroup = species.taxonGroup,
                commonName = species.commonName,
                scientificName = species.scientificName,
                observed = collected != null,
                card = SpeciesCardModel(
                    key = "taxon:${species.taxonId}",
                    label = label,
                    supportingText = if (species.commonName != null) {
                        species.scientificName
                    } else {
                        "Scientific name"
                    },
                    photoUrl = collected?.photoUrl ?: CataloguePhotoPolicy.cardUrl(species),
                    silhouetteUrl = details?.silhouetteUrl ?: species.silhouetteUrl,
                    supportingTextItalic = true,
                    status = status,
                    noPhotoLabel = if (collected == null) "Not observed" else "Photo unavailable",
                ),
            )
        }
    }
}

class ExploreViewModel(application: Application) : AndroidViewModel(application) {
    var uiState by mutableStateOf(loadLocal())
        private set

    fun refreshLocal() {
        if (!uiState.syncing) uiState = loadLocal()
    }

    fun syncCatalogue() {
        if (uiState.syncing) return
        uiState = uiState.copy(syncing = true, errorMessage = null)
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    OnDeviceWildlifeRepository(getApplication()).syncCataloniaCatalogue()
                }
            }
            uiState = result.fold(
                onSuccess = { loadLocal() },
                onFailure = { error ->
                    loadLocal().copy(
                        errorMessage = "Update failed. The offline catalogue is still available. " +
                            (error.message ?: "Try again later."),
                    )
                },
            )
        }
    }

    private fun loadLocal(): ExploreUiState = runCatching {
        val context = getApplication<Application>()
        val catalogueStore = CatalogueStore(context)
        val snapshot = catalogueStore.load()
        val detailsByTaxon = catalogueStore.loadTaxonDetails()
        val observations = AccountStore(context).verified()?.let { account ->
            ObservationStore(context).observations(account.userId)
        }.orEmpty()
        ExploreUiState(
            snapshot = snapshot,
            entries = ExploreProjection.entries(snapshot, observations, detailsByTaxon),
        )
    }.getOrElse { error ->
        ExploreUiState(errorMessage = error.message ?: "The offline catalogue could not be read.")
    }
}
