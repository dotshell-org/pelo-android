package com.pelotcl.app.generic.utils.orphans

import com.pelotcl.app.generic.data.repository.itinerary.itinerary.JourneyLeg

fun isSameJourneyLeg(first: JourneyLeg, second: JourneyLeg): Boolean {
    return first.fromStopId == second.fromStopId &&
            first.toStopId == second.toStopId &&
            first.departureTime == second.departureTime &&
            first.arrivalTime == second.arrivalTime &&
            first.routeName == second.routeName
}
