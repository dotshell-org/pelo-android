package com.pelotcl.app.generic.utils.orphans

import com.pelotcl.app.generic.data.repository.itinerary.itinerary.JourneyLeg

fun computeTransferWaitSeconds(
    currentLeg: JourneyLeg,
    nextLeg: JourneyLeg,
    journeyReferenceSeconds: Int
): Int {
    val currentArrivalNormalized =
        normalizeTimeAroundReference(currentLeg.arrivalTime, journeyReferenceSeconds)
    var nextDepartureNormalized =
        normalizeTimeAroundReference(nextLeg.departureTime, journeyReferenceSeconds)
    while (nextDepartureNormalized < currentArrivalNormalized) {
        nextDepartureNormalized += 24 * 3600
    }
    return (nextDepartureNormalized - currentArrivalNormalized).coerceAtLeast(0)
}
