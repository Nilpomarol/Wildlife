package com.wildlife.feasibility.ui.screens.observations

import com.wildlife.feasibility.MarkerState
import com.wildlife.feasibility.MatchProposal
import com.wildlife.feasibility.ObservationRegion
import com.wildlife.feasibility.ObservationRegionAssignment
import com.wildlife.feasibility.PendingMarker
import com.wildlife.feasibility.PlaceKeys
import com.wildlife.feasibility.SyncedObservation
import com.wildlife.feasibility.VerifiedAccount

data class ManagedPhotoUi(
    val markerId: String,
    val imageUri: String,
)

/** A resolved coordinate for a record, plus whether the public source obscured it. */
data class ObservationLocation(
    val latitude: Double,
    val longitude: Double,
    val obscured: Boolean,
)

/**
 * A candidate public observation that might be the same sighting as a Wildlife handoff, with the
 * context a person needs to decide: when it was recorded, roughly where, and how close it is to
 * the app record in time and space.
 */
data class MatchProposalUi(
    val proposal: MatchProposal,
    val observedAtMs: Long,
    val place: String?,
    val obscured: Boolean,
    val distanceKm: Double?,
    val timeDeltaMinutes: Long,
    val highConfidence: Boolean,
)

data class ManagedObservationUi(
    val groupId: String,
    val state: MarkerState,
    // Species name only when the record has been matched to a public observation; handoffs and
    // drafts are pre-identification and legitimately have no species yet.
    val label: String?,
    val capturedAtMs: Long,
    val location: ObservationLocation?,
    val place: String?,
    // True only when the linked public observation actually reached research grade.
    val researchGrade: Boolean,
    val photos: List<ManagedPhotoUi>,
    val proposals: List<MatchProposalUi>,
    val matchedObservationUuid: String?,
    val regionalContext: RegionalContextUi? = null,
)

data class PublicObservationUi(
    val uuid: String,
    val taxonId: Long?,
    val label: String,
    val observedAtMs: Long,
    val qualityGrade: String,
    val photoUrl: String?,
    val location: ObservationLocation?,
    val place: String?,
    val regionalContext: RegionalContextUi? = null,
)

data class RegionalContextUi(
    val label: String,
    val countsTowardRegion: Boolean,
)

data class ObservationsUiState(
    val account: VerifiedAccount? = null,
    val managed: List<ManagedObservationUi> = emptyList(),
    val publicObservations: List<PublicObservationUi> = emptyList(),
    val syncing: Boolean = false,
    val message: String? = null,
    val taxonFilter: Long? = null,
)

