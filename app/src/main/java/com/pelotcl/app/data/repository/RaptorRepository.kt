package com.pelotcl.app.data.repository

import android.content.Context
import android.util.Log
import android.util.LruCache
import com.pelotcl.app.data.cache.JourneyCache
import com.pelotcl.app.utils.SearchUtils
import io.raptor.PeriodData
import io.raptor.RaptorLibrary
import io.raptor.model.Stop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Repository to handle raptor-kt route calculations.
 * Uses lazy initialization - Raptor library is only loaded when first needed,
 * not at app startup, to improve initial loading time.
 *
 * Supports multiple schedule periods:
 * - saturday: Saturday schedules
 * - sunday: Sunday and public holiday schedules
 * - school_on_weekdays: Weekday schedules during school periods
 * - school_off_weekdays: Weekday schedules during school holidays
 *
 * Performance optimizations:
 * - Multi-level cache: Memory LRU (30min) -> Disk cache (daily) -> Raptor calculation
 * - HashMap indexes for O(1) stop lookups by ID and index
 * - Pre-computed normalized name index for fast search
 * - Buffered I/O for asset loading
 * - Singleton pattern to avoid multiple initializations
 * - Dispatchers.Default for CPU-bound Raptor calculations (optimized thread pool)
 * - Reusable StringBuilder for cache key building (reduces GC pressure)
 * - Pre-allocated ArrayLists for result mapping (avoids resizing)
 */
class RaptorRepository private constructor(private val context: Context) {

    private var raptorLibrary: RaptorLibrary? = null
    private var stopsCache: List<Stop> = emptyList()
    private val mutex = Mutex()

    // School holidays data parsed from assets
    private var schoolHolidays: List<HolidayPeriod> = emptyList()

    // Multi-level disk cache for journey persistence
    private val journeyDiskCache: JourneyCache by lazy { JourneyCache.getInstance(context) }

    // Performance: HashMap index for O(1) stop lookup by index position
    private var stopsByIndex: Map<Int, Stop> = emptyMap()

    // Performance: Cache of normalized stop names to avoid repeated normalization during search
    private var normalizedStopNames: Map<Stop, String> = emptyMap()

    // Performance: Reusable StringBuilder for cache key building (ThreadLocal for thread safety)
    private val cacheKeyBuilder = ThreadLocal.withInitial { StringBuilder(64) }

    // Period IDs matching asset file naming
    companion object {
        private const val TAG = "RaptorRepository"

        // Set to false for production builds to reduce log overhead
        private const val DEBUG_LOGGING = false

        // Period constants
        private const val PERIOD_SATURDAY = "saturday"
        private const val PERIOD_SUNDAY = "sunday"
        private const val PERIOD_SCHOOL_ON_WEEKDAYS = "school_on_weekdays"
        private const val PERIOD_SCHOOL_OFF_WEEKDAYS = "school_off_weekdays"

        // LRU Cache for journey results: key = "origin|dest|time"
        // Level 1 cache: 50 entries in memory with 30-minute validity
        private val journeyCache = LruCache<String, List<JourneyResult>>(50)

        // Cache validity: 30 minutes (increased from 5min for better hit rate)
        private const val JOURNEY_CACHE_VALIDITY_MS = 30 * 60 * 1000L
        private val journeyCacheTimestamps = mutableMapOf<String, Long>()

        // Singleton instance - uses applicationContext so no memory leak
        // StaticFieldLeak is safe here because we only store applicationContext (not Activity context)
        // The instance lifecycle matches the application lifecycle, and applicationContext doesn't
        // hold references to any Activity, preventing memory leaks
        @Suppress("StaticFieldLeak")
        @Volatile
        private var INSTANCE: RaptorRepository? = null

        /**
         * Get singleton instance of RaptorRepository.
         * Creates instance on first call, returns existing instance on subsequent calls.
         */
        fun getInstance(context: Context): RaptorRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RaptorRepository(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }

        /**
         * Clear all journey caches (memory and disk).
         * Call when underlying GTFS data changes.
         */
        @Suppress("unused") // Public API for cache invalidation when GTFS data is updated
        fun clearJourneyCache() {
            journeyCache.evictAll()
            journeyCacheTimestamps.clear()
            JourneyCache.clearAllCaches()
        }

        /**
         * Check if instance exists without creating it
         */
        @Suppress("unused") // Public API to check singleton state
        fun hasInstance(): Boolean = INSTANCE != null
    }

