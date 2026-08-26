package com.wildlife.feasibility.ui.screens.collection

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.wildlife.feasibility.ui.components.WildlifeScaffold
import com.wildlife.feasibility.ui.screens.map.PersonalMapContent
import com.wildlife.feasibility.ui.screens.map.PersonalObservationMap
import com.wildlife.feasibility.ui.screens.map.RegionalMapProgress

@Composable
fun CollectionMapScreen(
    accountLinked: Boolean,
    map: PersonalObservationMap,
    regionalProgress: List<RegionalMapProgress>,
    onOpenObservation: (String) -> Unit,
    onMapVisibilityChanged: (String, Boolean) -> Unit,
    selectedSection: CollectionSection,
    onSectionChange: (CollectionSection) -> Unit,
    bottomBar: @Composable () -> Unit = {},
) {
    WildlifeScaffold(
        title = "Collection",
        onBack = null,
        bottomBar = bottomBar,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            CollectionSectionSelector(selectedSection, onSectionChange)
            PersonalMapContent(
                accountLinked = accountLinked,
                map = map,
                regionalProgress = regionalProgress,
                onOpenObservation = onOpenObservation,
                onMapVisibilityChanged = onMapVisibilityChanged,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
