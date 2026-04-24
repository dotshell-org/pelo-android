package com.pelotcl.app.generic.utils.orphans

import com.pelotcl.app.generic.data.models.itinerary.JourneyStopCandidate
import com.pelotcl.app.generic.data.repository.itinerary.itinerary.JourneyResult
import org.maplibre.android.geometry.LatLng

fun findNearestJourneyStopCandidate(
    journey: JourneyResult,
    userLocation: LatLng?
): JourneyStopCandidate? {
    if (userLocation == null) return null

    val candidates = mutableListOf<JourneyStopCandidate>()
    journey.legs.filterNot { it.isWalking }.forEachIndexed { legIndex, leg ->
        if (isValidJourneyCoordinate(leg.fromLat, leg.fromLon)) {
            candidates += JourneyStopCandidate(
                legIndex = legIndex,
                isLegEnd = false,
                lat = leg.fromLat,
                lon = leg.fromLon
            )
        }
        leg.intermediateStops.forEach { stop ->
            if (isValidJourneyCoordinate(stop.lat, stop.lon)) {
                candidates += JourneyStopCandidate(
                    legIndex = legIndex,
                    isLegEnd = false,
                    lat = stop.lat,
                    lon = stop.lon
                )
            }
        }
        if (isValidJourneyCoordinate(leg.toLat, leg.toLon)) {
            candidates += JourneyStopCandidate(
                legIndex = legIndex,
                isLegEnd = true,
                lat = leg.toLat,
                lon = leg.toLon
            )
        }
    }
    if (candidates.isEmpty()) return null

    return candidates.minByOrNull { stop ->
        squaredDistance(
            lat1 = userLocation.latitude,
            lon1 = userLocation.longitude,
            lat2 = stop.lat,
            lon2 = stop.lon
        )
    }
}
