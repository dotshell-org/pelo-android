package com.pelotcl.app.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pelotcl.app.data.model.TrafficAlert
import com.pelotcl.app.data.repository.FavoritesRepository
import com.pelotcl.app.data.repository.TrafficAlertsRepository
import com.pelotcl.app.utils.NotificationHelper
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class TrafficAlertsWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    companion object {
        private const val TAG = "TrafficAlertsWorker"
        private const val PREFS_NAME = "traffic_alerts_worker"
        private const val KEY_NOTIFIED_ALERTS = "notified_alert_ids"
        
        // Major lines that should always trigger notifications
        private val MAJOR_LINES = setOf(
            // Metro
            "A", "B", "C", "D",
            // Tram
            "T1", "T2", "T3", "T4", "T5", "T6", "T7",
            // Funicular
            "F1", "F2",
            // Navigone
            "NAVI1",
            // Trambus
            "TB11", "TB12"
        )
    }
    
    private val trafficAlertsRepository = TrafficAlertsRepository(applicationContext)
    private val favoritesRepository = FavoritesRepository(applicationContext)
    private val notificationHelper = NotificationHelper(applicationContext)
    private val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "Starting traffic alerts check...")
            
            val alertsResult = trafficAlertsRepository.getTrafficAlerts()
            
            if (alertsResult.isFailure) {
                Log.e(TAG, "Failed to fetch alerts: ${alertsResult.exceptionOrNull()?.message}")
                return Result.retry()
            }
            
            val allAlerts = alertsResult.getOrThrow()
            val validAlerts = filterValidAlerts(allAlerts)
            val favoriteLines = favoritesRepository.getFavorites().map { it.uppercase() }.toSet()
            
            // Get previously notified alert IDs
            val notifiedAlertIds = getNotifiedAlertIds()
            
            // Find new alerts for relevant lines
            val newRelevantAlerts = validAlerts.filter { alert ->
                val alertId = generateAlertId(alert)
                val lineName = alert.lineName.uppercase()
                val lineCode = alert.lineCode.uppercase()
                
                // Check if alert is not already notified
                val isNew = !notifiedAlertIds.contains(alertId)
                
                // Check if it's a major line or favorite
                val isMajorLine = MAJOR_LINES.any { major ->
                    lineName.equals(major, ignoreCase = true) || 
                    lineCode.equals(major, ignoreCase = true)
                }
                val isFavoriteLine = favoriteLines.any { fav ->
                    lineName.equals(fav, ignoreCase = true) || 
                    lineCode.equals(fav, ignoreCase = true)
                }
                
                isNew && (isMajorLine || isFavoriteLine)
            }
            
            Log.d(TAG, "Found ${newRelevantAlerts.size} new relevant alerts")
            
            // Send notifications for new alerts
            if (newRelevantAlerts.isNotEmpty()) {
                // Group alerts by line for cleaner notifications
                val alertsByLine = newRelevantAlerts.groupBy { it.lineName.uppercase() }
                
                alertsByLine.forEach { (lineName, alerts) ->
                    notificationHelper.sendTrafficAlertNotification(lineName, alerts)
                }
                
                // Save notified alert IDs
                val newNotifiedIds = notifiedAlertIds + newRelevantAlerts.map { generateAlertId(it) }
                saveNotifiedAlertIds(newNotifiedIds)
            }
            
            // Clean up old notified IDs (keep only IDs of currently valid alerts)
            cleanupOldNotifiedIds(validAlerts)
            
            Log.d(TAG, "Traffic alerts check completed successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking traffic alerts", e)
            Result.retry()
        }
    }
    
    private fun filterValidAlerts(alerts: List<TrafficAlert>): List<TrafficAlert> {
        val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val now = LocalDateTime.now()
        
        return alerts.filter { alert ->
            try {
                val endDate = LocalDateTime.parse(alert.endDate, dateFormatter)
                endDate.isAfter(now)
            } catch (e: Exception) {
                true // Keep if we can't parse
            }
        }
    }
    
    private fun generateAlertId(alert: TrafficAlert): String {
        // Create a unique ID based on alert properties
        return "${alert.alertNumber}_${alert.lineCode}_${alert.startDate}"
    }
    
    private fun getNotifiedAlertIds(): Set<String> {
        return prefs.getStringSet(KEY_NOTIFIED_ALERTS, emptySet()) ?: emptySet()
    }
    
    private fun saveNotifiedAlertIds(ids: Set<String>) {
        prefs.edit().putStringSet(KEY_NOTIFIED_ALERTS, ids).apply()
    }
    
    private fun cleanupOldNotifiedIds(currentValidAlerts: List<TrafficAlert>) {
        val currentAlertIds = currentValidAlerts.map { generateAlertId(it) }.toSet()
        val notifiedIds = getNotifiedAlertIds()
        
        // Keep only IDs that still exist in current alerts
        val cleanedIds = notifiedIds.intersect(currentAlertIds)
        saveNotifiedAlertIds(cleanedIds)
    }
}
