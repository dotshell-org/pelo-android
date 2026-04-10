package com.pelotcl.app.generic.data.model

import androidx.compose.runtime.Immutable
import com.google.gson.annotations.SerializedName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a single user stop alert with karma status
 * Used for identifying problematic stops that should be avoided in route planning
 */
@Immutable
@Serializable
data class UserStopAlert(
    @SerializedName("id")
    @SerialName("id")
    val id: String,

    @SerializedName("stopId")
    @SerialName("stopId")
    val stopId: String,

    @SerializedName("type")
    @SerialName("type")
    val type: String, // e.g., "crowding", "incident", etc.

    @SerializedName("karma")
    @SerialName("karma")
    val karma: Int,

    @SerializedName("createdAt")
    @SerialName("createdAt")
    val createdAt: String
)

/**
 * Represents alerts grouped by karma threshold status for a stop
 */
@Immutable
data class StopAlertsStatus(
    @SerializedName("karma_below_threshold")
    val karmaBelowThreshold: List<UserStopAlert> = emptyList(),

    @SerializedName("karma_at_or_above_threshold")
    val karmaAtOrAboveThreshold: List<UserStopAlert> = emptyList()
) {
    /**
     * Returns true if this stop has alerts at or above the threshold (problematic stop)
     */
    fun hasProblematicAlerts(): Boolean = karmaAtOrAboveThreshold.isNotEmpty()

    /**
     * Returns all alerts (both below and above threshold)
     */
    fun allAlerts(): List<UserStopAlert> = karmaBelowThreshold + karmaAtOrAboveThreshold
}

/**
 * API response containing all stop alerts
 * Structure: { "stopId1": { "karma_below_threshold": [...], "karma_at_or_above_threshold": [...] }, ... }
 */
typealias UserStopAlertsResponse = Map<String, StopAlertsStatus>
