package com.pelotcl.app.generic.utils.orphans

import org.maplibre.android.maps.Style

fun clearItineraryLayers(style: Style) {
    val layerIds = style.layers.map { it.id }.filter { it.startsWith("inline-itinerary-") }
    layerIds.forEach { layerId ->
        style.getLayer(layerId)?.let { style.removeLayer(it) }
        val sourceId = layerId.replace("-layer-", "-source-")
        style.getSource(sourceId)?.let { style.removeSource(it) }
    }
}
