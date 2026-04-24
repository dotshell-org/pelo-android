package com.pelotcl.app.generic.utils.orphans

import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.layers.PropertyFactory

fun hideMapLines(
    map: MapLibreMap
) {
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
