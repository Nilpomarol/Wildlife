package com.wildlife.feasibility.ui.screens.observations

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wildlife.feasibility.AccountStore
import com.wildlife.feasibility.CandidateMatcher
import com.wildlife.feasibility.LocalObservationRecords
import com.wildlife.feasibility.MarkerState
import com.wildlife.feasibility.MarkerStore
import com.wildlife.feasibility.MatchAssignment
import com.wildlife.feasibility.MatchProposal
import com.wildlife.feasibility.ObservationRegion
import com.wildlife.feasibility.ObservationStore
import com.wildlife.feasibility.OnDeviceWildlifeRepository
import com.wildlife.feasibility.PendingMarker
import com.wildlife.feasibility.PlaceLabeler
import com.wildlife.feasibility.PlaceRequest
import com.wildlife.feasibility.SyncedObservation
import com.wildlife.feasibility.VerifiedAccount
import com.wildlife.feasibility.ui.ProjectionLoadGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Owns long-term observation management so the screen can live inside the navigation shell
 * instead of a focused Activity. Capture still owns creation of a new draft.
 */
class ObservationsViewModel(application: Application) : AndroidViewModel(application) {
    private val markerStore = MarkerStore(application)
    private val accountStore = AccountStore(application)
    private val observationStore = ObservationStore(application)
    private val placeLabeler by lazy { PlaceLabeler(application) }

    private var markers: List<PendingMarker> = emptyList()
    private var observations: List<SyncedObservation> = emptyList()
    private var regionsByObservationUuid: Map<String, ObservationRegion> = emptyMap()
    private var account: VerifiedAccount? = null
    private var placeNames: Map<String, String> = emptyMap()
    private var proposalsByMarker: Map<String, List<MatchProposal>> = emptyMap()
    private var syncing = false
    private var message: String? = null
    private var taxonFilter: Long? = null
    private var stateJob: Job? = null
    private val stateGate = ProjectionLoadGate()

    var uiState by mutableStateOf(ObservationsUiState(isLoading = true))
        private set

    init {
        reload()
    }

    /** Species Detail opens this screen filtered; the shell clears the filter on a plain open. */
    fun setTaxonFilter(taxonId: Long?) {
        if (taxonFilter == taxonId) return
        taxonFilter = taxonId
        projectUi()
    }

    fun refresh() = reload()

    override fun onCleared() {
        observationStore.close()
        super.onCleared()
    }

