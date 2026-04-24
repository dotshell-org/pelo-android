package com.pelotcl.app.generic.data.models.realtime.vehiclepositions

import com.google.gson.annotations.SerializedName
import com.pelotcl.app.generic.data.models.realtime.vehiclepositions.ServiceDelivery

data class Siri(
    @SerializedName("ServiceDelivery")
    val serviceDelivery: ServiceDelivery?
)