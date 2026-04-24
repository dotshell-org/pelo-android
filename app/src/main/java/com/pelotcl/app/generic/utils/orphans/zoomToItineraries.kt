package com.pelotcl.app.generic.utils.orphans

import com.pelotcl.app.generic.data.repository.itinerary.itinerary.JourneyResult
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import kotlin.collections.forEach

fun zoomToItineraries(
    map: MapLibreMap,
    journeys: List<JourneyResult>
) {
    if (journeys.isEmpty()) return

    val boundsBuilder = LatLngBounds.Builder()
    var hasCoordinates = false

    journeys.forEach { journey ->
        journey.legs.forEach { leg ->
            boundsBuilder.include(LatLng(leg.fromLat, leg.fromLon))
            boundsBuilder.include(LatLng(leg.toLat, leg.toLon))
            hasCoordinates = true

            leg.intermediateStops.forEach { stop ->
                boundsBuilder.include(LatLng(stop.lat, stop.lon))
                hasCoordinates = true
            }
        }
    }

    if (!hasCoordinates) return

    try {
        val bounds = boundsBuilder.build()
        map.animateCamera(
            CameraUpdateFactory.newLatLngBounds(
                bounds,
                70,
                120,
                70,
                520
            ),
            900
        )
    } catch (_: Exception) {
        // Ignore invalid bounds edge cases.
    }
}
