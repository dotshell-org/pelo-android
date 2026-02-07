package com.pelotcl.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentCallbacks2
import android.os.Build
import android.util.Log
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.pelotcl.app.data.cache.JourneyCache
import com.pelotcl.app.data.gtfs.SchedulesRepository
import com.pelotcl.app.utils.BusIconHelper
import com.pelotcl.app.worker.TrafficAlertsWorker
import java.util.concurrent.TimeUnit

class PeloApplication : Application(), Configuration.Provider {

    companion object {
        private const val TAG = "PeloApplication"
        const val TRAFFIC_ALERTS_CHANNEL_ID = "traffic_alerts"
        const val TRAFFIC_ALERTS_WORK_NAME = "traffic_alerts_periodic"
    }

    // On-demand WorkManager initialization (replaces automatic ContentProvider init)
    // This defers SQLite init until WorkManager is first accessed, saving ~50-100ms on cold start
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "PeloApplication onCreate()")
        createNotificationChannel()
        scheduleTrafficAlertsWork()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                TRAFFIC_ALERTS_CHANNEL_ID,
                "Alertes trafic",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications pour les nouvelles alertes de trafic sur vos lignes favorites et les lignes fortes"
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created")
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE) {
            Log.d(TAG, "onTrimMemory level=$level, trimming caches")
            SchedulesRepository.trimCaches(level)
            BusIconHelper.trimCache(level)
            try {
                JourneyCache.getInstance(this).trimMemory(level)
            } catch (_: Exception) {
                // JourneyCache may not be initialized yet
            }
        }
    }

    private fun scheduleTrafficAlertsWork() {
        // Schedule periodic work (minimum 15 minutes)
        val workRequest = PeriodicWorkRequestBuilder<TrafficAlertsWorker>(
            30, TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            TRAFFIC_ALERTS_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
        Log.d(TAG, "Periodic work scheduled (every 30 minutes)")
    }
}
