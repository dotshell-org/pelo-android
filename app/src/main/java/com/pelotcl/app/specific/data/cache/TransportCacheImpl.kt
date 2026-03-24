package com.pelotcl.app.specific.data.cache

import android.content.Context
import android.util.Log
import com.pelotcl.app.generic.data.model.Feature
import com.pelotcl.app.generic.data.model.StopFeature
import com.pelotcl.app.generic.data.offline.sanitizeForSerialization
import com.pelotcl.app.generic.data.offline.sanitizeStopsForSerialization
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
import com.pelotcl.app.generic.data.cache.TransportCache

/**
 * Lyon-specific implementation of TransportCache
 * Handles caching for Lyon transport data including Navigone and Trambus
 */
class TransportCacheImpl(context: Context) : TransportCache {

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
    @Volatile
    private var metroLinesCache: List<Feature>? = null
    @Volatile
    private var tramLinesCache: List<Feature>? = null

    @Volatile
    private var navigoneLinesCache: List<Feature>? = null
    @Volatile
    private var trambusLinesCache: List<Feature>? = null
    @Volatile
    private var stopsCache: List<StopFeature>? = null

    // Timestamps for expiration management
    @Volatile
    private var metroLinesTimestamp: Long = 0
    @Volatile
    private var tramLinesTimestamp: Long = 0

    @Volatile
    private var navigoneLinesTimestamp: Long = 0
    @Volatile
    private var trambusLinesTimestamp: Long = 0
    @Volatile
    private var stopsTimestamp: Long = 0

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
     * Check if any cached data is available (even if expired).
     * Useful for offline mode or immediate display while refreshing.
     */
    override fun hasAnyCachedData(): Boolean {
        // Check memory cache first (fastest) - no lock needed for volatile reads
        if (metroLinesCache != null || tramLinesCache != null || stopsCache != null) {
            return true
        }

        // Check disk cache timestamps - no lock needed for SharedPreferences read
        val hasDiskCache = prefs.getLong(KEY_METRO_LINES_TIMESTAMP, 0) > 0 ||
                prefs.getLong(KEY_TRAM_LINES_TIMESTAMP, 0) > 0 ||
                prefs.getLong(KEY_STOPS_TIMESTAMP, 0) > 0

        return hasDiskCache
    }

