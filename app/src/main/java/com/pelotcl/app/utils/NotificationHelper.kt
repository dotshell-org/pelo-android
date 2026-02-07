package com.pelotcl.app.utils

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.pelotcl.app.MainActivity
import com.pelotcl.app.PeloApplication
import com.pelotcl.app.R
import com.pelotcl.app.data.model.TrafficAlert

class NotificationHelper(private val context: Context) {
    
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    
    fun sendTrafficAlertNotification(lineName: String, alerts: List<TrafficAlert>) {
        // Check notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }
        
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            lineName.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val title = if (alerts.size == 1) {
            "Alerte trafic - Ligne $lineName"
        } else {
            "${alerts.size} alertes trafic - Ligne $lineName"
        }
        
        val content = if (alerts.size == 1) {
            alerts.first().title
        } else {
            alerts.joinToString(", ") { it.title }
        }
        
        val notification = NotificationCompat.Builder(context, PeloApplication.TRAFFIC_ALERTS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        
        // Use line name hash as notification ID to group by line
        notificationManager.notify(lineName.hashCode(), notification)
    }
}
