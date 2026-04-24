package com.pelotcl.app.generic.utils.orphans

import com.pelotcl.app.generic.data.repository.itinerary.itinerary.JourneyResult

fun buildJourneyAlertSessionKey(journey: JourneyResult): String = buildString {
    append(journey.departureTime)
    append('_')
    append(journey.arrivalTime)
    append('_')
    append(journey.legs.joinToString(separator = "|") { leg ->
        "${leg.fromStopId}>${leg.toStopId}>${leg.departureTime}>${leg.arrivalTime}>${leg.routeName ?: ""}"
    })
}
