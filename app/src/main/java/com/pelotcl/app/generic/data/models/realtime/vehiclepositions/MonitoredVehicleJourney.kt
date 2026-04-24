package com.pelotcl.app.generic.data.models.realtime.vehiclepositions

import com.google.gson.annotations.SerializedName
import com.pelotcl.app.generic.data.models.realtime.vehiclepositions.RefValue
import com.pelotcl.app.generic.data.models.TranslatedString

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