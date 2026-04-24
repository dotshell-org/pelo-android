package com.pelotcl.app.generic.utils.orphans

import com.pelotcl.app.generic.data.models.geojson.StopFeature
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap

fun zoomToStop(
    map: MapLibreMap,
    stopName: String,
    allStops: List<StopFeature>
) {
    fun normalizeStopName(name: String): String {
        return name.filter { it.isLetter() }.lowercase()
    }

    val normalizedStopName = normalizeStopName(stopName)

    var stop = allStops.find {
        it.properties.nom.equals(stopName, ignoreCase = true)
    }

    if (stop == null) {
        stop = allStops.find {
            normalizeStopName(it.properties.nom) == normalizedStopName
        }
    }

    if (stop == null) {
        return
    }

    val coordinates = stop.geometry.coordinates
    if (coordinates.size < 2) return
    val lat = coordinates[1]
    val lon = coordinates[0]
    val stopLocation = LatLng(lat, lon)

    map.animateCamera(
        CameraUpdateFactory.newLatLngZoom(stopLocation, 15.0),
        1000
    )
}
