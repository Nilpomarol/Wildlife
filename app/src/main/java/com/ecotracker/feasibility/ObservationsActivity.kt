package com.wildlife.feasibility

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.wildlife.feasibility.ui.screens.observations.ObservationsProjection
import com.wildlife.feasibility.ui.screens.observations.ObservationsScreen
import com.wildlife.feasibility.ui.screens.observations.ObservationsUiState
import com.wildlife.feasibility.ui.theme.WildlifeTheme
import java.io.File
import kotlin.concurrent.thread

class ObservationsActivity : ComponentActivity() {
    private lateinit var markerStore: MarkerStore
    private lateinit var accountStore: AccountStore
    private lateinit var observationStore: ObservationStore
    private val placeLabeler by lazy { PlaceLabeler(this) }
    private var markers: List<PendingMarker> = emptyList()
    private var observations: List<SyncedObservation> = emptyList()
    private var regionsByObservationUuid: Map<String, ObservationRegion> = emptyMap()
    private var hidden: Set<String> = emptySet()
    private var account: VerifiedAccount? = null
    private var placeNames: Map<String, String> = emptyMap()
    private var proposalsByMarker: Map<String, List<MatchProposal>> = emptyMap()
    private var syncing = false
    private var message: String? = null
    private val taxonFilter by lazy { intent.getLongExtra(EXTRA_TAXON_ID, NO_TAXON).takeIf { it != NO_TAXON } }
    private var uiState by mutableStateOf(ObservationsUiState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.rgb(8, 11, 9)),
        )
        markerStore = MarkerStore(this)
        accountStore = AccountStore(this)
        observationStore = ObservationStore(this)
        reload()
        setContent {
            WildlifeTheme {
                ObservationsScreen(
                    state = uiState,
                    onBack = ::finish,
                    onSync = ::sync,
                    onSubmitted = ::markSubmitted,
                    onNotSubmitted = ::markNotSubmitted,
                    onConfirm = ::confirm,
                    onOpenObservation = { open("https://www.inaturalist.org/observations/$it") },
                    onOpenINaturalist = { open("https://www.inaturalist.org") },
                    onDeleteLocal = ::deleteLocalGroup,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::markerStore.isInitialized) reload()
    }

    override fun onDestroy() {
        if (::observationStore.isInitialized) observationStore.close()
        super.onDestroy()
    }

    private fun reload() {
        markers = markerStore.load()
        account = accountStore.verified()
        observations = account?.let { observationStore.observations(it.userId) }.orEmpty()
        regionsByObservationUuid = account?.let { linked ->
            observations.mapNotNull { observation ->
                observationStore.observationRegion(linked.userId, observation.uuid)
            }.associateBy(ObservationRegion::observationUuid)
        }.orEmpty()
        hidden = account?.let { observationStore.mapHiddenObservationUuids(it.userId) }.orEmpty()
        projectUi()
        requestPlaces()
    }

    private fun projectUi() {
        uiState = ObservationsProjection.build(
            markers, proposalsByMarker, observations, hidden, account, syncing, message, taxonFilter, placeNames,
            regionsByObservationUuid,
        )
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
        placeLabeler.ensureResolved(requests) { resolved ->
            runOnUiThread {
                placeNames = resolved
                projectUi()
            }
        }
    }

    private fun markSubmitted(groupId: String) {
        markers = markers.map { marker ->
            if (groupKey(marker) == groupId && marker.state == MarkerState.HANDED_OFF) marker.copy(state = MarkerState.PENDING) else marker
        }
        message = "Submission recorded. Checking the public iNaturalist record."
        persist()
        sync()
    }

    private fun markNotSubmitted(groupId: String) {
        markers = markers.map { marker ->
            if (groupKey(marker) == groupId && marker.state == MarkerState.HANDED_OFF) {
                marker.copy(state = MarkerState.CAPTURED, sharedAtMs = null, handoffId = null)
            } else marker
        }
        message = "Observation returned to Capture as a draft."
        persist()
    }

    private fun sync() {
        val account = accountStore.verified()
        if (account == null) {
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
        thread(name = "wildlife-observation-sync") {
            runCatching {
                OnDeviceWildlifeRepository(this).use { repository ->
                    repository.syncObservations(account, force = true)
                    markers.filter { it.state == MarkerState.CONFIRMED }
                        .mapNotNull(PendingMarker::matchedObservationUuid).distinct()
                        .forEach { repository.confirmObservation(account, it) }
                    val candidates = observationStore.candidates(account.userId)
                    pending.associate { marker ->
                        marker.id to CandidateMatcher.proposals(marker, candidates)
                    }
                }
            }.onSuccess { proposals -> runOnUiThread {
                syncing = false
                proposalsByMarker = proposals
                message = if (proposals.values.any { it.isNotEmpty() }) {
                    "Possible public match found. Inspect it, then confirm only if it is your sighting."
                } else "No public match yet. If needed, reopen iNaturalist to finish uploading."
                reload()
            } }.onFailure { error -> runOnUiThread {
                syncing = false
                message = "Could not check iNaturalist: ${error.message ?: "try again later"}"
                reload()
            } }
        }
    }

    private fun confirm(proposal: MatchProposal) {
        val groupId = markers.firstOrNull { it.id == proposal.markerId }?.let(::groupKey) ?: return
        markers = markers.map { marker ->
            if (groupKey(marker) == groupId) marker.copy(
                state = MarkerState.CONFIRMED,
                matchedObservationUuid = proposal.candidate.uuid,
            ) else marker
        }
        proposalsByMarker = proposalsByMarker - proposal.markerId
        persist()
        val account = accountStore.verified() ?: return
        syncing = true
        message = "Confirming the Wildlife collection reward…"
        reload()
        thread(name = "wildlife-observation-confirm") {
            runCatching {
                OnDeviceWildlifeRepository(this).use { it.confirmObservation(account, proposal.candidate.uuid) }
            }.onSuccess { result -> runOnUiThread {
                syncing = false
                message = if (result.xpAwarded > 0) "Observation confirmed · +${result.xpAwarded} XP" else "Observation was already rewarded."
                reload()
            } }.onFailure { error -> runOnUiThread {
                syncing = false
                message = "Confirmed locally. The reward will retry on the next sync: ${error.message}"
                reload()
            } }
        }
    }

    private fun deleteLocalGroup(groupId: String) {
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
            val directory = File(filesDir, "handoffs").canonicalFile
            val target = File(directory, filename).canonicalFile
            if (target.parentFile == directory && target.isFile) target.delete()
        }
    }

    private fun persist() {
        markerStore.save(markers)
        reload()
    }

    private fun groupKey(marker: PendingMarker) = marker.handoffId ?: marker.id

    private fun open(url: String) = runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }

    companion object {
        private const val EXTRA_TAXON_ID = "observations_taxon_id"
        private const val NO_TAXON = Long.MIN_VALUE

        fun intent(context: Context, taxonId: Long? = null) = Intent(context, ObservationsActivity::class.java).apply {
            taxonId?.let { putExtra(EXTRA_TAXON_ID, it) }
        }
    }
}
