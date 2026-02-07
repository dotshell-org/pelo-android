package com.pelotcl.app.data.model

import androidx.compose.runtime.Immutable
import com.google.gson.annotations.SerializedName

/**
 * Response wrapper for the SIRI-lite vehicle monitoring API
 */
data class VehiclePositionsResponse(
    val success: Boolean,
    val data: SiriData?
)

data class SiriData(
    @SerializedName("Siri")
    val siri: Siri?
)

data class Siri(
    @SerializedName("ServiceDelivery")
    val serviceDelivery: ServiceDelivery?
)

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

data class RefValue(
    val value: String?
)

data class VehicleMonitoringDelivery(
    @SerializedName("VehicleActivity")
    val vehicleActivity: List<VehicleActivity>?
)

data class VehicleActivity(
    @SerializedName("ValidUntilTime")
    val validUntilTime: String?,
    @SerializedName("VehicleMonitoringRef")
    val vehicleMonitoringRef: RefValue?,
    @SerializedName("MonitoredVehicleJourney")
    val monitoredVehicleJourney: MonitoredVehicleJourney?
)

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

data class FramedVehicleJourneyRef(
    @SerializedName("DataFrameRef")
    val dataFrameRef: RefValue?,
    @SerializedName("DatedVehicleJourneyRef")
    val datedVehicleJourneyRef: String?
)

data class TranslatedString(
    val value: String?,
    val lang: String?
)

data class VehicleLocation(
    @SerializedName("Longitude")
    val longitude: Double?,
    @SerializedName("Latitude")
    val latitude: Double?
)

/**
 * Simplified vehicle position for UI display
 */
@Immutable
data class SimpleVehiclePosition(
    val vehicleId: String,
    val lineName: String,
    val latitude: Double,
    val longitude: Double,
    val bearing: Double?,
    val destinationName: String?,
    val direction: String?
)
