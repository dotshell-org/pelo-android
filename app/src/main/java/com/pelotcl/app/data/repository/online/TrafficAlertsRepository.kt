package com.pelotcl.app.data.repository.online

import android.content.Context
import android.util.Log
import com.pelotcl.app.core.data.network.TransportApi
import com.pelotcl.app.core.data.cache.TrafficAlertsCache
import com.pelotcl.app.core.data.model.TrafficAlert
import com.pelotcl.app.core.data.offline.OfflineRepository
import com.pelotcl.app.utils.withRetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Repository for managing traffic alerts data
 * Uses TransportServiceProvider for dependency access
 */
class TrafficAlertsRepository(
    private val transportApi: TransportApi,
    private val context: Context
) {

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
                if (cachedAlerts != null && !isCacheExpired(cachedAlerts.second.toLongOrNull() ?: System.currentTimeMillis())) {
                    return@withContext Result.success(cachedAlerts.first)
                }

                // Fetch from API with retry on transient failures
                Log.d("TrafficAlertsRepository", "Fetching traffic alerts from API...")
                val response = withRetry(maxRetries = 2, initialDelayMs = 500) {
                    transportApi.getTrafficAlerts()
                }

                if (response.success && response.alerts.isNotEmpty()) {
                    // Cache the response
                    val timestamp = try {
                        response.timestamp.toLong()
                    } catch (e: Exception) {
                        System.currentTimeMillis()
                    }
                    cache.saveTrafficAlerts(response.alerts, timestamp.toString())

                    // Also persist to offline storage so alerts stay fresh even without
                    // a manual offline data download
                    try {
                        offlineRepo.saveTrafficAlerts(response.alerts)
                    } catch (e: Exception) {
                        Log.w(
                            "TrafficAlertsRepository",
                            "Failed to save traffic alerts to offline storage: ${e.message}"
                        )
                    }

                    Result.success(response.alerts)
                } else {
                    // Fallback to offline data if API returns empty/no success
                    val offlineAlerts = offlineRepo.loadTrafficAlerts()
                    if (offlineAlerts != null && offlineAlerts.isNotEmpty()) {
                        Result.success(offlineAlerts)
                    } else {
                        Result.success(emptyList())
                    }
                }
            } catch (e: Exception) {
                Log.e("TrafficAlertsRepository", "Failed to fetch traffic alerts", e)

                // Fallback to offline data on error
                val offlineAlerts = offlineRepo.loadTrafficAlerts()
                if (offlineAlerts != null && offlineAlerts.isNotEmpty()) {
                    Result.success(offlineAlerts)
                } else {
                    Result.failure(e)
                }
            }
        }
    }

    private fun isCacheExpired(timestamp: Long): Boolean {
        val now = System.currentTimeMillis()
        val cacheDuration = TimeUnit.MINUTES.toMillis(15) // 15 minutes cache
        return now - timestamp > cacheDuration
    }
}
