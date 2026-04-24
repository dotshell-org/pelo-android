package com.pelotcl.app.generic.utils.orphans

import com.pelotcl.app.generic.ui.screens.plan.PRIORITY_STOPS_MIN_ZOOM
import com.pelotcl.app.generic.ui.screens.plan.SECONDARY_STOPS_MIN_ZOOM
import com.pelotcl.app.generic.ui.screens.plan.TRAM_STOPS_MIN_ZOOM
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.SymbolLayer

fun showAllMapStops(
    style: Style
) {
    val priorityLayerPrefix = "transport-stops-layer-priority"
    val tramLayerPrefix = "transport-stops-layer-tram"
    val secondaryLayerPrefix = "transport-stops-layer-secondary"

    // Reset filters to show all stops — only iterate slots that exist
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
