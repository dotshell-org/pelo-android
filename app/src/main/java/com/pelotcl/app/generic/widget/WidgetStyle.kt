package com.pelotcl.app.generic.widget

import android.appwidget.AppWidgetManager
import android.content.Context

enum class WidgetStyle(
    val id: Int,
    val timeDisplayMode: TimeDisplayMode
) {
    ALL_LINES_MINUTES(
        id = 1,
        timeDisplayMode = TimeDisplayMode.MINUTES
    ),
    ALL_LINES_CLOCK(
        id = 2,
        timeDisplayMode = TimeDisplayMode.CLOCK
    ),
    LINE_MINUTES(
        id = 3,
        timeDisplayMode = TimeDisplayMode.MINUTES
    ),
    LINE_CLOCK(
        id = 4,
        timeDisplayMode = TimeDisplayMode.CLOCK
    );

    val requiresSpecificLine: Boolean
        get() = when (this) {
            LINE_MINUTES, LINE_CLOCK -> true
            else -> false
        }
    val refreshIntervalMinutes: Int
        get() = if (timeDisplayMode == TimeDisplayMode.MINUTES) 1 else 5

    companion object {
        fun fromId(id: Int?): WidgetStyle? = entries.firstOrNull { it.id == id }

        fun fromProviderClassName(providerClassName: String?): WidgetStyle {
            return when (providerClassName) {
                "com.pelotcl.app.generic.widget.PeloWidgetClockAllLinesReceiver" -> ALL_LINES_CLOCK
                "com.pelotcl.app.generic.widget.PeloWidgetLineMinutesReceiver" -> LINE_MINUTES
                "com.pelotcl.app.generic.widget.PeloWidgetLineClockReceiver" -> LINE_CLOCK
                else -> ALL_LINES_MINUTES
            }
        }
    }
}

fun resolveWidgetStyle(context: Context, appWidgetId: Int): WidgetStyle {
    val providerClassName = AppWidgetManager.getInstance(context)
        .getAppWidgetInfo(appWidgetId)
        ?.provider
        ?.className

    return WidgetStyle.fromProviderClassName(providerClassName)
}
