package com.wildlife.feasibility.ui.screens.capture

import com.wildlife.feasibility.MarkerState
import com.wildlife.feasibility.MatchProposal
import com.wildlife.feasibility.PendingMarker
import com.wildlife.feasibility.VerifiedAccount

data class CapturePhotoUi(
    val markerId: String,
    val imageUri: String,
    val capturedAtMs: Long,
    val latitude: Double?,
    val longitude: Double?,
    val capturedAtReliable: Boolean,
    val locationReliable: Boolean,
    val selected: Boolean,
)

data class CaptureObservationUi(
    val groupId: String,
    val state: MarkerState,
    val photos: List<CapturePhotoUi>,
    val proposals: List<MatchProposal>,
    val matchedObservationUuid: String?,
)

data class CaptureRewardUi(
    val speciesLabel: String,
    val xpAwarded: Int,
    val photoUri: String?,
)

data class CaptureUiState(
    val account: VerifiedAccount? = null,
    val observations: List<CaptureObservationUi> = emptyList(),
    val statusMessage: String = "Add photos to start one observation.",
    val busy: Boolean = false,
    val reward: CaptureRewardUi? = null,
    val handoffUnavailable: Boolean = false,
) {
    val selectedDraftPhotos: Int
        get() = observations.sumOf { observation ->
            if (observation.state == MarkerState.CAPTURED) {
                observation.photos.count(CapturePhotoUi::selected)
            } else {
                0
            }
        }
}

object CaptureProjection {
    fun build(
        markers: List<PendingMarker>,
        selectedMarkerIds: Set<String>,
        proposalsByMarker: Map<String, List<MatchProposal>>,
        account: VerifiedAccount?,
        statusMessage: String,
        busy: Boolean,
        reward: CaptureRewardUi?,
        handoffUnavailable: Boolean = false,
    ): CaptureUiState {
        val observations = markers
            .groupBy { marker -> marker.handoffId ?: marker.id }
            .values
            .sortedByDescending { group -> group.maxOf(PendingMarker::capturedAtMs) }
            .map { unsortedGroup ->
                val group = unsortedGroup.sortedBy(PendingMarker::capturedAtMs)
                val leader = group.first()
                CaptureObservationUi(
                    groupId = leader.handoffId ?: leader.id,
                    state = leader.state,
                    photos = group.map { marker ->
                        CapturePhotoUi(
                            markerId = marker.id,
                            imageUri = marker.imageUri,
                            capturedAtMs = marker.capturedAtMs,
                            latitude = marker.latitude,
                            longitude = marker.longitude,
                            capturedAtReliable = marker.capturedAtReliable,
                            locationReliable = marker.locationReliable,
                            selected = marker.id in selectedMarkerIds,
                        )
                    },
                    proposals = proposalsByMarker[leader.id].orEmpty(),
                    matchedObservationUuid = leader.matchedObservationUuid,
                )
            }
        return CaptureUiState(
            account = account,
            observations = observations,
            statusMessage = statusMessage,
            busy = busy,
            reward = reward,
            handoffUnavailable = handoffUnavailable,
        )
    }
}
