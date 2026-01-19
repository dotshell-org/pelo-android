package com.pelotcl.app.data.repository

import android.content.Context
import android.util.Log
import android.util.LruCache
import io.raptor.RaptorLibrary
import io.raptor.model.Stop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.util.Calendar
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Repository to handle raptor-kt route calculations.
 * Uses lazy initialization - Raptor library is only loaded when first needed,
 * not at app startup, to improve initial loading time.
 *
 * Performance optimizations:
 * - LRU cache for journey results to avoid repeated calculations
 * - HashMap indexes for O(1) stop lookups by ID and index
 * - Pre-computed normalized name index for fast search
 * - Buffered I/O for asset loading
 */
class RaptorRepository(private val context: Context) {

    private var raptorLibrary: RaptorLibrary? = null
    private var stopsCache: List<Stop> = emptyList()
    private val mutex = Mutex()

    // Performance: HashMap index for O(1) stop lookup by index position
    private var stopsByIndex: Map<Int, Stop> = emptyMap()

    // Performance: Pre-computed normalized name index for fast search
    private var stopsByNormalizedName: Map<String, List<Stop>> = emptyMap()

    companion object {
        // Set to false for production builds to reduce log overhead
        private const val DEBUG_LOGGING = false

        // LRU Cache for journey results: key = "origin|dest|time"
        // Typical usage: 20-30 recent searches, each result ~5-10KB
        private val journeyCache = LruCache<String, List<JourneyResult>>(30)

        // Cache validity: 5 minutes (schedules may change throughout the day)
        private const val JOURNEY_CACHE_VALIDITY_MS = 5 * 60 * 1000L
        private val journeyCacheTimestamps = mutableMapOf<String, Long>()

        /**
         * Clear journey cache (call when underlying data changes)
         */
        fun clearJourneyCache() {
            journeyCache.evictAll()
            journeyCacheTimestamps.clear()
        }
    }

    @Volatile
    private var isInitialized = false

    @Volatile
    private var isInitializing = false

    /**
     * Initialize the Raptor library with stops.bin and routes.bin from assets.
     * This is called lazily on first use, not at startup.
     * Uses buffered I/O and builds performance indexes.
     */
    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        // Fast path: already initialized
        if (isInitialized && raptorLibrary != null) {
            return@withContext Result.success(Unit)
        }

