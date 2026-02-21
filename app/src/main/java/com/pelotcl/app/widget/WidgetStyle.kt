package com.pelotcl.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context

enum class TimeDisplayMode {
    MINUTES,
    CLOCK
}

enum class WidgetStyle(
    val id: Int,
    val requiresSpecificLine: Boolean,
    val timeDisplayMode: TimeDisplayMode
) {
    ALL_LINES_MINUTES(
        id = 1,
        requiresSpecificLine = false,
        timeDisplayMode = TimeDisplayMode.MINUTES
    ),
    ALL_LINES_CLOCK(
        id = 2,
        requiresSpecificLine = false,
        timeDisplayMode = TimeDisplayMode.CLOCK
    ),
    LINE_MINUTES(
        id = 3,
        requiresSpecificLine = true,
        timeDisplayMode = TimeDisplayMode.MINUTES
    ),
    LINE_CLOCK(
        id = 4,
        requiresSpecificLine = true,
        timeDisplayMode = TimeDisplayMode.CLOCK
    );

    val refreshIntervalMinutes: Int
        get() = if (timeDisplayMode == TimeDisplayMode.MINUTES) 1 else 5

    companion object {
        fun fromId(id: Int?): WidgetStyle? = entries.firstOrNull { it.id == id }

        fun fromProviderClassName(providerClassName: String?): WidgetStyle {
            return when (providerClassName) {
                "com.pelotcl.app.widget.PeloWidgetClockAllLinesReceiver" -> ALL_LINES_CLOCK
                "com.pelotcl.app.widget.PeloWidgetLineMinutesReceiver" -> LINE_MINUTES
                "com.pelotcl.app.widget.PeloWidgetLineClockReceiver" -> LINE_CLOCK
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
