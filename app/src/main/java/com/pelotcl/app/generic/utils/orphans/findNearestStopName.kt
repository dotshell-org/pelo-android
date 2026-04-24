package com.pelotcl.app.generic.utils.orphans

import com.pelotcl.app.generic.data.models.geojson.StopFeature
import org.maplibre.android.geometry.LatLng
import kotlin.collections.forEach

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
            // Consider all stops, not just those with desserte
            // This prevents bus stops from disappearing when Raptor assets are missing
            if (distance < nearestDistance) {
                nearestDistance = distance
                nearestName = stop.properties.nom
            }
        }
    }

    return nearestName
}
