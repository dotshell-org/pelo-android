package com.pelotcl.app.generic.utils.orphans

import com.pelotcl.app.generic.data.repository.itinerary.itinerary.JourneyLeg
import com.pelotcl.app.generic.data.repository.itinerary.itinerary.JourneyResult
import com.pelotcl.app.generic.ui.screens.plan.NAV_ALERT_APPROACH_DISTANCE_METERS
import com.pelotcl.app.generic.ui.screens.plan.NAV_ALERT_APPROACH_TIME_SECONDS
import org.maplibre.android.geometry.LatLng

fun findApproachingAlertStop(
    journey: JourneyResult,
    currentLeg: JourneyLeg,
    nextLeg: JourneyLeg,
    userLocation: LatLng?,
    nowSeconds: Int
): JourneyLeg? {
    val candidateLegs = listOf(currentLeg, nextLeg)
    val nearestByDistance = userLocation?.let { location ->
        candidateLegs
            .minByOrNull { leg ->
                distanceMeters(
                    lat1 = location.latitude,
                    lon1 = location.longitude,
                    lat2 = leg.toLat,
                    lon2 = leg.toLon
                )
            }
            ?.takeIf { leg ->
                distanceMeters(
                    lat1 = location.latitude,
                    lon1 = location.longitude,
                    lat2 = leg.toLat,
                    lon2 = leg.toLon
                ) <= NAV_ALERT_APPROACH_DISTANCE_METERS
            }
    }

    val reference = journey.departureTime
    val nowNormalized = normalizeTimeAroundReference(nowSeconds, reference)
    val nearestByTime = candidateLegs
        .minByOrNull { leg ->
            kotlin.math.abs(
                normalizeTimeAroundReference(leg.arrivalTime, reference) - nowNormalized
            )
        }
        ?.takeIf { leg ->
            kotlin.math.abs(
                normalizeTimeAroundReference(leg.arrivalTime, reference) - nowNormalized
            ) <= NAV_ALERT_APPROACH_TIME_SECONDS
        }

    return when {
        isAtCurrentLegTransferStop(journey, currentLeg, userLocation) -> currentLeg
        nearestByDistance != null -> nearestByDistance
        nearestByTime != null -> nearestByTime
        else -> null
    }
}
