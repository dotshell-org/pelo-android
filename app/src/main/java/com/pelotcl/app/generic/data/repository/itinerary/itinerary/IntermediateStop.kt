package com.pelotcl.app.generic.data.repository.itinerary.itinerary

import java.util.Locale

/**
 * Data class representing an intermediate stop
 */
data class IntermediateStop(
    val stopName: String,
    val arrivalTime: Int,
    val lat: Double = 0.0,
    val lon: Double = 0.0
) {
    fun formatArrivalTime(): String {
        val hours = arrivalTime / 3600
        val minutes = (arrivalTime % 3600) / 60
        return String.format(Locale.ROOT, "%02d:%02d", hours, minutes)
    }
}