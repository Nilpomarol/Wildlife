package com.wildlife.feasibility.ui.screens.map

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Bundle
import android.view.animation.LinearInterpolator
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.rememberCoroutineScope
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
import coil.decode.SvgDecoder
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.wildlife.feasibility.BuildConfig
import com.wildlife.feasibility.ui.components.RegionalCollectionMark
import com.wildlife.feasibility.ui.components.RegionalCollectionStamp
import com.wildlife.feasibility.ui.theme.WildlifeSpacing
import com.wildlife.feasibility.ui.theme.WildlifeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleOpacity
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconOffset
import org.maplibre.android.style.layers.PropertyFactory.iconSize
import org.maplibre.android.style.layers.PropertyFactory.lineBlur
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineGradient
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.PropertyFactory.visibility
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.MultiPolygon
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

@Composable
fun PersonalMapContent(
    accountLinked: Boolean,
    map: PersonalObservationMap,
    regionalProgress: List<RegionalMapProgress>,
    onOpenObservation: (String) -> Unit,
    onMapVisibilityChanged: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(
        start = WildlifeSpacing.Screen,
        end = WildlifeSpacing.Screen,
        bottom = WildlifeSpacing.Section,
    ),
) {
    var showVisibilityManager by rememberSaveable { mutableStateOf(false) }
    var showRegionalProgress by rememberSaveable { mutableStateOf(true) }
    var showPersonalObservations by rememberSaveable { mutableStateOf(true) }
    var previewMode by rememberSaveable { mutableStateOf(false) }
    var selectedRegion by remember { mutableStateOf<RegionalMapSelection?>(null) }
    val displayedMap = if (previewMode) mapPreview else map
    val displayedProgress = if (previewMode) regionalPreview else regionalProgress
    val displayedAccountLinked = accountLinked || previewMode
    if (showVisibilityManager) {
        MapVisibilityDialog(
            observations = displayedMap.observations,
            onVisibilityChanged = onMapVisibilityChanged,
            onDismiss = { showVisibilityManager = false },
        )
    }
    LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small),
            ) {
                item {
                    if (BuildConfig.DEBUG) DebugPreviewControl(
                        enabled = previewMode,
                        onEnabledChanged = { previewMode = it },
                    )
                }
                item {
                    Text(
                        text = "Regional progress",
                        style = MaterialTheme.typography.titleMedium,
                        color = WildlifeTheme.colors.parchment,
                    )
                    Text(
                        text = "All 24 local regional boundaries are shown. Progress is available for installed pilot catalogues only.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = WildlifeSpacing.Micro),
                    )
                }
                item {
                    MapLayerControls(
                        showRegionalProgress = showRegionalProgress,
                        showPersonalObservations = showPersonalObservations,
                        personalLayerAvailable = displayedAccountLinked && displayedMap.cells.isNotEmpty(),
                        onRegionalProgressChanged = { showRegionalProgress = it },
                        onPersonalObservationsChanged = { showPersonalObservations = it },
                    )
                }
                item {
                    Text(
                        text = if (previewMode) {
                            "Preview data — no observations or settings will be changed"
                        } else if (displayedMap.mappedObservationCount > 0) {
                            "${displayedMap.mappedObservationCount} public observations across ${displayedMap.cells.size} coarse areas"
                        } else {
                            "No observations are currently shown on your map"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item {
                    key(displayedMap.cells, displayedProgress) {
                        ObservationBasemap(
                            map = displayedMap,
                            regionalProgress = displayedProgress,
                            showRegionalProgress = showRegionalProgress,
                            showPersonalObservations = showPersonalObservations,
                            enableIconPerimeterLight = BuildConfig.DEBUG && previewMode,
                            selectedRegionKey = selectedRegion?.regionKey,
                            onRegionSelected = { selectedRegion = it },
                        )
                    }
                }
                selectedRegion?.let { selection ->
                    item {
                        SelectedRegionCard(
                            selection = selection,
                            progress = displayedProgress.firstOrNull { it.regionKey == selection.regionKey },
                        )
                    }
                }
                if (!previewMode && displayedMap.mappedObservationCount == 0) {
                    item {
                        Text(
                            text = if (displayedMap.hiddenObservationCount > 0) {
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
                    RegionalMapLegend(displayedProgress)
                }
                if (displayedAccountLinked) item {
                    Text(
                        text = "Privacy-safe 0.1° cells (roughly 8–11 km in Catalonia) are drawn on Wildlife's local field atlas. Exact pins are not rendered or sent as overlay data.",
                        style = MaterialTheme.typography.labelMedium,
                        color = WildlifeTheme.colors.mutedText,
                    )
                    if (!previewMode && displayedMap.unavailableLocationCount > 0) {
                        Text(
                            text = "${displayedMap.unavailableLocationCount} observations have hidden or unavailable locations and are not plotted.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = WildlifeSpacing.Micro),
                        )
                    }
                    if (!previewMode && displayedMap.hiddenObservationCount > 0) {
                        Text(
                            text = "${displayedMap.hiddenObservationCount} observations are hidden by your local map settings.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = WildlifeSpacing.Micro),
                        )
                    }
                    Text("The atlas is bundled locally and works without loading external map tiles.", style = MaterialTheme.typography.labelSmall, color = WildlifeTheme.colors.mutedText, modifier = Modifier.padding(top = WildlifeSpacing.Micro))
                }
                if (!previewMode && accountLinked) item {
                    OutlinedButton(
                        onClick = { showVisibilityManager = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Manage map visibility")
                    }
                }
                if (displayedProgress.isNotEmpty()) {
                    item {
                        Text("Installed regions", style = MaterialTheme.typography.titleMedium, color = WildlifeTheme.colors.parchment)
                    }
                    items(displayedProgress, key = RegionalMapProgress::regionKey) { progress ->
                        RegionalMapProgressRow(progress)
                    }
                }
                if (displayedMap.cells.isNotEmpty()) {
                    item {
                        Text(
                            text = "Areas",
                            style = MaterialTheme.typography.titleMedium,
                            color = WildlifeTheme.colors.parchment,
                            modifier = Modifier.padding(top = WildlifeSpacing.Small),
                        )
                    }
                    items(displayedMap.cells, key = PersonalObservationMapCell::key) { cell ->
                        PersonalMapCellRow(cell, onOpenObservation)
                    }
                }
            }
}

@Composable
private fun DebugPreviewControl(enabled: Boolean, onEnabledChanged: (Boolean) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(WildlifeSpacing.Card),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Preview map appearance", style = MaterialTheme.typography.titleSmall)
                Text("Debug-only sample regions, coarse areas and experimental Icon perimeter light. It is never saved or synced.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = enabled, onCheckedChange = onEnabledChanged, modifier = Modifier.semantics { stateDescription = if (enabled) "Preview shown" else "Your data shown" })
        }
    }
}

@Composable
private fun MapLayerControls(
    showRegionalProgress: Boolean,
    showPersonalObservations: Boolean,
    personalLayerAvailable: Boolean,
    onRegionalProgressChanged: (Boolean) -> Unit,
    onPersonalObservationsChanged: (Boolean) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(WildlifeSpacing.Card)) {
            MapLayerSwitch("Regional progress", "Completion and curated achievement marks", showRegionalProgress, true, onRegionalProgressChanged)
            MapLayerSwitch("My observation areas", "Coarse, privacy-safe cells", showPersonalObservations, personalLayerAvailable, onPersonalObservationsChanged)
        }
    }
}

@Composable
private fun MapLayerSwitch(label: String, detail: String, checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled, modifier = Modifier.semantics { stateDescription = if (checked) "Shown" else "Hidden" })
    }
}

@Composable
private fun RegionalMapLegend(progress: List<RegionalMapProgress>) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(WildlifeSpacing.Card)) {
            Text("Map legend", style = MaterialTheme.typography.titleSmall)
            Text("Warm neutral land has no catalogue progress. Olive deepens with completion; the Essential and Icon field marks identify completed regional checklists.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = WildlifeSpacing.Micro))
            Row(
                modifier = Modifier.padding(top = WildlifeSpacing.Small),
                horizontalArrangement = Arrangement.spacedBy(WildlifeSpacing.Card),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RegionalCollectionStamp(RegionalCollectionMark.ESSENTIAL)
                Text("Essentials complete", style = MaterialTheme.typography.labelMedium)
                RegionalCollectionStamp(RegionalCollectionMark.ICON)
                Text("Icons complete", style = MaterialTheme.typography.labelMedium)
            }
            Text(
                "Local atlas geometry derived from Natural Earth 5.1.1 (public domain).",
                style = MaterialTheme.typography.labelSmall,
                color = WildlifeTheme.colors.mutedText,
                modifier = Modifier.padding(top = WildlifeSpacing.Small),
            )
            if (progress.isEmpty()) Text("No regional catalogue is installed yet.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = WildlifeSpacing.Small))
        }
    }
}

