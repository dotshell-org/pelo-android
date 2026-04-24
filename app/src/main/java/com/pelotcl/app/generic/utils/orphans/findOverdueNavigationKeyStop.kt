package com.pelotcl.app.generic.utils.orphans

import com.pelotcl.app.generic.data.models.navigation.NavigationKeyStopDeadline
import com.pelotcl.app.generic.data.repository.itinerary.itinerary.JourneyResult
import com.pelotcl.app.generic.ui.screens.plan.LATE_TRANSFER_RECALC_THRESHOLD_SECONDS
import com.pelotcl.app.generic.ui.screens.plan.NAV_ALERT_APPROACH_DISTANCE_METERS
import org.maplibre.android.geometry.LatLng

fun findOverdueNavigationKeyStop(
    journey: JourneyResult,
    userLocation: LatLng?,
    nowSeconds: Int,
    maxDistanceMeters: Double = NAV_ALERT_APPROACH_DISTANCE_METERS,
    overdueThresholdSeconds: Int = LATE_TRANSFER_RECALC_THRESHOLD_SECONDS
): NavigationKeyStopDeadline? {
    val location = userLocation ?: return null
    val keyStops = buildNavigationKeyStopDeadlines(journey)
    if (keyStops.isEmpty()) return null

    val nearest = keyStops.minByOrNull { stop ->
        distanceMeters(
            lat1 = location.latitude,
            lon1 = location.longitude,
            lat2 = stop.lat,
            lon2 = stop.lon
        )
    } ?: return null

    val nearestDistance = distanceMeters(
        lat1 = location.latitude,
        lon1 = location.longitude,
        lat2 = nearest.lat,
        lon2 = nearest.lon
    )
    if (nearestDistance > maxDistanceMeters) return null

    val reference = journey.departureTime
    val nowNormalized = normalizeTimeAroundReference(nowSeconds, reference)
    var deadlineNormalized = normalizeTimeAroundReference(nearest.deadlineSeconds, reference)
    while (deadlineNormalized < nowNormalized - 12 * 3600) {
        deadlineNormalized += 24 * 3600
    }

    return if (nowNormalized >= deadlineNormalized + overdueThresholdSeconds) {
        nearest
    } else {
        null
    }
}
