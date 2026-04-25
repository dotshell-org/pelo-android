package com.pelotcl.app.generic.utils

import com.pelotcl.app.generic.data.models.geojson.StopFeature
import org.maplibre.android.geometry.LatLng
import java.util.Calendar
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object GeometryUtils {

    fun currentTimeInSeconds(): Int {
        val calendar = Calendar.getInstance()
        return calendar.get(Calendar.HOUR_OF_DAY) * 3600 +
                calendar.get(Calendar.MINUTE) * 60 +
                calendar.get(Calendar.SECOND)
    }

    fun computeBearingDegrees(from: LatLng, to: LatLng): Double {
        val fromLat = Math.toRadians(from.latitude)
        val fromLon = Math.toRadians(from.longitude)
        val toLat = Math.toRadians(to.latitude)
        val toLon = Math.toRadians(to.longitude)
        val dLon = toLon - fromLon

        val y = sin(dLon) * cos(toLat)
        val x = cos(fromLat) * sin(toLat) - sin(fromLat) * cos(toLat) * cos(dLon)
        val bearing = Math.toDegrees(atan2(y, x))
        return (bearing + 360.0) % 360.0
    }

    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) *
                cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }

    fun squaredDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = lat1 - lat2
        val dLon = lon1 - lon2
        return dLat * dLat + dLon * dLon
    }

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
                bestProjectedPoint = LatLng(projLat, projLon)
                bestNextPoint = if (clampedT >= 0.999 && index + 2 <= pathPoints.lastIndex) {
                    pathPoints[index + 2]
                } else {
                    end
                }
            }
        }

        val from = bestProjectedPoint ?: return null
        val to = bestNextPoint ?: return null
        if (from.latitude == to.latitude && from.longitude == to.longitude) return null
        return from to to
    }

    fun findNearestStopName(userLocation: LatLng, stops: List<StopFeature>): String? {
        var nearestName: String? = null
        var nearestDistance = Double.MAX_VALUE

        stops.forEach { stop ->
            val coordinates = stop.geometry.coordinates
            if (coordinates.size >= 2) {
                val lon = coordinates[0]
                val lat = coordinates[1]
                val distance = squaredDistance(
                    lat1 = userLocation.latitude,
                    lon1 = userLocation.longitude,
                    lat2 = lat,
                    lon2 = lon
                )
                if (distance < nearestDistance) {
                    nearestDistance = distance
                    nearestName = stop.properties.nom
                }
            }
        }

        return nearestName
    }
}