@Composable
private fun RegionalMapProgressRow(progress: RegionalMapProgress) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(WildlifeSpacing.Card), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(progress.displayName, style = MaterialTheme.typography.titleSmall)
                Text("${progress.observedSpecies} / ${progress.totalSpecies} species", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(WildlifeSpacing.Micro), verticalAlignment = Alignment.CenterVertically) {
                if (progress.essentialsComplete) RegionalCollectionStamp(RegionalCollectionMark.ESSENTIAL)
                if (progress.iconsComplete) RegionalCollectionStamp(RegionalCollectionMark.ICON)
                if (!progress.essentialsComplete && !progress.iconsComplete) {
                    Text("In progress", style = MaterialTheme.typography.labelLarge, color = WildlifeTheme.colors.oliveStrong)
                }
            }
        }
    }
}

private data class RegionalMapSelection(val regionKey: String, val displayName: String)

@Composable
private fun SelectedRegionCard(selection: RegionalMapSelection, progress: RegionalMapProgress?) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, WildlifeTheme.colors.gold.copy(alpha = 0.62f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(WildlifeSpacing.Card),
            horizontalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(selection.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = progress?.let { "${it.observedSpecies} of ${it.totalSpecies} species observed" }
                        ?: "Regional catalogue not installed",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (progress != null) {
                    Text(
                        text = "Essentials ${if (progress.essentialsComplete) "complete" else "in progress"} • Icons ${if (progress.iconsComplete) "complete" else "in progress"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = WildlifeSpacing.Micro),
                    )
                }
            }
            if (progress?.essentialsComplete == true) RegionalCollectionStamp(RegionalCollectionMark.ESSENTIAL)
            if (progress?.iconsComplete == true) RegionalCollectionStamp(RegionalCollectionMark.ICON)
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
private fun ObservationBasemap(
    map: PersonalObservationMap,
    regionalProgress: List<RegionalMapProgress>,
    showRegionalProgress: Boolean,
    showPersonalObservations: Boolean,
    enableIconPerimeterLight: Boolean,
    selectedRegionKey: String?,
    onRegionSelected: (RegionalMapSelection?) -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val coroutineScope = rememberCoroutineScope()
    val atlasJson = remember(context) {
        context.assets.open(REGIONAL_ATLAS_ASSET).bufferedReader().use { it.readText() }
    }
    val labelJson = remember(context) {
        context.assets.open(REGIONAL_LABEL_ASSET).bufferedReader().use { it.readText() }
    }
    val labelFeatures = remember(labelJson) {
        FeatureCollection.fromJson(labelJson).features().orEmpty()
    }
    val essentialFeatures = remember(labelFeatures, regionalProgress) {
        achievementFeatures(labelFeatures, regionalProgress, RegionalCollectionMark.ESSENTIAL)
    }
    val iconFeatures = remember(labelFeatures, regionalProgress) {
        achievementFeatures(labelFeatures, regionalProgress, RegionalCollectionMark.ICON)
    }
    val iconPerimeterFeatures = remember(atlasJson, regionalProgress) {
        iconPerimeterFeatures(atlasJson, regionalProgress)
    }
    val iconPerimeterAnimator = remember {
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 4_800L
            repeatMode = ValueAnimator.RESTART
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
        }
    }
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
            iconPerimeterAnimator.cancel()
            iconPerimeterAnimator.removeAllUpdateListeners()
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
                        mapLibreMap.uiSettings.isRotateGesturesEnabled = false
                        mapLibreMap.uiSettings.isTiltGesturesEnabled = false
                        mapLibreMap.uiSettings.isCompassEnabled = false
                        mapLibreMap.uiSettings.isLogoEnabled = false
                        mapLibreMap.uiSettings.isAttributionEnabled = false
                        mapLibreMap.setMinZoomPreference(0.0)
                        mapLibreMap.setMaxZoomPreference(7.0)
                        mapLibreMap.setStyle(Style.Builder().fromJson(FIELD_ATLAS_STYLE)) { style ->
                            style.addSource(GeoJsonSource(REGIONAL_SOURCE, atlasJson))
                            style.addLayer(FillLayer(BASE_LAND_LAYER, REGIONAL_SOURCE).withProperties(
                                fillColor(ATLAS_LAND), fillOpacity(1f),
                            ))
                            style.addLayer(FillLayer(REGIONAL_FILL_LAYER, REGIONAL_SOURCE).withProperties(
                                fillColor(regionalFillExpression(regionalProgress)), fillOpacity(1f),
                            ).withFilter(Expression.eq(Expression.get("progressing"), Expression.literal(true))))
                            style.addLayer(FillLayer(ICON_PULSE_FILL_LAYER, REGIONAL_SOURCE)
                                .withFilter(completedRegionFilter(regionalProgress, RegionalCollectionMark.ICON))
                                .withProperties(fillColor(ATLAS_ICON_SHINE), fillOpacity(if (enableIconPerimeterLight) 0.12f else 0f)))
                            style.addSource(
                                GeoJsonSource(
                                    ICON_PERIMETER_SOURCE,
                                    FeatureCollection.fromFeatures(iconPerimeterFeatures),
                                    GeoJsonOptions().withLineMetrics(true),
                                ),
                            )
                            style.addLayer(LineLayer(ICON_PERIMETER_HALO_LAYER, ICON_PERIMETER_SOURCE)
                                .withProperties(
                                    lineGradient(iconPerimeterGradient(0f)),
                                    lineOpacity(if (enableIconPerimeterLight) 1f else 0f),
                                    lineWidth(8f),
                                    lineBlur(4.2f),
                                ))
                            style.addLayer(LineLayer(REGIONAL_OUTLINE_LAYER, REGIONAL_SOURCE).withProperties(
                                lineColor(regionalOutlineExpression(regionalProgress)), lineWidth(regionalOutlineWidthExpression(regionalProgress)),
                            ))
                            style.addLayer(LineLayer(ICON_PERIMETER_LIGHT_LAYER, ICON_PERIMETER_SOURCE)
                                .withProperties(
                                    lineGradient(iconPerimeterGradient(0f)),
                                    lineOpacity(if (enableIconPerimeterLight) 1f else 0f),
                                    lineWidth(2.6f),
                                    lineBlur(0.35f),
                                ))
                            style.addLayer(LineLayer(SELECTED_REGION_LAYER, REGIONAL_SOURCE)
                                .withFilter(regionFilter(selectedRegionKey))
                                .withProperties(lineColor(ATLAS_SELECTION), lineWidth(3.2f)))
                            style.addSource(
                                GeoJsonSource(
                                    ESSENTIAL_SOURCE,
                                    FeatureCollection.fromFeatures(essentialFeatures),
                                ),
                            )
                            style.addSource(
                                GeoJsonSource(
                                    ICON_SOURCE,
                                    FeatureCollection.fromFeatures(iconFeatures),
                                ),
                            )
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
                                    circleColor(ATLAS_OBSERVATION),
                                    circleRadius(8f),
                                    circleOpacity(0.88f),
                                    circleStrokeColor(ATLAS_LABEL_HALO),
                                    circleStrokeWidth(1.8f),
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
                                        circleColor(ATLAS_ACHIEVEMENT),
                                        circleRadius(3.5f),
                                    ),
                            )
                            updateLayerVisibility(style, showRegionalProgress, showPersonalObservations)
                            configureIconPerimeterLight(style, iconPerimeterAnimator, enableIconPerimeterLight)
                            mapLibreMap.moveCamera(
                                CameraUpdateFactory.newLatLngZoom(ATLAS_CENTER, 0.0),
                            )
                            mapLibreMap.addOnMapClickListener { point ->
                                val screenPoint = mapLibreMap.projection.toScreenLocation(point)
                                val feature = mapLibreMap.queryRenderedFeatures(screenPoint, BASE_LAND_LAYER)
                                    .firstOrNull()
                                val regionKey = feature?.getStringProperty("region")
                                if (regionKey == null || regionKey == "unsupported_land") {
                                    onRegionSelected(null)
                                } else {
                                    onRegionSelected(
                                        RegionalMapSelection(
                                            regionKey,
                                            feature.getStringProperty("display_name") ?: regionKey,
                                        ),
                                    )
                                }
                                true
                            }
                            coroutineScope.launch {
                                val essentialBitmap = loadMapMarkBitmap(context, RegionalCollectionMark.ESSENTIAL, ATLAS_ESSENTIAL)
                                val iconBitmap = loadMapMarkBitmap(context, RegionalCollectionMark.ICON, ATLAS_ACHIEVEMENT)
                                if (mapLibreMap.style !== style) return@launch
                                essentialBitmap?.let { bitmap ->
                                    style.addImage(ESSENTIAL_IMAGE, bitmap)
                                    style.addLayer(SymbolLayer(ESSENTIAL_LAYER, ESSENTIAL_SOURCE).withProperties(
                                        iconImage(ESSENTIAL_IMAGE), iconSize(0.34f), iconOffset(arrayOf(-16f, 0f)),
                                        iconAllowOverlap(true), iconIgnorePlacement(true),
                                    ))
                                }
                                iconBitmap?.let { bitmap ->
                                    style.addImage(ICON_IMAGE, bitmap)
                                    style.addLayer(SymbolLayer(ICON_LAYER, ICON_SOURCE).withProperties(
                                        iconImage(ICON_IMAGE), iconSize(0.34f), iconOffset(arrayOf(16f, 0f)),
                                        iconAllowOverlap(true), iconIgnorePlacement(true),
                                    ))
                                }
                                updateLayerVisibility(style, showRegionalProgress, showPersonalObservations)
                            }
                        }
                    }
                }
            },
            update = { view ->
                view.getMapAsync { mapLibreMap ->
                    mapLibreMap.style?.let { style ->
                        updateLayerVisibility(style, showRegionalProgress, showPersonalObservations)
                        style.getLayerAs<LineLayer>(SELECTED_REGION_LAYER)?.setFilter(regionFilter(selectedRegionKey))
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
                .semantics {
                    contentDescription = "Interactive Wildlife field atlas showing 24 regions and ${map.cells.size} coarse observation areas"
                },
        )
    }
}

