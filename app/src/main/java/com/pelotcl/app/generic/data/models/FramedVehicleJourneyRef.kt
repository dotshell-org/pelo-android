package com.pelotcl.app.generic.data.models

import com.google.gson.annotations.SerializedName

data class FramedVehicleJourneyRef(
    @SerializedName("DataFrameRef")
    val dataFrameRef: RefValue?,
    @SerializedName("DatedVehicleJourneyRef")
    val datedVehicleJourneyRef: String?
)