package com.pelotcl.app.generic.data.repository.itinerary

/**
 * Data class representing a journey result
 */
data class JourneyResult(
    val departureTime: Int, // in seconds from midnight
    val arrivalTime: Int, // in seconds from midnight
    val legs: List<JourneyLeg>
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

    /**
     * Extract all stop IDs from this journey (used for alert checking)
     */
    fun getAllStopIds(): Set<String> {
        val stopIds = mutableSetOf<String>()
        for (leg in legs) {
            if (!leg.isWalking) {
                stopIds.add(leg.fromStopId)
                stopIds.add(leg.toStopId)
                leg.intermediateStops.forEach { stop ->
                    // Try to extract stop ID from intermediate stop name if available
                    stopIds.add(stop.stopName)
                }
            }
        }
        return stopIds
    }

    /**
     * Check if this journey passes through any of the problematic stops
     */
    fun passesThroughProblematicStops(problematicStopIds: Set<String>): Boolean {
        return getAllStopIds().any { it in problematicStopIds }
    }
}