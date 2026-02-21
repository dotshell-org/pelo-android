package com.pelotcl.app.widget

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
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
import com.pelotcl.app.utils.LineColorHelper

class PeloWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override val sizeMode = SizeMode.Single

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
                context, stopName, lineName, directionId, 4
            )
        } else {
            ScheduleWidgetHelper.getAllUpcomingDepartures(
                context, stopName, desserte, 5
            )
        }
    } else {
        emptyList()
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surface)
            .cornerRadius(16.dp)
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>())
    ) {
        // Header: stop name + refresh button
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stopName,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 14.sp,
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
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant)
                )
            }
        }

        if (lineName != null) {
            Text(
                text = "Ligne $lineName",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 11.sp
                )
            )
        }

        Spacer(modifier = GlanceModifier.height(8.dp))

        if (departures.isEmpty()) {
            Box(
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Aucun départ à venir",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                )
            }
        } else {
            departures.forEach { departure ->
                DepartureRow(departure, showLineBadge = lineName == null)
                Spacer(modifier = GlanceModifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun DepartureRow(departure: UpcomingDeparture, showLineBadge: Boolean) {
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
            val lineColor = LineColorHelper.getColorForLineString(departure.lineName)
            Box(
                modifier = GlanceModifier
                    .size(width = 32.dp, height = 20.dp)
                    .background(androidx.compose.ui.graphics.Color(lineColor))
                    .cornerRadius(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = departure.lineName,
                    style = TextStyle(
                        color = ColorProvider(
                            androidx.compose.ui.graphics.Color.White
                        ),
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
                color = GlanceTheme.colors.onSurface,
                fontSize = 12.sp
            ),
            modifier = GlanceModifier.defaultWeight(),
            maxLines = 1
        )

        Text(
            text = when {
                departure.minutesUntil == 0L -> "< 1 min"
                departure.minutesUntil >= 60 -> "${departure.minutesUntil / 60}h${(departure.minutesUntil % 60).toString().padStart(2, '0')}min"
                else -> "${departure.minutesUntil} min"
            },
            style = TextStyle(
                color = countdownColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }
}
