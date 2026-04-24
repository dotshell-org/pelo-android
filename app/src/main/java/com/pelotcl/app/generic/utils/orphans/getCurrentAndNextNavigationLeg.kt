package com.pelotcl.app.generic.utils.orphans

import com.pelotcl.app.generic.data.repository.itinerary.itinerary.JourneyLeg
import com.pelotcl.app.generic.data.repository.itinerary.itinerary.JourneyResult
import org.maplibre.android.geometry.LatLng

fun getCurrentAndNextNavigationLeg(
    journey: JourneyResult,
    nowSeconds: Int,
    userLocation: LatLng?
): Pair<JourneyLeg?, JourneyLeg?> {
    val nonWalkingLegs = journey.legs.filterNot { it.isWalking }
    if (nonWalkingLegs.isEmpty()) return null to null

    val reference = journey.departureTime
    val now = normalizeTimeAroundReference(nowSeconds, reference)
    val normalizedLegs = nonWalkingLegs.map { leg ->
        val dep = normalizeTimeAroundReference(leg.departureTime, reference)
        val arr = normalizeTimeAroundReference(leg.arrivalTime, reference)
        dep to arr
    }

    var currentIndex = normalizedLegs.indexOfFirst { (dep, arr) -> now in dep..arr }
    if (currentIndex == -1) {
        currentIndex = normalizedLegs.indexOfFirst { (dep, _) -> now < dep }
    }
    if (currentIndex == -1) {
        currentIndex = nonWalkingLegs.lastIndex
    }

    // Never move to the next line before reaching the transfer stop.
    val nearestStop = findNearestJourneyStopCandidate(journey, userLocation)
    if (nearestStop != null) {
        val maxLegIndexByLocation =
            if (nearestStop.isLegEnd && nearestStop.legIndex < nonWalkingLegs.lastIndex) {
                nearestStop.legIndex + 1
            } else {
                nearestStop.legIndex
            }
        currentIndex = currentIndex.coerceAtMost(maxLegIndexByLocation)
    }

    val currentLeg = nonWalkingLegs.getOrNull(currentIndex)
    val nextLeg = nonWalkingLegs.drop(currentIndex + 1).firstOrNull()
    return currentLeg to nextLeg
}
