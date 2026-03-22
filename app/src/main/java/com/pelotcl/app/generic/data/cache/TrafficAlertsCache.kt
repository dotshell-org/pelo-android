package com.pelotcl.app.generic.data.cache

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pelotcl.app.generic.data.model.TrafficAlert

/**
 * Cache for traffic alerts data using SharedPreferences
 */
class TrafficAlertsCache(context: Context) {

    companion object {
        private const val CACHE_FILE_NAME = "traffic_alerts_cache"
        private const val ALERTS_CACHE_KEY = "traffic_alerts"
        private const val TIMESTAMP_CACHE_KEY = "traffic_alerts_timestamp"
        private const val TIMESTAMP_MILLIS_KEY = "traffic_alerts_timestamp_millis"
    }

    private val sharedPrefs = context.getSharedPreferences(CACHE_FILE_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    /**
     * Saves traffic alerts to cache
     */
    fun saveTrafficAlerts(alerts: List<TrafficAlert>, timestamp: String) {
        try {
            val alertsJson = gson.toJson(alerts)
            sharedPrefs.edit {
                putString(ALERTS_CACHE_KEY, alertsJson)
                    .putString(TIMESTAMP_CACHE_KEY, timestamp)
                    .putLong(TIMESTAMP_MILLIS_KEY, System.currentTimeMillis())
            }
        } catch (e: Exception) {
            Log.e("TrafficAlertsCache", "Error saving traffic alerts to cache", e)
        }
    }

    /**
     * Gets traffic alerts from cache
     */
    fun getTrafficAlerts(): Pair<List<TrafficAlert>, String>? {
        try {
            val alertsJson = sharedPrefs.getString(ALERTS_CACHE_KEY, null)
            val timestamp = sharedPrefs.getString(TIMESTAMP_CACHE_KEY, null)

            return if (alertsJson != null && timestamp != null) {
                val type = object : TypeToken<List<TrafficAlert>>() {}.type
                val alerts = gson.fromJson<List<TrafficAlert>>(alertsJson, type)
                Pair(alerts, timestamp)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("TrafficAlertsCache", "Error reading traffic alerts from cache", e)
            return null
        }
    }

}