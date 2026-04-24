package com.pelotcl.app.generic.data.models.realtime.vehiclepositions

import com.google.gson.annotations.SerializedName

data class VehicleLocation(
    @SerializedName("Longitude")
    val longitude: Double?,
    @SerializedName("Latitude")
    val latitude: Double?
)