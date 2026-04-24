package com.pelotcl.app.generic.utils.orphans

import com.pelotcl.app.generic.data.repository.itinerary.itinerary.JourneyResult
import org.maplibre.android.geometry.LatLng

fun isNearestJourneyStopTerminus(
    journey: JourneyResult,
    userLocation: LatLng?
): Boolean {
    if (userLocation == null) return false

    val stops = mutableListOf<LatLng>()
    journey.legs.filterNot { it.isWalking }.forEach { leg ->
        if (isValidJourneyCoordinate(leg.fromLat, leg.fromLon)) {
            stops.add(LatLng(leg.fromLat, leg.fromLon))
        }
        leg.intermediateStops.forEach { stop ->
            if (isValidJourneyCoordinate(stop.lat, stop.lon)) {
                stops.add(LatLng(stop.lat, stop.lon))
            }
        }
        if (isValidJourneyCoordinate(leg.toLat, leg.toLon)) {
            stops.add(LatLng(leg.toLat, leg.toLon))
        }
    }
    if (stops.isEmpty()) return false

    val nearestIndex = stops.indices.minByOrNull { index ->
        squaredDistance(
            lat1 = userLocation.latitude,
            lon1 = userLocation.longitude,
            lat2 = stops[index].latitude,
            lon2 = stops[index].longitude
        )
    } ?: return false

    return nearestIndex == stops.lastIndex
}
