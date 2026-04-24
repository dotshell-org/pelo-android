package com.pelotcl.app.generic.utils.orphans

import com.pelotcl.app.generic.data.models.navigation.NavigationKeyStopDeadline
import com.pelotcl.app.generic.data.models.navigation.NavigationKeyStopType
import com.pelotcl.app.generic.data.repository.itinerary.itinerary.JourneyResult

fun buildNavigationKeyStopDeadlines(journey: JourneyResult): List<NavigationKeyStopDeadline> {
    val nonWalkingLegs = journey.legs.filterNot { it.isWalking }
    if (nonWalkingLegs.isEmpty()) return emptyList()

    val result = mutableListOf<NavigationKeyStopDeadline>()
    val firstLeg = nonWalkingLegs.first()
    if (isValidJourneyCoordinate(firstLeg.fromLat, firstLeg.fromLon)) {
        result += NavigationKeyStopDeadline(
            stopId = firstLeg.fromStopId,
            stopName = firstLeg.fromStopName,
            lat = firstLeg.fromLat,
            lon = firstLeg.fromLon,
            deadlineSeconds = firstLeg.departureTime,
            type = NavigationKeyStopType.START
        )
    }

    for (index in 0 until nonWalkingLegs.lastIndex) {
        val current = nonWalkingLegs[index]
        val next = nonWalkingLegs[index + 1]
        if (isValidJourneyCoordinate(current.toLat, current.toLon)) {
            result += NavigationKeyStopDeadline(
                stopId = current.toStopId,
                stopName = current.toStopName,
                lat = current.toLat,
                lon = current.toLon,
                deadlineSeconds = next.departureTime,
                type = NavigationKeyStopType.TRANSFER
            )
        }
    }

    val lastLeg = nonWalkingLegs.last()
    if (isValidJourneyCoordinate(lastLeg.toLat, lastLeg.toLon)) {
        result += NavigationKeyStopDeadline(
            stopId = lastLeg.toStopId,
            stopName = lastLeg.toStopName,
            lat = lastLeg.toLat,
            lon = lastLeg.toLon,
            deadlineSeconds = lastLeg.arrivalTime,
            type = NavigationKeyStopType.TERMINUS
        )
    }

    return result
}
