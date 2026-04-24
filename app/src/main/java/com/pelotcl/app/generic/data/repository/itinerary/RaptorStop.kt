package com.pelotcl.app.generic.data.repository.itinerary

/**
 * Data class representing a stop from Raptor
 */
data class RaptorStop(
    val id: Int,
    val name: String,
    val lat: Double = 0.0,
    val lon: Double = 0.0
)