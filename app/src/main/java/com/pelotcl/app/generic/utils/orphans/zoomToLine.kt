package com.pelotcl.app.generic.utils.orphans

import com.pelotcl.app.generic.data.models.geojson.Feature
import com.pelotcl.app.specific.utils.orphans.areEquivalentLineNames
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap

fun zoomToLine(
    map: MapLibreMap,
    allLines: List<Feature>,
    selectedLineName: String
) {
    val lineFeatures = allLines.filter {
        areEquivalentLineNames(it.properties.lineName, selectedLineName)
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

    val paddingLeft = 200
    val paddingTop = 100
    val paddingRight = 200
    val paddingBottom = 600

    map.animateCamera(
        CameraUpdateFactory.newLatLngBounds(
            bounds,
            paddingLeft,
            paddingTop,
            paddingRight,
            paddingBottom
        ),
        1000
    )
}
