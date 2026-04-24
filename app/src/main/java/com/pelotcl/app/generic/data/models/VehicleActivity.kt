package com.pelotcl.app.generic.data.models

import com.google.gson.annotations.SerializedName

data class VehicleActivity(
    @SerializedName("ValidUntilTime")
    val validUntilTime: String?,
    @SerializedName("VehicleMonitoringRef")
    val vehicleMonitoringRef: RefValue?,
    @SerializedName("MonitoredVehicleJourney")
    val monitoredVehicleJourney: MonitoredVehicleJourney?
)