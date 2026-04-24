package com.pelotcl.app.generic.data.models

import com.google.gson.annotations.SerializedName

data class MonitoredVehicleJourney(
    @SerializedName("LineRef")
    val lineRef: RefValue?,
    @SerializedName("DirectionRef")
    val directionRef: RefValue?,
    @SerializedName("FramedVehicleJourneyRef")
    val framedVehicleJourneyRef: FramedVehicleJourneyRef?,
    @SerializedName("DestinationRef")
    val destinationRef: RefValue?,
    @SerializedName("DestinationName")
    val destinationName: List<TranslatedString>?,
    @SerializedName("Bearing")
    val bearing: Double?,
    @SerializedName("VehicleLocation")
    val vehicleLocation: VehicleLocation?,
    @SerializedName("VehicleStatus")
    val vehicleStatus: String?
)