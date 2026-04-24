package com.pelotcl.app.generic.data.models.realtime.vehiclepositions

import com.google.gson.annotations.SerializedName

data class VehicleMonitoringDelivery(
    @SerializedName("VehicleActivity")
    val vehicleActivity: List<VehicleActivity>?
)