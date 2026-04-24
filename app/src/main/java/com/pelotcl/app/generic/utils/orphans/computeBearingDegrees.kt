package com.pelotcl.app.generic.utils.orphans

import org.maplibre.android.geometry.LatLng
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

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
