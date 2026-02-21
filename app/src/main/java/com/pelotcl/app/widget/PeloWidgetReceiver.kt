package com.pelotcl.app.widget

import androidx.glance.appwidget.GlanceAppWidgetReceiver

class PeloWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = PeloWidget()
}