private fun regionalFillExpression(progress: List<RegionalMapProgress>): Expression {
    val stops = progress.map { region ->
        val color = when {
            region.completionFraction <= 0f -> ATLAS_NO_PROGRESS
            region.completionFraction < 0.34f -> ATLAS_PROGRESS_LOW
            region.completionFraction < 0.67f -> ATLAS_PROGRESS_MID
            else -> ATLAS_PROGRESS_HIGH
        }
        Expression.stop(region.regionKey, color)
    }.toTypedArray()
    return Expression.match(Expression.get("region"), Expression.literal(ATLAS_NO_PROGRESS), *stops)
}

private fun regionalOutlineExpression(progress: List<RegionalMapProgress>): Expression {
    val stops = progress.map { region ->
        val color = when {
            region.iconsComplete -> ATLAS_ACHIEVEMENT
            region.essentialsComplete -> ATLAS_ESSENTIAL_BORDER
            else -> ATLAS_REGION_OUTLINE
        }
        Expression.stop(region.regionKey, color)
    }.toTypedArray()
    return Expression.match(Expression.get("region"), Expression.literal(ATLAS_REGION_OUTLINE), *stops)
}

private fun regionalOutlineWidthExpression(progress: List<RegionalMapProgress>): Expression {
    val stops = progress.map { region ->
        Expression.stop(region.regionKey, if (region.iconsComplete) 2.35 else if (region.essentialsComplete) 1.9 else 1.05)
    }.toTypedArray()
    return Expression.match(Expression.get("region"), Expression.literal(1.05), *stops)
}

