package com.pelotcl.app.generic.utils.orphans

import com.pelotcl.app.generic.data.repository.itinerary.itinerary.JourneyLeg
import com.pelotcl.app.generic.data.repository.itinerary.itinerary.JourneyResult
import org.maplibre.android.geometry.LatLng

fun isAtCurrentLegTransferStop(
    journey: JourneyResult,
    currentLeg: JourneyLeg,
    userLocation: LatLng?
): Boolean {
    val nearestStop = findNearestJourneyStopCandidate(journey, userLocation) ?: return false
    val nonWalkingLegs = journey.legs.filterNot { it.isWalking }
    val currentLegIndex = nonWalkingLegs.indexOfFirst { leg -> isSameJourneyLeg(leg, currentLeg) }
    if (currentLegIndex == -1 || currentLegIndex >= nonWalkingLegs.lastIndex) return false
    return nearestStop.legIndex == currentLegIndex && nearestStop.isLegEnd
}
