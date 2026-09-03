package com.wildlife.feasibility.ui.screenshot

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.github.takahirom.roborazzi.captureRoboImage
import com.wildlife.feasibility.InstalledRegionalCatalogue
import com.wildlife.feasibility.ui.components.SpeciesCardModel
import com.wildlife.feasibility.ui.components.SpeciesCardStatus
import com.wildlife.feasibility.ui.screens.explore.ExploreScreen
import com.wildlife.feasibility.ui.screens.explore.ExploreSection
import com.wildlife.feasibility.ui.screens.explore.ExploreSpecies
import com.wildlife.feasibility.ui.screens.explore.ExploreUiState
import com.wildlife.feasibility.ui.screens.explore.RegionalExploreCatalogue
import com.wildlife.feasibility.ui.screens.collection.CollectionFilterSheet
import com.wildlife.feasibility.ui.screens.collection.CollectionFilters
import com.wildlife.feasibility.ui.screens.collection.DiscoverySourceFilter
import com.wildlife.feasibility.ui.screens.collection.SpeciesGroup
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class ExtraDiscoveryScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun regionalExtraDiscoveries() {
        val catalogue = InstalledRegionalCatalogue(
            "mediterranean_europe", "Mediterranean Europe", "2026.1",
        )
        composeRule.setContent {
            StillTheme {
                ExploreScreen(
                    state = ExploreUiState(
                        browsedCatalogue = RegionalExploreCatalogue(
                            catalogue.regionKey, catalogue.displayName, catalogue.version, 1,
                        ),
                        installedCatalogues = listOf(catalogue),
                        entries = listOf(entry(1, "European robin", "Erithacus rubecula")),
                        extraDiscoveries = listOf(
                            entry(2, "Little owl", "Athene noctua", extra = true),
                        ),
                    ),
                    onBack = null,
                    onRefresh = {},
                    onOpenTaxon = {},
                    onDiscoverNearby = {},
                    onSelectRegion = {},
                    onVisibleTaxaChanged = {},
                    selectedSection = ExploreSection.GUIDE,
                    onSectionChange = {},
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("build/screenshots/extra-discoveries.png")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun extraDiscoveryFilter() {
        val filters = CollectionFilters(discoverySource = DiscoverySourceFilter.EXTRAS)
        composeRule.setContent {
            StillTheme {
                CollectionFilterSheet(
                    filters = filters,
                    onFilters = {},
                    presentGroups = listOf(SpeciesGroup.BIRDS),
                    matchCount = 2,
                    showDiscoverySource = true,
                    onDismiss = {},
                )
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage("build/screenshots/extra-discoveries-filter.png")
    }

    private fun entry(id: Long, name: String, scientific: String, extra: Boolean = false) =
        ExploreSpecies(
            taxonId = id,
            taxonGroup = "birds",
            commonName = name,
            scientificName = scientific,
            observed = true,
            card = SpeciesCardModel(
                key = "taxon:$id",
                label = name,
                supportingText = scientific,
                photoUrl = null,
                fallbackSilhouetteGroup = "birds",
                supportingTextItalic = true,
                collected = true,
                status = SpeciesCardStatus.OBSERVED,
                extraDiscoveryLabel = "Extra".takeIf { extra },
                extraDiscoveryDescription =
                    "Extra discovery in Mediterranean Europe, catalogue 2026.1".takeIf { extra },
            ),
        )
}