    /**
     * Check if cache needs refresh (has data but expired).
     * Useful to decide whether to show stale data while refreshing.
     */
    override fun needsCacheRefresh(): Boolean {
        // If no cache at all, we need to load
        if (!hasAnyCachedData()) return true

        // Check if any of the main caches are expired
        val now = System.currentTimeMillis()

        val metroExpired = prefs.getLong(KEY_METRO_LINES_TIMESTAMP, 0) > 0 &&
                (now - prefs.getLong(KEY_METRO_LINES_TIMESTAMP, 0)) >= CACHE_VALIDITY_DURATION

        val tramExpired = prefs.getLong(KEY_TRAM_LINES_TIMESTAMP, 0) > 0 &&
                (now - prefs.getLong(KEY_TRAM_LINES_TIMESTAMP, 0)) >= CACHE_VALIDITY_DURATION

        val stopsExpired = prefs.getLong(KEY_STOPS_TIMESTAMP, 0) > 0 &&
                (now - prefs.getLong(KEY_STOPS_TIMESTAMP, 0)) >= CACHE_VALIDITY_DURATION

        // If any main cache is expired, we should refresh
        return metroExpired || tramExpired || stopsExpired
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
    private suspend inline fun <reified T> writeToCompressedFile(fileName: String, data: T) =
        withContext(Dispatchers.IO) {
            try {
                val file = File(cacheDir, fileName)
                val jsonString = json.encodeToString(data)
                GZIPOutputStream(FileOutputStream(file).buffered()).use { gzip ->
                    gzip.write(jsonString.toByteArray(Charsets.UTF_8))
                }
            } catch (e: Exception) {
                Log.e("LyonTransportCache", "Error writing to $fileName", e)
            }
        }

    /**
     * Read data from compressed Gzip file (runs on IO dispatcher)
     * Uses kotlinx.serialization for fast decoding
     */
    private suspend inline fun <reified T> readFromCompressedFile(fileName: String): T? =
        withContext(Dispatchers.IO) {
            try {
                val file = File(cacheDir, fileName)
                if (file.exists()) {
                    val jsonString = GZIPInputStream(FileInputStream(file).buffered()).use { gzip ->
                        gzip.bufferedReader(Charsets.UTF_8).readText()
                    }
                    json.decodeFromString<T>(jsonString)
                } else null
            } catch (e: Exception) {
                Log.e("LyonTransportCache", "Error reading from $fileName", e)
                null
            }
        }

    private suspend fun invalidateLineCache(fileName: String, timestampKey: String) {
        withContext(Dispatchers.IO) {
            runCatching {
                File(cacheDir, fileName).delete()
            }
        }
        prefs.edit { putLong(timestampKey, 0L) }
    }

    /**
     * Detect legacy cache payloads from pre-refactor models where line identifiers were stored
     * with old property names and now deserialize to empty strings.
     */
    private fun isInvalidLineCache(lines: List<Feature>): Boolean {
        if (lines.isEmpty()) return false
        val validEntries = lines.count {
            it.properties.lineName.isNotBlank() && it.properties.traceCode.isNotBlank()
        }
        return validEntries == 0
    }

    /**
     * Saves metro/funicular lines to cache
     */
    override suspend fun saveMetroLines(lines: List<Feature>) {
        mutex.withLock {
            metroLinesCache = lines
            metroLinesTimestamp = System.currentTimeMillis()

            // Save timestamp to prefs and data to file asynchronously
            prefs.edit { putLong(KEY_METRO_LINES_TIMESTAMP, metroLinesTimestamp) }
            writeToCompressedFile(FILE_METRO_LINES, lines.sanitizeForSerialization())
        }
    }

    /**
     * Retrieves metro/funicular lines from cache
     */
    override suspend fun getMetroLines(): List<Feature>? = mutex.withLock {
        // Check memory cache first
        if (metroLinesCache != null && isTimestampValid(metroLinesTimestamp)) {
            if (isInvalidLineCache(metroLinesCache.orEmpty())) {
                metroLinesCache = null
                metroLinesTimestamp = 0L
                invalidateLineCache(FILE_METRO_LINES, KEY_METRO_LINES_TIMESTAMP)
                return@withLock null
            }
            return@withLock metroLinesCache
        }

        // Otherwise, load from disk
        val timestamp = prefs.getLong(KEY_METRO_LINES_TIMESTAMP, 0)
        if (isTimestampValid(timestamp)) {
            val lines = readFromCompressedFile<List<Feature>>(FILE_METRO_LINES)
            if (lines != null) {
                if (isInvalidLineCache(lines)) {
                    invalidateLineCache(FILE_METRO_LINES, KEY_METRO_LINES_TIMESTAMP)
                    return@withLock null
                }
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
    override suspend fun saveTramLines(lines: List<Feature>) {
        mutex.withLock {
            tramLinesCache = lines
            tramLinesTimestamp = System.currentTimeMillis()

            // Save timestamp to prefs and data to file asynchronously
            prefs.edit { putLong(KEY_TRAM_LINES_TIMESTAMP, tramLinesTimestamp) }
            writeToCompressedFile(FILE_TRAM_LINES, lines.sanitizeForSerialization())
        }
    }

    /**
     * Retrieves tram lines from cache
     */
    override suspend fun getTramLines(): List<Feature>? = mutex.withLock {
        // Check memory cache first
        if (tramLinesCache != null && isTimestampValid(tramLinesTimestamp)) {
            if (isInvalidLineCache(tramLinesCache.orEmpty())) {
                tramLinesCache = null
                tramLinesTimestamp = 0L
                invalidateLineCache(FILE_TRAM_LINES, KEY_TRAM_LINES_TIMESTAMP)
                return@withLock null
            }
            return@withLock tramLinesCache
        }

        // Otherwise, load from disk
        val timestamp = prefs.getLong(KEY_TRAM_LINES_TIMESTAMP, 0)
        if (isTimestampValid(timestamp)) {
            val lines = readFromCompressedFile<List<Feature>>(FILE_TRAM_LINES)
            if (lines != null) {
                if (isInvalidLineCache(lines)) {
                    invalidateLineCache(FILE_TRAM_LINES, KEY_TRAM_LINES_TIMESTAMP)
                    return@withLock null
                }
                tramLinesCache = lines
                tramLinesTimestamp = timestamp
                return@withLock lines
            }
        }

        null
    }

    /**
     * Saves bus lines to cache
     */
    override suspend fun saveBusLines(lines: List<Feature>) = mutex.withLock {
        // For Lyon, we don't cache bus lines by default to save space
        // But we implement the interface method for completeness
    }

    /**
     * Retrieves bus lines from cache
     */
    override suspend fun getBusLines(): List<Feature>? = mutex.withLock {
        // For Lyon, bus lines are not cached by default
        null
    }

    /**
     * Saves Navigone lines to cache (with disk persistence)
     */
    suspend fun saveNavigoneLines(lines: List<Feature>) = mutex.withLock {
        navigoneLinesCache = lines
        navigoneLinesTimestamp = System.currentTimeMillis()

        prefs.edit { putLong(KEY_NAVIGONE_LINES_TIMESTAMP, navigoneLinesTimestamp) }
        writeToCompressedFile(FILE_NAVIGONE_LINES, lines.sanitizeForSerialization())
    }

    /**
     * Retrieves Navigone lines from cache
     */
    suspend fun getNavigoneLines(): List<Feature>? = mutex.withLock {
        // Check memory cache first
        if (navigoneLinesCache != null && isTimestampValid(navigoneLinesTimestamp)) {
            if (isInvalidLineCache(navigoneLinesCache.orEmpty())) {
                navigoneLinesCache = null
                navigoneLinesTimestamp = 0L
                invalidateLineCache(FILE_NAVIGONE_LINES, KEY_NAVIGONE_LINES_TIMESTAMP)
                return@withLock null
            }
            return@withLock navigoneLinesCache
        }

        // Otherwise, load from disk
        val timestamp = prefs.getLong(KEY_NAVIGONE_LINES_TIMESTAMP, 0)
        if (isTimestampValid(timestamp)) {
            val lines = readFromCompressedFile<List<Feature>>(FILE_NAVIGONE_LINES)
            if (lines != null) {
                if (isInvalidLineCache(lines)) {
                    invalidateLineCache(FILE_NAVIGONE_LINES, KEY_NAVIGONE_LINES_TIMESTAMP)
                    return@withLock null
                }
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

        prefs.edit { putLong(KEY_TRAMBUS_LINES_TIMESTAMP, trambusLinesTimestamp) }
        writeToCompressedFile(FILE_TRAMBUS_LINES, lines.sanitizeForSerialization())
    }

    /**
     * Retrieves Trambus lines from cache
     */
    suspend fun getTrambusLines(): List<Feature>? = mutex.withLock {
        // Check memory cache first
        if (trambusLinesCache != null && isTimestampValid(trambusLinesTimestamp)) {
            if (isInvalidLineCache(trambusLinesCache.orEmpty())) {
                trambusLinesCache = null
                trambusLinesTimestamp = 0L
                invalidateLineCache(FILE_TRAMBUS_LINES, KEY_TRAMBUS_LINES_TIMESTAMP)
                return@withLock null
            }
            return@withLock trambusLinesCache
        }

        // Otherwise, load from disk
        val timestamp = prefs.getLong(KEY_TRAMBUS_LINES_TIMESTAMP, 0)
        if (isTimestampValid(timestamp)) {
            val lines = readFromCompressedFile<List<Feature>>(FILE_TRAMBUS_LINES)
            if (lines != null) {
                if (isInvalidLineCache(lines)) {
                    invalidateLineCache(FILE_TRAMBUS_LINES, KEY_TRAMBUS_LINES_TIMESTAMP)
                    return@withLock null
                }
                trambusLinesCache = lines
                trambusLinesTimestamp = timestamp
                return@withLock lines
            }
        }

        null
    }

    /**
     * Saves stops to cache
     */
    override suspend fun saveStops(stops: List<StopFeature>) {
        mutex.withLock {
            stopsCache = stops
            stopsTimestamp = System.currentTimeMillis()

            prefs.edit { putLong(KEY_STOPS_TIMESTAMP, stopsTimestamp) }
            writeToCompressedFile(FILE_STOPS, stops.sanitizeStopsForSerialization())
        }
    }

    /**
     * Retrieves stops from cache
     */
    override suspend fun getStops(): List<StopFeature>? = mutex.withLock {
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
    override suspend fun preloadFromDisk() {
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
}