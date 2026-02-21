package com.pelotcl.app.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
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
                val prefs = getAppWidgetState(
                    context,
                    PreferencesGlanceStateDefinition,
                    glanceId
                )
                val intervalMinutes = getRefreshIntervalMinutes(prefs)
                WidgetRefreshScheduler.scheduleNext(context, appWidgetId, intervalMinutes)
            } catch (_: Exception) {
                // Widget may have been removed
            } finally {
                pendingResult.finish()
            }
        }
    }
}

object WidgetRefreshScheduler {

    private fun getPendingIntent(context: Context, appWidgetId: Int): PendingIntent {
        val intent = Intent(context, WidgetAlarmReceiver::class.java).apply {
            action = "com.pelotcl.app.WIDGET_REFRESH_$appWidgetId"
            putExtra("appWidgetId", appWidgetId)
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
        val pendingIntent = getPendingIntent(context, appWidgetId)
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
        val pendingIntent = getPendingIntent(context, appWidgetId)
        alarmManager.cancel(pendingIntent)
    }

    /** Reschedule all active widgets (e.g. after reboot) */
    suspend fun rescheduleAll(context: Context) {
        val manager = GlanceAppWidgetManager(context)
        val glanceIds = manager.getGlanceIds(PeloWidget::class.java)
        glanceIds.forEach { glanceId ->
            try {
                val appWidgetId = manager.getAppWidgetId(glanceId)
                val prefs = getAppWidgetState(
                    context,
                    PreferencesGlanceStateDefinition,
                    glanceId
                )
                val interval = getRefreshIntervalMinutes(prefs)
                schedule(context, appWidgetId, interval)
            } catch (_: Exception) {
                // Skip if widget state can't be read
            }
        }
    }
}

private fun getRefreshIntervalMinutes(prefs: androidx.datastore.preferences.core.Preferences): Int {
    val widgetStyle = WidgetStyle.fromId(prefs[PeloWidget.PREF_WIDGET_STYLE])
    if (widgetStyle != null) {
        return widgetStyle.refreshIntervalMinutes
    }
    return prefs[PeloWidget.PREF_REFRESH_INTERVAL] ?: 5
}
