package com.pelotcl.app.generic.utils

import androidx.compose.ui.graphics.Color
import com.pelotcl.app.generic.ui.theme.AccentColor
import com.pelotcl.app.generic.ui.theme.Green500
import com.pelotcl.app.generic.ui.theme.Orange500
import java.util.Calendar

class DepartureManager {
    /**
     * Bottom sheet affichant les informations d'une station
     * (nom de la station et toutes les lignes qui la desservent)
     */
    fun parseDepartureToMinutes(rawTime: String): Int? {
        val clean = if (rawTime.count { it == ':' } >= 2) rawTime.substringBeforeLast(":") else rawTime
        val parts = clean.split(":")
        if (parts.size < 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        if (minute !in 0..59) return null
        return (hour * 60) + minute
    }

    fun formatRelativeDeparture(departureTime: String): String? {
        val now = Calendar.getInstance()
        val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val departureMinutes = parseDepartureToMinutes(departureTime) ?: return null
        val diff = departureMinutes - nowMinutes

        if (diff < 0) return null
        if (diff == 0) return "< 1 min"
        if (diff < 60) return "dans ${diff}min"

        val hours = diff / 60
        val minutes = diff % 60
        return "dans ${hours}h${minutes.toString().padStart(2, '0')}min"
    }

    fun getDepartureColor(departureTime: String): Color {
        val now = Calendar.getInstance()
        val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val departureMinutes = parseDepartureToMinutes(
            departureTime
        ) ?: return Green500
        val diff = departureMinutes - nowMinutes

        if (diff < 0) return Green500

        return when (diff) {
            in 0..1 -> AccentColor
            in 2..14 -> Orange500
            else -> Green500
        }
    }

    fun minutesUntilDeparture(rawTime: String): Int {
        val now = Calendar.getInstance()
        val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val departureMinutes = parseDepartureToMinutes(rawTime)
            ?: return Int.MAX_VALUE
        return if (departureMinutes >= nowMinutes) {
            departureMinutes - nowMinutes
        } else {
            // Treat past times as next-day departures to keep ordering stable.
            (24 * 60 - nowMinutes) + departureMinutes
        }
    }
}