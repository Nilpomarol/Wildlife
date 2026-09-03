package com.wildlife.feasibility

import android.content.Context

/**
 * Bounded foreground enrichment for off-catalogue taxa already visible in a projection.
 *
 * The first render always uses observation identity and personal media. This helper then
 * asks the existing stale-while-revalidate repository for only a few explicit taxa; it is
 * never a catalogue crawl and never runs from a composable.
 */
object OffCatalogueTaxonEnricher {
    private const val MAX_TAXA_PER_PASS = 4

    fun enrich(context: Context, taxonIds: Collection<Long>): Boolean {
        val bounded = taxonIds.distinct().take(MAX_TAXA_PER_PASS)
        if (bounded.isEmpty()) return false
        var improved = false
        OnDeviceWildlifeRepository(context).use { repository ->
            bounded.forEach { taxonId ->
                if (runCatching { repository.syncTaxonDetail(taxonId) }.isSuccess) improved = true
            }
        }
        return improved
    }
}