private fun regionFilter(regionKey: String?): Expression =
    Expression.eq(Expression.get("region"), Expression.literal(regionKey ?: "__none__"))

private fun completedRegionFilter(
    progress: List<RegionalMapProgress>,
    mark: RegionalCollectionMark,
): Expression {
    val stops = progress.filter { item ->
        if (mark == RegionalCollectionMark.ESSENTIAL) item.essentialsComplete else item.iconsComplete
    }.map { Expression.stop(it.regionKey, true) }.toTypedArray()
    if (stops.isEmpty()) {
        return Expression.eq(Expression.literal(1), Expression.literal(0))
    }
    return Expression.match(Expression.get("region"), Expression.literal(false), *stops)
}

private fun iconPerimeterFeatures(
    atlasJson: String,
    progress: List<RegionalMapProgress>,
): List<Feature> {
    val completed = progress.filter(RegionalMapProgress::iconsComplete)
        .mapTo(mutableSetOf(), RegionalMapProgress::regionKey)
    return FeatureCollection.fromJson(atlasJson).features().orEmpty().flatMap { feature ->
        val region = feature.getStringProperty("region")
        if (region !in completed) return@flatMap emptyList()
        val exteriorRings = when (val geometry = feature.geometry()) {
            is Polygon -> geometry.coordinates().firstOrNull()?.let(::listOf).orEmpty()
            is MultiPolygon -> geometry.coordinates().mapNotNull { polygon -> polygon.firstOrNull() }
            else -> emptyList()
        }
        exteriorRings.map { ring ->
            Feature.fromGeometry(LineString.fromLngLats(ring)).apply {
                addStringProperty("region", region)
            }
        }
    }
}

