package com.pelotcl.app.utils

import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import org.maplibre.android.geometry.LatLng
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Helper object for location-related utilities
 */
object LocationHelper {

    private var locationCallback: LocationCallback? = null

    /**
     * Start receiving continuous location updates
     * @param fusedLocationClient The fused location client to use
     * @param onLocationUpdate Callback invoked when a new location is received
     */
    @Suppress("MissingPermission") // Permission should be checked before calling this function
    fun startLocationUpdates(
        fusedLocationClient: FusedLocationProviderClient,
        onLocationUpdate: (LatLng) -> Unit
    ) {
        try {
            // Create location request for real-time updates
            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                5000L // Update every 5 seconds
            ).apply {
                setMinUpdateIntervalMillis(3000L) // Fastest update interval: 3 seconds
                setWaitForAccurateLocation(false)
            }.build()

            // Create location callback
            locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    locationResult.lastLocation?.let { location ->
                        onLocationUpdate(LatLng(location.latitude, location.longitude))
                    }
                }
            }

            // Start receiving location updates
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
        } catch (_: SecurityException) {
            // Permission denied
        }
    }

    /**
     * Stop receiving location updates
     * @param fusedLocationClient The fused location client to use
     */
    fun stopLocationUpdates(fusedLocationClient: FusedLocationProviderClient) {
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
            locationCallback = null
        }
    }

    /**
     * Calculate the distance in meters between two LatLng points using the Haversine formula
     * @param from The starting point
     * @param to The ending point
     * @return The distance in meters
     */
    fun distanceInMeters(from: LatLng, to: LatLng): Double {
        val earthRadius = 6371000.0 // Earth's radius in meters

        val lat1Rad = Math.toRadians(from.latitude)
        val lat2Rad = Math.toRadians(to.latitude)
        val deltaLatRad = Math.toRadians(to.latitude - from.latitude)
        val deltaLonRad = Math.toRadians(to.longitude - from.longitude)

        val a = kotlin.math.sin(deltaLatRad / 2).pow(2) +
                kotlin.math.cos(lat1Rad) * kotlin.math.cos(lat2Rad) *
                kotlin.math.sin(deltaLonRad / 2).pow(2)
        val c = 2 * kotlin.math.atan2(sqrt(a), sqrt(1 - a))

        return earthRadius * c
    }

    /**
     * Check if two locations are significantly different (more than threshold meters apart)
     * @param oldLocation The previous location
     * @param newLocation The new location
     * @param thresholdMeters The minimum distance in meters to consider locations as different (default: 100m)
     * @return true if the locations are significantly different
     */
    fun isSignificantlyDifferent(
        oldLocation: LatLng?,
        newLocation: LatLng?,
        thresholdMeters: Double = 100.0
    ): Boolean {
        if (oldLocation == null || newLocation == null) return true
        return distanceInMeters(oldLocation, newLocation) > thresholdMeters
    }
}
