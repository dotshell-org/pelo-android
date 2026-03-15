package com.pelotcl.app.data.cache

import android.content.Context
import com.pelotcl.app.data.model.Feature
import com.pelotcl.app.data.model.StopFeature
import com.pelotcl.app.data.offline.sanitizeForSerialization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import androidx.core.content.edit

/**
 * In-memory and disk cache class for transport data.
 * Uses kotlinx.serialization for fast JSON serialization and Gzip compression
 * for reduced disk I/O and storage.
 * Avoids repeated API calls and improves performance.
 */
class TransportCache(context: Context) {

    // High-performance JSON parser with optimized settings
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val prefs = context.getSharedPreferences("transport_cache_meta", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val cacheDir = File(context.cacheDir, "transport_data").also { it.mkdirs() }

    // In-memory cache for ultra-fast access
    @Volatile private var metroLinesCache: List<Feature>? = null
    @Volatile private var tramLinesCache: List<Feature>? = null
    @Volatile private var busLinesCache: List<Feature>? = null
    @Volatile private var navigoneLinesCache: List<Feature>? = null
    @Volatile private var trambusLinesCache: List<Feature>? = null
    @Volatile private var stopsCache: List<StopFeature>? = null

    // Timestamps for expiration management
    @Volatile private var metroLinesTimestamp: Long = 0
    @Volatile private var tramLinesTimestamp: Long = 0
    @Volatile private var busLinesTimestamp: Long = 0
    @Volatile private var navigoneLinesTimestamp: Long = 0
    @Volatile private var trambusLinesTimestamp: Long = 0
    @Volatile private var stopsTimestamp: Long = 0

    companion object {
        // Cache validity duration: 24 hours
        private val CACHE_VALIDITY_DURATION = TimeUnit.HOURS.toMillis(24)
        
        // Keys for SharedPreferences (metadata only)
        private const val KEY_METRO_LINES_TIMESTAMP = "metro_lines_timestamp"
        private const val KEY_TRAM_LINES_TIMESTAMP = "tram_lines_timestamp"
        private const val KEY_NAVIGONE_LINES_TIMESTAMP = "navigone_lines_timestamp"
        private const val KEY_TRAMBUS_LINES_TIMESTAMP = "trambus_lines_timestamp"
        private const val KEY_STOPS_TIMESTAMP = "stops_timestamp"
        
        // Compressed cache file names
        private const val FILE_METRO_LINES = "metro_lines.json.gz"
        private const val FILE_TRAM_LINES = "tram_lines.json.gz"
        private const val FILE_NAVIGONE_LINES = "navigone_lines.json.gz"
        private const val FILE_TRAMBUS_LINES = "trambus_lines.json.gz"
        private const val FILE_STOPS = "stops.json.gz"

    }

    /**
     * Checks if a timestamp is still valid
     */
    private fun isTimestampValid(timestamp: Long): Boolean {
        return (System.currentTimeMillis() - timestamp) < CACHE_VALIDITY_DURATION
    }
    
    /**
     * Write data to compressed Gzip file (runs on IO dispatcher)
     * Uses kotlinx.serialization for fast encoding
     */
    private suspend inline fun <reified T> writeToCompressedFile(fileName: String, data: T) = withContext(Dispatchers.IO) {
        try {
            val file = File(cacheDir, fileName)
            val jsonString = json.encodeToString(data)
            GZIPOutputStream(FileOutputStream(file).buffered()).use { gzip ->
                gzip.write(jsonString.toByteArray(Charsets.UTF_8))
            }
        } catch (e: Exception) {
            android.util.Log.e("TransportCache", "Error writing to $fileName", e)
        }
    }

    /**
     * Read data from compressed Gzip file (runs on IO dispatcher)
     * Uses kotlinx.serialization for fast decoding
     */
    private suspend inline fun <reified T> readFromCompressedFile(fileName: String): T? = withContext(Dispatchers.IO) {
        try {
            val file = File(cacheDir, fileName)
            if (file.exists()) {
                val jsonString = GZIPInputStream(FileInputStream(file).buffered()).use { gzip ->
                    gzip.bufferedReader(Charsets.UTF_8).readText()
                }
                json.decodeFromString<T>(jsonString)
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
        prefs.edit { putLong(KEY_METRO_LINES_TIMESTAMP, metroLinesTimestamp)}
        writeToCompressedFile(FILE_METRO_LINES, lines.sanitizeForSerialization())
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
            val lines = readFromCompressedFile<List<Feature>>(FILE_METRO_LINES)
            if (lines != null) {
                metroLinesCache = lines
                metroLinesTimestamp = timestamp
                return@withLock lines
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
        prefs.edit { putLong(KEY_TRAM_LINES_TIMESTAMP, tramLinesTimestamp)}
        writeToCompressedFile(FILE_TRAM_LINES, lines.sanitizeForSerialization())
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
            val lines = readFromCompressedFile<List<Feature>>(FILE_TRAM_LINES)
            if (lines != null) {
                tramLinesCache = lines
                tramLinesTimestamp = timestamp
                return@withLock lines
            }
        }

        null
    }

    /**
     * Saves Navigone lines to cache (with disk persistence)
     */
    suspend fun saveNavigoneLines(lines: List<Feature>) = mutex.withLock {
        navigoneLinesCache = lines
        navigoneLinesTimestamp = System.currentTimeMillis()

        prefs.edit { putLong(KEY_NAVIGONE_LINES_TIMESTAMP, navigoneLinesTimestamp)}
        writeToCompressedFile(FILE_NAVIGONE_LINES, lines.sanitizeForSerialization())
    }

    /**
     * Retrieves Navigone lines from cache
     */
    suspend fun getNavigoneLines(): List<Feature>? = mutex.withLock {
        // Check memory cache first
        if (navigoneLinesCache != null && isTimestampValid(navigoneLinesTimestamp)) {
            return@withLock navigoneLinesCache
        }

        // Otherwise, load from disk
        val timestamp = prefs.getLong(KEY_NAVIGONE_LINES_TIMESTAMP, 0)
        if (isTimestampValid(timestamp)) {
            val lines = readFromCompressedFile<List<Feature>>(FILE_NAVIGONE_LINES)
            if (lines != null) {
                navigoneLinesCache = lines
                navigoneLinesTimestamp = timestamp
                return@withLock lines
            }
        }

        null
    }

    /**
     * Saves Trambus lines to cache (with disk persistence)
     */
    suspend fun saveTrambusLines(lines: List<Feature>) = mutex.withLock {
        trambusLinesCache = lines
        trambusLinesTimestamp = System.currentTimeMillis()

        prefs.edit { putLong(KEY_TRAMBUS_LINES_TIMESTAMP, trambusLinesTimestamp)}
        writeToCompressedFile(FILE_TRAMBUS_LINES, lines.sanitizeForSerialization())
    }

    /**
     * Retrieves Trambus lines from cache
     */
    suspend fun getTrambusLines(): List<Feature>? = mutex.withLock {
        // Check memory cache first
        if (trambusLinesCache != null && isTimestampValid(trambusLinesTimestamp)) {
            return@withLock trambusLinesCache
        }

        // Otherwise, load from disk
        val timestamp = prefs.getLong(KEY_TRAMBUS_LINES_TIMESTAMP, 0)
        if (isTimestampValid(timestamp)) {
            val lines = readFromCompressedFile<List<Feature>>(FILE_TRAMBUS_LINES)
            if (lines != null) {
                trambusLinesCache = lines
                trambusLinesTimestamp = timestamp
                return@withLock lines
            }
        }

        null
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
     * Saves stops to cache using compressed file storage
     * WARNING: Stops are large, we keep all stops (including buses) on disk with Gzip compression
     */
    suspend fun saveStops(stops: List<StopFeature>) = mutex.withLock {
        stopsCache = stops
        stopsTimestamp = System.currentTimeMillis()
        
        // Save timestamp to prefs and data to compressed file
        prefs.edit { putLong(KEY_STOPS_TIMESTAMP, stopsTimestamp) }
        writeToCompressedFile(FILE_STOPS, stops)
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
            val stops = readFromCompressedFile<List<StopFeature>>(FILE_STOPS)
            if (stops != null) {
                stopsCache = stops
                stopsTimestamp = timestamp
                return@withLock stops
            }
        }
        
        null
    }

    /**
     * Preload cache from disk into memory (call on background thread at startup)
     * Now uses compressed files for faster I/O
     * Loads all cached line types including Navigone and Trambus
     */
    suspend fun preloadFromDisk() {
        // Trigger lazy loading from disk in parallel for faster startup
        coroutineScope {
            awaitAll(
                async { getMetroLines() },
                async { getTramLines() },
                async { getNavigoneLines() },
                async { getTrambusLines() },
                async { getStops() }
            )
        }
    }

    // ===== STALE CACHE METHODS FOR CACHE-FIRST STRATEGY =====
    // These methods return cached data even if expired, for immediate display
    // The caller should then refresh from network in the background

    /**
     * Returns metro lines from cache even if expired (stale).
     * Use this for immediate display, then refresh from network.
     */
    suspend fun getMetroLinesStale(): List<Feature>? = mutex.withLock {
        // Return memory cache if available (regardless of expiration)
        if (metroLinesCache != null) {
            return@withLock metroLinesCache
        }

        // Try to load from disk (regardless of expiration)
        val lines = readFromCompressedFile<List<Feature>>(FILE_METRO_LINES)
        if (lines != null) {
            metroLinesCache = lines
            metroLinesTimestamp = prefs.getLong(KEY_METRO_LINES_TIMESTAMP, 0)
            return@withLock lines
        }

        null
    }

    /**
     * Returns tram lines from cache even if expired (stale).
     */
    suspend fun getTramLinesStale(): List<Feature>? = mutex.withLock {
        if (tramLinesCache != null) {
            return@withLock tramLinesCache
        }

        val lines = readFromCompressedFile<List<Feature>>(FILE_TRAM_LINES)
        if (lines != null) {
            tramLinesCache = lines
            tramLinesTimestamp = prefs.getLong(KEY_TRAM_LINES_TIMESTAMP, 0)
            return@withLock lines
        }

        null
    }

    /**
     * Returns Navigone lines from cache even if expired (stale).
     */
    suspend fun getNavigoneLinesStale(): List<Feature>? = mutex.withLock {
        if (navigoneLinesCache != null) {
            return@withLock navigoneLinesCache
        }

        val lines = readFromCompressedFile<List<Feature>>(FILE_NAVIGONE_LINES)
        if (lines != null) {
            navigoneLinesCache = lines
            navigoneLinesTimestamp = prefs.getLong(KEY_NAVIGONE_LINES_TIMESTAMP, 0)
            return@withLock lines
        }

        null
    }

    /**
     * Returns Trambus lines from cache even if expired (stale).
     */
    suspend fun getTrambusLinesStale(): List<Feature>? = mutex.withLock {
        if (trambusLinesCache != null) {
            return@withLock trambusLinesCache
        }

        val lines = readFromCompressedFile<List<Feature>>(FILE_TRAMBUS_LINES)
        if (lines != null) {
            trambusLinesCache = lines
            trambusLinesTimestamp = prefs.getLong(KEY_TRAMBUS_LINES_TIMESTAMP, 0)
            return@withLock lines
        }

        null
    }

}
