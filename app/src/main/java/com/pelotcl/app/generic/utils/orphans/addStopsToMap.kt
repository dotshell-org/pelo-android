package com.pelotcl.app.generic.utils.orphans

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import com.google.gson.JsonParser
import com.pelotcl.app.generic.data.models.geojson.StopFeature
import com.pelotcl.app.generic.data.models.stops.StationInfo
import com.pelotcl.app.generic.ui.screens.plan.PRIORITY_STOPS_MIN_ZOOM
import com.pelotcl.app.generic.ui.screens.plan.SECONDARY_STOPS_MIN_ZOOM
import com.pelotcl.app.generic.ui.screens.plan.TRAM_STOPS_MIN_ZOOM
import com.pelotcl.app.generic.ui.viewmodel.TransportViewModel
import com.pelotcl.app.generic.utils.BusIconHelper
import com.pelotcl.app.specific.utils.orphans.getModeIconForLine
import com.pelotcl.app.specific.utils.orphans.isMetroTramOrFunicular
import com.pelotcl.app.specific.utils.orphans.normalizeLineNameForUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach

suspend fun addStopsToMap(
    map: MapLibreMap,
    stops: List<StopFeature>,
    context: Context,
    onStationClick: (StationInfo) -> Unit = {},
    onLineClick: (String) -> Unit = {},
    scope: CoroutineScope,
    viewModel: TransportViewModel? = null
) {
    // Holder for the current map click listener to allow removal before adding a new one
    var currentMapClickListener: MapLibreMap.OnMapClickListener? = null

    val (stopsGeoJson, requiredIcons, usedSlots) = // Full cache hit — GeoJSON, icons, AND usedSlots are all cached
        withContext(Dispatchers.Default) {
            val requiredIcons = mutableSetOf<String>()
            val usedSlots = mutableSetOf<Int>()

            // Use centralized BusIconHelper cache for resource ID lookups
            fun checkIconAvailable(name: String): Boolean {
                return BusIconHelper.getResourceIdForDrawableName(context, name) != 0
            }

            // Add mode icons to required icons
            listOf("mode_bus", "mode_chrono", "mode_jd").forEach { modeIcon ->
                if (checkIconAvailable(modeIcon)) {
                    requiredIcons.add(modeIcon)
                }
            }

            stops.forEach { stop ->
                val lineNames = BusIconHelper.getAllLinesForStop(stop)
                if (lineNames.isEmpty()) return@forEach

                // Separate lignes fortes from bus lines
                val lignesFortes = lineNames.filter { isMetroTramOrFunicular(it) }
                val busLines = lineNames.filter { !isMetroTramOrFunicular(it) }

                // Add line icons for lignes fortes only
                lignesFortes.forEach { lineName ->
                    val drawableName = BusIconHelper.getDrawableNameForLineName(lineName)
                    if (checkIconAvailable(drawableName)) {
                        requiredIcons.add(drawableName)
                    }
                }

                // Calculate usedSlots
                val uniqueModes = busLines.mapNotNull { getModeIconForLine(it) }.distinct()
                    .filter { checkIconAvailable(it) }
                val validLignesFortes = lignesFortes.count { lineName ->
                    val drawableName = BusIconHelper.getDrawableNameForLineName(lineName)
                    checkIconAvailable(drawableName)
                }
                val n = validLignesFortes + uniqueModes.size
                if (n > 0) {
                    var slot = -(n - 1)
                    repeat(n) {
                        usedSlots.add(slot)
                        slot += 2
                    }
                }
            }

            // Pass all stops to merge function, using StringBuilder for fast GeoJSON creation
            val stopsGeoJson = createStopsGeoJsonFromStops(stops, requiredIcons)

            Triple(stopsGeoJson, requiredIcons, usedSlots)
        }

    map.getStyle { style ->
        val sourceId = "transport-stops"
        val priorityLayerPrefix = "transport-stops-layer-priority"
        val tramLayerPrefix = "transport-stops-layer-tram"
        val secondaryLayerPrefix = "transport-stops-layer-secondary"


        // Only remove layers for slots that were actually created (instead of all -25..25)
        currentMapSlots.forEach { idx ->
            style.getLayer("$priorityLayerPrefix-$idx")?.let { style.removeLayer(it) }
            style.getLayer("$tramLayerPrefix-$idx")?.let { style.removeLayer(it) }
            style.getLayer("$secondaryLayerPrefix-$idx")?.let { style.removeLayer(it) }
        }
        style.getLayer("clusters")?.let { style.removeLayer(it) }
        style.getLayer("cluster-count")?.let { style.removeLayer(it) }

        style.getSource(sourceId)?.let { style.removeSource(it) }

        // OPTIMIZATION: Use cached bitmaps if available, otherwise load and cache
        // Uses direct LruCache accessors to avoid snapshot() full-copy allocation
        scope.launch(Dispatchers.IO) {
            val allCached = viewModel?.hasAllIcons(requiredIcons.toList()) == true

            val bitmaps: Map<String, Bitmap> = if (allCached) {
                // All icons are cached - retrieve them directly without snapshot copy
                requiredIcons.mapNotNull { iconName ->
                    viewModel.getIconBitmap(iconName)?.let { iconName to it }
                }.toMap()
            } else {
                // Load missing bitmaps and cache them individually
                requiredIcons.mapNotNull { iconName ->
                    // Check cache first for this specific icon
                    viewModel?.getIconBitmap(iconName)?.let { return@mapNotNull iconName to it }

                    try {
                        val resourceId =
                            BusIconHelper.getResourceIdForDrawableName(context, iconName)
                        if (resourceId != 0) {
                            val drawable = ContextCompat.getDrawable(context, resourceId)
                            drawable?.let { d ->
                                val bitmap = if (d is BitmapDrawable) {
                                    d.bitmap
                                } else {
                                    val bitmap = createBitmap(
                                        d.intrinsicWidth.coerceAtLeast(1),
                                        d.intrinsicHeight.coerceAtLeast(1),
                                        Bitmap.Config.ARGB_8888
                                    )
                                    val canvas = android.graphics.Canvas(bitmap)
                                    d.setBounds(0, 0, canvas.width, canvas.height)
                                    d.draw(canvas)
                                    bitmap
                                }
                                // Cache individually as loaded
                                viewModel?.cacheIconBitmap(iconName, bitmap)
                                iconName to bitmap
                            }
                        } else null
                    } catch (_: Exception) {
                        null
                    }
                }.toMap()
            }

            withContext(Dispatchers.Main) {
                // Batch add images if possible, otherwise simple loop
                bitmaps.forEach { (name, bitmap) ->
                    if (style.getImage(name) == null) { // Avoid re-adding if existing
                        style.addImage(name, bitmap)
                    }
                }

                // Add source and layers only AFTER images are added
                val stopsSource = GeoJsonSource(
                    sourceId,
                    stopsGeoJson,
                    GeoJsonOptions()
                        .withCluster(true)
                        .withClusterRadius(50)
                        .withClusterMaxZoom(11) // Below PRIORITY_STOPS_MIN_ZOOM (12.5) to ensure stops are unclustered when they become visible
                )
                style.addSource(stopsSource)

                // 1. Cluster Circles (Aggregated stops)
                val clusterLayer = CircleLayer("clusters", sourceId).apply {
                    setProperties(
                        PropertyFactory.circleColor(
                            Expression.step(
                                Expression.get("point_count"),
                                Expression.literal("#E60000"), // Default TCL Red
                                Expression.stop(10, "#E60000"),
                                Expression.stop(50, "#B71C1C")
                            )
                        ),
                        PropertyFactory.circleRadius(18f)
                    )
                    setFilter(Expression.has("point_count"))
                }
                style.addLayer(clusterLayer)

                val countLayer = SymbolLayer("cluster-count", sourceId).apply {
                    setProperties(
                        PropertyFactory.textField(Expression.toString(Expression.get("point_count_abbreviated"))),
                        PropertyFactory.textSize(12f),
                        PropertyFactory.textColor(android.graphics.Color.WHITE),
                        PropertyFactory.textIgnorePlacement(true),
                        PropertyFactory.textAllowOverlap(true)
                    )
                    setFilter(Expression.has("point_count"))
                }
                style.addLayer(countLayer)

                // 2. Individual Stops Icons (Unclustered)
                // OPTIMIZED: Create layers only for slots that are actually used
                val iconSizesPriority = 0.7f
                val iconSizesSecondary = 0.62f

                // Save used slots for the other map filter helpers.
                currentMapSlots = usedSlots.toSet()

                usedSlots.sorted().forEach { idx ->
                    val yOffset = idx * 13f

                    // Priority Stops (Metro, Funiculaire - stop_priority = 2)
                    val priorityLayer = SymbolLayer("$priorityLayerPrefix-$idx", sourceId).apply {
                        setProperties(
                            PropertyFactory.iconImage(Expression.get("icon")),
                            PropertyFactory.iconSize(iconSizesPriority),
                            PropertyFactory.iconAllowOverlap(true),
                            PropertyFactory.iconIgnorePlacement(true),
                            PropertyFactory.iconAnchor("center"),
                            PropertyFactory.iconOffset(arrayOf(0f, yOffset))
                        )
                        setFilter(
                            Expression.all(
                                Expression.not(Expression.has("point_count")),
                                Expression.eq(Expression.get("stop_priority"), 2),
                                Expression.eq(Expression.get("slot"), idx)
                            )
                        )
                        minZoom = PRIORITY_STOPS_MIN_ZOOM
                    }
                    style.addLayerBelow(priorityLayer, "clusters")

                    // Tram Stops (stop_priority = 1)
                    val tramLayer = SymbolLayer("$tramLayerPrefix-$idx", sourceId).apply {
                        setProperties(
                            PropertyFactory.iconImage(Expression.get("icon")),
                            PropertyFactory.iconSize(iconSizesPriority),
                            PropertyFactory.iconAllowOverlap(true),
                            PropertyFactory.iconIgnorePlacement(true),
                            PropertyFactory.iconAnchor("center"),
                            PropertyFactory.iconOffset(arrayOf(0f, yOffset))
                        )
                        setFilter(
                            Expression.all(
                                Expression.not(Expression.has("point_count")),
                                Expression.eq(Expression.get("stop_priority"), 1),
                                Expression.eq(Expression.get("slot"), idx)
                            )
                        )
                        minZoom = TRAM_STOPS_MIN_ZOOM
                    }
                    style.addLayerBelow(tramLayer, "clusters")

                    // Secondary Stops (Bus - stop_priority = 0)
                    val secondaryLayer = SymbolLayer("$secondaryLayerPrefix-$idx", sourceId).apply {
                        setProperties(
                            PropertyFactory.iconImage(Expression.get("icon")),
                            PropertyFactory.iconSize(iconSizesSecondary),
                            PropertyFactory.iconAllowOverlap(true),
                            PropertyFactory.iconIgnorePlacement(true),
                            PropertyFactory.iconAnchor("center"),
                            PropertyFactory.iconOffset(arrayOf(0f, yOffset))
                        )
                        setFilter(
                            Expression.all(
                                Expression.not(Expression.has("point_count")),
                                Expression.eq(Expression.get("stop_priority"), 0),
                                Expression.eq(Expression.get("slot"), idx)
                            )
                        )
                        minZoom = SECONDARY_STOPS_MIN_ZOOM
                    }
                    style.addLayerBelow(secondaryLayer, "clusters")
                }

                // Remove previous listener before adding a new one to prevent duplicates
                currentMapClickListener?.let { map.removeOnMapClickListener(it) }

                // Interaction listener for stops and lines
                val clickListener = MapLibreMap.OnMapClickListener { point ->
                    val screenPoint = map.projection.toScreenLocation(point)

                    // Check clusters first
                    val clusterFeatures = map.queryRenderedFeatures(screenPoint, "clusters")
                    if (clusterFeatures.isNotEmpty()) {
                        val cameraUpdate =
                            CameraUpdateFactory.newLatLngZoom(point, map.cameraPosition.zoom + 2)
                        map.animateCamera(cameraUpdate)
                        return@OnMapClickListener true
                    }

                    // In global LIVE mode, clicking a vehicle opens its line details.
                    val globalVehicleFeatures =
                        map.queryRenderedFeatures(screenPoint, "global-vehicle-positions-layer")
                    if (globalVehicleFeatures.isNotEmpty()) {
                        val feature = globalVehicleFeatures.first()
                        val props = feature.properties()
                        if (props != null) {
                            try {
                                val lineName =
                                    if (props.has("lineName")) props.get("lineName").asString else ""
                                if (lineName.isNotEmpty()) {
                                    onLineClick(normalizeLineNameForUi(lineName))
                                    return@OnMapClickListener true
                                }
                            } catch (_: Exception) {
                                // Ignore parse errors
                            }
                        }
                    }

                    // Check individual stops first (higher priority than lines)
                    val interactableLayers = usedSlots.flatMap { idx ->
                        listOf(
                            "$priorityLayerPrefix-$idx",
                            "$tramLayerPrefix-$idx",
                            "$secondaryLayerPrefix-$idx"
                        )
                    }.toTypedArray()

                    if (interactableLayers.isNotEmpty()) {
                        val stopFeatures = map.queryRenderedFeatures(screenPoint, *interactableLayers)
                        if (stopFeatures.isNotEmpty()) {
                            val feature = stopFeatures.first()
                            val props = feature.properties()
                            if (props != null) {
                                try {
                                    val stopName =
                                        if (props.has("nom")) props.get("nom").asString else ""
                                    val stopId =
                                        if (props.has("stop_id")) props.get("stop_id").asInt else null
                                    val lignesJson =
                                        if (props.has("lignes")) props.get("lignes").asString else "[]"

                                    val lignes = try {
                                        val jsonArray = JsonParser.parseString(lignesJson).asJsonArray
                                        jsonArray.map { it.asString }
                                    } catch (_: Exception) {
                                        emptyList()
                                    }

                                    if (stopName.isNotBlank()) {
                                        val stationInfo = StationInfo(
                                            nom = stopName,
                                            lignes = lignes,
                                            stopIds = stopId?.let { listOf(it) } ?: emptyList()
                                        )
                                        onStationClick(stationInfo)
                                        return@OnMapClickListener true
                                    }
                                } catch (_: Exception) {
                                    // Ignore parse errors
                                }
                            }
                        }
                    }

                    // Check for line clicks (only if no stop was clicked)
                    // Use a larger hitbox for easier line selection (30px padding around touch point)
                    val hitboxPadding = 30f
                    val lineHitbox = RectF(
                        screenPoint.x - hitboxPadding,
                        screenPoint.y - hitboxPadding,
                        screenPoint.x + hitboxPadding,
                        screenPoint.y + hitboxPadding
                    )

                    // Query all-lines-layer and individual line layers
                    // Get all layer IDs that could contain line features
                    val currentStyle = map.style
                    val allLineLayerIds = mutableListOf("all-lines-layer")
                    currentStyle?.layers?.forEach { layer ->
                        if (layer.id.startsWith("layer-") && !layer.id.startsWith("layer-stops")) {
                            allLineLayerIds.add(layer.id)
                        }
                    }

                    val lineFeatures =
                        map.queryRenderedFeatures(lineHitbox, *allLineLayerIds.toTypedArray())

                    if (lineFeatures.isNotEmpty()) {
                        val feature = lineFeatures.first()
                        val props = feature.properties()
                        if (props != null) {
                            try {
                                val lineName =
                                    if (props.has("ligne")) props.get("ligne").asString else ""
                                if (lineName.isNotEmpty()) {
                                    onLineClick(normalizeLineNameForUi(lineName))
                                    return@OnMapClickListener true
                                }
                            } catch (_: Exception) {
                                // Ignore parse errors
                            }
                        }
                    }
                    false
                }

                currentMapClickListener = clickListener
                map.addOnMapClickListener(clickListener)
            }
        }
    }
}
