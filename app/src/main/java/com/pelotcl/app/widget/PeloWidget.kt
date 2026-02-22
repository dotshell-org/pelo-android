package com.pelotcl.app.widget

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.cornerRadius
import com.pelotcl.app.MainActivity
import com.pelotcl.app.R
import com.pelotcl.app.utils.BusIconHelper

class PeloWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                WidgetContent(context)
            }
        }
    }

    companion object {
        val PREF_STOP_NAME = stringPreferencesKey("widget_stop_name")
        val PREF_LINE_NAME = stringPreferencesKey("widget_line_name")
        val PREF_DIRECTION_ID = intPreferencesKey("widget_direction_id")
        val PREF_DESSERTE = stringPreferencesKey("widget_desserte")
        val PREF_WIDGET_STYLE = intPreferencesKey("widget_style")
        val PREF_REFRESH_INTERVAL = intPreferencesKey("widget_refresh_interval")
    }
}

@Composable
private fun WidgetContent(context: Context) {
    val prefs = currentState<Preferences>()
    val stopName = prefs[PeloWidget.PREF_STOP_NAME]
    val lineName = prefs[PeloWidget.PREF_LINE_NAME]
    val directionId = prefs[PeloWidget.PREF_DIRECTION_ID] ?: 0
    val desserte = prefs[PeloWidget.PREF_DESSERTE] ?: ""
    val widgetStyle = WidgetStyle.fromId(prefs[PeloWidget.PREF_WIDGET_STYLE]) ?: WidgetStyle.ALL_LINES_MINUTES

    // Request enough departures to fill tall widgets; RemoteViews will clip overflow.
    val size = LocalSize.current
    val headerAndPaddingDp = 52 // 20dp padding + 22dp header + 8dp spacer + 2dp safety margin
    val rowHeightDp = 25 // 16sp text height + 3dp spacer + 6dp margins (optimized)
    val calculatedDepartures = ((size.height.value - headerAndPaddingDp) / rowHeightDp).toInt()
    val maxDepartures = maxOf(calculatedDepartures, 12).coerceAtMost(30)

    if (stopName == null) {
        // Not configured
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .cornerRadius(16.dp)
                .clickable(actionStartActivity<MainActivity>()),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Configurez le widget",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 14.sp
                )
            )
        }
        return
    }

    val departures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        if (lineName != null) {
            ScheduleWidgetHelper.getUpcomingDepartures(
                context, stopName, lineName, directionId, maxDepartures
            )
        } else {
            ScheduleWidgetHelper.getAllUpcomingDepartures(
                context, stopName, desserte, maxDepartures
            )
        }
    } else {
        emptyList()
    }

    val pureBlack = ColorProvider(Color(0xFF000000))
    val primaryText = ColorProvider(Color(0xFFFFFFFF))
    val secondaryText = ColorProvider(Color(0xFFB3B3B3))

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(pureBlack)
            .cornerRadius(16.dp)
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .clickable(actionStartActivity<MainActivity>())
    ) {
        LazyColumn(
            modifier = GlanceModifier.fillMaxSize()
        ) {
            item {
                // Header: stop name + refresh button
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (lineName != null) {
                        val lineIconResId = BusIconHelper.getResourceIdForLine(context, lineName)
                        if (lineIconResId != 0) {
                            Image(
                                provider = ImageProvider(lineIconResId),
                                contentDescription = "Ligne $lineName",
                                modifier = GlanceModifier.size(width = 36.dp, height = 34.dp)
                            )
                            Spacer(modifier = GlanceModifier.width(8.dp))
                        }
                    }
                    Text(
                        text = stopName,
                        style = TextStyle(
                            color = primaryText,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = GlanceModifier.defaultWeight(),
                        maxLines = 1
                    )
                    Box(
                        modifier = GlanceModifier
                            .size(28.dp)
                            .clickable(actionRunCallback<RefreshWidgetAction>()),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_refresh),
                            contentDescription = "Rafraîchir",
                            modifier = GlanceModifier.size(18.dp),
                            colorFilter = ColorFilter.tint(secondaryText)
                        )
                    }
                }
                Spacer(modifier = GlanceModifier.height(10.dp))
            }
            if (departures.isEmpty()) {
                item {
                    Box(
                        modifier = GlanceModifier.fillMaxWidth().padding(bottom=6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Aucun départ à venir",
                            style = TextStyle(
                                color = secondaryText,
                                fontSize = 16.sp
                            )
                        )
                    }
                }
            } else {
                items(departures) { departure ->
                    DepartureRow(
                        context = context,
                        departure = departure,
                        showLineBadge = lineName == null,
                        timeDisplayMode = widgetStyle.timeDisplayMode
                    )
                    Spacer(modifier = GlanceModifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
private fun DepartureRow(
    context: Context,
    departure: UpcomingDeparture,
    showLineBadge: Boolean,
    timeDisplayMode: TimeDisplayMode
) {
    val countdownColor = when {
        departure.minutesUntil <= 2 -> ColorProvider(
            androidx.compose.ui.graphics.Color(0xFFEF4444)
        )
        departure.minutesUntil <= 5 -> ColorProvider(
            androidx.compose.ui.graphics.Color(0xFFF59E0B)
        )
        else -> ColorProvider(
            androidx.compose.ui.graphics.Color(0xFF22C55E)
        )
    }

    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showLineBadge) {
            val lineIconResId = BusIconHelper.getResourceIdForLine(context, departure.lineName)
            if (lineIconResId != 0) {
                Image(
                    provider = ImageProvider(lineIconResId),
                    contentDescription = "Ligne ${departure.lineName}",
                    modifier = GlanceModifier.size(width = 32.dp, height = 20.dp)
                )
            } else {
                Text(
                    text = departure.lineName,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
            Spacer(modifier = GlanceModifier.width(6.dp))
        }

        Text(
            text = departure.directionName,
            style = TextStyle(
                color = ColorProvider(Color(0xFFFFFFFF)),
                fontSize = 16.sp
            ),
            modifier = GlanceModifier.defaultWeight(),
            maxLines = 1
        )

        Text(
            text = when {
                timeDisplayMode == TimeDisplayMode.CLOCK -> formatDepartureTime(departure.time)
                departure.minutesUntil == 0L -> "< 1 min"
                departure.minutesUntil >= 60 -> "${departure.minutesUntil / 60}h${(departure.minutesUntil % 60).toString().padStart(2, '0')}min"
                else -> "${departure.minutesUntil} min"
            },
            style = TextStyle(
                color = countdownColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

private fun formatDepartureTime(rawTime: String): String {
    val cleanTime = if (rawTime.count { it == ':' } == 2) {
        rawTime.substringBeforeLast(":")
    } else {
        rawTime
    }
    val parts = cleanTime.split(":")
    if (parts.size < 2) return rawTime
    val hour = parts[0].toIntOrNull() ?: return rawTime
    val minute = parts[1].toIntOrNull() ?: return rawTime
    if (hour !in 0..23 || minute !in 0..59) return rawTime
    return "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
}
