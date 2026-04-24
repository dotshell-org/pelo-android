package com.pelotcl.app.generic.data.models

import com.google.gson.annotations.SerializedName

/**
 * Represents the API response for traffic alerts
 */
data class TrafficAlertsResponse(
    @SerializedName("success")
    val success: Boolean,

    @SerializedName("data")
    val alerts: List<TrafficAlert>,

    @SerializedName("timestamp")
    val timestamp: String,

    @SerializedName("lastUpdated")
    val lastUpdated: String
)