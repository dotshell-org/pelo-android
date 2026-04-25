package com.pelotcl.app.generic.data.repository

import android.util.Log
import com.pelotcl.app.generic.data.models.realtime.alerts.community.StopAlertsStatus
import com.pelotcl.app.generic.data.models.realtime.alerts.community.UserStopAlert
import com.pelotcl.app.generic.data.models.realtime.alerts.community.UserStopAlertsResponse
import com.pelotcl.app.specific.data.network.LyonTransportApi
import com.pelotcl.app.generic.utils.search.SearchUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for managing user stop alerts (karma-based alerts)
 * Handles fetching and caching of alerts to avoid problematic stops in route planning
 */
class UserStopAlertsRepository(
    private val api: LyonTransportApi
) {
    companion object {
        private const val TAG = "UserStopAlertsRepository"
        private const val API_STOPS_CHUNK_SIZE = 10
    }

    private fun hasKarmaAtOrAboveThreshold(status: StopAlertsStatus): Boolean {
        // Source of truth is backend bucketing.
        return status.karmaAtOrAboveThreshold.isNotEmpty()
    }

    /**
     * Fetch alerts for the given stops
     * @param stopIds List of stop IDs to check for alerts
     * @return Map of stopId to StopAlertsStatus
     */
    suspend fun getUserStopAlerts(stopIds: List<String>): UserStopAlertsResponse =
        withContext(Dispatchers.IO) {
            if (stopIds.isEmpty()) {
                return@withContext emptyMap()
            }

            val requestedStops = stopIds.distinct()
            Log.i(TAG, "Fetching user stop alerts for ${requestedStops.size} stops")

            val merged = linkedMapOf<String, StopAlertsStatus>()

            requestedStops.chunked(API_STOPS_CHUNK_SIZE).forEach { chunk ->
                try {
                    val response = api.getUserStopAlerts(chunk)
                    merged.putAll(response)
                } catch (chunkError: Exception) {
                    Log.w(
                        TAG,
                        "Chunk request failed for ${chunk.size} stops, retrying one by one: ${chunkError.message}"
                    )

                    // Isolate bad stop ids so one backend failure does not hide all alerts.
                    chunk.forEach { stopId ->
                        try {
                            val singleResponse = api.getUserStopAlerts(listOf(stopId))
                            merged.putAll(singleResponse)
                        } catch (singleError: Exception) {
                            Log.w(
                                TAG,
                                "Single stop alert request failed for '$stopId': ${singleError.message}"
                            )
                        }
                    }
                }
            }

            merged
        }

    /**
     * Get all problematic stop IDs (those with karma_at_or_above_threshold alerts)
     * @param stopIds List of stop IDs to check
     * @return Set of stop IDs that have problematic alerts
     */
    suspend fun getProblematicStops(stopIds: List<String>): Set<String> =
        withContext(Dispatchers.Default) {
            getProblematicAlertDetails(stopIds).keys
        }

    /**
     * Return problematic alerts mapped to caller stop names (not raw API keys).
     * This prevents false positives when names differ by accents/casing/punctuation.
     */
    suspend fun getProblematicAlertDetails(stopIds: List<String>): Map<String, List<UserStopAlert>> =
        withContext(Dispatchers.Default) {
            val alerts = getUserStopAlerts(stopIds)
            if (alerts.isEmpty()) return@withContext emptyMap()

            val problematicEntries = alerts
                .filter { (_, status) -> hasKarmaAtOrAboveThreshold(status) }

            Log.d(
                TAG,
                "Problematic API stops: ${problematicEntries.size}/${alerts.size} for ${stopIds.size} requested stops"
            )

            if (problematicEntries.isEmpty()) {
                return@withContext emptyMap()
            }

            // Build a normalized index, but keep ambiguity information to avoid false positives.
            val normalizedToApiStops = problematicEntries.keys
                .groupBy(SearchUtils::normalizeStopKey)

            val problematicByExact = problematicEntries
                .mapValues { (_, status) -> status.karmaAtOrAboveThreshold }

            val result = linkedMapOf<String, List<UserStopAlert>>()
            stopIds.forEach { callerStopName ->
                // 1) Prefer exact key matching to avoid accidental normalized collisions.
                val exact = problematicByExact[callerStopName]
                if (!exact.isNullOrEmpty()) {
                    result[callerStopName] = exact
                    return@forEach
                }

                // 2) Fallback to normalized key only when it maps to exactly one API stop.
                val normalized = SearchUtils.normalizeStopKey(callerStopName)
                val candidates = normalizedToApiStops[normalized].orEmpty()
                if (candidates.size == 1) {
                    val fallback = problematicByExact[candidates.first()]
                    if (!fallback.isNullOrEmpty()) {
                        result[callerStopName] = fallback
                    }
                }
            }

            Log.d(
                TAG,
                "Matched problematic caller stops: ${result.keys.joinToString()}"
            )

            result
        }
}