    private fun reload() {
        val revision = stateGate.next()
        val syncingSnapshot = syncing
        val messageSnapshot = message
        val filterSnapshot = taxonFilter
        val placesSnapshot = placeNames
        val proposalsSnapshot = proposalsByMarker
        stateJob?.cancel()
        uiState = uiState.copy(isLoading = true)
        stateJob = viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val loadedMarkers = markerStore.load()
                val loadedAccount = accountStore.verified()
                val loadedObservations = loadedAccount?.let {
                    observationStore.observations(it.userId)
                }.orEmpty()
                val loadedRegions = loadedAccount?.let { linked ->
                    loadedObservations.mapNotNull { observation ->
                        observationStore.observationRegion(linked.userId, observation.uuid)
                    }.associateBy(ObservationRegion::observationUuid)
                }.orEmpty()
                ObservationReload(
                    markers = loadedMarkers,
                    observations = loadedObservations,
                    regions = loadedRegions,
                    account = loadedAccount,
                    state = ObservationsProjection.build(
                        loadedMarkers, proposalsSnapshot, loadedObservations,
                        loadedAccount, syncingSnapshot, messageSnapshot, filterSnapshot,
                        placesSnapshot, loadedRegions,
                    ).copy(isLoading = false),
                )
            }
            if (!stateGate.isLatest(revision)) return@launch
            markers = loaded.markers
            observations = loaded.observations
            regionsByObservationUuid = loaded.regions
            account = loaded.account
            uiState = loaded.state
            requestPlaces()
        }
    }

    private fun projectUi() {
        val revision = stateGate.next()
        val markersSnapshot = markers
        val proposalsSnapshot = proposalsByMarker
        val observationsSnapshot = observations
        val accountSnapshot = account
        val syncingSnapshot = syncing
        val messageSnapshot = message
        val filterSnapshot = taxonFilter
        val placesSnapshot = placeNames
        val regionsSnapshot = regionsByObservationUuid
        stateJob?.cancel()
        stateJob = viewModelScope.launch {
            val projected = withContext(Dispatchers.Default) {
                ObservationsProjection.build(
                    markersSnapshot, proposalsSnapshot, observationsSnapshot,
                    accountSnapshot, syncingSnapshot, messageSnapshot, filterSnapshot,
                    placesSnapshot, regionsSnapshot,
                )
            }
            if (stateGate.isLatest(revision)) uiState = projected.copy(isLoading = false)
        }
    }

    /** Reverse-geocodes any new coordinates in the background, then re-projects with the names. */
    private fun requestPlaces() {
        val requests = buildList {
            markers.forEach { m ->
                if (m.latitude != null && m.longitude != null) add(PlaceRequest(m.latitude, m.longitude))
            }
            observations.forEach { o ->
                if (o.latitude != null && o.longitude != null) add(PlaceRequest(o.latitude, o.longitude))
            }
            proposalsByMarker.values.flatten().forEach { proposal ->
                val c = proposal.candidate
                if (c.latitude != null && c.longitude != null) add(PlaceRequest(c.latitude, c.longitude))
            }
        }
        if (requests.isEmpty()) return
        val sourceRevision = stateGate.next()
        placeLabeler.ensureResolved(requests) { resolved ->
            viewModelScope.launch {
                if (!stateGate.isLatest(sourceRevision)) return@launch
                placeNames = resolved
                projectUi()
            }
        }
    }

    fun markSubmitted(groupId: String) {
        markers = markers.map { marker ->
            if (groupKey(marker) == groupId && marker.state == MarkerState.HANDED_OFF) {
                marker.copy(state = MarkerState.PENDING)
            } else {
                marker
            }
        }
        message = "Submission recorded. Checking the public iNaturalist record."
        persist()
        sync()
    }

    fun markNotSubmitted(groupId: String) {
        markers = markers.map { marker ->
            if (groupKey(marker) == groupId && marker.state == MarkerState.HANDED_OFF) {
                marker.copy(state = MarkerState.CAPTURED, sharedAtMs = null, handoffId = null)
            } else {
                marker
            }
        }
        message = "Observation returned to Capture as a draft."
        persist()
    }

    fun sync() {
        val verified = accountStore.verified()
        if (verified == null) {
            message = "Link iNaturalist before checking submitted observations."
            reload()
            return
        }
        if (syncing) return
        syncing = true
        message = "Checking the verified iNaturalist account…"
        reload()
        val pending = markers.filter { it.state == MarkerState.PENDING }
            .groupBy(::groupKey).values.map { it.minBy(PendingMarker::capturedAtMs) }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    OnDeviceWildlifeRepository(getApplication()).use { repository ->
                        repository.syncObservations(verified, force = true)
                        val linked = markers.filter { it.state == MarkerState.CONFIRMED }
                            .mapNotNull(PendingMarker::matchedObservationUuid).distinct()
                        linked.forEach { repository.confirmObservation(verified, it) }
                        // One resolution across every outstanding capture, so a record
                        // cannot be filed to two of them and a contested pairing is not
                        // filed at all.
                        val assignment = CandidateMatcher.assign(
                            pending, observationStore.candidates(verified.userId), linked.toSet(),
                        )
                        assignment.automatic.forEach { proposal ->
                            repository.confirmObservation(verified, proposal.candidate.uuid)
                        }
                        assignment
                    }
                }
            }.onSuccess { assignment ->
                syncing = false
                applyAutomaticMatches(assignment)
                proposalsByMarker = assignment.proposals
                message = syncMessage(assignment)
                persist()
            }.onFailure { error ->
                syncing = false
                message = "Could not check iNaturalist: ${error.message ?: "try again later"}"
                reload()
            }
        }
    }

    /**
     * Links every capture the matcher was confident about, marking each one as filed by
     * Wildlife rather than by the user so the screen can offer to undo it.
     */
    private fun applyAutomaticMatches(assignment: MatchAssignment) {
        if (assignment.automatic.isEmpty()) return
        val uuidByGroup = assignment.automatic.mapNotNull { proposal ->
            markers.firstOrNull { it.id == proposal.markerId }
                ?.let { groupKey(it) to proposal.candidate.uuid }
        }.toMap()
        markers = markers.map { marker ->
            uuidByGroup[groupKey(marker)]?.let { uuid ->
                marker.copy(
                    state = MarkerState.CONFIRMED,
                    matchedObservationUuid = uuid,
                    autoMatched = true,
                )
            } ?: marker
        }
    }

    private fun syncMessage(assignment: MatchAssignment): String {
        val filed = assignment.automatic.size
        val offered = assignment.proposals.values.count { it.isNotEmpty() }
        return when {
            filed > 0 && offered > 0 -> "Filed $filed automatically. $offered still need your eye."
            filed == 1 -> "One sighting matched and filed. Undo it below if it is not yours."
            filed > 1 -> "$filed sightings matched and filed. Undo any that are not yours."
            offered > 0 ->
                "Possible public match found. Inspect it, then confirm only if it is your sighting."
            else -> "No public match yet. If needed, reopen iNaturalist to finish uploading."
        }
    }

    fun confirm(proposal: MatchProposal) {
        val groupId = markers.firstOrNull { it.id == proposal.markerId }?.let(::groupKey) ?: return
        markers = markers.map { marker ->
            if (groupKey(marker) == groupId) {
                marker.copy(
                    state = MarkerState.CONFIRMED,
                    matchedObservationUuid = proposal.candidate.uuid,
                    autoMatched = false,
                )
            } else {
                marker
            }
        }
        proposalsByMarker = proposalsByMarker - proposal.markerId
        persist()
        val verified = accountStore.verified() ?: return
        syncing = true
        message = "Confirming the Wildlife collection reward…"
        reload()
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    OnDeviceWildlifeRepository(getApplication()).use {
                        it.confirmObservation(verified, proposal.candidate.uuid)
                    }
                }
            }.onSuccess { result ->
                syncing = false
                message = if (result.xpAwarded > 0) {
                    "Observation confirmed · +${result.xpAwarded} XP"
                } else {
                    "Observation was already rewarded."
                }
                reload()
            }.onFailure { error ->
                syncing = false
                message = "Confirmed locally. The reward will retry on the next sync: ${error.message}"
                reload()
            }
        }
    }

    /**
     * Records that a proposed match is not this capture's sighting.
     *
     * Nothing is confirmed and nothing is reversed — the record was never linked. The
     * rejection is remembered against the capture so the next sync does not offer it again,
     * which is the same memory an undone automatic match writes.
     */
    fun rejectMatch(proposal: MatchProposal) {
        val groupId = markers.firstOrNull { it.id == proposal.markerId }?.let(::groupKey) ?: return
        val uuid = proposal.candidate.uuid
        markers = markers.map { marker ->
            if (groupKey(marker) == groupId) {
                marker.copy(rejectedObservationUuids = marker.rejectedObservationUuids + uuid)
            } else {
                marker
            }
        }
        proposalsByMarker = proposalsByMarker.mapValues { (_, forMarker) ->
            forMarker.filterNot { it.candidate.uuid == uuid }
        }
        message = "Dismissed. Wildlife will not offer that record for this sighting again."
        persist()
    }

    /** Accepts an automatic match: the link stays, the offer to undo it goes. */
    fun keepAutomaticMatch(groupId: String) {
        if (markers.none { groupKey(it) == groupId && it.autoMatched }) return
        markers = markers.map { marker ->
            if (groupKey(marker) == groupId) marker.copy(autoMatched = false) else marker
        }
        message = "Match kept."
        persist()
    }

    /**
     * Rejects an automatic match, returning the capture to the queue it came from.
     *
     * The public record is untouched — Wildlife has no write access to iNaturalist, and
     * this unpicks only Wildlife's own link — but the XP that confirmation awarded is
     * genuinely reversed, because leaving it would credit a sighting the user has just
     * said was not theirs.
     */
    fun undoAutomaticMatch(groupId: String) {
        val uuid = markers.firstOrNull { groupKey(it) == groupId && it.autoMatched }
            ?.matchedObservationUuid ?: return
        markers = markers.map { marker ->
            if (groupKey(marker) == groupId) {
                marker.copy(
                    state = MarkerState.PENDING,
                    matchedObservationUuid = null,
                    autoMatched = false,
                    // Remembered, or the next sync scores it exactly as well and files it
                    // straight back — the rejection would last until the user looked away.
                    rejectedObservationUuids = marker.rejectedObservationUuids + uuid,
                )
            } else {
                marker
            }
        }
        persist()
        val verified = accountStore.verified() ?: return
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    OnDeviceWildlifeRepository(getApplication()).use {
                        it.unconfirmObservation(verified, uuid)
                    }
                }
            }.onSuccess {
                message = "Match undone. Nothing changed on iNaturalist."
                reload()
            }.onFailure { error ->
                message = "Match undone here, but the reward could not be reversed: ${error.message}"
                reload()
            }
        }
    }

    fun deleteLocalGroup(groupId: String) {
        markers.filter { groupKey(it) == groupId }.forEach(::deletePrivatePhotoIfOwned)
        markers = LocalObservationRecords.removeGroup(markers, groupId)
        proposalsByMarker = proposalsByMarker.filterKeys { key -> markers.any { it.id == key } }
        message = "Removed from Wildlife. Nothing was deleted from iNaturalist."
        persist()
    }

    private fun deletePrivatePhotoIfOwned(marker: PendingMarker) {
        if (marker.source != "camera") return
        val filename = Uri.parse(marker.imageUri).lastPathSegment ?: return
        runCatching {
            val directory = File(getApplication<Application>().filesDir, "handoffs").canonicalFile
            val target = File(directory, filename).canonicalFile
            if (target.parentFile == directory && target.isFile) target.delete()
        }
    }

    private fun persist() {
        markerStore.save(markers)
        reload()
    }

    private fun groupKey(marker: PendingMarker) = marker.handoffId ?: marker.id

    private data class ObservationReload(
        val markers: List<PendingMarker>,
        val observations: List<SyncedObservation>,
        val regions: Map<String, ObservationRegion>,
        val account: VerifiedAccount?,
        val state: ObservationsUiState,
    )
}
