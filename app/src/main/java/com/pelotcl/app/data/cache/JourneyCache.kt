package com.pelotcl.app.data.cache

import android.content.Context
import android.util.Log
import android.util.LruCache
import com.pelotcl.app.data.repository.IntermediateStop
import com.pelotcl.app.data.repository.JourneyLeg
import com.pelotcl.app.data.repository.JourneyResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Calendar
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Multi-level cache for journey results:
 * - Level 1: In-memory LRU cache (fast, limited size)
 * - Level 2: Disk cache with daily validity (persists across app restarts)
 *
 * Cache invalidation strategy:
 * - Memory cache: 30 minutes validity
 * - Disk cache: Valid until midnight (journeys are day-specific due to schedules)
 * - Manual invalidation when GTFS data is updated
 */
class JourneyCache private constructor(context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val cacheDir = File(context.cacheDir, "journey_cache").also { it.mkdirs() }
    private val mutex = Mutex()

    // Level 1: Memory cache (50 entries, ~30min validity)
    private val memoryCache = LruCache<String, CachedJourney>(50)

    // Track today's date for cache invalidation
    @Volatile
    private var cachedDate: Int = getCurrentDayOfYear()

    companion object {
        private const val TAG = "JourneyCache"

        // Memory cache validity: 30 minutes
        private const val MEMORY_CACHE_VALIDITY_MS = 30 * 60 * 1000L

        // Disk cache file prefix
        private const val CACHE_FILE_PREFIX = "journey_"
        private const val CACHE_FILE_SUFFIX = ".json.gz"

        // Maximum disk cache size: 5MB
        private const val MAX_DISK_CACHE_SIZE_BYTES = 5 * 1024 * 1024L

        // Maximum entries on disk (prevents unbounded growth)
        private const val MAX_DISK_ENTRIES = 200

        @Volatile
        private var INSTANCE: JourneyCache? = null

        // Coroutine scope for background operations
        private val cacheScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun getInstance(context: Context): JourneyCache {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: JourneyCache(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }

        /**
         * Clear all caches (call when GTFS data is updated)
         * Note: If no cache instance exists yet, this operation is silently skipped
         */
        fun clearAllCaches() {
            cacheScope.launch {
                val instance = INSTANCE
                if (instance != null) {
                    instance.clearAll()
                } else {
                    Log.d(TAG, "clearAllCaches: No cache instance to clear")
                }
            }
        }
    }

    /**
     * Get journeys from cache (memory first, then disk)
     * Returns null if not found or expired
     */
    suspend fun get(cacheKey: String): List<JourneyResult>? {
        // Check if day changed (invalidate all caches at midnight)
        checkDateChange()

        // Level 1: Check memory cache
        val memoryCached = memoryCache.get(cacheKey)
        if (memoryCached != null) {
            val age = System.currentTimeMillis() - memoryCached.timestamp
            if (age < MEMORY_CACHE_VALIDITY_MS) {
                Log.d(TAG, "Memory cache HIT for $cacheKey (age: ${age / 1000}s)")
                return memoryCached.journeys.map { it.toJourneyResult() }
            } else {
                // Expired, remove from memory
                memoryCache.remove(cacheKey)
            }
        }

        // Level 2: Check disk cache
        return withContext(Dispatchers.IO) {
            val diskResult = readFromDisk(cacheKey)
            if (diskResult != null) {
                Log.d(TAG, "Disk cache HIT for $cacheKey")
                // Promote to memory cache
                memoryCache.put(cacheKey, CachedJourney(
                    journeys = diskResult.map { SerializableJourneyResult.fromJourneyResult(it) },
                    timestamp = System.currentTimeMillis()
                ))
                diskResult
            } else {
                Log.d(TAG, "Cache MISS for $cacheKey")
                null
            }
        }
    }

    /**
     * Store journeys in both memory and disk cache
     */
    suspend fun put(cacheKey: String, journeys: List<JourneyResult>) {
        if (journeys.isEmpty()) return

        val serializableJourneys = journeys.map { SerializableJourneyResult.fromJourneyResult(it) }
        val cachedJourney = CachedJourney(
            journeys = serializableJourneys,
            timestamp = System.currentTimeMillis()
        )

        // Level 1: Store in memory
        memoryCache.put(cacheKey, cachedJourney)

        // Level 2: Store on disk asynchronously
        withContext(Dispatchers.IO) {
            writeToDisk(cacheKey, serializableJourneys)
        }
    }

    /**
     * Preload disk cache entries into memory (call at startup)
     */
    suspend fun preloadToMemory() = withContext(Dispatchers.IO) {
        checkDateChange()

        try {
            val files = cacheDir.listFiles { file ->
                file.name.startsWith(CACHE_FILE_PREFIX) && file.name.endsWith(CACHE_FILE_SUFFIX)
            } ?: return@withContext

            // Load most recent files first (by modification time)
            val sortedFiles = files.sortedByDescending { it.lastModified() }.take(30)

            var loadedCount = 0
            for (file in sortedFiles) {
                try {
                    val cacheKey = file.name
                        .removePrefix(CACHE_FILE_PREFIX)
                        .removeSuffix(CACHE_FILE_SUFFIX)
                        .replace("_", "|") // Restore key format

                    // Skip if already in memory
                    if (memoryCache.get(cacheKey) != null) continue

                    val journeys = readFromDiskFile(file)
                    if (journeys != null) {
                        memoryCache.put(cacheKey, CachedJourney(
                            journeys = journeys.map { SerializableJourneyResult.fromJourneyResult(it) },
                            timestamp = System.currentTimeMillis()
                        ))
                        loadedCount++
                    }
                } catch (e: Exception) {
                    // Skip corrupted files
                    Log.w(TAG, "Failed to load cache file ${file.name}: ${e.message}")
                }
            }

            Log.d(TAG, "Preloaded $loadedCount journey cache entries to memory")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to preload journey cache: ${e.message}")
        }
    }

    /**
     * Clear all caches (memory and disk)
     */
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        mutex.withLock {
            memoryCache.evictAll()
            cacheDir.listFiles()?.forEach { it.delete() }
            Log.d(TAG, "All journey caches cleared")
        }
    }

    /**
     * Clear only today's expired entries (call periodically)
     */
    suspend fun cleanupExpired() = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val files = cacheDir.listFiles() ?: return@withContext
                val now = System.currentTimeMillis()
                val oneDayMs = 24 * 60 * 60 * 1000L

                var deletedCount = 0
                for (file in files) {
                    // Delete files older than 24 hours
                    if (now - file.lastModified() > oneDayMs) {
                        file.delete()
                        deletedCount++
                    }
                }

                // Also enforce size limit
                enforceDiskSizeLimit()

                if (deletedCount > 0) {
                    Log.d(TAG, "Cleaned up $deletedCount expired cache files")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Cleanup failed: ${e.message}")
            }
        }
    }

    private fun checkDateChange() {
        val today = getCurrentDayOfYear()
        if (today != cachedDate) {
            Log.d(TAG, "Date changed, invalidating memory cache")
            memoryCache.evictAll()
            cachedDate = today
            // Note: Disk cache will be cleaned up by cleanupExpired()
        }
    }

    private fun getCurrentDayOfYear(): Int {
        val calendar = Calendar.getInstance()
        return calendar.get(Calendar.DAY_OF_YEAR) + calendar.get(Calendar.YEAR) * 1000
    }

    private fun getCacheFile(cacheKey: String): File {
        // Sanitize key for filename using URL encoding to handle all invalid characters
        val safeKey = URLEncoder.encode(cacheKey, StandardCharsets.UTF_8)
            .replace("+", "_") // Replace + with _ for readability
        return File(cacheDir, "$CACHE_FILE_PREFIX$safeKey$CACHE_FILE_SUFFIX")
    }

    private suspend fun writeToDisk(cacheKey: String, journeys: List<SerializableJourneyResult>) {
        mutex.withLock {
            try {
                val file = getCacheFile(cacheKey)
                val jsonString = json.encodeToString(journeys)

                GZIPOutputStream(FileOutputStream(file).buffered()).use { gzip ->
                    gzip.write(jsonString.toByteArray(Charsets.UTF_8))
                }

                // Enforce disk size limit after writing
                enforceDiskSizeLimit()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to write cache to disk: ${e.message}")
            }
        }
    }

    private fun readFromDisk(cacheKey: String): List<JourneyResult>? {
        return try {
            val file = getCacheFile(cacheKey)
            readFromDiskFile(file)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read cache from disk: ${e.message}")
            null
        }
    }

    private fun readFromDiskFile(file: File): List<JourneyResult>? {
        if (!file.exists()) return null

        return try {
            val jsonString = GZIPInputStream(FileInputStream(file).buffered()).use { gzip ->
                gzip.bufferedReader(Charsets.UTF_8).readText()
            }
            val serializableJourneys = json.decodeFromString<List<SerializableJourneyResult>>(jsonString)
            serializableJourneys.map { it.toJourneyResult() }
        } catch (_: Exception) {
            // Delete corrupted file
            file.delete()
            null
        }
    }

    private fun enforceDiskSizeLimit() {
        try {
            val files = cacheDir.listFiles()?.toMutableList() ?: return

            // Check entry count limit
            if (files.size > MAX_DISK_ENTRIES) {
                // Delete oldest files
                files.sortBy { it.lastModified() }
                val toDelete = files.take(files.size - MAX_DISK_ENTRIES)
                toDelete.forEach { it.delete() }
                files.removeAll(toDelete.toSet())
            }

            // Check size limit
            var totalSize = files.sumOf { it.length() }
            if (totalSize > MAX_DISK_CACHE_SIZE_BYTES) {
                // Delete oldest files until under limit
                files.sortBy { it.lastModified() }
                for (file in files) {
                    if (totalSize <= MAX_DISK_CACHE_SIZE_BYTES) break
                    totalSize -= file.length()
                    file.delete()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to enforce disk size limit: ${e.message}")
        }
    }

    /**
     * Get cache statistics for debugging
     */
    fun getStats(): CacheStats {
        val diskFiles = cacheDir.listFiles() ?: emptyArray()
        val diskSize = diskFiles.sumOf { it.length() }
        return CacheStats(
            memoryEntries = memoryCache.size(),
            diskEntries = diskFiles.size,
            diskSizeBytes = diskSize
        )
    }

    data class CacheStats(
        val memoryEntries: Int,
        val diskEntries: Int,
        val diskSizeBytes: Long
    ) {
        val diskSizeKB: Long get() = diskSizeBytes / 1024
    }
}

/**
 * Wrapper for cached journey with timestamp
 */
private data class CachedJourney(
    val journeys: List<SerializableJourneyResult>,
    val timestamp: Long
)

/**
 * Serializable version of JourneyResult for JSON storage
 */
@Serializable
private data class SerializableJourneyResult(
    val departureTime: Int,
    val arrivalTime: Int,
    val legs: List<SerializableJourneyLeg>
) {
    fun toJourneyResult() = JourneyResult(
        departureTime = departureTime,
        arrivalTime = arrivalTime,
        legs = legs.map { it.toJourneyLeg() }
    )

    companion object {
        fun fromJourneyResult(result: JourneyResult) = SerializableJourneyResult(
            departureTime = result.departureTime,
            arrivalTime = result.arrivalTime,
            legs = result.legs.map { SerializableJourneyLeg.fromJourneyLeg(it) }
        )
    }
}

@Serializable
private data class SerializableJourneyLeg(
    val fromStopId: String,
    val fromStopName: String,
    val toStopId: String,
    val toStopName: String,
    val departureTime: Int,
    val arrivalTime: Int,
    val routeName: String?,
    val routeColor: String?,
    val isWalking: Boolean,
    val direction: String?,
    val intermediateStops: List<SerializableIntermediateStop>
) {
    fun toJourneyLeg() = JourneyLeg(
        fromStopId = fromStopId,
        fromStopName = fromStopName,
        toStopId = toStopId,
        toStopName = toStopName,
        departureTime = departureTime,
        arrivalTime = arrivalTime,
        routeName = routeName,
        routeColor = routeColor,
        isWalking = isWalking,
        direction = direction,
        intermediateStops = intermediateStops.map { it.toIntermediateStop() }
    )

    companion object {
        fun fromJourneyLeg(leg: JourneyLeg) = SerializableJourneyLeg(
            fromStopId = leg.fromStopId,
            fromStopName = leg.fromStopName,
            toStopId = leg.toStopId,
            toStopName = leg.toStopName,
            departureTime = leg.departureTime,
            arrivalTime = leg.arrivalTime,
            routeName = leg.routeName,
            routeColor = leg.routeColor,
            isWalking = leg.isWalking,
            direction = leg.direction,
            intermediateStops = leg.intermediateStops.map { SerializableIntermediateStop.fromIntermediateStop(it) }
        )
    }
}

@Serializable
private data class SerializableIntermediateStop(
    val stopName: String,
    val arrivalTime: Int
) {
    fun toIntermediateStop() = IntermediateStop(
        stopName = stopName,
        arrivalTime = arrivalTime
    )

    companion object {
        fun fromIntermediateStop(stop: IntermediateStop) = SerializableIntermediateStop(
            stopName = stop.stopName,
            arrivalTime = stop.arrivalTime
        )
    }
}
