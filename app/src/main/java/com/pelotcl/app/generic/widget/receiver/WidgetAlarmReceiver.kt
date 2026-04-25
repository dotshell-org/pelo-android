package com.pelotcl.app.generic.widget.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.pelotcl.app.generic.widget.PeloWidget
import com.pelotcl.app.generic.widget.schedule.WidgetRefreshScheduler
import com.pelotcl.app.generic.widget.schedule.getRefreshIntervalMinutes
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

            } finally {
                pendingResult.finish()
            }
        }
    }
}
