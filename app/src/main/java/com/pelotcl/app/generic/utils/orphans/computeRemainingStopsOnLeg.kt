package com.pelotcl.app.generic.utils.orphans

import com.pelotcl.app.generic.data.models.itinerary.LegStopPosition
import com.pelotcl.app.generic.data.repository.itinerary.itinerary.JourneyLeg
import org.maplibre.android.geometry.LatLng

fun computeRemainingStopsOnLeg(
    leg: JourneyLeg,
    userLocation: LatLng?
): Int {
    val stops = ArrayList<LegStopPosition>(leg.intermediateStops.size + 2)
    stops += LegStopPosition(index = 0, lat = leg.fromLat, lon = leg.fromLon)
    leg.intermediateStops.forEachIndexed { stopIndex, stop ->
        stops += LegStopPosition(index = stopIndex + 1, lat = stop.lat, lon = stop.lon)
    }
    val terminusIndex = stops.size
    stops += LegStopPosition(index = terminusIndex, lat = leg.toLat, lon = leg.toLon)

    val nearestStopIndex = userLocation?.let { location ->
        stops
            .filter { isValidJourneyCoordinate(it.lat, it.lon) }
            .minByOrNull { stop ->
                squaredDistance(
                    lat1 = location.latitude,
                    lon1 = location.longitude,
                    lat2 = stop.lat,
                    lon2 = stop.lon
                )
            }?.index
    } ?: 0

    return (terminusIndex - nearestStopIndex).coerceAtLeast(0)
}
