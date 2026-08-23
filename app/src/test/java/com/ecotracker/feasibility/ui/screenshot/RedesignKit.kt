package com.wildlife.feasibility.ui.screenshot

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.wildlife.feasibility.CollectionSpecies
import com.wildlife.feasibility.EncounterRarity
import kotlin.math.abs

/**
 * Shared scaffolding for the redesign candidates. This lives in the test source set on
 * purpose: it is a sandbox for choosing an art direction, not production code.
 */

data class RedesignItem(
    val key: String,
    val label: String,
    val group: String?,
    val rarity: EncounterRarity?,
    val collected: Boolean,
    val confirmed: Boolean,
    val legendary: Boolean,
    val essential: Boolean,
    val icon: Boolean,
    val awaiting: Boolean,
)

fun CollectionSpecies.toRedesignItem() = RedesignItem(
    key = key,
    label = label,
    group = taxonGroup,
    rarity = encounterRarity,
    collected = observationCount > 0,
    confirmed = bestQualityGrade == "research",
    // The candidates were drawn while Legend was still a tier of its own, above Icon.
    // The tier is gone from the app — it always named the same species as Icon — but the
    // studies are kept as they were reviewed, so the flag now comes from the sandbox's own
    // list rather than from a model field that no longer exists.
    legendary = key in SampleCollection.legendaryStudyKeys,
    essential = regionalEssential,
    icon = regionalIcon,
    awaiting = awaitingSpeciesIdentification,
)

val redesignItems: List<RedesignItem> = SampleCollection.entries.map { it.toRedesignItem() }

fun EncounterRarity?.shortLabel() = when (this) {
    EncounterRarity.COMMON -> "Common"
    EncounterRarity.UNCOMMON -> "Uncommon"
    EncounterRarity.RARE -> "Rare"
    EncounterRarity.VERY_RARE -> "Very rare"
    else -> "Unranked"
}

/** Deterministic hue per species so placeholder plates stay stable between runs. */
private fun hueFor(key: String): Float {
    var h = 0
    for (c in key) h = h * 31 + c.code
    return (abs(h) % 360).toFloat()
}

/**
 * Stands in for wildlife photography, which the screenshot harness cannot load.
 * These are abstract tone plates for judging composition only — never real imagery.
 */
@Composable
fun PhotoPlate(
    key: String,
    collected: Boolean,
    modifier: Modifier = Modifier,
    saturation: Float = 0.30f,
    lightness: Float = 0.42f,
) {
    val hue = hueFor(key)
    val top = Color.hsl(hue, if (collected) saturation else 0.05f, if (collected) lightness else 0.20f)
    val bottom = Color.hsl(
        (hue + 28f) % 360f,
        if (collected) saturation * 0.8f else 0.04f,
        if (collected) lightness * 0.55f else 0.12f,
    )
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                Brush.linearGradient(
                    listOf(top, bottom),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                )
            )
        }
    }
}
