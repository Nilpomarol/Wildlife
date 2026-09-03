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
    val draft: CaptureObservationUi?
        get() = observations.firstOrNull { it.state == MarkerState.CAPTURED }

    val selectedDraftPhotos: Int
        get() = draft?.photos?.size ?: 0
}

object CaptureProjection {
    const val DRAFT_GROUP_ID = "new-observation-draft"

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
            .groupBy { marker ->
                if (marker.state == MarkerState.CAPTURED) DRAFT_GROUP_ID else marker.handoffId ?: marker.id
            }
            .values
            .sortedByDescending { group -> group.maxOf(PendingMarker::capturedAtMs) }
            .map { unsortedGroup ->
                val group = unsortedGroup.sortedBy(PendingMarker::capturedAtMs)
                val leader = group.first()
                CaptureObservationUi(
                    groupId = if (leader.state == MarkerState.CAPTURED) {
                        DRAFT_GROUP_ID
                    } else {
                        leader.handoffId ?: leader.id
                    },
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
                            // Capture is one observation workspace. Every photo in the draft is
                            // included; removing a photo is an explicit destructive action rather
                            // than a checkbox whose state is lost when the Activity is recreated.
                            selected = marker.state == MarkerState.CAPTURED || marker.id in selectedMarkerIds,
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
