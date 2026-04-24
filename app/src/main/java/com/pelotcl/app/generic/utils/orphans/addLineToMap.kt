package com.pelotcl.app.generic.utils.orphans

import com.pelotcl.app.generic.data.models.geojson.Feature
import com.pelotcl.app.specific.utils.LineColorHelper
import com.pelotcl.app.specific.utils.orphans.isNavigoneLine
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource

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

        val lineGeoJson = createGeoJsonFromFeature(feature)

        val lineSource = GeoJsonSource(sourceId, lineGeoJson)
        style.addSource(lineSource)

        val lineColor = LineColorHelper.getColorForLine(feature)

        val upperLineName = ligne.uppercase()
        val familleTransport = feature.properties.transportType
        val lineWidth = when {
            familleTransport == "BAT" || isNavigoneLine(upperLineName) -> 2f
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
