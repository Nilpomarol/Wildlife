package com.wildlife.feasibility.ui.screens.map

import android.os.Bundle
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.wildlife.feasibility.ui.components.WildlifeScaffold
import com.wildlife.feasibility.ui.theme.WildlifeSpacing
import com.wildlife.feasibility.ui.theme.WildlifeTheme
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleOpacity
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

@Composable
fun PersonalMapScreen(
    accountLinked: Boolean,
    map: PersonalObservationMap,
    onBack: () -> Unit,
    onOpenObservation: (String) -> Unit,
    onMapVisibilityChanged: (String, Boolean) -> Unit,
) {
    var showVisibilityManager by rememberSaveable { mutableStateOf(false) }
    if (showVisibilityManager) {
        MapVisibilityDialog(
            observations = map.observations,
            onVisibilityChanged = onMapVisibilityChanged,
            onDismiss = { showVisibilityManager = false },
        )
    }
    WildlifeScaffold(title = "My map", onBack = onBack) { innerPadding ->
        when {
            !accountLinked -> MapMessage(
                "Link your iNaturalist account to map your public observation history.",
                Modifier.padding(innerPadding),
            )
            map.observations.isEmpty() -> MapMessage(
                "Your map will appear after a public observation is synced.",
                Modifier.padding(innerPadding),
            )
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(
                    start = WildlifeSpacing.Screen,
                    end = WildlifeSpacing.Screen,
                    bottom = WildlifeSpacing.Section,
                ),
                verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small),
            ) {
                item {
                    Text(
                        text = if (map.mappedObservationCount > 0) {
                            "${map.mappedObservationCount} public observations across ${map.cells.size} coarse areas"
                        } else {
                            "No observations are currently shown on your map"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (map.mappedObservationCount > 0) {
                    item {
                        key(map.cells) { ObservationBasemap(map) }
                    }
                } else {
                    item {
                        Text(
                            text = if (map.hiddenObservationCount > 0) {
                                "Your mapped observations are hidden by your local visibility choices."
                            } else {
                                "Your observations do not expose a usable public location. Wildlife never requests private coordinates."
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = WildlifeSpacing.Section),
                        )
                    }
                }
                item {
                    Text(
                        text = "OpenFreeMap / OpenStreetMap references are shown beneath privacy-safe 0.1° cells (roughly 8–11 km in Catalonia). Exact pins are not rendered or sent as overlay data.",
                        style = MaterialTheme.typography.labelMedium,
                        color = WildlifeTheme.colors.mutedText,
                    )
                    if (map.unavailableLocationCount > 0) {
                        Text(
                            text = "${map.unavailableLocationCount} observations have hidden or unavailable locations and are not plotted.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = WildlifeSpacing.Micro),
                        )
                    }
                    if (map.hiddenObservationCount > 0) {
                        Text(
                            text = "${map.hiddenObservationCount} observations are hidden by your local map settings.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = WildlifeSpacing.Micro),
                        )
                    }
                    Text(
                        text = "Opening the map requests the viewed tile regions from OpenFreeMap. The area list remains available when tiles cannot load.",
                        style = MaterialTheme.typography.labelSmall,
                        color = WildlifeTheme.colors.mutedText,
                        modifier = Modifier.padding(top = WildlifeSpacing.Micro),
                    )
                }
                item {
                    OutlinedButton(
                        onClick = { showVisibilityManager = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Manage map visibility")
                    }
                }
                if (map.cells.isNotEmpty()) {
                    item {
                        Text(
                            text = "Areas",
                            style = MaterialTheme.typography.titleMedium,
                            color = WildlifeTheme.colors.parchment,
                            modifier = Modifier.padding(top = WildlifeSpacing.Small),
                        )
                    }
                    items(map.cells, key = PersonalObservationMapCell::key) { cell ->
                        PersonalMapCellRow(cell, onOpenObservation)
                    }
                }
            }
        }
    }
}

@Composable
private fun MapVisibilityDialog(
    observations: List<PersonalMapObservation>,
    onVisibilityChanged: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp),
        ) {
            Column(Modifier.padding(WildlifeSpacing.Card)) {
                Text("Map visibility", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "Choose which public observations Wildlife includes in your private map projection. This does not change iNaturalist.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = WildlifeSpacing.Micro),
                )
                LazyColumn(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(top = WildlifeSpacing.Small),
                ) {
                    items(observations, key = PersonalMapObservation::uuid) { observation ->
                        MapVisibilityRow(observation, onVisibilityChanged)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("Done")
                }
            }
        }
    }
}

