package com.pelotcl.app.generic.data.repository.online

import com.pelotcl.app.generic.data.model.SimpleVehiclePosition
import com.pelotcl.app.generic.data.network.VehiclePositionsService
import kotlinx.coroutines.flow.Flow

/**
 * Repository for fetching real-time vehicle positions
 * Uses VehiclePositionsService for city-specific implementations
 */
class VehiclePositionsRepository(
    private val vehiclePositionsService: VehiclePositionsService
) {

    /**
     * Streams all vehicle positions from the service.
     */
    fun streamAllVehiclePositions(): Flow<Result<List<SimpleVehiclePosition>>> {
        return vehiclePositionsService.streamAllVehiclePositions()
    }

    /**
     * Streams vehicle positions for a specific line
     */
    fun streamVehiclePositionsByLine(lineName: String): Flow<Result<List<SimpleVehiclePosition>>> {
        return vehiclePositionsService.streamVehiclePositionsByLine(lineName)
    }

    /**
     * Streams vehicle positions for strong lines only
     */
    fun streamStrongLinesVehiclePositions(): Flow<Result<List<SimpleVehiclePosition>>> {
        return vehiclePositionsService.streamStrongLinesVehiclePositions()
    }

}
