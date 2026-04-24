package com.pelotcl.app.generic.utils.orphans

fun isValidJourneyCoordinate(lat: Double, lon: Double): Boolean {
    return lat in -90.0..90.0 && lon in -180.0..180.0 && (lat != 0.0 || lon != 0.0)
}
