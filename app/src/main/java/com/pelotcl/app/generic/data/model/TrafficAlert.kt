package com.pelotcl.app.generic.data.model

import androidx.compose.runtime.Immutable
import com.google.gson.annotations.SerializedName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a traffic alert for a line of transport
 * Generic model that can be used across different transport networks
 */
@Immutable
@Serializable
data class TrafficAlert(
    @SerializedName("cause")
    @SerialName("cause")
    val cause: String,

    @SerializedName("startDate")
    @SerialName("startDate")
    val startDate: String,

    @SerializedName("endDate")
    @SerialName("endDate")
    val endDate: String,

    @SerializedName("lastUpdate")
    @SerialName("lastUpdate")
    val lastUpdate: String,

    @SerializedName("lineCode")
    @SerialName("lineCode")
    val lineCode: String,

    @SerializedName("lineName")
    @SerialName("lineName")
    val lineName: String,

    @SerializedName("objectList")
    @SerialName("objectList")
    val objectList: String,

    @SerializedName("message")
    @SerialName("message")
    val message: String,

    @SerializedName("mode")
    @SerialName("mode")
    val mode: String,

    @SerializedName("alertNumber")
    @SerialName("alertNumber")
    val alertNumber: Int,

    @SerializedName("severityLevel")
    @SerialName("severityLevel")
    val severityLevel: Int,

    @SerializedName("title")
    @SerialName("title")
    val title: String,

    @SerializedName("alertType")
    @SerialName("alertType")
    val alertType: String,

    @SerializedName("objectType")
    @SerialName("objectType")
    val objectType: String,

    @SerializedName("severityType")
    @SerialName("severityType")
    val severityType: String
)

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

/**
 * Enumeration of alert severity types
 */
enum class AlertSeverity(val level: Int, val color: Long) {
    SIGNIFICANT_DELAYS(20, 0xFFFF5722), // Orange
    OTHER_EFFECT(30, 0xFF2196F3), // Blue
    INFORMATION(40, 0xFF4CAF50), // Green
    UNKNOWN(0, 0xFF9E9E9E); // Gray

    companion object {
        fun fromSeverityType(severityType: String, severityLevel: Int): AlertSeverity {
            return when (severityType) {
                "SIGNIFICANT_DELAYS" -> SIGNIFICANT_DELAYS
                "OTHER_EFFECT" -> OTHER_EFFECT
                "INFORMATION" -> INFORMATION
                else -> entries.firstOrNull { it.level == severityLevel } ?: UNKNOWN
            }
        }
    }
}