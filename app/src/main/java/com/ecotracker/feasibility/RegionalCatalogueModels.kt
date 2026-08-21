package com.wildlife.feasibility

enum class EncounterRarity { UNKNOWN, COMMON, UNCOMMON, RARE, VERY_RARE }

enum class RegionalPrestige { STANDARD, LEGENDARY }

enum class ObservationRegionAssignment {
    LAND_POLYGON,
    OFFSHORE_BUFFER,
    MARINE_WORLDWIDE,
    REGION_UNCERTAIN,
    UNSUPPORTED_LAND,
}

data class Region(
    val key: String,
    val displayName: String,
    val polygonVersion: String,
    val displayOrder: Int,
)

data class CatalogueVersion(
    val regionKey: String,
    val version: String,
    val frozen: Boolean,
    val contentRulesVersion: String,
)

data class RegionalTaxon(
    val regionKey: String,
    val catalogueVersion: String,
    val taxonId: Long,
    val encounterRarity: EncounterRarity,
    val prestige: RegionalPrestige,
    val inclusionProvenance: String,
)

data class RegionalAchievement(
    val regionKey: String,
    val catalogueVersion: String,
    val key: String,
    val type: Type,
    val taxonIds: List<Long>,
) {
    enum class Type { ESSENTIALS, ICONS }
}

data class ObservationRegion(
    val observationUuid: String,
    val regionKey: String?,
    val boundaryVersion: String,
    val assignment: ObservationRegionAssignment,
) {
    val earnsRegionalProgress: Boolean
        get() = assignment == ObservationRegionAssignment.LAND_POLYGON ||
            assignment == ObservationRegionAssignment.OFFSHORE_BUFFER
}

data class RegionalRewardContext(
    val regionKey: String,
    val catalogueVersion: String,
    val taxonId: Long,
    val rarity: EncounterRarity,
    val prestige: RegionalPrestige,
    val essentials: Set<Long>,
    val icons: Set<Long>,
)

/** The minimal immutable catalogue data required to resolve a missing species silhouette. */
data class RegionalSilhouetteCandidate(
    val taxonId: Long,
    val scientificName: String,
    val taxonGroup: String?,
)

object RegionalAssignmentPolicy {
    const val MARINE_WORLDWIDE = "marine_worldwide"

    fun assignmentFor(
        hasSingleLandPolygon: Boolean,
        hasSingleOffshoreBuffer: Boolean,
        coordinateIsMarine: Boolean,
        coordinateIsSupportedLand: Boolean,
    ): ObservationRegionAssignment = when {
        hasSingleLandPolygon -> ObservationRegionAssignment.LAND_POLYGON
        hasSingleOffshoreBuffer -> ObservationRegionAssignment.OFFSHORE_BUFFER
        coordinateIsMarine -> ObservationRegionAssignment.MARINE_WORLDWIDE
        coordinateIsSupportedLand -> ObservationRegionAssignment.REGION_UNCERTAIN
        else -> ObservationRegionAssignment.UNSUPPORTED_LAND
    }
}