internal object ObservationsProjection {
    fun build(
        markers: List<PendingMarker>,
        proposalsByMarker: Map<String, List<MatchProposal>>,
        observations: List<SyncedObservation>,
        hiddenUuids: Set<String>,
        account: VerifiedAccount?,
        syncing: Boolean,
        message: String?,
        taxonFilter: Long?,
        placeNames: Map<String, String> = emptyMap(),
        regionsByObservationUuid: Map<String, ObservationRegion> = emptyMap(),
    ): ObservationsUiState {
        val observationByUuid = observations.associateBy { it.uuid }
        fun placeFor(location: ObservationLocation?): String? =
            location?.let { placeNames[PlaceKeys.of(it.latitude, it.longitude)] }
        fun locationOf(latitude: Double?, longitude: Double?, obscured: Boolean): ObservationLocation? =
            latitude?.let { lat -> longitude?.let { lon -> ObservationLocation(lat, lon, obscured) } }

        // Public observations already linked to a Wildlife record belong in "In your collection",
        // not the public history, so exclude them from the public list below.
        val linkedUuids = markers.mapNotNull(PendingMarker::matchedObservationUuid).toSet()

        return ObservationsUiState(
            account = account,
            managed = markers.groupBy { it.handoffId ?: it.id }
                .values
                .sortedByDescending { it.maxOf(PendingMarker::capturedAtMs) }
                .map { group ->
                    val sorted = group.sortedBy(PendingMarker::capturedAtMs)
                    val lead = sorted.first()
                    val location = locationOf(lead.latitude, lead.longitude, obscured = false)
                    val matched = lead.matchedObservationUuid?.let(observationByUuid::get)
                    ManagedObservationUi(
                        groupId = lead.handoffId ?: lead.id,
                        state = lead.state,
                        label = matched?.label,
                        capturedAtMs = lead.capturedAtMs,
                        location = location,
                        place = placeFor(location),
                        researchGrade = matched?.qualityGrade == "research",
                        photos = sorted.map { ManagedPhotoUi(it.id, it.imageUri) },
                        proposals = proposalsByMarker[lead.id].orEmpty().map { proposal ->
                            val candidateLocation = locationOf(
                                proposal.candidate.latitude,
                                proposal.candidate.longitude,
                                proposal.candidate.obscured,
                            )
                            MatchProposalUi(
                                proposal = proposal,
                                observedAtMs = proposal.candidate.observedAtMs,
                                place = placeFor(candidateLocation),
                                obscured = proposal.candidate.obscured,
                                distanceKm = proposal.distanceKm,
                                timeDeltaMinutes = proposal.timeDeltaMinutes,
                                highConfidence = proposal.confidence == com.wildlife.feasibility.MatchConfidence.HIGH,
                            )
                        },
                        matchedObservationUuid = lead.matchedObservationUuid,
                        regionalContext = lead.matchedObservationUuid
                            ?.let(regionsByObservationUuid::get)
                            ?.toUi(),
                    )
                },
            publicObservations = observations
                .asSequence()
                .filter { it.uuid !in linkedUuids }
                .filter { taxonFilter == null || it.taxonId == taxonFilter || it.collectionTaxonId == taxonFilter }
                .map {
                    val location = locationOf(it.latitude, it.longitude, it.obscured)
                    PublicObservationUi(
                        uuid = it.uuid,
                        taxonId = it.collectionTaxonId ?: it.taxonId,
                        label = it.label,
                        observedAtMs = it.observedAtMs,
                        qualityGrade = it.qualityGrade,
                        photoUrl = it.photoUrl,
                        location = location,
                        place = placeFor(location),
                        regionalContext = regionsByObservationUuid[it.uuid]?.toUi(),
                    )
                }
                .toList(),
            syncing = syncing,
            message = message,
            taxonFilter = taxonFilter,
        )
    }

    private fun ObservationRegion.toUi(): RegionalContextUi = when (assignment) {
        ObservationRegionAssignment.LAND_POLYGON,
        ObservationRegionAssignment.OFFSHORE_BUFFER -> RegionalContextUi(
            label = "Region · ${regionName(regionKey)}", countsTowardRegion = true,
        )
        ObservationRegionAssignment.MARINE_WORLDWIDE -> RegionalContextUi(
            label = "Not counted · Marine worldwide", countsTowardRegion = false,
        )
        ObservationRegionAssignment.UNSUPPORTED_LAND -> RegionalContextUi(
            label = "Not counted · Unsupported land", countsTowardRegion = false,
        )
        ObservationRegionAssignment.REGION_UNCERTAIN -> RegionalContextUi(
            label = "Not counted · Region uncertain", countsTowardRegion = false,
        )
    }

    private fun regionName(key: String?): String = when (key) {
        "mediterranean_europe" -> "Mediterranean Europe"
        "temperate_europe" -> "Temperate Europe"
        "boreal_europe" -> "Boreal Europe"
        "eastern_europe_steppe" -> "Eastern Europe / Steppe"
        "siberia_boreal_asia" -> "Siberia and Boreal Asia"
        "maghreb_sahara" -> "Maghreb and Sahara"
        "sahel" -> "Sahel"
        "west_central_tropical_africa" -> "West / Central Tropical Africa"
        "east_africa" -> "East Africa"
        "southern_africa" -> "Southern Africa"
        "madagascar_west_indian_ocean" -> "Madagascar and Western Indian Ocean"
        "middle_east_arabia" -> "Middle East and Arabia"
        "central_asia" -> "Central Asia"
        "indian_subcontinent" -> "Indian Subcontinent"
        "temperate_east_asia" -> "Temperate East Asia"
        "mainland_southeast_asia" -> "Mainland Southeast Asia"
        "insular_southeast_asia" -> "Insular Southeast Asia"
        "australasia" -> "Australasia"
        "new_zealand_pacific" -> "New Zealand and Pacific"
        "temperate_boreal_north_america" -> "Temperate / Boreal North America"
        "mesoamerica" -> "Mesoamerica"
        "caribbean" -> "Caribbean"
        "tropical_north_south_america_amazon" -> "Tropical Northern South America / Amazon"
        "andes_southern_south_america" -> "Andes and Southern South America"
        else -> "Assigned region"
    }
}
