package com.pelotcl.app.generic.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pelotcl.app.generic.data.model.TrafficAlert
import com.pelotcl.app.generic.service.TransportServiceProvider
import com.pelotcl.app.generic.data.repository.online.TrafficAlertsRepository
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class TrafficAlertsWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "TrafficAlertsWorker"

    }

    private val trafficAlertsRepository = TrafficAlertsRepository(TransportServiceProvider.getTransportApi(), applicationContext)

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
            Log.d(TAG, "Fetched ${validAlerts.size} valid traffic alerts")
            // No notification logic, just fetch and filter
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

}
