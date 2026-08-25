package com.wildlife.feasibility.ui.components

import android.os.Bundle
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.wildlife.feasibility.ObservationDensitySnapshot
import com.wildlife.feasibility.ui.screens.map.ATLAS_LABEL_HALO
import com.wildlife.feasibility.ui.screens.map.ATLAS_LAND
import com.wildlife.feasibility.ui.screens.map.ATLAS_OBSERVATION
import com.wildlife.feasibility.ui.screens.map.FIELD_ATLAS_STYLE
import com.wildlife.feasibility.ui.screens.map.REGIONAL_ATLAS_ASSET
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleOpacity
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

/** Non-interactive world overview of already-coarsened public observation density cells. */
@Composable
fun ObservationDensityMap(snapshot: ObservationDensitySnapshot, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val atlasJson = remember(context) {
        context.assets.open(REGIONAL_ATLAS_ASSET).bufferedReader().use { it.readText() }
    }
    val features = remember(snapshot) { densityFeatures(snapshot) }
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
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        AndroidView(
            factory = {
                mapView.apply {
                    getMapAsync { map ->
                        map.uiSettings.isScrollGesturesEnabled = false
                        map.uiSettings.isZoomGesturesEnabled = false
                        map.uiSettings.isRotateGesturesEnabled = false
                        map.uiSettings.isTiltGesturesEnabled = false
                        map.uiSettings.isCompassEnabled = false
                        map.uiSettings.isLogoEnabled = false
                        map.uiSettings.isAttributionEnabled = false
                        map.setStyle(Style.Builder().fromJson(FIELD_ATLAS_STYLE)) { style ->
                            style.addSource(GeoJsonSource(LAND_SOURCE, atlasJson))
                            style.addLayer(
                                FillLayer(LAND_LAYER, LAND_SOURCE).withProperties(
                                    fillColor(ATLAS_LAND), fillOpacity(1f),
                                ),
                            )
                            style.addSource(GeoJsonSource(DENSITY_SOURCE, FeatureCollection.fromFeatures(features)))
                            style.addLayer(
                                CircleLayer(DENSITY_LAYER, DENSITY_SOURCE).withProperties(
                                    circleColor(ATLAS_OBSERVATION),
                                    circleRadius(
                                        Expression.interpolate(
                                            Expression.linear(), Expression.get("count"),
                                            Expression.stop(1, 3),
                                            Expression.stop(10, 7),
                                            Expression.stop(50, 12),
                                        ),
                                    ),
                                    circleOpacity(0.72f),
                                    circleStrokeColor(ATLAS_LABEL_HALO),
                                    circleStrokeWidth(0.8f),
                                ),
                            )
                            map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(12.0, 8.0), 0.0))
                        }
                    }
                }
            },
            update = { view ->
                view.getMapAsync { map ->
                    map.style?.getSourceAs<GeoJsonSource>(DENSITY_SOURCE)
                        ?.setGeoJson(FeatureCollection.fromFeatures(features))
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .semantics {
                    contentDescription = "World field atlas showing ${snapshot.cells.size} coarse public observation density areas"
                },
        )
    }
}

private fun densityFeatures(snapshot: ObservationDensitySnapshot): List<Feature> =
    snapshot.cells.map { cell ->
        Feature.fromGeometry(Point.fromLngLat(cell.longitude, cell.latitude)).apply {
            addNumberProperty("count", cell.count)
        }
    }

private const val LAND_SOURCE = "species-density-land"
private const val LAND_LAYER = "species-density-land"
private const val DENSITY_SOURCE = "species-density-cells"
private const val DENSITY_LAYER = "species-density-cells"
