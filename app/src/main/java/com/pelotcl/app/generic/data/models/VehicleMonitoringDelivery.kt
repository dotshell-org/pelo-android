package com.pelotcl.app.generic.data.models

import com.google.gson.annotations.SerializedName

data class VehicleMonitoringDelivery(
    @SerializedName("VehicleActivity")
    val vehicleActivity: List<VehicleActivity>?
)