private fun iconPerimeterGradient(position: Float): Expression {
    val wrappedProgress = Expression.mod(
        Expression.sum(
            Expression.subtract(Expression.lineProgress(), Expression.literal(position)),
            Expression.literal(1f),
        ),
        Expression.literal(1f),
    )
    return Expression.interpolate(
        Expression.linear(),
        wrappedProgress,
        Expression.stop(0f, Expression.rgba(255, 244, 200, 1f)),
        Expression.stop(0.035f, Expression.rgba(245, 190, 72, 0.72f)),
        Expression.stop(0.10f, Expression.rgba(240, 182, 70, 0f)),
        Expression.stop(0.88f, Expression.rgba(240, 182, 70, 0f)),
        Expression.stop(0.955f, Expression.rgba(245, 190, 72, 0.42f)),
        Expression.stop(1f, Expression.rgba(255, 244, 200, 1f)),
    )
}

private fun configureIconPerimeterLight(style: Style, animator: ValueAnimator, enabled: Boolean) {
    animator.cancel()
    animator.removeAllUpdateListeners()
    val fill = style.getLayerAs<FillLayer>(ICON_PULSE_FILL_LAYER)
    val halo = style.getLayerAs<LineLayer>(ICON_PERIMETER_HALO_LAYER)
    val light = style.getLayerAs<LineLayer>(ICON_PERIMETER_LIGHT_LAYER)
    if (!enabled || fill == null || halo == null || light == null) {
        fill?.setProperties(fillOpacity(0f))
        halo?.setProperties(lineOpacity(0f))
        light?.setProperties(lineOpacity(0f))
        return
    }
    fill.setProperties(fillOpacity(0.12f))
    halo.setProperties(lineOpacity(1f))
    light.setProperties(lineOpacity(1f))
    animator.addUpdateListener { animation ->
        val gradient = iconPerimeterGradient(animation.animatedValue as Float)
        halo.setProperties(lineGradient(gradient))
        light.setProperties(lineGradient(gradient))
    }
    animator.start()
}

