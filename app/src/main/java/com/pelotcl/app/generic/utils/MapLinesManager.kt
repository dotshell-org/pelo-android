package com.pelotcl.app.generic.utils

import com.pelotcl.app.generic.data.models.geojson.Feature
import com.pelotcl.app.specific.utils.LineColorHelper
import com.pelotcl.app.specific.utils.LineClassificationUtils
import com.pelotcl.app.specific.utils.LineNamingUtils
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource

object MapLinesManager {
    fun addLineToMap(
    map: MapLibreMap,
    feature: Feature
) {
    map.getStyle { style ->
        val ligne = feature.properties.lineName
        val codeTrace = feature.properties.traceCode

        val sourceId = "line-${ligne}-${codeTrace}"
        val layerId = "layer-${ligne}-${codeTrace}"

        style.getLayer(layerId)?.let { style.removeLayer(it) }
        style.getSource(sourceId)?.let { style.removeSource(it) }

        val lineGeoJson = StopsGeoJsonManager.createGeoJsonFromFeature(feature)

        val lineSource = GeoJsonSource(sourceId, lineGeoJson)
        style.addSource(lineSource)

        val lineColor = LineColorHelper.getColorForLine(feature)

        val upperLineName = ligne.uppercase()
        val familleTransport = feature.properties.transportType
        val lineWidth = when {
            familleTransport == "BAT" || LineClassificationUtils.isNavigoneLine(upperLineName) -> 2f
            familleTransport == "TRA" || familleTransport == "TRAM" || upperLineName.startsWith("TB") -> 2f
            else -> 4f
        }

        val lineLayer = LineLayer(layerId, sourceId).apply {
            setProperties(
                PropertyFactory.lineColor(lineColor),
                PropertyFactory.lineWidth(lineWidth),
                PropertyFactory.lineOpacity(0.8f),
                PropertyFactory.lineCap("round"),
                PropertyFactory.lineJoin("round")
            )
        }

        val firstStopLayer = style.layers.find { it.id.startsWith("transport-stops-layer") }
        if (firstStopLayer != null) {
            style.addLayerBelow(lineLayer, firstStopLayer.id)
        } else {
            style.addLayer(lineLayer)
        }
    }
    }

    fun showAllMapLines(
        map: MapLibreMap,
        allLines: List<Feature>
    ) {
        map.getStyle { style ->
            ItineraryMapManager.clearItineraryLayers(style)

            (style.getLayer("all-lines-layer") as? LineLayer)?.let { allLinesLayer ->
                allLinesLayer.setProperties(PropertyFactory.visibility("visible"))
                allLinesLayer.setFilter(Expression.literal(true))
            }

            allLines.forEach { feature ->
                val ligne = feature.properties.lineName
                val codeTrace = feature.properties.traceCode
                val layerId = "layer-${ligne}-${codeTrace}"
                val sourceId = "line-${ligne}-${codeTrace}"

                val existingLayer = style.getLayer(layerId)
                if (existingLayer == null) {
                    addLineToMap(map, feature)
                } else {
                    existingLayer.setProperties(PropertyFactory.visibility("visible"))
                }

                if (style.getSource(sourceId) == null) {
                    addLineToMap(map, feature)
                }
            }

            MapStopsManager.showAllMapStops(style)
            style.getLayer("line-stops-circles")?.let { style.removeLayer(it) }
            style.getSource("line-stops-circles-source")?.let { style.removeSource(it) }
        }
    }

    fun hideMapLines(map: MapLibreMap) {
        map.getStyle { style ->
            style.getLayer("all-lines-layer")?.setProperties(PropertyFactory.visibility("none"))

            style.layers
                .map { it.id }
                .filter { it.startsWith("layer-") }
                .forEach { layerId ->
                    style.getLayer(layerId)?.setProperties(PropertyFactory.visibility("none"))
                }

            style.getLayer("line-stops-circles")?.let { style.removeLayer(it) }
            style.getSource("line-stops-circles-source")?.let { style.removeSource(it) }
        }
    }

    fun zoomToLine(
        map: MapLibreMap,
        allLines: List<Feature>,
        selectedLineName: String
    ) {
        val lineFeatures = allLines.filter {
            LineNamingUtils.areEquivalentLineNames(it.properties.lineName, selectedLineName)
        }

        if (lineFeatures.isEmpty()) return

        val boundsBuilder = LatLngBounds.Builder()
        var hasCoordinates = false

        lineFeatures.forEach { feature ->
            feature.geometry.coordinates.forEach { lineString ->
                lineString.forEach { coord ->
                    boundsBuilder.include(LatLng(coord[1], coord[0]))
                    hasCoordinates = true
                }
            }
        }

        if (!hasCoordinates) return

        val bounds = boundsBuilder.build()
        map.animateCamera(
            CameraUpdateFactory.newLatLngBounds(bounds, 200, 100, 200, 600),
            1000
        )
    }
}
