package com.pelotcl.app.generic.service

import android.content.Context

object NavigationModeStateStore {

    private const val PREFS_NAME = "navigation_mode_prefs"
    private const val KEY_NAV_ACTIVE = "navigation_active"

    fun setNavigationActive(context: Context, active: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_NAV_ACTIVE, active)
            .apply()
    }

    fun isNavigationActive(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_NAV_ACTIVE, false)
    }
}
