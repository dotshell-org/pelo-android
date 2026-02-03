package com.pelotcl.app.data.repository

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pelotcl.app.data.model.TrafficAlert
import com.pelotcl.app.data.model.TrafficAlertsResponse
import com.pelotcl.app.data.model.TrafficStatusResponse
import java.io.File
import java.io.IOException

/**
 * Cache for traffic alerts data using SharedPreferences
 */
class TrafficAlertsCache(private val context: Context) {
    
    companion object {
        private const val CACHE_FILE_NAME = "traffic_alerts_cache"
        private const val STATUS_CACHE_KEY = "traffic_status"
        private const val ALERTS_CACHE_KEY = "traffic_alerts"
        private const val TIMESTAMP_CACHE_KEY = "traffic_alerts_timestamp"
    }
    
    private val sharedPrefs = context.getSharedPreferences(CACHE_FILE_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    
    /**
     * Saves traffic status to cache
     */
    fun saveTrafficStatus(status: TrafficStatusResponse) {
        try {
            val statusJson = gson.toJson(status)
            sharedPrefs.edit()
                .putString(STATUS_CACHE_KEY, statusJson)
                .putString(TIMESTAMP_CACHE_KEY, status.timestamp)
                .apply()
        } catch (e: Exception) {
            Log.e("TrafficAlertsCache", "Error saving traffic status to cache", e)
        }
    }
    
    /**
     * Gets traffic status from cache
     */
    fun getTrafficStatus(): TrafficStatusResponse? {
        try {
            val statusJson = sharedPrefs.getString(STATUS_CACHE_KEY, null)
            return if (statusJson != null) {
                gson.fromJson(statusJson, TrafficStatusResponse::class.java)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("TrafficAlertsCache", "Error reading traffic status from cache", e)
            return null
        }
    }
    
    /**
     * Saves traffic alerts to cache
     */
    fun saveTrafficAlerts(alerts: List<TrafficAlert>, timestamp: String) {
        try {
            val alertsJson = gson.toJson(alerts)
            sharedPrefs.edit()
                .putString(ALERTS_CACHE_KEY, alertsJson)
                .putString(TIMESTAMP_CACHE_KEY, timestamp)
                .apply()
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
    
    /**
     * Clears the cache
     */
    fun clearCache() {
        try {
            sharedPrefs.edit()
                .remove(STATUS_CACHE_KEY)
                .remove(ALERTS_CACHE_KEY)
                .remove(TIMESTAMP_CACHE_KEY)
                .apply()
        } catch (e: Exception) {
            Log.e("TrafficAlertsCache", "Error clearing traffic alerts cache", e)
        }
    }
}