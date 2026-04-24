package com.pelotcl.app.generic.utils.orphans

import com.pelotcl.app.generic.data.repository.itinerary.itinerary.JourneyResult

fun computeRemainingJourneySeconds(
    journey: JourneyResult,
    nowSeconds: Int
): Int {
    val reference = journey.departureTime
    val nowNormalized = normalizeTimeAroundReference(nowSeconds, reference)
    val arrivalNormalized = normalizeTimeAroundReference(journey.arrivalTime, reference)
    return (arrivalNormalized - nowNormalized).coerceAtLeast(0)
}
