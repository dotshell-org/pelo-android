package com.pelotcl.app.generic.utils.orphans

import com.pelotcl.app.generic.data.models.geojson.Feature
import com.pelotcl.app.generic.data.models.geojson.StopFeature
import com.pelotcl.app.generic.ui.screens.plan.SELECTED_STOP_MIN_ZOOM
import com.pelotcl.app.generic.ui.viewmodel.TransportViewModel
import com.pelotcl.app.specific.utils.orphans.canonicalLineName
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.SymbolLayer

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
        val linePropertyName = "has_line_${canonicalLineName(selectedLineName)}"

        // Filter layers only for slots that exist
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
