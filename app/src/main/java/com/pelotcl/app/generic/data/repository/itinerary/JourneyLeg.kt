package com.pelotcl.app.generic.data.repository.itinerary

/**
 * Data class representing a leg of a journey
 */
data class JourneyLeg(
    val fromStopId: String,
    val fromStopName: String,
    val fromLat: Double,
    val fromLon: Double,
    val toStopId: String,
    val toStopName: String,
    val toLat: Double,
    val toLon: Double,
    val departureTime: Int,
    val arrivalTime: Int,
    val routeName: String?,
    val routeColor: String?,
    val isWalking: Boolean,
    val direction: String? = null,
    val intermediateStops: List<IntermediateStop> = emptyList()
) {
    val durationMinutes: Int
        get() = (arrivalTime - departureTime) / 60

    fun formatDepartureTime(): String = formatTime(departureTime)
    fun formatArrivalTime(): String = formatTime(arrivalTime)

    private fun formatTime(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        return String.format(java.util.Locale.ROOT, "%02d:%02d", hours, minutes)
    }
}