@Composable
private fun MapVisibilityRow(
    observation: PersonalMapObservation,
    onVisibilityChanged: (String, Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = WildlifeSpacing.Small),
        horizontalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(observation.label, style = MaterialTheme.typography.titleSmall)
            Text(
                text = when {
                    !observation.locationAvailable -> "No usable public location — not mapped"
                    observation.obscured -> "Obscured public location — shown only as a coarse area"
                    else -> "Public location — shown only as a coarse area"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = observation.mapVisible,
            onCheckedChange = { visible ->
                onVisibilityChanged(observation.uuid, visible)
            },
            modifier = Modifier.semantics {
                contentDescription = "Show ${observation.label} on My map"
                stateDescription = if (observation.mapVisible) "Included" else "Hidden"
            },
        )
    }
}

@Composable
private fun ObservationBasemap(map: PersonalObservationMap) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember {
        MapLibre.getInstance(context.applicationContext)
        MapView(context).apply { onCreate(Bundle()) }
    }
    DisposableEffect(mapView, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        AndroidView(
            factory = {
                mapView.apply {
                    getMapAsync { mapLibreMap ->
                        mapLibreMap.setStyle(Style.Builder().fromUri(OPEN_FREE_MAP_STYLE)) { style ->
                            val features = map.cells.map { cell ->
                                Feature.fromGeometry(
                                    Point.fromLngLat(cell.longitude, cell.latitude),
                                ).apply {
                                    addNumberProperty("research", cell.researchGradeCount)
                                }
                            }
                            style.addSource(
                                GeoJsonSource(
                                    OBSERVATION_SOURCE,
                                    FeatureCollection.fromFeatures(features),
                                ),
                            )
                            style.addLayer(
                                CircleLayer(OBSERVATION_LAYER, OBSERVATION_SOURCE).withProperties(
                                    circleColor("#9AA23D"),
                                    circleRadius(10f),
                                    circleOpacity(0.86f),
                                    circleStrokeColor("#F1E6CF"),
                                    circleStrokeWidth(1.5f),
                                ),
                            )
                            style.addLayer(
                                CircleLayer(RESEARCH_LAYER, OBSERVATION_SOURCE)
                                    .withFilter(
                                        Expression.gt(
                                            Expression.get("research"),
                                            Expression.literal(0),
                                        ),
                                    )
                                    .withProperties(
                                        circleColor("#D9A441"),
                                        circleRadius(4f),
                                    ),
                            )
                            val bounds = LatLngBounds.Builder()
                                .include(LatLng(map.minLatitude, map.minLongitude))
                                .include(LatLng(map.maxLatitude, map.maxLongitude))
                                .build()
                            mapLibreMap.moveCamera(
                                CameraUpdateFactory.newLatLngBounds(bounds, 72),
                            )
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
                .semantics {
                    contentDescription = "Interactive basemap showing ${map.cells.size} coarse observation areas"
                },
        )
    }
}

@Composable
private fun PersonalMapCellRow(
    cell: PersonalObservationMapCell,
    onOpenObservation: (String) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenObservation(cell.latestObservationUuid) },
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
                    text = cell.latestLabel,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = buildString {
                        append("${cell.observationCount} observation")
                        if (cell.observationCount != 1) append("s")
                        if (cell.researchGradeCount > 0) {
                            append(" • ${cell.researchGradeCount} Research Grade")
                        }
                        if (cell.obscuredCount > 0) append(" • includes obscured locations")
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "Open",
                style = MaterialTheme.typography.labelLarge,
                color = WildlifeTheme.colors.oliveStrong,
                modifier = Modifier.padding(start = WildlifeSpacing.Small),
            )
        }
    }
}

@Composable
private fun MapMessage(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(WildlifeSpacing.Section),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF080B09, widthDp = 390, heightDp = 720)
@Composable
private fun PersonalMapUnlinkedPreview() {
    WildlifeTheme {
        PersonalMapScreen(
            accountLinked = false,
            map = PersonalObservationMapProjection.build(emptyList()),
            onBack = {},
            onOpenObservation = {},
            onMapVisibilityChanged = { _, _ -> },
        )
    }
}

private const val OPEN_FREE_MAP_STYLE = "https://tiles.openfreemap.org/styles/fiord"
private const val OBSERVATION_SOURCE = "wildlife-observation-cells"
private const val OBSERVATION_LAYER = "wildlife-observation-circles"
private const val RESEARCH_LAYER = "wildlife-research-circles"
