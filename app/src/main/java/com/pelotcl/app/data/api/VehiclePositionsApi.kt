package com.pelotcl.app.data.api

import com.pelotcl.app.data.model.VehiclePositionsResponse
import retrofit2.http.GET

/**
 * Retrofit interface for the SIRI-lite vehicle monitoring API (dotshell.eu)
 */
interface VehiclePositionsApi {
    
    /**
     * Get real-time vehicle positions from the SIRI-lite API
     */
    @GET("pelo/v1/vehicle-monitoring/positions")
    suspend fun getVehiclePositions(): VehiclePositionsResponse
}