    @Volatile
    private var isInitialized = false

    @Volatile
    private var isInitializing = false

    /**
     * Initialize the Raptor library with all period data from assets.
     * This is called lazily on first use, not at startup.
     * Uses buffered I/O and builds performance indexes.
     * Loads all schedule periods: saturday, sunday, school_on_weekdays, school_off_weekdays
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

                // Load school holidays from assets
                loadSchoolHolidays()

                // Load all period data
                val periods = listOf(
                    PERIOD_SATURDAY,
                    PERIOD_SUNDAY,
                    PERIOD_SCHOOL_ON_WEEKDAYS,
                    PERIOD_SCHOOL_OFF_WEEKDAYS
                ).map { periodId ->
                    PeriodData(
                        periodId = periodId,
                        stopsInputStream = BufferedInputStream(
                            context.assets.open("stops_$periodId.bin"), 8192
                        ),
                        routesInputStream = BufferedInputStream(
                            context.assets.open("routes_$periodId.bin"), 8192
                        )
                    )
                }

                raptorLibrary = RaptorLibrary(periods)

                // Set initial period based on current day
                updatePeriodForDate(LocalDate.now())
                
                // Cache all stops for lookup (from current period)
                stopsCache = raptorLibrary?.searchStopsByName("") ?: emptyList()

                // Build performance indexes
                buildStopIndexes()

                isInitialized = true

                Log.d(TAG, "Raptor initialized in ${System.currentTimeMillis() - startTime}ms with ${periods.size} periods, current: ${raptorLibrary?.getCurrentPeriod()}")

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
     * Load school holidays data from assets/holidays.json
     */
    private fun loadSchoolHolidays() {
        try {
            val json = context.assets.open("holidays.json").bufferedReader().use { it.readText() }
            val holidaysData = Json.decodeFromString<HolidaysData>(json)
            schoolHolidays = holidaysData.holidays.mapNotNull { holiday ->
                val startDate = try {
                    LocalDate.parse(holiday.startDateInclusive, DateTimeFormatter.ISO_DATE)
                } catch (e: Exception) { null }
                
                val endDate = try {
                    holiday.endDateInclusive?.let { LocalDate.parse(it, DateTimeFormatter.ISO_DATE) }
                } catch (e: Exception) { null }
                
                if (startDate != null) {
                    HolidayPeriod(
                        name = holiday.name,
                        startDate = startDate,
                        endDate = endDate ?: startDate.plusMonths(2) // Summer holidays fallback
                    )
                } else null
            }
            Log.d(TAG, "Loaded ${schoolHolidays.size} school holiday periods")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load school holidays: ${e.message}", e)
            schoolHolidays = emptyList()
        }
    }

    /**
     * Determine if a given date falls within school holidays
     */
    private fun isSchoolHoliday(date: LocalDate): Boolean {
        return schoolHolidays.any { period ->
            !date.isBefore(period.startDate) && !date.isAfter(period.endDate)
        }
    }

    /**
     * Get the appropriate period ID for a given date
     */
    private fun getPeriodForDate(date: LocalDate): String {
        val dayOfWeek = date.dayOfWeek.value // 1 = Monday, 7 = Sunday

        return when (dayOfWeek) {
            6 -> PERIOD_SATURDAY
            7 -> PERIOD_SUNDAY
            else -> {
                if (isSchoolHoliday(date)) {
                    PERIOD_SCHOOL_OFF_WEEKDAYS
                } else {
                    PERIOD_SCHOOL_ON_WEEKDAYS
                }
            }
        }
    }

