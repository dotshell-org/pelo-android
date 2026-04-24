package com.pelotcl.app.generic.data.models.realtime.vehiclepositions

import com.google.gson.annotations.SerializedName
import com.pelotcl.app.generic.data.models.realtime.vehiclepositions.RefValue

data class FramedVehicleJourneyRef(
    @SerializedName("DataFrameRef")
    val dataFrameRef: RefValue?,
    @SerializedName("DatedVehicleJourneyRef")
    val datedVehicleJourneyRef: String?
)