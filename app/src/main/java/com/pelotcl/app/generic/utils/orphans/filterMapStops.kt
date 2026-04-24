package com.pelotcl.app.generic.utils.orphans

import com.pelotcl.app.generic.ui.screens.plan.PRIORITY_STOPS_MIN_ZOOM
import com.pelotcl.app.specific.utils.orphans.canonicalLineName
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.SymbolLayer

var currentMapSlots: Set<Int> = emptySet()

fun filterMapStops(
    style: Style,
    selectedLineName: String
) {
    val priorityLayerPrefix = "transport-stops-layer-priority"
    val tramLayerPrefix = "transport-stops-layer-tram"
    val secondaryLayerPrefix = "transport-stops-layer-secondary"

    val linePropertyName = "has_line_${canonicalLineName(selectedLineName)}"

    // Filter layers only for slots that exist (instead of all -25..25)
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
