package com.wildlife.feasibility

object LocalObservationRecords {
    fun removeGroup(markers: List<PendingMarker>, groupId: String): List<PendingMarker> =
        markers.filterNot { (it.handoffId ?: it.id) == groupId }
}
