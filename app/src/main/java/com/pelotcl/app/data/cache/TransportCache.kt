package com.pelotcl.app.data.cache

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pelotcl.app.data.model.Feature
import com.pelotcl.app.data.model.StopFeature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * In-memory and disk cache class for transport data.
 * Uses binary serialization for faster disk I/O and reduced storage.
 * Avoids repeated API calls and improves performance.
 */
class TransportCache(private val context: Context) {

    private val gson = Gson()
    private val prefs = context.getSharedPreferences("transport_cache_meta", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val cacheDir = File(context.cacheDir, "transport_data").also { it.mkdirs() }

    // In-memory cache for ultra-fast access
    @Volatile private var metroLinesCache: List<Feature>? = null
    @Volatile private var tramLinesCache: List<Feature>? = null
    @Volatile private var busLinesCache: List<Feature>? = null
    @Volatile private var stopsCache: List<StopFeature>? = null

    // Timestamps for expiration management
    @Volatile private var metroLinesTimestamp: Long = 0
    @Volatile private var tramLinesTimestamp: Long = 0
    @Volatile private var busLinesTimestamp: Long = 0
    @Volatile private var stopsTimestamp: Long = 0

    companion object {
        // Cache validity duration: 24 hours
        private val CACHE_VALIDITY_DURATION = TimeUnit.HOURS.toMillis(24)
        
        // Keys for SharedPreferences (metadata only)
        private const val KEY_METRO_LINES_TIMESTAMP = "metro_lines_timestamp"
        private const val KEY_TRAM_LINES_TIMESTAMP = "tram_lines_timestamp"
        private const val KEY_STOPS_TIMESTAMP = "stops_timestamp"
        
        // Binary cache file names
        private const val FILE_METRO_LINES = "metro_lines.bin"
        private const val FILE_TRAM_LINES = "tram_lines.bin"
        private const val FILE_STOPS = "stops.json" // Keep JSON for stops due to size/complexity

        // Cache schema version - increment when model classes change
        private const val CACHE_VERSION = 1
        private const val KEY_CACHE_VERSION = "cache_version"

        @Volatile
        private var INSTANCE: TransportCache? = null
        
        fun getInstance(context: Context): TransportCache {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TransportCache(context.applicationContext).also {
                    INSTANCE = it
                    it.checkCacheVersion()
                }
            }
        }
    }

    /**
     * Check and invalidate cache if version changed
     */
    private fun checkCacheVersion() {
        val storedVersion = prefs.getInt(KEY_CACHE_VERSION, 0)
        if (storedVersion != CACHE_VERSION) {
            clearAllCache()
            prefs.edit().putInt(KEY_CACHE_VERSION, CACHE_VERSION).apply()
        }
    }

    /**
     * Clear all cached data
     */
    fun clearAllCache() {
        metroLinesCache = null
        tramLinesCache = null
        busLinesCache = null
        stopsCache = null
        metroLinesTimestamp = 0
        tramLinesTimestamp = 0
        busLinesTimestamp = 0
        stopsTimestamp = 0

        // Clear disk cache
        cacheDir.listFiles()?.forEach { it.delete() }
        prefs.edit().clear().putInt(KEY_CACHE_VERSION, CACHE_VERSION).apply()
    }

    /**
     * Checks if a timestamp is still valid
     */
    private fun isTimestampValid(timestamp: Long): Boolean {
        return (System.currentTimeMillis() - timestamp) < CACHE_VALIDITY_DURATION
    }
    
    /**
     * Write features to binary file (runs on IO dispatcher)
     */
    private suspend fun writeToBinaryFile(fileName: String, data: String) = withContext(Dispatchers.IO) {
        try {
            val file = File(cacheDir, fileName)
            FileOutputStream(file).bufferedWriter().use { writer ->
                writer.write(data)
            }
        } catch (e: Exception) {
            android.util.Log.e("TransportCache", "Error writing to $fileName", e)
        }
    }

    /**
     * Read features from binary file (runs on IO dispatcher)
     */
    private suspend fun readFromBinaryFile(fileName: String): String? = withContext(Dispatchers.IO) {
        try {
            val file = File(cacheDir, fileName)
            if (file.exists()) {
                FileInputStream(file).bufferedReader().use { reader ->
                    reader.readText()
                }
            } else null
        } catch (e: Exception) {
            android.util.Log.e("TransportCache", "Error reading from $fileName", e)
            null
        }
    }

    /**
     * Saves metro/funicular lines to cache
     */
    suspend fun saveMetroLines(lines: List<Feature>) = mutex.withLock {
        metroLinesCache = lines
        metroLinesTimestamp = System.currentTimeMillis()
        
        // Save timestamp to prefs and data to file asynchronously
        prefs.edit().putLong(KEY_METRO_LINES_TIMESTAMP, metroLinesTimestamp).apply()
        writeToBinaryFile(FILE_METRO_LINES, gson.toJson(lines))
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
            val json = readFromBinaryFile(FILE_METRO_LINES)
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
        
        // Save timestamp to prefs and data to file asynchronously
        prefs.edit().putLong(KEY_TRAM_LINES_TIMESTAMP, tramLinesTimestamp).apply()
        writeToBinaryFile(FILE_TRAM_LINES, gson.toJson(lines))
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
            val json = readFromBinaryFile(FILE_TRAM_LINES)
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
     * Bus lines are too large for disk storage
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
     * Saves stops to cache using file storage
     * WARNING: Stops are large, we keep all stops (including buses) on disk
     */
    suspend fun saveStops(stops: List<StopFeature>) = mutex.withLock {
        stopsCache = stops
        stopsTimestamp = System.currentTimeMillis()
        
        // Save timestamp to prefs and data to file
        prefs.edit().putLong(KEY_STOPS_TIMESTAMP, stopsTimestamp).apply()

        // Save to file asynchronously (large data)
        withContext(Dispatchers.IO) {
            try {
                val file = File(cacheDir, FILE_STOPS)
                FileOutputStream(file).bufferedWriter().use { writer ->
                    gson.toJson(stops, writer)
                }
            } catch (e: Exception) {
                android.util.Log.e("TransportCache", "Failed to save stops to disk", e)
            }
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
            val stops = withContext(Dispatchers.IO) {
                try {
                    val file = File(cacheDir, FILE_STOPS)
                    if (file.exists()) {
                        FileInputStream(file).bufferedReader().use { reader ->
                            val type = object : TypeToken<List<StopFeature>>() {}.type
                            gson.fromJson<List<StopFeature>>(reader, type)
                        }
                    } else null
                } catch (e: Exception) {
                    android.util.Log.e("TransportCache", "Error loading stops from cache", e)
                    null
                }
            }

            if (stops != null) {
                stopsCache = stops
                stopsTimestamp = timestamp
                return@withLock stops
            }
        }
        
        null
    }

    /**
     * Check if metro/tram cache is populated in memory (for fast sync check)
     */
    fun hasMemoryCache(): Boolean {
        return metroLinesCache != null && tramLinesCache != null
    }

    /**
     * Preload cache from disk into memory (call on background thread at startup)
     */
    suspend fun preloadFromDisk() {
        // Trigger lazy loading from disk
        getMetroLines()
        getTramLines()
        getStops()
    }
}
