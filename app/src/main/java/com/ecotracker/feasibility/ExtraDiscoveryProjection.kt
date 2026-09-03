package com.wildlife.feasibility

/**
 * The regional catalogue context in which a confirmed species is an Extra discovery.
 *
 * This is deliberately not a Boolean on the taxon. A species can be outside one frozen
 * regional guide and inside another, and a later catalogue version may classify it
 * differently without changing the user's observation history or XP ledger.
 */
data class ExtraDiscoveryContext(
    val regionKey: String,
    val regionName: String,
    val catalogueVersion: String,
)

object ExtraDiscoveryProjection {
    /**
     * Returns every frozen regional context in which each confirmed species-level taxon is
     * absent from the catalogue. Uncertain, unsupported and worldwide-marine assignments do
     * not make a regional claim.
     */
    fun contextsByTaxon(
        observations: List<SyncedObservation>,
        regionsByObservationUuid: Map<String, ObservationRegion>,
        catalogues: List<InstalledRegionalCatalogue>,
        catalogueTaxaByRegion: Map<String, Set<Long>>,
    ): Map<Long, List<ExtraDiscoveryContext>> {
        val cataloguesByRegion = catalogues.associateBy(InstalledRegionalCatalogue::regionKey)
        return observations.asSequence()
            .filter { it.confirmed && it.collectionTaxonRank == "species" }
            .mapNotNull { observation ->
                val taxonId = observation.collectionTaxonId ?: return@mapNotNull null
                val assignment = regionsByObservationUuid[observation.uuid]
                    ?.takeIf(ObservationRegion::earnsRegionalProgress)
                    ?: return@mapNotNull null
                val regionKey = assignment.regionKey ?: return@mapNotNull null
                val catalogue = cataloguesByRegion[regionKey] ?: return@mapNotNull null
                if (taxonId in catalogueTaxaByRegion[regionKey].orEmpty()) return@mapNotNull null
                taxonId to ExtraDiscoveryContext(
                    regionKey = regionKey,
                    regionName = catalogue.displayName,
                    catalogueVersion = catalogue.version,
                )
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, contexts) ->
                contexts.distinctBy { Triple(it.regionKey, it.catalogueVersion, it.regionName) }
                    .sortedWith(compareBy(ExtraDiscoveryContext::regionName, ExtraDiscoveryContext::catalogueVersion))
            }
    }
}
