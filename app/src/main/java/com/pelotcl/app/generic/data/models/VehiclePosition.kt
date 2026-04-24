package com.pelotcl.app.generic.data.models

/**
 * Response wrapper for the SIRI-lite vehicle monitoring API
 */
data class VehiclePositionsResponse(
    val success: Boolean,
    val data: SiriData?
)
