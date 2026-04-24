package com.pelotcl.app.specific.data.model

import com.google.gson.annotations.SerializedName

/**
 * Lyon-specific API response for traffic alerts
 */
data class LyonTrafficAlertsResponse(
    @SerializedName("success")
    val success: Boolean,

    @SerializedName("data")
    val alerts: List<LyonTrafficAlert>,

    @SerializedName("timestamp")
    val timestamp: String,

    @SerializedName("lastUpdated")
    val lastUpdated: String
)