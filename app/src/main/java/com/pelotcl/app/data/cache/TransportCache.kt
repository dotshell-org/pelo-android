package com.pelotcl.app.data.cache

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pelotcl.app.data.model.Feature
import com.pelotcl.app.data.model.StopFeature
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

/**
 * In-memory and disk cache class for transport data.
 * Avoids repeated API calls and improves performance.
 */
class TransportCache(context: Context) {
    
    private val gson = Gson()
    private val prefs = context.getSharedPreferences("transport_cache", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    
    // In-memory cache for ultra-fast access
    private var metroLinesCache: List<Feature>? = null
    private var tramLinesCache: List<Feature>? = null
    private var busLinesCache: List<Feature>? = null
    private var stopsCache: List<StopFeature>? = null
    
    // Timestamps for expiration management
    private var metroLinesTimestamp: Long = 0
    private var tramLinesTimestamp: Long = 0
    private var busLinesTimestamp: Long = 0
    private var stopsTimestamp: Long = 0
    
    companion object {
        // Cache validity duration: 24 hours
        private val CACHE_VALIDITY_DURATION = TimeUnit.HOURS.toMillis(24)
        
        // Keys for SharedPreferences
        private const val KEY_METRO_LINES = "metro_lines"
        private const val KEY_METRO_LINES_TIMESTAMP = "metro_lines_timestamp"
        private const val KEY_TRAM_LINES = "tram_lines"
        private const val KEY_TRAM_LINES_TIMESTAMP = "tram_lines_timestamp"
        private const val KEY_STOPS = "stops"
        private const val KEY_STOPS_TIMESTAMP = "stops_timestamp"
        
        @Volatile
        private var INSTANCE: TransportCache? = null
        
        fun getInstance(context: Context): TransportCache {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TransportCache(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    /**
     * Checks if a timestamp is still valid
     */
    private fun isTimestampValid(timestamp: Long): Boolean {
        return (System.currentTimeMillis() - timestamp) < CACHE_VALIDITY_DURATION
    }
    
    /**
     * Saves metro/funicular lines to cache
     */
    suspend fun saveMetroLines(lines: List<Feature>) = mutex.withLock {
        metroLinesCache = lines
        metroLinesTimestamp = System.currentTimeMillis()
        
        // Save to disk
        prefs.edit().apply {
            putString(KEY_METRO_LINES, gson.toJson(lines))
            putLong(KEY_METRO_LINES_TIMESTAMP, metroLinesTimestamp)
            apply()
        }
    }
    
    /**
     * Retrieves metro/funicular lines from cache
     */
    suspend fun getMetroLines(): List<Feature>? = mutex.withLock {
        // Check memory cache first
        if (metroLinesCache != null && isTimestampValid(metroLinesTimestamp)) {
            return@withLock metroLinesCache
        }
        
        // Otherwise, load from disk
        val timestamp = prefs.getLong(KEY_METRO_LINES_TIMESTAMP, 0)
        if (isTimestampValid(timestamp)) {
            val json = prefs.getString(KEY_METRO_LINES, null)
            if (json != null) {
                try {
                    val type = object : TypeToken<List<Feature>>() {}.type
                    val lines = gson.fromJson<List<Feature>>(json, type)
                    metroLinesCache = lines
                    metroLinesTimestamp = timestamp
                    return@withLock lines
                } catch (e: Exception) {
                    android.util.Log.e("TransportCache", "Error loading metro lines from cache", e)
                }
            }
        }
        
        null
    }
    
    /**
     * Saves tram lines to cache
     */
    suspend fun saveTramLines(lines: List<Feature>) = mutex.withLock {
        tramLinesCache = lines
        tramLinesTimestamp = System.currentTimeMillis()
        
        // Save to disk
        prefs.edit().apply {
            putString(KEY_TRAM_LINES, gson.toJson(lines))
            putLong(KEY_TRAM_LINES_TIMESTAMP, tramLinesTimestamp)
            apply()
        }
    }
    
    /**
     * Retrieves tram lines from cache
     */
    suspend fun getTramLines(): List<Feature>? = mutex.withLock {
        // Check memory cache first
        if (tramLinesCache != null && isTimestampValid(tramLinesTimestamp)) {
            return@withLock tramLinesCache
        }
        
        // Otherwise, load from disk
        val timestamp = prefs.getLong(KEY_TRAM_LINES_TIMESTAMP, 0)
        if (isTimestampValid(timestamp)) {
            val json = prefs.getString(KEY_TRAM_LINES, null)
            if (json != null) {
                try {
                    val type = object : TypeToken<List<Feature>>() {}.type
                    val lines = gson.fromJson<List<Feature>>(json, type)
                    tramLinesCache = lines
                    tramLinesTimestamp = timestamp
                    return@withLock lines
                } catch (e: Exception) {
                    android.util.Log.e("TransportCache", "Error loading tram lines from cache", e)
                }
            }
        }
        
        null
    }
    
    /**
     * Saves bus lines to cache (MEMORY ONLY)
     * Bus lines are too large for SharedPreferences
     */
    suspend fun saveBusLines(lines: List<Feature>) = mutex.withLock {
        busLinesCache = lines
        busLinesTimestamp = System.currentTimeMillis()
    }
    
    /**
     * Retrieves bus lines from cache (MEMORY ONLY)
     * Buses are not persisted to disk because they're too large
     */
    suspend fun getBusLines(): List<Feature>? = mutex.withLock {
        // Check memory cache only
        if (busLinesCache != null && isTimestampValid(busLinesTimestamp)) {
            return@withLock busLinesCache
        }
        
        // Buses are not cached on disk
        null
    }
    
    /**
     * Saves stops to cache
     * WARNING: Stops are large, we keep all stops (including buses) on disk
     */
    suspend fun saveStops(stops: List<StopFeature>) = mutex.withLock {
        stopsCache = stops
        stopsTimestamp = System.currentTimeMillis()
        
        // Save all stops to disk (including buses)
        try {
            prefs.edit().apply {
                putString(KEY_STOPS, gson.toJson(stops))
                putLong(KEY_STOPS_TIMESTAMP, stopsTimestamp)
                apply()
            }
        } catch (e: Exception) {
            android.util.Log.e("TransportCache", "Failed to save stops to disk, keeping memory cache only", e)
            // In case of error, we still keep the memory cache
        }
    }
    
    /**
     * Retrieves stops from cache
     */
    suspend fun getStops(): List<StopFeature>? = mutex.withLock {
        // Check memory cache first
        if (stopsCache != null && isTimestampValid(stopsTimestamp)) {
            return@withLock stopsCache
        }
        
        // Otherwise, load from disk
        val timestamp = prefs.getLong(KEY_STOPS_TIMESTAMP, 0)
        if (isTimestampValid(timestamp)) {
            val json = prefs.getString(KEY_STOPS, null)
            if (json != null) {
                try {
                    val type = object : TypeToken<List<StopFeature>>() {}.type
                    val stops = gson.fromJson<List<StopFeature>>(json, type)
                    stopsCache = stops
                    stopsTimestamp = timestamp
                    return@withLock stops
                } catch (e: Exception) {
                    android.util.Log.e("TransportCache", "Error loading stops from cache", e)
                }
            }
        }
        
        null
    }
}
