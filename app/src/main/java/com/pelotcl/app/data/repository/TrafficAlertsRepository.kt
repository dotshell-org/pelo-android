package com.pelotcl.app.data.repository

import android.content.Context
import android.util.Log
import com.pelotcl.app.data.api.RetrofitInstance
import com.pelotcl.app.data.model.TrafficAlert
import com.pelotcl.app.data.offline.OfflineRepository
import com.pelotcl.app.utils.withRetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Repository for managing traffic alerts data
 */
class TrafficAlertsRepository(context: Context) {

    private val api = RetrofitInstance.api
    private val cache = TrafficAlertsCache(context)
    private val offlineRepo = OfflineRepository(context)

    /**
     * Fetches all traffic alerts
     */
    suspend fun getTrafficAlerts(): Result<List<TrafficAlert>> {
        return withContext(Dispatchers.IO) {
            try {
                // Check cache first
                val cachedAlerts = cache.getTrafficAlerts()
                if (cachedAlerts != null && !isCacheExpired(cachedAlerts.second)) {
                    return@withContext Result.success(cachedAlerts.first)
                }

                // Fetch from API with retry on transient failures
                Log.d("TrafficAlertsRepository", "Fetching traffic alerts from API...")
                val response = withRetry(maxRetries = 2, initialDelayMs = 500) {
                    api.getTrafficAlerts()
                }

                if (response.success && response.alerts.isNotEmpty()) {
                    // Cache the response
                    cache.saveTrafficAlerts(response.alerts, response.timestamp)

                    // Also persist to offline storage so alerts stay fresh even without
                    // a manual offline data download
                    try {
                        offlineRepo.saveTrafficAlerts(response.alerts)
                    } catch (e: Exception) {
                        Log.w(
                            "TrafficAlertsRepository",
                            "Failed to persist alerts to offline storage",
                            e
                        )
                    }

                    Result.success(response.alerts)
                } else {
                    Result.success(emptyList())
                }
            } catch (e: Exception) {
                Log.e(
                    "TrafficAlertsRepository",
                    "Error fetching traffic alerts, trying fallbacks",
                    e
                )
                // Fallback 1: stale cache (any age)
                val staleAlerts = cache.getTrafficAlertsStale()
                if (!staleAlerts.isNullOrEmpty()) {
                    Log.d(
                        "TrafficAlertsRepository",
                        "Using stale cached alerts: ${staleAlerts.size}"
                    )
                    return@withContext Result.success(staleAlerts)
                }
                // Fallback 2: offline repository
                try {
                    val offlineAlerts = offlineRepo.loadTrafficAlerts()
                    if (!offlineAlerts.isNullOrEmpty()) {
                        Log.d(
                            "TrafficAlertsRepository",
                            "Using offline alerts: ${offlineAlerts.size}"
                        )
                        return@withContext Result.success(offlineAlerts)
                    }
                } catch (offlineEx: Exception) {
                    Log.w("TrafficAlertsRepository", "Offline alerts fallback failed", offlineEx)
                }
                Result.failure(e)
            }
        }
    }

    /**
     * Returns the timestamp (millis) of the last cached alerts update, or null.
     */
    fun getAlertsTimestampMillis(): Long? = cache.getTimestampMillis()

    /**
     * Checks if cache is expired (older than 5 minutes)
     */
    private fun isCacheExpired(timestamp: String): Boolean {
        try {
            // Parse the timestamp (ISO 8601 format)
            // Simple parsing - could be improved with proper date parsing
            val cacheTimeMillis = timestamp.toLongOrNull() ?: return true
            val currentTimeMillis = System.currentTimeMillis()

            val cacheAgeMinutes =
                TimeUnit.MILLISECONDS.toMinutes(currentTimeMillis - cacheTimeMillis)

            return cacheAgeMinutes > 5 // Cache expires after 5 minutes
        } catch (e: Exception) {
            return true // If we can't parse, consider cache expired
        }
    }

}
