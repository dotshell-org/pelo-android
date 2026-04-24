package com.pelotcl.app.generic.utils.orphans

import com.pelotcl.app.generic.data.repository.itinerary.itinerary.JourneyLeg
import com.pelotcl.app.generic.data.repository.itinerary.itinerary.JourneyResult

fun findUpcomingNonWalkingLeg(
    journey: JourneyResult,
    currentLeg: JourneyLeg,
    offsetFromCurrent: Int
): JourneyLeg? {
    val nonWalkingLegs = journey.legs.filterNot { it.isWalking }
    val currentIndex = nonWalkingLegs.indexOfFirst { leg -> isSameJourneyLeg(leg, currentLeg) }
    if (currentIndex == -1) return null
    return nonWalkingLegs.getOrNull(currentIndex + offsetFromCurrent)
}
