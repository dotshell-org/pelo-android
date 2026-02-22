package com.pelotcl.app.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

abstract class BasePeloWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = PeloWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        // Reschedule all widget workers (e.g. after reboot)
        MainScope().launch {
            WidgetRefreshScheduler.rescheduleAll(context)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        // Cancel workers for deleted widgets
        appWidgetIds.forEach { id ->
            WidgetRefreshScheduler.cancel(context, id)
        }
    }
}

class PeloWidgetReceiver : BasePeloWidgetReceiver()

class PeloWidgetClockAllLinesReceiver : BasePeloWidgetReceiver()

class PeloWidgetLineMinutesReceiver : BasePeloWidgetReceiver()

class PeloWidgetLineClockReceiver : BasePeloWidgetReceiver()
