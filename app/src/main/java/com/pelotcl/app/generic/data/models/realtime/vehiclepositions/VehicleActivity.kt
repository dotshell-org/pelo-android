package com.pelotcl.app.generic.data.models.realtime.vehiclepositions

import com.google.gson.annotations.SerializedName
import com.pelotcl.app.generic.data.models.realtime.vehiclepositions.RefValue

data class VehicleActivity(
    @SerializedName("ValidUntilTime")
    val validUntilTime: String?,
    @SerializedName("VehicleMonitoringRef")
    val vehicleMonitoringRef: RefValue?,
    @SerializedName("MonitoredVehicleJourney")
    val monitoredVehicleJourney: MonitoredVehicleJourney?
)