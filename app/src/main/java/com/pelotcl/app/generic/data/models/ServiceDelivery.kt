package com.pelotcl.app.generic.data.models

import com.google.gson.annotations.SerializedName

data class ServiceDelivery(
    @SerializedName("ResponseTimestamp")
    val responseTimestamp: String?,
    @SerializedName("ProducerRef")
    val producerRef: RefValue?,
    @SerializedName("ResponseMessageIdentifier")
    val responseMessageIdentifier: RefValue?,
    @SerializedName("MoreData")
    val moreData: Boolean?,
    @SerializedName("VehicleMonitoringDelivery")
    val vehicleMonitoringDelivery: List<VehicleMonitoringDelivery>?
)