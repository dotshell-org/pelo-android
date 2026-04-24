package com.pelotcl.app.specific.utils.orphans

import com.pelotcl.app.generic.data.models.geojson.Feature
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import kotlin.collections.forEach

fun filterMapLines(
    map: MapLibreMap,
    allLines: List<Feature>,
    selectedLineName: String
): Int {
    val selectedAliases = when (canonicalLineName(selectedLineName)) {
        "NAV1" -> listOf("NAV1", "NAVI1")
        else -> listOf(selectedLineName.trim().uppercase())
    }

    map.getStyle { style ->
        val layerId = "all-lines-layer"
        val existingLayer = style.getLayer(layerId)

        if (existingLayer != null) {
            val filterExpressions = selectedAliases.map { alias ->
                Expression.eq(Expression.get("ligne"), alias)
            }.toTypedArray()
            val lineFilter = if (filterExpressions.size == 1) {
                filterExpressions.first()
            } else {
                Expression.any(*filterExpressions)
            }
            (existingLayer as? LineLayer)?.setFilter(
                lineFilter
            )
        }

        // Also hide/show individual line layers (for lignes fortes)
        allLines.forEach { feature ->
            val ligne = feature.properties.lineName
            val codeTrace = feature.properties.traceCode

            val individualLayerId = "layer-${ligne}-${codeTrace}"
            style.getLayer(individualLayerId)?.let { layer ->
                val shouldBeVisible = areEquivalentLineNames(ligne, selectedLineName)
                layer.setProperties(
                    PropertyFactory.visibility(if (shouldBeVisible) "visible" else "none")
                )
            }
        }
    }
    val visibleCandidates =
        allLines.count { areEquivalentLineNames(it.properties.lineName, selectedLineName) }
    return visibleCandidates
}