        mutex.withLock {
            // Double-check after acquiring lock
            if (isInitialized && raptorLibrary != null) {
                return@withContext Result.success(Unit)
            }

            if (isInitializing) {
                // Another coroutine is initializing, wait and return
                return@withContext Result.success(Unit)
            }

            isInitializing = true

            try {
                val startTime = System.currentTimeMillis()

                // Use BufferedInputStream for faster asset loading
                val stopsInputStream = BufferedInputStream(context.assets.open("stops.bin"), 8192)
                val routesInputStream = BufferedInputStream(context.assets.open("routes.bin"), 8192)

                raptorLibrary = RaptorLibrary(
                    stopsInputStream = stopsInputStream,
                    routesInputStream = routesInputStream
                )
                
                // Cache all stops for lookup
                stopsCache = raptorLibrary?.searchStopsByName("") ?: emptyList()

                // Build performance indexes
                buildStopIndexes()

                isInitialized = true

                val elapsed = System.currentTimeMillis() - startTime
                Log.d("RaptorRepository", "Raptor library initialized in ${elapsed}ms with ${stopsCache.size} stops")

                // Debug logs only when DEBUG_LOGGING is enabled
                if (DEBUG_LOGGING) {
                    stopsCache.take(5).forEachIndexed { index, stop ->
                        Log.d("RaptorRepository", "Stop at index $index: id=${stop.id}, name='${stop.name}'")
                    }

                    // Test: try a simple path calculation with first and last stops
                    if (stopsCache.size >= 2) {
                        val testOrigin = listOf(stopsCache.first().id)
                        val testDest = listOf(stopsCache.last().id)
                        Log.d("RaptorRepository", "Test path: from ${stopsCache.first().name}(${stopsCache.first().id}) to ${stopsCache.last().name}(${stopsCache.last().id}) at 9:00")
                        val testResult = raptorLibrary?.getOptimizedPaths(testOrigin, testDest, 9 * 3600)
                        Log.d("RaptorRepository", "Test result: ${testResult?.size ?: 0} journeys")
                    }
                }

                Result.success(Unit)
            } catch (e: Exception) {
                Log.e("RaptorRepository", "Failed to initialize Raptor library: ${e.message}", e)
                Result.failure(e)
            } finally {
                isInitializing = false
            }
        }
    }

    /**
     * Build HashMap indexes for O(1) stop lookups.
     * Called once during initialization.
     */
    private fun buildStopIndexes() {
        // Index by position (for leg.fromStopIndex / leg.toStopIndex lookups)
        stopsByIndex = stopsCache.mapIndexed { index, stop -> index to stop }.toMap()

        // Index by normalized name for fast search
        stopsByNormalizedName = stopsCache.groupBy { normalizeStopName(it.name) }

        if (DEBUG_LOGGING) {
            Log.d("RaptorRepository", "Built indexes: ${stopsByIndex.size} by index, ${stopsByNormalizedName.size} unique normalized names")
        }
    }

    /**
     * Check if Raptor is initialized without triggering initialization
     */
    fun isReady(): Boolean = isInitialized && raptorLibrary != null

    /**
     * Ensure initialized before use (internal helper)
     */
    private suspend fun ensureInitialized() {
        if (!isInitialized) {
            initialize()
        }
    }

    /**
     * Search for stops by name with multiple strategies for better matching.
     * Uses pre-computed normalized name index for fast lookups.
     */
    suspend fun searchStopsByName(query: String): List<RaptorStop> = withContext(Dispatchers.IO) {
        ensureInitialized()
        try {
            // First try: exact search via library
            var results = raptorLibrary?.searchStopsByName(query)?.map { stop ->
                RaptorStop(
                    id = stop.id,
                    name = stop.name,
                    lat = stop.lat,
                    lon = stop.lon
                )
            } ?: emptyList()

            if (DEBUG_LOGGING) {
                Log.d("RaptorRepository", "searchStopsByName('$query') - library search found ${results.size} stops")
            }

            // If no results, try searching in our pre-computed index with flexible matching
            if (results.isEmpty() && stopsByNormalizedName.isNotEmpty()) {
                val normalizedQuery = normalizeStopName(query)

                // O(1) exact normalized match using pre-computed index
                val exactMatches = stopsByNormalizedName[normalizedQuery]
                if (exactMatches != null) {
                    results = exactMatches.map { stop ->
                        RaptorStop(id = stop.id, name = stop.name, lat = stop.lat, lon = stop.lon)
                    }
                    if (DEBUG_LOGGING) {
                        Log.d("RaptorRepository", "searchStopsByName('$query') - index exact match found ${results.size} stops")
                    }
                }

                // If still no results, try contains match (fallback, slower)
                if (results.isEmpty()) {
                    results = stopsByNormalizedName.entries
                        .filter { (normalizedName, _) ->
                            normalizedName.contains(normalizedQuery) || normalizedQuery.contains(normalizedName)
                        }
                        .flatMap { (_, stops) -> stops }
                        .map { stop ->
                            RaptorStop(id = stop.id, name = stop.name, lat = stop.lat, lon = stop.lon)
                        }
                    if (DEBUG_LOGGING) {
                        Log.d("RaptorRepository", "searchStopsByName('$query') - index contains match found ${results.size} stops")
                    }
                }
            }

            if (DEBUG_LOGGING) {
                Log.d("RaptorRepository", "searchStopsByName('$query') final: ${results.size} stops - ${results.take(5).map { "${it.name}(${it.id})" }}")
            }
            results
        } catch (e: Exception) {
            Log.e("RaptorRepository", "Error searching stops: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Normalize stop name for comparison (remove accents, lowercase, remove special chars)
     */
    private fun normalizeStopName(name: String): String {
        return name
            .lowercase()
            .replace(Regex("[àáâãäå]"), "a")
            .replace(Regex("[èéêë]"), "e")
            .replace(Regex("[ìíîï]"), "i")
            .replace(Regex("[òóôõö]"), "o")
            .replace(Regex("[ùúûü]"), "u")
            .replace(Regex("[ýÿ]"), "y")
            .replace(Regex("[ç]"), "c")
            .replace(Regex("[^a-z0-9]"), "")
    }

    /**
     * Find the closest stop to the given GPS coordinates
     */
    suspend fun findClosestStop(latitude: Double, longitude: Double): RaptorStop? = withContext(Dispatchers.IO) {
        ensureInitialized()
        try {
            // Find the closest stop by calculating distance
            stopsCache.minByOrNull { stop ->
                val latDiff = stop.lat - latitude
                val lonDiff = stop.lon - longitude
                sqrt(latDiff.pow(2) + lonDiff.pow(2))
            }?.let { stop ->
                RaptorStop(
                    id = stop.id,
                    name = stop.name,
                    lat = stop.lat,
                    lon = stop.lon
                )
            }
        } catch (e: Exception) {
            Log.e("RaptorRepository", "Error finding closest stop: ${e.message}", e)
            null
        }
    }

    /**
     * Calculate optimized journeys between origin and destination stops.
     * Uses LRU cache to avoid repeated calculations for same origin/destination.
     *
     * @param originStopIds List of origin stop IDs (to handle stops with multiple platforms)
     * @param destinationStopIds List of destination stop IDs
     * @param departureTimeSeconds Departure time in seconds from midnight (default: current time)
     */
    suspend fun getOptimizedPaths(
        originStopIds: List<Int>,
        destinationStopIds: List<Int>,
        departureTimeSeconds: Int? = null
    ): List<JourneyResult> = withContext(Dispatchers.IO) {
        ensureInitialized()
        try {
            val depTime = departureTimeSeconds ?: getCurrentTimeInSeconds()
            
            // Round departure time to 5-minute intervals for better cache hits
            val roundedDepTime = (depTime / 300) * 300

            // Check LRU cache first
            val cacheKey = buildCacheKey(originStopIds, destinationStopIds, roundedDepTime)
            val cachedResult = journeyCache.get(cacheKey)
            val cacheTimestamp = journeyCacheTimestamps[cacheKey]

            if (cachedResult != null && cacheTimestamp != null) {
                val cacheAge = System.currentTimeMillis() - cacheTimestamp
                if (cacheAge < JOURNEY_CACHE_VALIDITY_MS) {
                    if (DEBUG_LOGGING) {
                        Log.d("RaptorRepository", "getOptimizedPaths: Cache HIT for $cacheKey (age: ${cacheAge}ms)")
                    }
                    return@withContext cachedResult
                }
            }

            if (DEBUG_LOGGING) {
                Log.d("RaptorRepository", "getOptimizedPaths: origin=$originStopIds, dest=$destinationStopIds, time=$depTime (${formatTimeFromSeconds(depTime)})")
            }

            if (originStopIds.isEmpty()) {
                Log.w("RaptorRepository", "getOptimizedPaths: originStopIds is empty!")
                return@withContext emptyList()
            }
            if (destinationStopIds.isEmpty()) {
                Log.w("RaptorRepository", "getOptimizedPaths: destinationStopIds is empty!")
                return@withContext emptyList()
            }

            // getOptimizedPaths returns List<List<JourneyLeg>> - list of journeys
            val journeys = raptorLibrary?.getOptimizedPaths(
                originStopIds = originStopIds,
                destinationStopIds = destinationStopIds,
                departureTime = depTime
            ) ?: emptyList()

            if (DEBUG_LOGGING) {
                Log.d("RaptorRepository", "getOptimizedPaths: Raptor returned ${journeys.size} raw journeys")
            }

            val results = journeys.mapNotNull { legs ->
                if (legs.isEmpty()) {
                    return@mapNotNull null
                }

                val journeyLegs = legs.mapNotNull { leg ->
                    // Use HashMap index for O(1) stop lookup instead of list.getOrNull()
                    val fromStop = stopsByIndex[leg.fromStopIndex]
                    val toStop = stopsByIndex[leg.toStopIndex]

                    if (fromStop == null || toStop == null) {
                        if (DEBUG_LOGGING) {
                            Log.w("RaptorRepository", "getOptimizedPaths: Stop not found - fromIdx=${leg.fromStopIndex}, toIdx=${leg.toStopIndex}")
                        }
                        return@mapNotNull null
                    }

                    // Map intermediate stops using HashMap index
                    val intermediateStops = leg.intermediateStopIndices.mapIndexedNotNull { idx, stopIndex ->
                        val stop = stopsByIndex[stopIndex]
                        val arrivalTime = leg.intermediateArrivalTimes.getOrNull(idx)
                        if (stop != null && arrivalTime != null) {
                            IntermediateStop(
                                stopName = stop.name,
                                arrivalTime = arrivalTime
                            )
                        } else null
                    }
                    
                    JourneyLeg(
                        fromStopId = fromStop.id.toString(),
                        fromStopName = fromStop.name,
                        toStopId = toStop.id.toString(),
                        toStopName = toStop.name,
                        departureTime = leg.departureTime,
                        arrivalTime = leg.arrivalTime,
                        routeName = leg.routeName,
                        routeColor = null, // Library doesn't provide color
                        isWalking = leg.isTransfer,
                        direction = leg.direction,
                        intermediateStops = intermediateStops
                    )
                }
                
                if (journeyLegs.isEmpty()) {
                    return@mapNotNull null
                }

                JourneyResult(
                    departureTime = legs.first().departureTime,
                    arrivalTime = legs.last().arrivalTime,
                    legs = journeyLegs
                )
            }

            // Store results in cache
            if (results.isNotEmpty()) {
                journeyCache.put(cacheKey, results)
                journeyCacheTimestamps[cacheKey] = System.currentTimeMillis()
            }

            if (DEBUG_LOGGING) {
                Log.d("RaptorRepository", "getOptimizedPaths: Returning ${results.size} journey results")
            }
            results
        } catch (e: Exception) {
            Log.e("RaptorRepository", "Error calculating paths: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Build a cache key for journey results.
     * Sorts IDs to ensure same key regardless of order.
     */
    private fun buildCacheKey(originIds: List<Int>, destIds: List<Int>, time: Int): String {
        val sortedOrigin = originIds.sorted().joinToString(",")
        val sortedDest = destIds.sorted().joinToString(",")
        return "$sortedOrigin|$sortedDest|$time"
    }

    private fun formatTimeFromSeconds(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        return "%02d:%02d".format(hours, minutes)
    }


    private fun getCurrentTimeInSeconds(): Int {
        val calendar = Calendar.getInstance()
        val hours = calendar.get(Calendar.HOUR_OF_DAY)
        val minutes = calendar.get(Calendar.MINUTE)
        val seconds = calendar.get(Calendar.SECOND)
        return hours * 3600 + minutes * 60 + seconds
    }
}

/**
 * Data class representing a stop from Raptor
 */
data class RaptorStop(
    val id: Int,
    val name: String,
    val lat: Double = 0.0,
    val lon: Double = 0.0
)

/**
 * Data class representing a journey result
 */
data class JourneyResult(
    val departureTime: Int, // in seconds from midnight
    val arrivalTime: Int,   // in seconds from midnight
    val legs: List<JourneyLeg>
) {
    val durationMinutes: Int
        get() = (arrivalTime - departureTime) / 60

    fun formatDepartureTime(): String = formatTime(departureTime)
    fun formatArrivalTime(): String = formatTime(arrivalTime)

    private fun formatTime(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        return String.format("%02d:%02d", hours, minutes)
    }
}

/**
 * Data class representing an intermediate stop
 */
data class IntermediateStop(
    val stopName: String,
    val arrivalTime: Int
) {
    fun formatArrivalTime(): String {
        val hours = arrivalTime / 3600
        val minutes = (arrivalTime % 3600) / 60
        return String.format("%02d:%02d", hours, minutes)
    }
}

/**
 * Data class representing a leg of a journey
 */
data class JourneyLeg(
    val fromStopId: String,
    val fromStopName: String,
    val toStopId: String,
    val toStopName: String,
    val departureTime: Int,
    val arrivalTime: Int,
    val routeName: String?,
    val routeColor: String?,
    val isWalking: Boolean,
    val direction: String? = null,
    val intermediateStops: List<IntermediateStop> = emptyList()
) {
    val durationMinutes: Int
        get() = (arrivalTime - departureTime) / 60

    fun formatDepartureTime(): String = formatTime(departureTime)
    fun formatArrivalTime(): String = formatTime(arrivalTime)

    private fun formatTime(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        return String.format("%02d:%02d", hours, minutes)
    }
}
