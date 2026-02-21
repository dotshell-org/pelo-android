package com.pelotcl.app.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.glance.appwidget.GlanceAppWidgetManager
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class WidgetAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appWidgetId = intent.getIntExtra("appWidgetId", -1)
        if (appWidgetId == -1) return

        val pendingResult = goAsync()
        MainScope().launch {
            try {
                val manager = GlanceAppWidgetManager(context)
                val glanceId = manager.getGlanceIdBy(appWidgetId)
                PeloWidget().update(context, glanceId)
            } catch (_: Exception) {
                // Widget may have been removed
            } finally {
                // Reschedule the next alarm
                val intervalMinutes = intent.getIntExtra("intervalMinutes", 15)
                WidgetRefreshScheduler.scheduleNext(context, appWidgetId, intervalMinutes)
                pendingResult.finish()
            }
        }
    }
}

object WidgetRefreshScheduler {

    private fun getPendingIntent(context: Context, appWidgetId: Int, intervalMinutes: Int): PendingIntent {
        val intent = Intent(context, WidgetAlarmReceiver::class.java).apply {
            action = "com.pelotcl.app.WIDGET_REFRESH_$appWidgetId"
            putExtra("appWidgetId", appWidgetId)
            putExtra("intervalMinutes", intervalMinutes)
        }
        return PendingIntent.getBroadcast(
            context,
            appWidgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun schedule(context: Context, appWidgetId: Int, intervalMinutes: Int) {
        scheduleNext(context, appWidgetId, intervalMinutes)
    }

    fun scheduleNext(context: Context, appWidgetId: Int, intervalMinutes: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = getPendingIntent(context, appWidgetId, intervalMinutes)
        val triggerAt = SystemClock.elapsedRealtime() + intervalMinutes * 60_000L

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAt,
                pendingIntent
            )
        } else {
            alarmManager.setExact(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAt,
                pendingIntent
            )
        }
    }

    fun cancel(context: Context, appWidgetId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = getPendingIntent(context, appWidgetId, 0)
        alarmManager.cancel(pendingIntent)
    }

    /** Reschedule all active widgets (e.g. after reboot) */
    suspend fun rescheduleAll(context: Context) {
        val manager = GlanceAppWidgetManager(context)
        val glanceIds = manager.getGlanceIds(PeloWidget::class.java)
        glanceIds.forEach { glanceId ->
            try {
                val appWidgetId = manager.getAppWidgetId(glanceId)
                val prefs = androidx.glance.appwidget.state.getAppWidgetState(
                    context,
                    androidx.glance.state.PreferencesGlanceStateDefinition,
                    glanceId
                )
                val interval = prefs[PeloWidget.PREF_REFRESH_INTERVAL] ?: 15
                schedule(context, appWidgetId, interval)
            } catch (_: Exception) {
                // Skip if widget state can't be read
            }
        }
    }
}
