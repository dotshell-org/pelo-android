package com.pelotcl.app.data.repository

import com.pelotcl.app.data.api.VehiclePositionsRetrofitInstance
import com.pelotcl.app.data.model.SimpleVehiclePosition
import com.pelotcl.app.utils.withRetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for fetching real-time vehicle positions from the SIRI-lite API
 */
class VehiclePositionsRepository {
    
    private val api = VehiclePositionsRetrofitInstance.api
    
    /**
     * Fetches all vehicle positions from the SIRI-lite API
     *
     * @return List of all vehicle positions across the entire network
     */
    suspend fun getAllVehiclePositions(): Result<List<SimpleVehiclePosition>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = withRetry(maxRetries = 2, initialDelayMs = 500) {
                    api.getVehiclePositions()
                }

                if (!response.success) {
                    return@withContext Result.failure(Exception("API returned success=false"))
                }

                val activities = response.data?.siri?.serviceDelivery?.vehicleMonitoringDelivery
                    ?.flatMap { it.vehicleActivity ?: emptyList() }
                    ?: emptyList()

                val positions = activities.mapNotNull { activity ->
                    val journey = activity.monitoredVehicleJourney ?: return@mapNotNull null
                    val location = journey.vehicleLocation ?: return@mapNotNull null
                    val lat = location.latitude ?: return@mapNotNull null
                    val lon = location.longitude ?: return@mapNotNull null
                    val lineRef = journey.lineRef?.value ?: return@mapNotNull null
                    val vehicleId = activity.vehicleMonitoringRef?.value ?: return@mapNotNull null

                    SimpleVehiclePosition(
                        vehicleId = vehicleId,
                        lineName = extractLineNameFromRef(lineRef),
                        latitude = lat,
                        longitude = lon,
                        bearing = journey.bearing,
                        destinationName = journey.destinationName?.firstOrNull()?.value,
                        direction = journey.directionRef?.value
                    )
                }

                Result.success(positions)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Fetches all vehicle positions and filters them by the given line name
     *
     * @param lineName The line name to filter by (e.g., "67", "C3", "T1")
     * @return List of vehicle positions for the specified line
     */
    suspend fun getVehiclePositionsForLine(lineName: String): Result<List<SimpleVehiclePosition>> {
        return getAllVehiclePositions().map { positions ->
            positions.filter { it.lineName.equals(lineName, ignoreCase = true) }
        }
    }
    
    /**
     * Extracts the line name from a LineRef value
     * Example: "ActIV:Line::67:SYTRAL" -> "67"
     * Example: "ActIV:Line::C3:SYTRAL" -> "C3"
     * Example: "ActIV:Line::T1:SYTRAL" -> "T1"
     */
    private fun extractLineNameFromRef(lineRef: String): String {
        // Format: "ActIV:Line::LINE_NAME:SYTRAL"
        val parts = lineRef.split("::")
        if (parts.size >= 2) {
            val afterDoubleDots = parts[1]
            val colonIndex = afterDoubleDots.indexOf(":")
            if (colonIndex > 0) {
                return afterDoubleDots.substring(0, colonIndex)
            }
            return afterDoubleDots
        }
        return lineRef
    }
}
