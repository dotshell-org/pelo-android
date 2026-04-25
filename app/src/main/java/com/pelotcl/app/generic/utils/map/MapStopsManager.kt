package com.pelotcl.app.generic.utils.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.pelotcl.app.generic.data.models.geojson.Feature
import com.pelotcl.app.generic.data.models.geojson.StopFeature
import com.pelotcl.app.generic.data.models.stops.StationInfo
import com.pelotcl.app.generic.ui.screens.plan.PRIORITY_STOPS_MIN_ZOOM
import com.pelotcl.app.generic.ui.screens.plan.SECONDARY_STOPS_MIN_ZOOM
import com.pelotcl.app.generic.ui.screens.plan.SELECTED_STOP_MIN_ZOOM
import com.pelotcl.app.generic.ui.screens.plan.TRAM_STOPS_MIN_ZOOM
import com.pelotcl.app.generic.ui.viewmodel.TransportViewModel
import com.pelotcl.app.generic.utils.geo.StopsGeoJsonManager
import com.pelotcl.app.generic.utils.graphics.BusIconHelper
import com.pelotcl.app.specific.utils.LineColorHelper
import com.pelotcl.app.specific.utils.LineClassificationUtils
import com.pelotcl.app.specific.utils.LineNamingUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource

object MapStopsManager {

    private var currentMapSlots: Set<Int> = emptySet()

    suspend fun addStopsToMap(
        map: MapLibreMap,
        stops: List<StopFeature>,
        context: Context,
        onStationClick: (StationInfo) -> Unit = {},
        onLineClick: (String) -> Unit = {},
        scope: CoroutineScope,
        viewModel: TransportViewModel? = null
    ) {
        var currentMapClickListener: MapLibreMap.OnMapClickListener? = null

        val (stopsGeoJson, requiredIcons, usedSlots) =
            withContext(Dispatchers.Default) {
                val requiredIcons = mutableSetOf<String>()
                val usedSlots = mutableSetOf<Int>()

                fun checkIconAvailable(name: String): Boolean {
                    return BusIconHelper.getResourceIdForDrawableName(context, name) != 0
                }

                listOf("mode_bus", "mode_chrono", "mode_jd").forEach { modeIcon ->
                    if (checkIconAvailable(modeIcon)) {
                        requiredIcons.add(modeIcon)
                    }
                }

                stops.forEach { stop ->
                    val lineNames = BusIconHelper.getAllLinesForStop(stop)
                    if (lineNames.isEmpty()) return@forEach

                    val lignesFortes =
                        lineNames.filter { LineClassificationUtils.isMetroTramOrFunicular(it) }
                    val busLines =
                        lineNames.filter { !LineClassificationUtils.isMetroTramOrFunicular(it) }

                    lignesFortes.forEach { lineName ->
                        val drawableName = BusIconHelper.getDrawableNameForLineName(lineName)
                        if (checkIconAvailable(drawableName)) {
                            requiredIcons.add(drawableName)
                        }
                    }

                    val uniqueModes = busLines
                        .mapNotNull { LineClassificationUtils.getModeIconForLine(it) }
                        .distinct()
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

                val stopsGeoJson =
                    StopsGeoJsonManager.createStopsGeoJsonFromStops(stops, requiredIcons)

                Triple(stopsGeoJson, requiredIcons, usedSlots)
            }

        map.getStyle { style ->
            val sourceId = "transport-stops"
            val priorityLayerPrefix = "transport-stops-layer-priority"
            val tramLayerPrefix = "transport-stops-layer-tram"
            val secondaryLayerPrefix = "transport-stops-layer-secondary"

            currentMapSlots.forEach { idx ->
                style.getLayer("$priorityLayerPrefix-$idx")?.let { style.removeLayer(it) }
                style.getLayer("$tramLayerPrefix-$idx")?.let { style.removeLayer(it) }
                style.getLayer("$secondaryLayerPrefix-$idx")?.let { style.removeLayer(it) }
            }
            style.getLayer("clusters")?.let { style.removeLayer(it) }
            style.getLayer("cluster-count")?.let { style.removeLayer(it) }
            style.getSource(sourceId)?.let { style.removeSource(it) }

            scope.launch(Dispatchers.IO) {
                val allCached = viewModel?.hasAllIcons(requiredIcons.toList()) == true

                val bitmaps: Map<String, Bitmap> = if (allCached) {
                    requiredIcons.mapNotNull { iconName ->
                        viewModel.getIconBitmap(iconName)?.let { iconName to it }
                    }.toMap()
                } else {
                    requiredIcons.mapNotNull { iconName ->
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
                                        val canvas = Canvas(bitmap)
                                        d.setBounds(0, 0, canvas.width, canvas.height)
                                        d.draw(canvas)
                                        bitmap
                                    }
                                    viewModel?.cacheIconBitmap(iconName, bitmap)
                                    iconName to bitmap
                                }
                            } else {
                                null
                            }
                        } catch (_: Exception) {
                            null
                        }
                    }.toMap()
                }

