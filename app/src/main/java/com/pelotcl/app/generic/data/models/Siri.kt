package com.pelotcl.app.generic.data.models

import com.google.gson.annotations.SerializedName

data class Siri(
    @SerializedName("ServiceDelivery")
    val serviceDelivery: ServiceDelivery?
)