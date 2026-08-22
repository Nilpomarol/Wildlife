package com.wildlife.feasibility.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.wildlife.feasibility.NearbySpecies
import com.wildlife.feasibility.ui.theme.WildlifeSpacing
import com.wildlife.feasibility.ui.theme.WildlifeTheme

/**
 * One reported-nearby species. Shared by the Explore Near me section and the Home preview so both
 * read the reporting count the same way; the count is reporting frequency, never rarity.
 */
@Composable
fun NearbySpeciesRow(
    species: NearbySpecies,
    onOpenTaxon: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onOpenTaxon(species.taxonId) },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(WildlifeSpacing.Card),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = species.commonName ?: species.scientificName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (species.commonName != null) {
                    Text(
                        text = species.scientificName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontStyle = FontStyle.Italic,
                    )
                }
            }
            Text(
                text = "${species.observationCount} reports",
                style = MaterialTheme.typography.labelLarge,
                color = WildlifeTheme.colors.oliveStrong,
                modifier = Modifier.padding(start = WildlifeSpacing.Small),
            )
        }
    }
}
