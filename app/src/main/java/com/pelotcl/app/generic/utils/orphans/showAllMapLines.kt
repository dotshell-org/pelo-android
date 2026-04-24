package com.pelotcl.app.generic.utils.orphans

import com.pelotcl.app.generic.data.models.geojson.Feature
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import kotlin.collections.forEach

fun showAllMapLines(
    map: MapLibreMap,
    allLines: List<Feature>
) {
    map.getStyle { style ->
        clearItineraryLayers(style)

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

        showAllMapStops(style)

        style.getLayer("line-stops-circles")?.let { style.removeLayer(it) }
        style.getSource("line-stops-circles-source")?.let { style.removeSource(it) }
    }
}
