package com.pelotcl.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource

@Composable
fun MapLibreView(
    modifier: Modifier = Modifier,
    initialPosition: LatLng = LatLng(45.75, 4.85),
    initialZoom: Double = 10.0,
    // Use local asset for faster startup (eliminates network latency for style loading)
    styleUrl: String = "asset://positron.json",
    onMapReady: (MapLibreMap) -> Unit = {},
    userLocation: LatLng? = null,
    centerOnUserLocation: Boolean = false
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    remember {
        MapLibre.getInstance(context)
    }

    val mapView = remember {
        MapView(context).apply {
            getMapAsync { map ->
                // Disable map rotation
                map.uiSettings.isRotateGesturesEnabled = false
                
                map.setStyle(styleUrl) { style ->
                    // Configuration of initial camera position
                    val targetPosition = if (centerOnUserLocation && userLocation != null) {
                        userLocation
                    } else {
                        initialPosition
                    }
                    
                    val zoom = if (centerOnUserLocation && userLocation != null) {
                        15.0
                    } else {
                        initialZoom
                    }
                    
                    map.cameraPosition = CameraPosition.Builder()
                        .target(targetPosition)
                        .zoom(zoom)
                        .build()
                    
                    onMapReady(map)
                }
            }
        }
    }

    // Track the initial style to skip the first LaunchedEffect trigger (already applied in remember block)
    val initialStyleApplied = remember { mutableStateOf(false) }

    // Re-apply map style when styleUrl changes (e.g. user changed it in settings)
    LaunchedEffect(styleUrl) {
        if (!initialStyleApplied.value) {
            initialStyleApplied.value = true
            return@LaunchedEffect
        }
        mapView.getMapAsync { map ->
            // Preserve current camera position before style change
            val currentCamera = map.cameraPosition
            map.setStyle(styleUrl) { _ ->
                // Restore camera position after style reload
                map.cameraPosition = currentCamera
                // Re-notify so that PlanScreen re-adds all its layers (lines, stops, etc.)
                onMapReady(map)
            }
        }
    }

    // Update user location on map when it changes
    LaunchedEffect(userLocation) {
        if (userLocation != null) {
            mapView.getMapAsync { map ->
                map.getStyle { style ->
                    // Remove existing user location layer and source if any
                    style.getLayer("user-location-layer")?.let { style.removeLayer(it) }
                    style.getSource("user-location-source")?.let { style.removeSource(it) }
                    
                    // Create GeoJSON for user location
                    val userLocationGeoJson = JsonObject().apply {
                        addProperty("type", "Feature")
                        val geometryObject = JsonObject().apply {
                            addProperty("type", "Point")
                            val coordinatesArray = JsonArray()
                            coordinatesArray.add(userLocation.longitude)
                            coordinatesArray.add(userLocation.latitude)
                            add("coordinates", coordinatesArray)
                        }
                        add("geometry", geometryObject)
                    }.toString()
                    
                    // Add source for user location
                    val userLocationSource = GeoJsonSource("user-location-source", userLocationGeoJson)
                    style.addSource(userLocationSource)
                    
                    // Add a blue circle layer for user location
                    val userLocationLayer = CircleLayer("user-location-layer", "user-location-source").apply {
                        setProperties(
                            PropertyFactory.circleRadius(10f),
                            PropertyFactory.circleColor("#3B82F6"),
                            PropertyFactory.circleStrokeWidth(3f),
                            PropertyFactory.circleStrokeColor("#FFFFFF"),
                            PropertyFactory.circleOpacity(1.0f)
                        )
                    }
                    style.addLayer(userLocationLayer)
                    
                    // Center on user location if requested
                    if (centerOnUserLocation) {
                        map.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(userLocation, 17.0),
                            1000
                        )
                    }
                }
            }
        }
    }

    // Manage MapView lifecycle
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(null)
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.matchParentSize()
        )
    }
}
