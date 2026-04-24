package com.pelotcl.app.generic.data.cache

import com.pelotcl.app.generic.data.repository.itinerary.IntermediateStop
import kotlinx.serialization.Serializable

@Serializable
data class SerializableIntermediateStop(
    val stopName: String,
    val arrivalTime: Int,
    val lat: Double = 0.0,
    val lon: Double = 0.0
) {
    fun toIntermediateStop() = IntermediateStop(
        stopName = stopName,
        arrivalTime = arrivalTime,
        lat = lat,
        lon = lon
    )

    companion object {
        fun fromIntermediateStop(stop: IntermediateStop) = SerializableIntermediateStop(
            stopName = stop.stopName,
            arrivalTime = stop.arrivalTime,
            lat = stop.lat,
            lon = stop.lon
        )
    }
}