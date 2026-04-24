package com.pelotcl.app.generic.data.repository.itinerary.itinerary

import android.content.Context
import androidx.core.content.edit

/**
 * Repository for managing itinerary routing preferences using SharedPreferences.
 * Stores user preferences for route filtering (JD lines, RX line).
 */
class ItineraryPreferencesRepository(private val context: Context) {
    private val prefs by lazy {
        context.getSharedPreferences("pelo_itinerary_prefs", Context.MODE_PRIVATE)
    }

    private val keyEnableJDLines = "enable_jd_lines"
    private val keyEnableRXLine = "enable_rx_line"

    /**
     * Check if Junior Direct (JD) lines should be included in routing.
     * Default: true (enabled)
     */
    fun isJdLinesEnabled(): Boolean {
        if (!prefs.contains(keyEnableJDLines)) {
            prefs.edit { putBoolean(keyEnableJDLines, true) }
        }
        return prefs.getBoolean(keyEnableJDLines, true)
    }

    /**
     * Enable or disable Junior Direct (JD) lines in routing.
     */
    fun setJdLinesEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(keyEnableJDLines, enabled) }
    }

    /**
     * Check if RhôneExpress (RX) line should be included in routing.
     * Default: true (enabled)
     */
    fun isRxLineEnabled(): Boolean {
        if (!prefs.contains(keyEnableRXLine)) {
            prefs.edit { putBoolean(keyEnableRXLine, true) }
        }
        return prefs.getBoolean(keyEnableRXLine, true)
    }

    /**
     * Enable or disable RhôneExpress (RX) line in routing.
     */
    fun setRxLineEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(keyEnableRXLine, enabled) }
    }

}