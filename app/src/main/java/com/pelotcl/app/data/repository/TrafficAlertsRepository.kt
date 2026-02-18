package com.pelotcl.app.data.repository

import android.content.Context
import android.util.Log
import com.pelotcl.app.data.api.RetrofitInstance
import com.pelotcl.app.data.model.AlertSeverity
import com.pelotcl.app.data.model.TrafficAlert
import com.pelotcl.app.data.model.TrafficAlertsResponse
import com.pelotcl.app.data.model.TrafficStatusResponse
import com.pelotcl.app.data.offline.OfflineRepository
import com.pelotcl.app.utils.withRetry
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Repository for managing traffic alerts data
 */
class TrafficAlertsRepository(private val context: Context) {

    private val api = RetrofitInstance.api
    private val cache = TrafficAlertsCache(context)
    private val offlineRepo = OfflineRepository.getInstance(context)
    
    /**
     * Fetches traffic status (alert count and last update)
     */
    suspend fun getTrafficStatus(): Result<TrafficStatusResponse> {
        return withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // Check cache first
                val cachedStatus = cache.getTrafficStatus()
                if (cachedStatus != null && !isCacheExpired(cachedStatus.timestamp)) {
                    return@withContext Result.success(cachedStatus)
                }
                
                // Fetch from API with retry on transient failures
                val response = withRetry(maxRetries = 2, initialDelayMs = 500) {
                    api.getTrafficStatus()
                }

                // Cache the response
                cache.saveTrafficStatus(response)

                Result.success(response)
            } catch (e: Exception) {
                Log.e("TrafficAlertsRepository", "Error fetching traffic status", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Fetches all traffic alerts
     */
    suspend fun getTrafficAlerts(): Result<List<TrafficAlert>> {
        return withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // Check cache first
                val cachedAlerts = cache.getTrafficAlerts()
                if (cachedAlerts != null && !isCacheExpired(cachedAlerts.second)) {
                    return@withContext Result.success(cachedAlerts.first)
                }
                
                // Fetch from API with retry on transient failures
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
                        Log.w("TrafficAlertsRepository", "Failed to persist alerts to offline storage", e)
                    }

                    Result.success(response.alerts)
                } else {
                    Result.success(emptyList())
                }
            } catch (e: Exception) {
                Log.e("TrafficAlertsRepository", "Error fetching traffic alerts, trying fallbacks", e)
                // Fallback 1: stale cache (any age)
                val staleAlerts = cache.getTrafficAlertsStale()
                if (!staleAlerts.isNullOrEmpty()) {
                    Log.d("TrafficAlertsRepository", "Using stale cached alerts: ${staleAlerts.size}")
                    return@withContext Result.success(staleAlerts)
                }
                // Fallback 2: offline repository
                try {
                    val offlineAlerts = offlineRepo.loadTrafficAlerts()
                    if (!offlineAlerts.isNullOrEmpty()) {
                        Log.d("TrafficAlertsRepository", "Using offline alerts: ${offlineAlerts.size}")
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
     * Gets alerts for a specific line
     */
    suspend fun getAlertsForLine(lineCode: String): Result<List<TrafficAlert>> {
        return withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val allAlertsResult = getTrafficAlerts()
                
                if (allAlertsResult.isSuccess) {
                    val allAlerts = allAlertsResult.getOrThrow()
                    val lineAlerts = allAlerts.filter { alert ->
                        alert.lineCode.equals(lineCode, ignoreCase = true) ||
                        alert.lineName.equals(lineCode, ignoreCase = true)
                    }
                    Result.success(lineAlerts)
                } else {
                    Result.failure(allAlertsResult.exceptionOrNull() ?: Exception("Failed to get alerts"))
                }
            } catch (e: Exception) {
                Log.e("TrafficAlertsRepository", "Error getting alerts for line $lineCode", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Gets the most severe alert for a specific line
     */
    suspend fun getMostSevereAlertForLine(lineCode: String): Result<TrafficAlert?> {
        return withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val alertsResult = getAlertsForLine(lineCode)
                
                if (alertsResult.isSuccess) {
                    val alerts = alertsResult.getOrThrow()
                    
                    if (alerts.isEmpty()) {
                        Result.success(null)
                    } else {
                        // Find the alert with the highest severity (lowest severityLevel value)
                        val mostSevere = alerts.minByOrNull { it.severityLevel }
                        Result.success(mostSevere)
                    }
                } else {
                    Result.failure(alertsResult.exceptionOrNull() ?: Exception("Failed to get alerts"))
                }
            } catch (e: Exception) {
                Log.e("TrafficAlertsRepository", "Error getting most severe alert for line $lineCode", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Gets the severity color for a line (or null if no alerts)
     */
    suspend fun getAlertSeverityForLine(lineCode: String): Result<AlertSeverity?> {
        return withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val alertResult = getMostSevereAlertForLine(lineCode)
                
                if (alertResult.isSuccess) {
                    val alert = alertResult.getOrThrow()
                    
                    if (alert != null) {
                        val severity = AlertSeverity.fromSeverityType(alert.severityType, alert.severityLevel)
                        Result.success(severity)
                    } else {
                        Result.success(null)
                    }
                } else {
                    Result.failure(alertResult.exceptionOrNull() ?: Exception("Failed to get alert severity"))
                }
            } catch (e: Exception) {
                Log.e("TrafficAlertsRepository", "Error getting alert severity for line $lineCode", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Refreshes the traffic alerts cache
     */
    suspend fun refreshTrafficAlerts(): Result<Boolean> {
        return withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // Clear old cache
                cache.clearCache()
                
                // Fetch fresh data
                val statusResult = getTrafficStatus()
                val alertsResult = getTrafficAlerts()
                
                if (statusResult.isSuccess && alertsResult.isSuccess) {
                    Result.success(true)
                } else {
                    Result.failure(Exception("Failed to refresh traffic alerts"))
                }
            } catch (e: Exception) {
                Log.e("TrafficAlertsRepository", "Error refreshing traffic alerts", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Checks if cache is expired (older than 5 minutes)
     */
    private fun isCacheExpired(timestamp: String): Boolean {
        try {
            // Parse the timestamp (ISO 8601 format)
            // Simple parsing - could be improved with proper date parsing
            val cacheTimeMillis = timestamp.toLongOrNull() ?: return true
            val currentTimeMillis = System.currentTimeMillis()
            
            val cacheAgeMinutes = TimeUnit.MILLISECONDS.toMinutes(currentTimeMillis - cacheTimeMillis)
            
            return cacheAgeMinutes > 5 // Cache expires after 5 minutes
        } catch (e: Exception) {
            return true // If we can't parse, consider cache expired
        }
    }
}