private fun achievementFeatures(
    anchors: List<Feature>,
    progress: List<RegionalMapProgress>,
    mark: RegionalCollectionMark,
): List<Feature> {
    val completed = progress.filter { item ->
        if (mark == RegionalCollectionMark.ESSENTIAL) item.essentialsComplete else item.iconsComplete
    }.mapTo(mutableSetOf(), RegionalMapProgress::regionKey)
    return anchors.mapNotNull { anchor ->
        val region = anchor.getStringProperty("region")
        val geometry = anchor.geometry()
        if (region !in completed || geometry == null) null else Feature.fromGeometry(geometry).apply {
            addStringProperty("region", region)
        }
    }
}

private suspend fun loadMapMarkBitmap(
    context: android.content.Context,
    mark: RegionalCollectionMark,
    tint: String,
): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val request = ImageRequest.Builder(context)
            .data("file:///android_asset/field_marks/${mark.assetName}")
            .decoderFactory(SvgDecoder.Factory())
            .allowHardware(false)
            .size(96)
            .build()
        val drawable = (context.imageLoader.execute(request) as? SuccessResult)?.drawable
            ?: return@runCatching null
        val source = drawable.toBitmap(96, 96, Bitmap.Config.ARGB_8888)
        Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).also { output ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                colorFilter = PorterDuffColorFilter(android.graphics.Color.parseColor(tint), PorterDuff.Mode.SRC_IN)
            }
            Canvas(output).drawBitmap(source, 0f, 0f, paint)
        }
    }.getOrNull()
}

