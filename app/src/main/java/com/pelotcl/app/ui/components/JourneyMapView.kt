import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.pelotcl.app.data.repository.JourneyResult
import com.pelotcl.app.utils.LineColorHelper
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource

/**
 * A map view component that displays a journey route with colored polylines.
 * Each leg of the journey is drawn with the corresponding transport line color.
 */
@Composable
fun JourneyMapView(
    modifier: Modifier = Modifier,
    journey: JourneyResult,
    onBack: () -> Unit,
    userLocation: LatLng? = null,
    styleUrl: String = "https://tiles.openfreemap.org/styles/positron"
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
                    // Calculate bounds for the journey
                    val boundsBuilder = LatLngBounds.Builder()

                    // Add all points from all legs to the bounds
                    journey.legs.forEach { leg ->
                        boundsBuilder.include(LatLng(leg.fromLat, leg.fromLon))
                        boundsBuilder.include(LatLng(leg.toLat, leg.toLon))
                        // Include intermediate stops
                        leg.intermediateStops.forEach { stop ->
                            boundsBuilder.include(LatLng(stop.lat, stop.lon))
                        }
                    }

                    // Fit camera to bounds with padding
                    try {
                        val bounds = boundsBuilder.build()
                        map.moveCamera(
                            CameraUpdateFactory.newLatLngBounds(bounds, 60)
                        )
                    } catch (e: Exception) {
                        // Fallback to Lyon center if bounds calculation fails
                        map.cameraPosition = CameraPosition.Builder()
                            .target(LatLng(45.75, 4.85))
                            .zoom(12.0)
                            .build()
                    }

                    // Draw each leg of the journey
                    journey.legs.forEachIndexed { index, leg ->
                        val lineColor = if (leg.isWalking) {
                            "#6B7280" // Gray for walking
                        } else {
                            // Get hex color from LineColorHelper
                            val colorInt = LineColorHelper.getColorForLineString(leg.routeName ?: "")
                            String.format("#%06X", 0xFFFFFF and colorInt)
                        }

                        // Build the coordinates array for this leg
                        // Include: fromStop -> intermediate stops -> toStop
                        val coordinatesArray = JsonArray()

                        // From stop
                        val fromCoord = JsonArray()
                        fromCoord.add(leg.fromLon)
                        fromCoord.add(leg.fromLat)
                        coordinatesArray.add(fromCoord)

                        // Intermediate stops
                        leg.intermediateStops.forEach { stop ->
                            val coord = JsonArray()
                            coord.add(stop.lon)
                            coord.add(stop.lat)
                            coordinatesArray.add(coord)
                        }

                        // To stop
                        val toCoord = JsonArray()
                        toCoord.add(leg.toLon)
                        toCoord.add(leg.toLat)
                        coordinatesArray.add(toCoord)

                        // Create GeoJSON for this leg's line
                        val lineGeoJson = JsonObject().apply {
                            addProperty("type", "Feature")
                            val geometry = JsonObject().apply {
                                addProperty("type", "LineString")
                                add("coordinates", coordinatesArray)
                            }
                            add("geometry", geometry)
                            val properties = JsonObject().apply {
                                addProperty("color", lineColor)
                                addProperty("isWalking", leg.isWalking)
                            }
                            add("properties", properties)
                        }

                        // Add source and layer for this leg
                        val sourceId = "journey-leg-$index"
                        val layerId = "journey-leg-layer-$index"

                        val lineSource = GeoJsonSource(sourceId, lineGeoJson.toString())
                        style.addSource(lineSource)

                        val lineLayer = LineLayer(layerId, sourceId).apply {
                            setProperties(
                                PropertyFactory.lineColor(lineColor),
                                PropertyFactory.lineWidth(if (leg.isWalking) 3f else 5f),
                                PropertyFactory.lineOpacity(1.0f),
                                PropertyFactory.lineCap(if (leg.isWalking) "round" else "round"),
                                PropertyFactory.lineJoin("round")
                            )
                            if (leg.isWalking) {
                                setProperties(
                                    PropertyFactory.lineDasharray(arrayOf(2f, 2f))
                                )
                            }
                        }
                        style.addLayer(lineLayer)

                        // Add endpoint circles for this leg
                        val endpointsGeoJson = JsonObject().apply {
                            addProperty("type", "FeatureCollection")
                            val features = JsonArray()

                            // Start point
                            val startFeature = JsonObject().apply {
                                addProperty("type", "Feature")
                                val geometry = JsonObject().apply {
                                    addProperty("type", "Point")
                                    val coordinates = JsonArray()
                                    coordinates.add(leg.fromLon)
                                    coordinates.add(leg.fromLat)
                                    add("coordinates", coordinates)
                                }
                                add("geometry", geometry)
                            }
                            features.add(startFeature)

                            // End point
                            val endFeature = JsonObject().apply {
                                addProperty("type", "Feature")
                                val geometry = JsonObject().apply {
                                    addProperty("type", "Point")
                                    val coordinates = JsonArray()
                                    coordinates.add(leg.toLon)
                                    coordinates.add(leg.toLat)
                                    add("coordinates", coordinates)
                                }
                                add("geometry", geometry)
                            }
                            features.add(endFeature)

                            add("features", features)
                        }

                        val endpointsSourceId = "journey-leg-endpoints-$index"
                        val endpointsLayerId = "journey-leg-endpoints-layer-$index"

                        val endpointsSource = GeoJsonSource(endpointsSourceId, endpointsGeoJson.toString())
                        style.addSource(endpointsSource)

                        val endpointsLayer = CircleLayer(endpointsLayerId, endpointsSourceId).apply {
                            setProperties(
                                PropertyFactory.circleRadius(if (leg.isWalking) 4f else 6f),
                                PropertyFactory.circleColor(lineColor),
                                PropertyFactory.circleStrokeWidth(2f),
                                PropertyFactory.circleStrokeColor("#FFFFFF"),
                                PropertyFactory.circleOpacity(1.0f),
                                PropertyFactory.circleStrokeOpacity(1.0f)
                            )
                        }
                        style.addLayer(endpointsLayer)
                    }

                    // Add stop markers (circles at each stop)
                    val stopsGeoJson = JsonObject().apply {
                        addProperty("type", "FeatureCollection")
                        val features = JsonArray()

                        // Collect all unique stops with their names
                        data class StopInfo(val lat: Double, val lon: Double, val name: String, val isMain: Boolean)
                        val stops = mutableListOf<StopInfo>()

                        journey.legs.forEachIndexed { legIndex, leg ->
                            // First stop of journey
                            if (legIndex == 0) {
                                stops.add(StopInfo(leg.fromLat, leg.fromLon, leg.fromStopName, true))
                            }

                            // Intermediate stops (not main stops, no labels)
                            leg.intermediateStops.forEach { stop ->
                                stops.add(StopInfo(stop.lat, stop.lon, stop.stopName, false))
                            }

                            // End stop of each leg (always show as main for transfers and final destination)
                            stops.add(StopInfo(leg.toLat, leg.toLon, leg.toStopName, true))
                        }

                        stops.forEach { stopInfo ->
                            val feature = JsonObject().apply {
                                addProperty("type", "Feature")
                                val geometry = JsonObject().apply {
                                    addProperty("type", "Point")
                                    val coordinates = JsonArray()
                                    coordinates.add(stopInfo.lon)
                                    coordinates.add(stopInfo.lat)
                                    add("coordinates", coordinates)
                                }
                                add("geometry", geometry)
                                val properties = JsonObject().apply {
                                    addProperty("isMain", stopInfo.isMain)
                                    addProperty("name", stopInfo.name)
                                }
                                add("properties", properties)
                            }
                            features.add(feature)
                        }

                        add("features", features)
                    }

                    // Add stops source and layers
                    val stopsSource = GeoJsonSource("journey-stops", stopsGeoJson.toString())
                    style.addSource(stopsSource)

                    var isZoomedIn = map.cameraPosition.zoom >= 14.0

                    map.addOnCameraMoveListener {
                        val currentZoom = map.cameraPosition.zoom
                        if (isZoomedIn && currentZoom < 13.0) {
                            isZoomedIn = false
                            style.getLayer("journey-intermediate-stops")?.setProperties(
                                PropertyFactory.visibility(org.maplibre.android.style.layers.Property.NONE)
                            )
                        } else if (!isZoomedIn && currentZoom >= 14.0) {
                            isZoomedIn = true
                            style.getLayer("journey-intermediate-stops")?.setProperties(
                                PropertyFactory.visibility(org.maplibre.android.style.layers.Property.VISIBLE)
                            )
                        }
                    }

                    // Intermediate stops layer (smaller circles)
                    val intermediateStopsLayer = CircleLayer("journey-intermediate-stops", "journey-stops").apply {
                        setProperties(
                            PropertyFactory.circleRadius(4f),
                            PropertyFactory.circleColor("#FFFFFF"),
                            PropertyFactory.circleStrokeWidth(2f),
                            PropertyFactory.circleStrokeColor("#6B7280"),
                            PropertyFactory.circleOpacity(1f),
                            PropertyFactory.circleStrokeOpacity(0.8f),
                            PropertyFactory.visibility(if (isZoomedIn) org.maplibre.android.style.layers.Property.VISIBLE else org.maplibre.android.style.layers.Property.NONE)
                        )
                        setFilter(org.maplibre.android.style.expressions.Expression.eq(
                            org.maplibre.android.style.expressions.Expression.get("isMain"),
                            org.maplibre.android.style.expressions.Expression.literal(false)
                        ))
                    }
                    style.addLayer(intermediateStopsLayer)

                    // Main stops layer (larger circles) - on top
                    val mainStopsLayer = CircleLayer("journey-main-stops", "journey-stops").apply {
                        setProperties(
                            PropertyFactory.circleRadius(10f),
                            PropertyFactory.circleColor("#FFFFFF"),
                            PropertyFactory.circleStrokeWidth(4f),
                            PropertyFactory.circleStrokeColor("#000000"),
                            PropertyFactory.circleOpacity(
                                org.maplibre.android.style.expressions.Expression.interpolate(
                                    org.maplibre.android.style.expressions.Expression.linear(),
                                    org.maplibre.android.style.expressions.Expression.zoom(),
                                    org.maplibre.android.style.expressions.Expression.stop(10.0f, 0f),
                                    org.maplibre.android.style.expressions.Expression.stop(11.0f, 1f)
                                )
                            ),
                            PropertyFactory.circleStrokeOpacity(
                                org.maplibre.android.style.expressions.Expression.interpolate(
                                    org.maplibre.android.style.expressions.Expression.linear(),
                                    org.maplibre.android.style.expressions.Expression.zoom(),
                                    org.maplibre.android.style.expressions.Expression.stop(10.0f, 0f),
                                    org.maplibre.android.style.expressions.Expression.stop(11.0f, 1f)
                                )
                            )
                        )
                        setFilter(org.maplibre.android.style.expressions.Expression.eq(
                            org.maplibre.android.style.expressions.Expression.get("isMain"),
                            org.maplibre.android.style.expressions.Expression.literal(true)
                        ))
                    }
                    style.addLayer(mainStopsLayer)

                    // Labels for main stops
                    val labelsLayer = SymbolLayer("journey-stop-labels", "journey-stops").apply {
                        setProperties(
                            PropertyFactory.textField(
                                org.maplibre.android.style.expressions.Expression.get("name")
                            ),
                            PropertyFactory.textSize(12f),
                            PropertyFactory.textColor("#000000"),
                            PropertyFactory.textHaloColor("#FFFFFF"),
                            PropertyFactory.textHaloWidth(2f),
                            PropertyFactory.textOffset(arrayOf(0f, 1.5f)),
                            PropertyFactory.textAnchor("top"),
                            PropertyFactory.textMaxWidth(10f),
                            PropertyFactory.textFont(arrayOf("Open Sans Bold", "Arial Unicode MS Bold")),
                            PropertyFactory.textAllowOverlap(false),
                            PropertyFactory.textIgnorePlacement(false),
                            PropertyFactory.textOpacity(
                                org.maplibre.android.style.expressions.Expression.interpolate(
                                    org.maplibre.android.style.expressions.Expression.linear(),
                                    org.maplibre.android.style.expressions.Expression.zoom(),
                                    org.maplibre.android.style.expressions.Expression.stop(11.0f, 0f),
                                    org.maplibre.android.style.expressions.Expression.stop(12.0f, 1f)
                                )
                            )
                        )
                        setFilter(org.maplibre.android.style.expressions.Expression.eq(
                            org.maplibre.android.style.expressions.Expression.get("isMain"),
                            org.maplibre.android.style.expressions.Expression.literal(true)
                        ))
                    }
                    style.addLayer(labelsLayer)

                    // Add user location blue circle if available
                    if (userLocation != null) {
                        val userLocationGeoJson = JsonObject().apply {
                            addProperty("type", "Feature")
                            val geometry = JsonObject().apply {
                                addProperty("type", "Point")
                                val coordinates = JsonArray()
                                coordinates.add(userLocation.longitude)
                                coordinates.add(userLocation.latitude)
                                add("coordinates", coordinates)
                            }
                            add("geometry", geometry)
                        }

                        val userLocationSource = GeoJsonSource("user-location-source", userLocationGeoJson.toString())
                        style.addSource(userLocationSource)

                        val userLocationLayer = CircleLayer("user-location-layer", "user-location-source").apply {
                            setProperties(
                                PropertyFactory.circleRadius(10f),
                                PropertyFactory.circleColor("#3B82F6"),
                                PropertyFactory.circleStrokeWidth(3f),
                                PropertyFactory.circleStrokeColor("#FFFFFF"),
                                PropertyFactory.circleOpacity(1.0f),
                                PropertyFactory.circleStrokeOpacity(1.0f)
                            )
                        }
                        style.addLayer(userLocationLayer)
                    }
                }
            }
        }
    }

    // Update user location in real-time when it changes
    LaunchedEffect(userLocation) {
        if (userLocation != null) {
            mapView.getMapAsync { map ->
                map.getStyle { style ->
                    // Create updated GeoJSON for user location
                    val userLocationGeoJson = JsonObject().apply {
                        addProperty("type", "Feature")
                        val geometry = JsonObject().apply {
                            addProperty("type", "Point")
                            val coordinates = JsonArray()
                            coordinates.add(userLocation.longitude)
                            coordinates.add(userLocation.latitude)
                            add("coordinates", coordinates)
                        }
                        add("geometry", geometry)
                    }

                    // Update existing source if it exists, otherwise it will be created on map load
                    val existingSource = style.getSource("user-location-source") as? GeoJsonSource
                    existingSource?.setGeoJson(userLocationGeoJson.toString())
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

        // Back button
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .padding(top=24.dp)
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.Black)
                .clickable { onBack() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Retour",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}