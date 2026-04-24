package com.pelotcl.app.generic.utils.orphans

import org.maplibre.android.geometry.LatLng

fun findNavigationAxisSegment(
    userLocation: LatLng,
    pathPoints: List<LatLng>
): Pair<LatLng, LatLng>? {
    if (pathPoints.size < 2) return null

    var bestDistanceSq = Double.MAX_VALUE
    var bestProjectedPoint: LatLng? = null
    var bestNextPoint: LatLng? = null

    for (index in 0 until pathPoints.lastIndex) {
        val start = pathPoints[index]
        val end = pathPoints[index + 1]

        val dx = end.longitude - start.longitude
        val dy = end.latitude - start.latitude
        val lengthSq = (dx * dx) + (dy * dy)
        if (lengthSq <= 1e-14) continue

        val ux = userLocation.longitude - start.longitude
        val uy = userLocation.latitude - start.latitude
        val t = ((ux * dx) + (uy * dy)) / lengthSq
        val clampedT = t.coerceIn(0.0, 1.0)

        val projLon = start.longitude + (clampedT * dx)
        val projLat = start.latitude + (clampedT * dy)

        val distanceSq = squaredDistance(
            lat1 = userLocation.latitude,
            lon1 = userLocation.longitude,
            lat2 = projLat,
            lon2 = projLon
        )

        if (distanceSq < bestDistanceSq) {
            bestDistanceSq = distanceSq
            val projected = LatLng(projLat, projLon)

            val nextPoint = if (clampedT >= 0.999 && index + 2 <= pathPoints.lastIndex) {
                pathPoints[index + 2]
            } else {
                end
            }

            bestProjectedPoint = projected
            bestNextPoint = nextPoint
        }
    }

    val from = bestProjectedPoint ?: return null
    val to = bestNextPoint ?: return null
    if (from.latitude == to.latitude && from.longitude == to.longitude) return null
    return from to to
}
