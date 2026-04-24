package com.pelotcl.app.generic.utils.orphans

fun squaredDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = lat1 - lat2
    val dLon = lon1 - lon2
    return dLat * dLat + dLon * dLon
}
