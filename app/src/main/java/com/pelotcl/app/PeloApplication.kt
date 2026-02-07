package com.pelotcl.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.pelotcl.app.worker.TrafficAlertsWorker
import java.util.concurrent.TimeUnit

class PeloApplication : Application() {
    
    companion object {
        const val TRAFFIC_ALERTS_CHANNEL_ID = "traffic_alerts"
        const val TRAFFIC_ALERTS_WORK_NAME = "traffic_alerts_periodic"
    }
    
    override fun onCreate() {
        super.onCreate()
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
        }
    }
    
    private fun scheduleTrafficAlertsWork() {
        val workRequest = PeriodicWorkRequestBuilder<TrafficAlertsWorker>(
            30, TimeUnit.MINUTES
        ).build()
        
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            TRAFFIC_ALERTS_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}