                withContext(Dispatchers.Main) {
                    bitmaps.forEach { (name, bitmap) ->
                        if (style.getImage(name) == null) {
                            style.addImage(name, bitmap)
                        }
                    }

                    val stopsSource = GeoJsonSource(
                        sourceId,
                        stopsGeoJson,
                        GeoJsonOptions()
                            .withCluster(true)
                            .withClusterRadius(50)
                            .withClusterMaxZoom(11)
                    )
                    style.addSource(stopsSource)

                    val clusterLayer = CircleLayer("clusters", sourceId).apply {
                        setProperties(
                            PropertyFactory.circleColor(
                                Expression.step(
                                    Expression.get("point_count"),
                                    Expression.literal("#E60000"),
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
                            PropertyFactory.textField(
                                Expression.toString(Expression.get("point_count_abbreviated"))
                            ),
                            PropertyFactory.textSize(12f),
                            PropertyFactory.textColor(Color.WHITE),
                            PropertyFactory.textIgnorePlacement(true),
                            PropertyFactory.textAllowOverlap(true)
                        )
                        setFilter(Expression.has("point_count"))
                    }
                    style.addLayer(countLayer)

                    val iconSizesPriority = 0.7f
                    val iconSizesSecondary = 0.62f

                    currentMapSlots = usedSlots.toSet()

                    usedSlots.sorted().forEach { idx ->
                        val yOffset = idx * 13f

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

                    currentMapClickListener?.let { map.removeOnMapClickListener(it) }

                    val clickListener = MapLibreMap.OnMapClickListener { point ->
                        val screenPoint = map.projection.toScreenLocation(point)

                        val clusterFeatures = map.queryRenderedFeatures(screenPoint, "clusters")
                        if (clusterFeatures.isNotEmpty()) {
                            val cameraUpdate =
                                CameraUpdateFactory.newLatLngZoom(
                                    point,
                                    map.cameraPosition.zoom + 2
                                )
                            map.animateCamera(cameraUpdate)
                            return@OnMapClickListener true
                        }

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
                                        onLineClick(LineNamingUtils.normalizeLineNameForUi(lineName))
                                        return@OnMapClickListener true
                                    }
                                } catch (_: Exception) {
                                }
                            }
                        }

                        val interactableLayers = usedSlots.flatMap { idx ->
                            listOf(
                                "$priorityLayerPrefix-$idx",
                                "$tramLayerPrefix-$idx",
                                "$secondaryLayerPrefix-$idx"
                            )
                        }.toTypedArray()

                        if (interactableLayers.isNotEmpty()) {
                            val stopFeatures =
                                map.queryRenderedFeatures(screenPoint, *interactableLayers)
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
                                    }
                                }
                            }
                        }

                        val hitboxPadding = 30f
                        val lineHitbox = RectF(
                            screenPoint.x - hitboxPadding,
                            screenPoint.y - hitboxPadding,
                            screenPoint.x + hitboxPadding,
                            screenPoint.y + hitboxPadding
                        )

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
                                        onLineClick(LineNamingUtils.normalizeLineNameForUi(lineName))
                                        return@OnMapClickListener true
                                    }
                                } catch (_: Exception) {
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

    fun filterMapStops(
        style: Style,
        selectedLineName: String
    ) {
        val priorityLayerPrefix = "transport-stops-layer-priority"
        val tramLayerPrefix = "transport-stops-layer-tram"
        val secondaryLayerPrefix = "transport-stops-layer-secondary"
        val linePropertyName = "has_line_${LineNamingUtils.canonicalLineName(selectedLineName)}"

        currentMapSlots.forEach { idx ->
            (style.getLayer("$priorityLayerPrefix-$idx") as? SymbolLayer)?.setFilter(
                Expression.all(
                    Expression.eq(Expression.get("stop_priority"), 2),
                    Expression.eq(Expression.get("slot"), idx),
                    Expression.eq(Expression.get(linePropertyName), true)
                )
            )

            (style.getLayer("$tramLayerPrefix-$idx") as? SymbolLayer)?.let { layer ->
                layer.setFilter(
                    Expression.all(
                        Expression.eq(Expression.get("stop_priority"), 1),
                        Expression.eq(Expression.get("slot"), idx),
                        Expression.eq(Expression.get(linePropertyName), true)
                    )
                )
                layer.minZoom = PRIORITY_STOPS_MIN_ZOOM
            }

            (style.getLayer("$secondaryLayerPrefix-$idx") as? SymbolLayer)?.let { layer ->
                layer.setFilter(
                    Expression.all(
                        Expression.eq(Expression.get("stop_priority"), 0),
                        Expression.eq(Expression.get("slot"), idx),
                        Expression.eq(Expression.get(linePropertyName), true)
                    )
                )
                layer.minZoom = PRIORITY_STOPS_MIN_ZOOM
            }
        }
    }

    fun filterMapStopsWithSelectedStop(
        map: MapLibreMap,
        selectedLineName: String,
        selectedStopName: String?,
        allStops: List<StopFeature>,
        allLines: List<Feature>,
        viewModel: TransportViewModel? = null
    ) {
        map.getStyle { style ->
            if (selectedStopName.isNullOrBlank()) {
                filterMapStops(style, selectedLineName)
                style.getLayer("line-stops-circles")?.let { style.removeLayer(it) }
                style.getSource("line-stops-circles-source")?.let { style.removeSource(it) }
                return@getStyle
            }

            fun normalizeStopName(name: String): String {
                return name.filter { it.isLetter() }.lowercase()
            }

            val normalizedSelectedStop = normalizeStopName(selectedStopName)
            val priorityLayerPrefix = "transport-stops-layer-priority"
            val tramLayerPrefix = "transport-stops-layer-tram"
            val secondaryLayerPrefix = "transport-stops-layer-secondary"
            val linePropertyName = "has_line_${LineNamingUtils.canonicalLineName(selectedLineName)}"

            currentMapSlots.forEach { idx ->
                (style.getLayer("$priorityLayerPrefix-$idx") as? SymbolLayer)?.let { layer ->
                    layer.setFilter(
                        Expression.all(
                            Expression.eq(Expression.get("stop_priority"), 2),
                            Expression.eq(Expression.get("slot"), idx),
                            Expression.eq(Expression.get(linePropertyName), true),
                            Expression.eq(Expression.get("normalized_nom"), normalizedSelectedStop)
                        )
                    )
                    layer.minZoom = SELECTED_STOP_MIN_ZOOM
                }

                (style.getLayer("$tramLayerPrefix-$idx") as? SymbolLayer)?.let { layer ->
                    layer.setFilter(
                        Expression.all(
                            Expression.eq(Expression.get("stop_priority"), 1),
                            Expression.eq(Expression.get("slot"), idx),
                            Expression.eq(Expression.get(linePropertyName), true),
                            Expression.eq(Expression.get("normalized_nom"), normalizedSelectedStop)
                        )
                    )
                    layer.minZoom = SELECTED_STOP_MIN_ZOOM
                }

                (style.getLayer("$secondaryLayerPrefix-$idx") as? SymbolLayer)?.let { layer ->
                    layer.setFilter(
                        Expression.all(
                            Expression.eq(Expression.get("stop_priority"), 0),
                            Expression.eq(Expression.get("slot"), idx),
                            Expression.eq(Expression.get(linePropertyName), true),
                            Expression.eq(Expression.get("normalized_nom"), normalizedSelectedStop)
                        )
                    )
                    layer.minZoom = SELECTED_STOP_MIN_ZOOM
                }
            }

            addCircleLayerForLineStops(
                style,
                selectedLineName,
                selectedStopName,
                allStops,
                allLines,
                viewModel
            )
        }
    }

    fun addCircleLayerForLineStops(
        style: Style,
        selectedLineName: String,
        selectedStopName: String,
        allStops: List<StopFeature>,
        allLines: List<Feature>,
        viewModel: TransportViewModel? = null
    ) {
        fun normalizeStopName(name: String): String {
            return name.filter { it.isLetter() }.lowercase()
        }

        val normalizedSelectedStop = normalizeStopName(selectedStopName)

        val lineColor = allLines
            .find { LineNamingUtils.areEquivalentLineNames(it.properties.lineName, selectedLineName) }
            ?.let { LineColorHelper.getColorForLine(it) }
            ?: "#EF4444"

        val lineStops = if (viewModel != null && viewModel.isStopsByLineIndexReady()) {
            viewModel.getStopsFeaturesForLine(selectedLineName)
                .filter { stop -> normalizeStopName(stop.properties.nom) != normalizedSelectedStop }
        } else {
            allStops.filter { stop ->
                val lines = BusIconHelper.getAllLinesForStop(stop)
                val hasLine = lines.any {
                    LineNamingUtils.areEquivalentLineNames(it, selectedLineName)
                }
                val isNotSelected = normalizeStopName(stop.properties.nom) != normalizedSelectedStop
                hasLine && isNotSelected
            }
        }

        val circlesGeoJson = JsonObject().apply {
            addProperty("type", "FeatureCollection")
            val features = JsonArray()

            lineStops.forEach { stop ->
                val pointFeature = JsonObject().apply {
                    addProperty("type", "Feature")

                    val pointGeometry = JsonObject().apply {
                        addProperty("type", "Point")
                        val coordinatesArray = JsonArray()
                        val coordinates = stop.geometry.coordinates
                        if (coordinates.size < 2) return@forEach
                        coordinatesArray.add(coordinates[0])
                        coordinatesArray.add(coordinates[1])
                        add("coordinates", coordinatesArray)
                    }
                    add("geometry", pointGeometry)

                    val properties = JsonObject().apply {
                        addProperty("nom", stop.properties.nom)
                        addProperty("desserte", stop.properties.desserte)
                    }
                    add("properties", properties)
                }
                features.add(pointFeature)
            }

            add("features", features)
        }

        val existingSource = style.getSource("line-stops-circles-source") as? GeoJsonSource
        if (existingSource != null) {
            existingSource.setGeoJson(circlesGeoJson.toString())
            (style.getLayer("line-stops-circles") as? CircleLayer)?.setProperties(
                PropertyFactory.circleStrokeColor(lineColor)
            )
        } else {
            val circlesSource = GeoJsonSource("line-stops-circles-source", circlesGeoJson.toString())
            style.addSource(circlesSource)

            val circlesLayer = CircleLayer("line-stops-circles", "line-stops-circles-source").apply {
                setProperties(
                    PropertyFactory.circleRadius(6f),
                    PropertyFactory.circleColor("#FFFFFF"),
                    PropertyFactory.circleStrokeWidth(4.5f),
                    PropertyFactory.circleStrokeColor(lineColor),
                    PropertyFactory.circleOpacity(1.0f),
                    PropertyFactory.circleStrokeOpacity(1.0f)
                )
                minZoom = SELECTED_STOP_MIN_ZOOM
            }
            style.addLayer(circlesLayer)
        }
    }

    fun showAllMapStops(style: Style) {
        val priorityLayerPrefix = "transport-stops-layer-priority"
        val tramLayerPrefix = "transport-stops-layer-tram"
        val secondaryLayerPrefix = "transport-stops-layer-secondary"

        currentMapSlots.forEach { idx ->
            (style.getLayer("$priorityLayerPrefix-$idx") as? SymbolLayer)?.let { layer ->
                layer.setFilter(
                    Expression.all(
                        Expression.eq(Expression.get("stop_priority"), 2),
                        Expression.eq(Expression.get("slot"), idx)
                    )
                )
                layer.minZoom = PRIORITY_STOPS_MIN_ZOOM
            }

            (style.getLayer("$tramLayerPrefix-$idx") as? SymbolLayer)?.let { layer ->
                layer.setFilter(
                    Expression.all(
                        Expression.eq(Expression.get("stop_priority"), 1),
                        Expression.eq(Expression.get("slot"), idx)
                    )
                )
                layer.minZoom = TRAM_STOPS_MIN_ZOOM
            }

            (style.getLayer("$secondaryLayerPrefix-$idx") as? SymbolLayer)?.let { layer ->
                layer.setFilter(
                    Expression.all(
                        Expression.eq(Expression.get("stop_priority"), 0),
                        Expression.eq(Expression.get("slot"), idx)
                    )
                )
                layer.minZoom = SECONDARY_STOPS_MIN_ZOOM
            }
        }
    }

    fun zoomToStop(
        map: MapLibreMap,
        stopName: String,
        allStops: List<StopFeature>
    ) {
        fun normalizeStopName(name: String): String {
            return name.filter { it.isLetter() }.lowercase()
        }

        val normalizedStopName = normalizeStopName(stopName)

        var stop = allStops.find {
            it.properties.nom.equals(stopName, ignoreCase = true)
        }

        if (stop == null) {
            stop = allStops.find {
                normalizeStopName(it.properties.nom) == normalizedStopName
            }
        }

        if (stop == null) return

        val coordinates = stop.geometry.coordinates
        if (coordinates.size < 2) return
        val stopLocation = LatLng(coordinates[1], coordinates[0])

        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(stopLocation, 15.0),
            1000
        )
    }
}
