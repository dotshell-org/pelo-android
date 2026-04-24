package com.pelotcl.app.generic.data.models

import androidx.compose.runtime.Immutable
import com.google.gson.annotations.SerializedName

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