    /**
     * Update the active period based on the given date.
     * This should be called when searching for journeys to ensure
     * the correct schedule is used.
     */
    private fun updatePeriodForDate(date: LocalDate) {
        val targetPeriod = getPeriodForDate(date)
        val currentPeriod = raptorLibrary?.getCurrentPeriod()
        
        if (currentPeriod != targetPeriod) {
            raptorLibrary?.setPeriod(targetPeriod)
            Log.d(TAG, "Switched period from $currentPeriod to $targetPeriod for date $date")
            
            // Rebuild stop cache and indexes for new period
            stopsCache = raptorLibrary?.searchStopsByName("") ?: emptyList()
            buildStopIndexes()
        }
    }

    /**
     * Ensure the correct period is set for the given date before queries.
     * Called before each route calculation.
     * @param date The date to use for period selection (default: today)
     */
    private fun ensureCorrectPeriod(date: LocalDate = LocalDate.now()) {
        updatePeriodForDate(date)
    }

    /**
     * Get the current active period ID
     */
    fun getCurrentPeriod(): String? = raptorLibrary?.getCurrentPeriod()

    /**
     * Get all available period IDs
     */
    fun getAvailablePeriods(): Set<String> = raptorLibrary?.getAvailablePeriods() ?: emptySet()

    /**
     * Build HashMap indexes for O(1) stop lookups.
     * Called once during initialization.
     */
    private fun buildStopIndexes() {
        // Index by position (for leg.fromStopIndex / leg.toStopIndex lookups)
        stopsByIndex = stopsCache.mapIndexed { index, stop -> index to stop }.toMap()

        // Pre-compute normalized names for fast accent-insensitive search
        normalizedStopNames = stopsCache.associateWith { stop ->
            SearchUtils.normalizeForSearch(stop.name)
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
     * Uses Dispatchers.Default as this is CPU-bound string matching work.
     */
    suspend fun searchStopsByName(query: String): List<RaptorStop> = withContext(Dispatchers.Default) {
        ensureInitialized()
        try {
            // Pré-calcul unique de la query normalisée
            val normalizedQuery = SearchUtils.normalizeForSearch(query)
            val firstWord = normalizedQuery.split(" ").firstOrNull() ?: ""

            // Étape 1: pré-filtrage rapide sur le premier mot (utilise le cache)
            val candidates = if (firstWord.isNotEmpty()) {
                stopsCache.filter { stop ->
                    (normalizedStopNames[stop] ?: SearchUtils.normalizeForSearch(stop.name)).contains(firstWord)
                }
            } else {
                stopsCache
            }
            
            // Étape 2: fuzzy matching précis avec valeurs pré-normalisées (évite les recalculs)
            val results = candidates.filter { stop ->
                val normalizedName = normalizedStopNames[stop] ?: SearchUtils.normalizeForSearch(stop.name)
                SearchUtils.fuzzyContainsNormalized(normalizedName, normalizedQuery)
            }.map { stop ->
                val normalizedName = normalizedStopNames[stop] ?: SearchUtils.normalizeForSearch(stop.name)
                RaptorStop(
                    id = stop.id,
                    name = stop.name,
                    lat = stop.lat,
                    lon = stop.lon
                ) to normalizedName
            }.sortedWith(
                compareBy(
                    { !SearchUtils.fuzzyStartsWithNormalized(it.second, normalizedQuery) },
                    { it.first.name }
                )
            ).map { it.first }

            results
        } catch (e: Exception) {
            Log.e("RaptorRepository", "Error searching stops: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Find the closest stop to the given GPS coordinates
     * Uses Dispatchers.Default as this is CPU-bound distance calculation.
     */
    suspend fun findClosestStop(latitude: Double, longitude: Double): RaptorStop? = withContext(Dispatchers.Default) {
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
            Log.e(TAG, "Error finding closest stop: ${e.message}", e)
            null
        }
    }

    /**
     * Find the N nearest stops to the given GPS coordinates, sorted by distance.
     * Uses Dispatchers.Default as this is CPU-bound distance calculation.
     *
     * @param latitude GPS latitude
     * @param longitude GPS longitude
     * @param limit Maximum number of stops to return (default 5)
     * @return List of RaptorStop sorted by distance (closest first), with unique names
     */
    suspend fun findNearestStops(latitude: Double, longitude: Double, limit: Int = 5): List<RaptorStop> = withContext(Dispatchers.Default) {
        ensureInitialized()
        try {
            // Calculate distance for each stop and sort by distance
            stopsCache
                .map { stop ->
                    val latDiff = stop.lat - latitude
                    val lonDiff = stop.lon - longitude
                    val distance = sqrt(latDiff.pow(2) + lonDiff.pow(2))
                    stop to distance
                }
                .sortedBy { it.second }
                // Group by stop name to get unique stop names (different platforms have same name)
                .distinctBy { it.first.name }
                .take(limit)
                .map { (stop, _) ->
                    RaptorStop(
                        id = stop.id,
                        name = stop.name,
                        lat = stop.lat,
                        lon = stop.lon
                    )
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding nearest stops: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Calculate optimized journeys between origin and destination stops.
     * Uses multi-level cache: Memory LRU -> Disk cache -> Raptor calculation.
     *
     * Uses Dispatchers.Default (CPU-optimized thread pool) instead of Dispatchers.IO
     * because Raptor algorithm is CPU-bound, not I/O-bound. Default uses all CPU cores
     * while IO is limited to 64 threads optimized for blocking I/O operations.
     *
     * @param originStopIds List of origin stop IDs (to handle stops with multiple platforms)
     * @param destinationStopIds List of destination stop IDs
     * @param departureTimeSeconds Departure time in seconds from midnight (default: current time)
     * @param date The date to search for (default: today). Used to select the correct schedule period.
     */
    suspend fun getOptimizedPaths(
        originStopIds: List<Int>,
        destinationStopIds: List<Int>,
        departureTimeSeconds: Int? = null,
        date: LocalDate = LocalDate.now()
    ): List<JourneyResult> = withContext(Dispatchers.Default) {
        ensureInitialized()
        ensureCorrectPeriod(date) // Auto-select period based on selected date
        try {
            val depTime = departureTimeSeconds ?: getCurrentTimeInSeconds()
            
            // Smart time rounding: 15 min in off-peak, 5 min in peak hours
            val roundedDepTime = getRoundedDepartureTime(depTime)

            // Build cache key (includes date to ensure different periods are cached separately)
            val cacheKey = buildCacheKey(originStopIds, destinationStopIds, roundedDepTime, date)

            // Level 1: Check in-memory LRU cache
            val memoryCached = journeyCache.get(cacheKey)
            val cacheTimestamp = journeyCacheTimestamps[cacheKey]

            if (memoryCached != null && cacheTimestamp != null) {
                val cacheAge = System.currentTimeMillis() - cacheTimestamp
                if (cacheAge < JOURNEY_CACHE_VALIDITY_MS) {
                    return@withContext memoryCached
                }
            }

            // Level 2: Check disk cache (daily validity)
            val diskCached = journeyDiskCache.get(cacheKey)
            if (diskCached != null) {
                // Promote to memory cache
                journeyCache.put(cacheKey, diskCached)
                journeyCacheTimestamps[cacheKey] = System.currentTimeMillis()
                return@withContext diskCached
            }

            if (originStopIds.isEmpty()) {
                Log.w(TAG, "getOptimizedPaths: originStopIds is empty!")
                return@withContext emptyList()
            }
            if (destinationStopIds.isEmpty()) {
                Log.w(TAG, "getOptimizedPaths: destinationStopIds is empty!")
                return@withContext emptyList()
            }

            // Level 3: Calculate with Raptor
            val journeys = raptorLibrary?.getOptimizedPaths(
                originStopIds = originStopIds,
                destinationStopIds = destinationStopIds,
                departureTime = depTime
            ) ?: emptyList()

            // Performance: Pre-allocate results list with estimated capacity
            val results = ArrayList<JourneyResult>(journeys.size)

            // Use explicit for loop instead of mapNotNull to reduce lambda allocations
            for (legs in journeys) {
                if (legs.isEmpty()) continue

                // Pre-allocate journey legs list
                val journeyLegs = ArrayList<JourneyLeg>(legs.size)
                var hasInvalidLeg = false

                for (leg in legs) {
                    // Use HashMap index for O(1) stop lookup
                    val fromStop = stopsByIndex[leg.fromStopIndex]
                    val toStop = stopsByIndex[leg.toStopIndex]

                    if (fromStop == null || toStop == null) {
                        if (DEBUG_LOGGING) {
                            Log.w("RaptorRepository", "getOptimizedPaths: Stop not found - fromIdx=${leg.fromStopIndex}, toIdx=${leg.toStopIndex}")
                        }
                        hasInvalidLeg = true
                        break
                    }

                    // Map intermediate stops using explicit for loop
                    val intermediateIndices = leg.intermediateStopIndices
                    val intermediateTimes = leg.intermediateArrivalTimes
                    val intermediateStops = ArrayList<IntermediateStop>(intermediateIndices.size)

                    for (idx in intermediateIndices.indices) {
                        val stop = stopsByIndex[intermediateIndices[idx]]
                        val arrivalTime = if (idx < intermediateTimes.size) intermediateTimes[idx] else null
                        if (stop != null && arrivalTime != null) {
                            intermediateStops.add(IntermediateStop(
                                stopName = stop.name,
                                arrivalTime = arrivalTime,
                                lat = stop.lat,
                                lon = stop.lon
                            ))
                        }
                    }
                    
                    journeyLegs.add(JourneyLeg(
                        fromStopId = fromStop.id.toString(),
                        fromStopName = fromStop.name,
                        fromLat = fromStop.lat,
                        fromLon = fromStop.lon,
                        toStopId = toStop.id.toString(),
                        toStopName = toStop.name,
                        toLat = toStop.lat,
                        toLon = toStop.lon,
                        departureTime = leg.departureTime,
                        arrivalTime = leg.arrivalTime,
                        routeName = leg.routeName,
                        routeColor = null, // Library doesn't provide color
                        isWalking = leg.isTransfer,
                        direction = leg.direction,
                        intermediateStops = intermediateStops
                    ))
                }
                
                // Skip this journey if any leg was invalid
                if (hasInvalidLeg || journeyLegs.isEmpty()) continue

                results.add(JourneyResult(
                    departureTime = legs.first().departureTime,
                    arrivalTime = legs.last().arrivalTime,
                    legs = journeyLegs
                ))
            }

            // Store results in both memory and disk cache
            if (results.isNotEmpty()) {
                // Level 1: Memory cache
                journeyCache.put(cacheKey, results)
                journeyCacheTimestamps[cacheKey] = System.currentTimeMillis()

                // Level 2: Disk cache (async, fire and forget)
                journeyDiskCache.put(cacheKey, results)
            }

            results
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating paths: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Calculate optimized journeys that arrive by a specific time.
     * Useful for "I need to be there by X" scenarios.
     *
     * @param originStopIds List of origin stop IDs
     * @param destinationStopIds List of destination stop IDs  
     * @param arrivalTimeSeconds Desired arrival time in seconds from midnight
     * @param searchWindowMinutes How far back to search for departures (default: 120 minutes)
     * @param date The date to search for (default: today). Used to select the correct schedule period.
     * @return List of JourneyResult sorted by latest departure (arrive as late as possible while still being on time)
     */
    suspend fun getOptimizedPathsArriveBy(
        originStopIds: List<Int>,
        destinationStopIds: List<Int>,
        arrivalTimeSeconds: Int,
        searchWindowMinutes: Int = 120,
        date: LocalDate = LocalDate.now()
    ): List<JourneyResult> = withContext(Dispatchers.Default) {
        ensureInitialized()
        ensureCorrectPeriod(date)
        try {
            if (originStopIds.isEmpty()) {
                Log.w(TAG, "getOptimizedPathsArriveBy: originStopIds is empty!")
                return@withContext emptyList()
            }
            if (destinationStopIds.isEmpty()) {
                Log.w(TAG, "getOptimizedPathsArriveBy: destinationStopIds is empty!")
                return@withContext emptyList()
            }

            // Use raptor-kt's arrive-by search
            val journeys = raptorLibrary?.getOptimizedPathsArriveBy(
                originStopIds = originStopIds,
                destinationStopIds = destinationStopIds,
                arrivalTime = arrivalTimeSeconds,
                searchWindowMinutes = searchWindowMinutes
            ) ?: emptyList()

            // Map results using the same logic as getOptimizedPaths
            val results = ArrayList<JourneyResult>(journeys.size)

            for (legs in journeys) {
                if (legs.isEmpty()) continue

                val journeyLegs = ArrayList<JourneyLeg>(legs.size)
                var hasInvalidLeg = false

                for (leg in legs) {
                    val fromStop = stopsByIndex[leg.fromStopIndex]
                    val toStop = stopsByIndex[leg.toStopIndex]

                    if (fromStop == null || toStop == null) {
                        if (DEBUG_LOGGING) {
                            Log.w(TAG, "getOptimizedPathsArriveBy: Stop not found - fromIdx=${leg.fromStopIndex}, toIdx=${leg.toStopIndex}")
                        }
                        hasInvalidLeg = true
                        break
                    }

                    val intermediateIndices = leg.intermediateStopIndices
                    val intermediateTimes = leg.intermediateArrivalTimes
                    val intermediateStops = ArrayList<IntermediateStop>(intermediateIndices.size)

                    for (idx in intermediateIndices.indices) {
                        val stop = stopsByIndex[intermediateIndices[idx]]
                        val arrivalTime = if (idx < intermediateTimes.size) intermediateTimes[idx] else null
                        if (stop != null && arrivalTime != null) {
                            intermediateStops.add(IntermediateStop(
                                stopName = stop.name,
                                arrivalTime = arrivalTime,
                                lat = stop.lat,
                                lon = stop.lon
                            ))
                        }
                    }
                    
                    journeyLegs.add(JourneyLeg(
                        fromStopId = fromStop.id.toString(),
                        fromStopName = fromStop.name,
                        fromLat = fromStop.lat,
                        fromLon = fromStop.lon,
                        toStopId = toStop.id.toString(),
                        toStopName = toStop.name,
                        toLat = toStop.lat,
                        toLon = toStop.lon,
                        departureTime = leg.departureTime,
                        arrivalTime = leg.arrivalTime,
                        routeName = leg.routeName,
                        routeColor = null,
                        isWalking = leg.isTransfer,
                        direction = leg.direction,
                        intermediateStops = intermediateStops
                    ))
                }
                
                if (hasInvalidLeg || journeyLegs.isEmpty()) continue

                results.add(JourneyResult(
                    departureTime = legs.first().departureTime,
                    arrivalTime = legs.last().arrivalTime,
                    legs = journeyLegs
                ))
            }

            results
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating arrive-by paths: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Smart time rounding for better cache hit rate:
     * - Peak hours (7-9, 17-19): 5 minute intervals (more precision needed)
     * - Off-peak hours: 15 minute intervals (fewer variations, better hits)
     */
    private fun getRoundedDepartureTime(timeSeconds: Int): Int {
        val hour = (timeSeconds / 3600) % 24 // Handle times past midnight (e.g., hour 25 -> 1)
        val isPeakHour = (hour in 7..9) || (hour in 17..19)

        return if (isPeakHour) {
            // 5 minute rounding during peak
            (timeSeconds / 300) * 300
        } else {
            // 15 minute rounding during off-peak
            (timeSeconds / 900) * 900
        }
    }

    /**
     * Build a cache key for journey results.
     * Sorts IDs to ensure same key regardless of order.
     * Uses reusable StringBuilder to reduce GC pressure.
     * Includes date to ensure different dates/periods return different results.
     */
    private fun buildCacheKey(originIds: List<Int>, destIds: List<Int>, time: Int, date: LocalDate): String {
        val sb = cacheKeyBuilder.get()!!
        sb.setLength(0) // Clear without allocation

        // Sort and append origin IDs
        val sortedOrigin = originIds.sorted()
        for (i in sortedOrigin.indices) {
            if (i > 0) sb.append(',')
            sb.append(sortedOrigin[i])
        }

        sb.append('|')

        // Sort and append destination IDs
        val sortedDest = destIds.sorted()
        for (i in sortedDest.indices) {
            if (i > 0) sb.append(',')
            sb.append(sortedDest[i])
        }

        sb.append('|')
        sb.append(time)
        sb.append('|')
        sb.append(date.toString()) // Include date to differentiate cache by period

        return sb.toString()
    }

    private fun formatTimeFromSeconds(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        return "%02d:%02d".format(hours, minutes)
    }

    /**
     * Preload journey cache from disk to memory.
     * Call at app startup for faster initial queries.
     */
    suspend fun preloadJourneyCache() {
        journeyDiskCache.preloadToMemory()
    }

    /**
     * Clean up expired cache entries.
     * Call periodically (e.g., once per day).
     */
    suspend fun cleanupExpiredCache() {
        journeyDiskCache.cleanupExpired()
    }

    /**
     * Get cache statistics for debugging/monitoring.
     */
    fun getCacheStats(): JourneyCache.CacheStats {
        return journeyDiskCache.getStats()
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
        return String.format(java.util.Locale.ROOT, "%02d:%02d", hours, minutes)
    }
}

/**
 * Data class representing an intermediate stop
 */
data class IntermediateStop(
    val stopName: String,
    val arrivalTime: Int,
    val lat: Double = 0.0,
    val lon: Double = 0.0
) {
    fun formatArrivalTime(): String {
        val hours = arrivalTime / 3600
        val minutes = (arrivalTime % 3600) / 60
        return String.format(java.util.Locale.ROOT, "%02d:%02d", hours, minutes)
    }
}

/**
 * Data class representing a leg of a journey
 */
data class JourneyLeg(
    val fromStopId: String,
    val fromStopName: String,
    val fromLat: Double,
    val fromLon: Double,
    val toStopId: String,
    val toStopName: String,
    val toLat: Double,
    val toLon: Double,
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
        return String.format(java.util.Locale.ROOT, "%02d:%02d", hours, minutes)
    }
}

/**
 * Data classes for parsing holidays.json
 */
@Serializable
private data class HolidaysData(
    val school_year: String,
    val location: HolidayLocation,
    val holidays: List<HolidayEntry>
)

@Serializable
private data class HolidayLocation(
    val department: String,
    val academy: String,
    val zone: String
)

@Serializable
private data class HolidayEntry(
    val name: String,
    @kotlinx.serialization.SerialName("start_date_inclusive")
    val startDateInclusive: String,
    @kotlinx.serialization.SerialName("end_date_inclusive")
    val endDateInclusive: String?,
    @kotlinx.serialization.SerialName("school_resumes")
    val schoolResumes: String
)

/**
 * Internal representation of a holiday period
 */
private data class HolidayPeriod(
    val name: String,
    val startDate: java.time.LocalDate,
    val endDate: java.time.LocalDate
)
