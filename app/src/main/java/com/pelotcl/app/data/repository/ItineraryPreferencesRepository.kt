package com.pelotcl.app.data.repository

import android.content.Context

/**
 * Repository for managing itinerary routing preferences using SharedPreferences.
 * Stores user preferences for route filtering (JD lines, RX line).
 */
class ItineraryPreferencesRepository(private val context: Context) {
    private val prefs by lazy {
        context.getSharedPreferences("pelo_itinerary_prefs", Context.MODE_PRIVATE)
    }

    private val KEY_ENABLE_JD_LINES = "enable_jd_lines"
    private val KEY_ENABLE_RX_LINE = "enable_rx_line"

    /**
     * Check if Junior Direct (JD) lines should be included in routing.
     * Default: true (enabled)
     */
    fun isJdLinesEnabled(): Boolean {
        if (!prefs.contains(KEY_ENABLE_JD_LINES)) {
            prefs.edit().putBoolean(KEY_ENABLE_JD_LINES, true).apply()
        }
        return prefs.getBoolean(KEY_ENABLE_JD_LINES, true)
    }

    /**
     * Enable or disable Junior Direct (JD) lines in routing.
     */
    fun setJdLinesEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLE_JD_LINES, enabled).apply()
    }

    /**
     * Check if RhôneExpress (RX) line should be included in routing.
     * Default: true (enabled)
     */
    fun isRxLineEnabled(): Boolean {
        if (!prefs.contains(KEY_ENABLE_RX_LINE)) {
            prefs.edit().putBoolean(KEY_ENABLE_RX_LINE, true).apply()
        }
        return prefs.getBoolean(KEY_ENABLE_RX_LINE, true)
    }

    /**
     * Enable or disable RhôneExpress (RX) line in routing.
     */
    fun setRxLineEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLE_RX_LINE, enabled).apply()
    }

    /**
     * Get set of route name patterns to block based on user preferences.
     * Returns patterns that can be used with blockedRouteNames parameter.
     * 
     * raptor-kt will block any route whose name starts with these patterns,
     * so "JD" will block all JD lines (JD2, JD3, JD844, etc.)
     */
    fun getBlockedRoutePatterns(): Set<String> {
        val blocked = mutableSetOf<String>()
        
        if (!isJdLinesEnabled()) {
            // Block all JD lines - raptor-kt will match any route starting with "JD"
            for (i in 2..999) {
                blocked.add("JD$i")
            }
        }
        
        if (!isRxLineEnabled()) {
            blocked.add("RX")
        }
        
        return blocked
    }
}
