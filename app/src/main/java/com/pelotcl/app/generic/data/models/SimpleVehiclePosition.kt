package com.pelotcl.app.generic.data.models

import androidx.compose.runtime.Immutable

/**
 * Simplified vehicle position for UI display
 */
@Immutable
data class SimpleVehiclePosition(
    val vehicleId: String,
    val lineName: String,
    val latitude: Double,
    val longitude: Double,
    val bearing: Double?,
    val destinationName: String?,
    val direction: String?
)