private fun updateLayerVisibility(style: Style, showRegionalProgress: Boolean, showPersonalObservations: Boolean) {
    val regional = if (showRegionalProgress) Property.VISIBLE else Property.NONE
    val observations = if (showPersonalObservations) Property.VISIBLE else Property.NONE
    style.getLayer(REGIONAL_FILL_LAYER)?.setProperties(visibility(regional))
    style.getLayer(ICON_PULSE_FILL_LAYER)?.setProperties(visibility(regional))
    style.getLayer(REGIONAL_OUTLINE_LAYER)?.setProperties(visibility(regional))
    style.getLayer(ICON_PERIMETER_HALO_LAYER)?.setProperties(visibility(regional))
    style.getLayer(ICON_PERIMETER_LIGHT_LAYER)?.setProperties(visibility(regional))
    style.getLayer(ESSENTIAL_LAYER)?.setProperties(visibility(regional))
    style.getLayer(ICON_LAYER)?.setProperties(visibility(regional))
    style.getLayer(OBSERVATION_LAYER)?.setProperties(visibility(observations))
    style.getLayer(RESEARCH_LAYER)?.setProperties(visibility(observations))
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
        PersonalMapContent(
            accountLinked = false,
            map = PersonalObservationMapProjection.build(emptyList()),
            regionalProgress = emptyList(),
            onOpenObservation = {},
            onMapVisibilityChanged = { _, _ -> },
        )
    }
}

/**
 * Debug-only visual fixture. It is selected only by [DebugPreviewControl] and never reaches a
 * repository, preference, or callback that can change a user's observation data.
 */
