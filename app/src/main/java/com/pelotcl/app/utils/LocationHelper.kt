package com.pelotcl.app.utils

import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import kotlinx.coroutines.suspendCancellableCoroutine
import org.maplibre.android.geometry.LatLng
import kotlin.coroutines.resume

/**
 * Helper object for location-related utilities
 */
object LocationHelper {

    private var locationCallback: LocationCallback? = null

    /**
     * Get the last known location immediately (cached by the system).
     * This is very fast as it doesn't require a new GPS fix.
     * @param fusedLocationClient The fused location client to use
     * @return The last known location, or null if not available
     */
    @Suppress("MissingPermission") // Permission should be checked before calling this function
    suspend fun getLastKnownLocation(fusedLocationClient: FusedLocationProviderClient): LatLng? {
        return try {
            suspendCancellableCoroutine { continuation ->
                fusedLocationClient.lastLocation
                    .addOnSuccessListener { location ->
                        if (location != null) {
                            continuation.resume(LatLng(location.latitude, location.longitude))
                        } else {
                            continuation.resume(null)
                        }
                    }
                    .addOnFailureListener {
                        continuation.resume(null)
                    }
            }
        } catch (_: Exception) {
            null
        }
    }

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

}
