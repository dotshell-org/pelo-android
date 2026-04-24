package com.pelotcl.app.generic.utils.orphans

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.pelotcl.app.generic.data.models.geojson.Feature
import com.pelotcl.app.generic.data.models.geojson.StopFeature
import com.pelotcl.app.generic.ui.screens.plan.SELECTED_STOP_MIN_ZOOM
import com.pelotcl.app.generic.ui.viewmodel.TransportViewModel
import com.pelotcl.app.generic.utils.BusIconHelper
import com.pelotcl.app.specific.utils.LineColorHelper
import com.pelotcl.app.specific.utils.orphans.areEquivalentLineNames
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource

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
        .find { areEquivalentLineNames(it.properties.lineName, selectedLineName) }
        ?.let { LineColorHelper.getColorForLine(it) }
        ?: "#EF4444"

    // OPTIMIZATION: Use pre-computed index from ViewModel if available (O(1) lookup)
    // Falls back to filtering all stops if index is not ready
    val lineStops = if (viewModel != null && viewModel.isStopsByLineIndexReady()) {
        // O(1) lookup from index, then filter only the selected stop
        viewModel.getStopsFeaturesForLine(selectedLineName)
            .filter { stop -> normalizeStopName(stop.properties.nom) != normalizedSelectedStop }
    } else {
        // Fallback: filter all stops (slower, but works if index not ready)
        allStops.filter { stop ->
            val lines = BusIconHelper.getAllLinesForStop(stop)
            val hasLine = lines.any { areEquivalentLineNames(it, selectedLineName) }
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

    // OPTIMIZATION: Use setGeoJson if source exists, otherwise create new source
    val existingSource = style.getSource("line-stops-circles-source") as? GeoJsonSource
    if (existingSource != null) {
        // Update existing source data without recreating
        existingSource.setGeoJson(circlesGeoJson.toString())
        // Update layer color (stroke color may have changed for different line)
        (style.getLayer("line-stops-circles") as? CircleLayer)?.setProperties(
            PropertyFactory.circleStrokeColor(lineColor)
        )
    } else {
        // Create new source and layer
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