private val mapPreview = PersonalObservationMap(
    cells = listOf(
        PersonalObservationMapCell("preview:1", 41.42, 2.15, 4, 2, 1, "preview-1", "Sample observation"),
        PersonalObservationMapCell("preview:2", 40.78, 0.72, 2, 0, 0, "preview-2", "Sample observation"),
        PersonalObservationMapCell("preview:3", 39.57, 2.64, 1, 1, 0, "preview-3", "Sample observation"),
    ),
    mappedObservationCount = 7,
    hiddenObservationCount = 0,
    unavailableLocationCount = 0,
    observations = emptyList(),
    minLatitude = 39.35,
    maxLatitude = 41.65,
    minLongitude = 0.45,
    maxLongitude = 2.9,
)

private val regionalPreview = listOf(
    RegionalMapProgress("mediterranean_europe", "Mediterranean Europe", 54, 120, essentialsComplete = true, iconsComplete = false),
    RegionalMapProgress("east_africa", "East Africa", 36, 96, essentialsComplete = false, iconsComplete = false),
    RegionalMapProgress("caribbean", "Caribbean", 79, 110, essentialsComplete = true, iconsComplete = true),
)

private const val FIELD_ATLAS_STYLE = """{"version":8,"name":"Wildlife Field Atlas","sources":{},"layers":[{"id":"atlas-water","type":"background","paint":{"background-color":"#B8C7C0"}}]}"""
private const val REGIONAL_ATLAS_ASSET = "atlas/regional-atlas-v1.geojson"
private const val REGIONAL_LABEL_ASSET = "atlas/regional-atlas-labels-v1.geojson"
private const val REGIONAL_SOURCE = "wildlife-regional-boundaries"
private const val BASE_LAND_LAYER = "wildlife-atlas-land"
private const val REGIONAL_FILL_LAYER = "wildlife-regional-progress"
private const val ICON_PULSE_FILL_LAYER = "wildlife-icon-pulse-fill"
private const val REGIONAL_OUTLINE_LAYER = "wildlife-regional-outlines"
private const val ICON_PERIMETER_SOURCE = "wildlife-icon-perimeters"
private const val ICON_PERIMETER_HALO_LAYER = "wildlife-icon-perimeter-halo"
private const val ICON_PERIMETER_LIGHT_LAYER = "wildlife-icon-perimeter-light"
private const val SELECTED_REGION_LAYER = "wildlife-selected-region"
private const val ESSENTIAL_SOURCE = "wildlife-regional-essentials"
private const val ESSENTIAL_LAYER = "wildlife-regional-essentials"
private const val ESSENTIAL_IMAGE = "wildlife-essential-mark"
private const val ICON_SOURCE = "wildlife-regional-icons"
private const val ICON_LAYER = "wildlife-regional-icons"
private const val ICON_IMAGE = "wildlife-icon-mark"
private const val OBSERVATION_SOURCE = "wildlife-observation-cells"
private const val OBSERVATION_LAYER = "wildlife-observation-circles"
private const val RESEARCH_LAYER = "wildlife-research-circles"

private const val ATLAS_LAND = "#D8D2BC"
private const val ATLAS_NO_PROGRESS = "#C9C8AE"
private const val ATLAS_PROGRESS_LOW = "#B6BD82"
private const val ATLAS_PROGRESS_MID = "#98A25D"
private const val ATLAS_PROGRESS_HIGH = "#7D8C3D"
private const val ATLAS_REGION_OUTLINE = "#777866"
private const val ATLAS_ESSENTIAL = "#737B2F"
private const val ATLAS_ESSENTIAL_BORDER = "#C89432"
private const val ATLAS_ACHIEVEMENT = "#B77B22"
private const val ATLAS_ICON_SHINE = "#F0B646"
private const val ATLAS_SELECTION = "#E0A43D"
private const val ATLAS_LABEL_HALO = "#EFE7D2"
private const val ATLAS_OBSERVATION = "#59682C"

private val ATLAS_CENTER = LatLng(12.0, 8.0)
