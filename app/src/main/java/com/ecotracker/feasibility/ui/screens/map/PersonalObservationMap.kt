package com.wildlife.feasibility.ui.screens.map

import com.wildlife.feasibility.SyncedObservation
import kotlin.math.floor

data class PersonalObservationMapCell(
    val key: String,
    val latitude: Double,
    val longitude: Double,
    val observationCount: Int,
    val researchGradeCount: Int,
    val obscuredCount: Int,
    val latestObservationUuid: String,
    val latestLabel: String,
)

data class PersonalMapObservation(
    val uuid: String,
    val label: String,
    val observedAtMs: Long,
    val mapVisible: Boolean,
    val locationAvailable: Boolean,
    val obscured: Boolean,
)

data class PersonalObservationMap(
    val cells: List<PersonalObservationMapCell>,
    val mappedObservationCount: Int,
    val hiddenObservationCount: Int,
    val unavailableLocationCount: Int,
    val observations: List<PersonalMapObservation>,
    val minLatitude: Double,
    val maxLatitude: Double,
    val minLongitude: Double,
    val maxLongitude: Double,
)

internal object PersonalObservationMapProjection {
    // Roughly 8–11 km in Catalonia. Exact public coordinates are never rendered as pins.
    const val CELL_SIZE_DEGREES = 0.1
    private const val MIN_VIEW_SPAN_DEGREES = 0.25

    fun build(
        observations: List<SyncedObservation>,
        hiddenObservationUuids: Set<String> = emptySet(),
    ): PersonalObservationMap {
        val observationRows = observations.map { observation ->
            PersonalMapObservation(
                uuid = observation.uuid,
                label = observation.displayLabel(),
                observedAtMs = observation.observedAtMs,
                mapVisible = observation.uuid !in hiddenObservationUuids,
                locationAvailable = observation.hasUsableLocation(),
                obscured = observation.obscured,
            )
        }.sortedByDescending(PersonalMapObservation::observedAtMs)
        val visible = observations.filterNot { it.uuid in hiddenObservationUuids }
        val located = visible.filter { observation ->
            observation.hasUsableLocation()
        }
        val cells = located
            .groupBy { observation ->
                val latitudeIndex = floor(observation.latitude!! / CELL_SIZE_DEGREES).toInt()
                val longitudeIndex = floor(observation.longitude!! / CELL_SIZE_DEGREES).toInt()
                latitudeIndex to longitudeIndex
            }
            .map { (indices, cellObservations) ->
                val latest = cellObservations.maxBy(SyncedObservation::observedAtMs)
                PersonalObservationMapCell(
                    key = "${indices.first}:${indices.second}",
                    latitude = (indices.first + 0.5) * CELL_SIZE_DEGREES,
                    longitude = (indices.second + 0.5) * CELL_SIZE_DEGREES,
                    observationCount = cellObservations.size,
                    researchGradeCount = cellObservations.count { it.qualityGrade == "research" },
                    obscuredCount = cellObservations.count(SyncedObservation::obscured),
                    latestObservationUuid = latest.uuid,
                    latestLabel = latest.displayLabel(),
                )
            }
            .sortedWith(
                compareByDescending<PersonalObservationMapCell> { it.observationCount }
                    .thenBy { it.key },
            )

        val latitudeBounds = paddedBounds(cells.map(PersonalObservationMapCell::latitude))
        val longitudeBounds = paddedBounds(cells.map(PersonalObservationMapCell::longitude))
        return PersonalObservationMap(
            cells = cells,
            mappedObservationCount = located.size,
            hiddenObservationCount = observationRows.count { !it.mapVisible },
            unavailableLocationCount = visible.size - located.size,
            observations = observationRows,
            minLatitude = latitudeBounds.first,
            maxLatitude = latitudeBounds.second,
            minLongitude = longitudeBounds.first,
            maxLongitude = longitudeBounds.second,
        )
    }


    private fun SyncedObservation.hasUsableLocation(): Boolean =
        latitude?.let { it.isFinite() && it in -90.0..90.0 } == true &&
            longitude?.let { it.isFinite() && it in -180.0..180.0 } == true

    private fun SyncedObservation.displayLabel(): String = label
        .takeUnless { it.isBlank() || it.equals("null", ignoreCase = true) }
        ?: "Unidentified observation"

    private fun paddedBounds(values: List<Double>): Pair<Double, Double> {
        if (values.isEmpty()) return 0.0 to 1.0
        val minimum = values.min()
        val maximum = values.max()
        val span = (maximum - minimum).coerceAtLeast(MIN_VIEW_SPAN_DEGREES)
        val centre = (minimum + maximum) / 2.0
        val padding = span * 0.12
        return centre - span / 2.0 - padding to centre + span / 2.0 + padding
    }
}
