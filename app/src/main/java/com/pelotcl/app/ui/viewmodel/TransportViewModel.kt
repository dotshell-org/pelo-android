package com.pelotcl.app.ui.viewmodel

import android.app.Application
import android.os.Build
import android.util.Log
import android.util.LruCache
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Stable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pelotcl.app.data.model.Feature
import com.pelotcl.app.data.model.SimpleVehiclePosition
import com.pelotcl.app.data.model.StopFeature
import com.pelotcl.app.data.repository.RaptorRepository
import com.pelotcl.app.data.repository.TransportRepository
import com.pelotcl.app.data.repository.TrafficAlertPush
import com.pelotcl.app.data.repository.TrafficAlertsRepository
import com.pelotcl.app.data.repository.VehiclePositionsRepository
import com.pelotcl.app.ui.components.LineSearchResult
import com.pelotcl.app.ui.components.StationSearchResult
import com.pelotcl.app.utils.Connection
import com.pelotcl.app.data.repository.FavoritesRepository
import com.pelotcl.app.data.cache.SpatialGrid
import com.pelotcl.app.data.network.ConnectivityObserver
import com.pelotcl.app.data.offline.OfflineDataInfo
import com.pelotcl.app.data.offline.OfflineDataManager
import com.pelotcl.app.utils.BusIconHelper
import com.pelotcl.app.utils.ConnectionsHelper
import com.pelotcl.app.utils.HolidayDetector
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * UI state for transport lines
 */
@Stable
sealed class TransportLinesUiState {
    data object Loading : TransportLinesUiState()
    data class PartialSuccess(val lines: List<Feature>, val source: String) : TransportLinesUiState()
    data class Success(val lines: List<Feature>) : TransportLinesUiState()
    data class Error(val message: String) : TransportLinesUiState()
}

/**
 * UI state for transport stops
 */
@Stable
sealed class TransportStopsUiState {
    data object Loading : TransportStopsUiState()
    data class Success(val stops: List<StopFeature>) : TransportStopsUiState()
    data class Error(val message: String) : TransportStopsUiState()
}

/**
 * ViewModel to manage transport line data and searches
 */
class TransportViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TransportRepository(application.applicationContext)
    private val trafficAlertsRepository = TrafficAlertsRepository(application.applicationContext)

    // Shared RaptorRepository singleton - kept alive during app lifetime
    val raptorRepository = RaptorRepository.getInstance(application.applicationContext)

    private val _uiState = MutableStateFlow<TransportLinesUiState>(TransportLinesUiState.Loading)
    val uiState: StateFlow<TransportLinesUiState> = _uiState.asStateFlow()

    private val _stopsUiState = MutableStateFlow<TransportStopsUiState>(TransportStopsUiState.Loading)
    val stopsUiState: StateFlow<TransportStopsUiState> = _stopsUiState.asStateFlow()

    private val schedulesRepository = com.pelotcl.app.data.gtfs.SchedulesRepository.getInstance(application.applicationContext)
    private val holidayDetector by lazy { HolidayDetector(application.applicationContext) }
    private val favoritesRepository = FavoritesRepository(application.applicationContext)

    private val _headsigns = MutableStateFlow<Map<Int, String>>(emptyMap())
    val headsigns: StateFlow<Map<Int, String>> = _headsigns.asStateFlow()

    private val _allSchedules = MutableStateFlow<List<String>>(emptyList())
    val allSchedules: StateFlow<List<String>> = _allSchedules.asStateFlow()

    private val _nextSchedules = MutableStateFlow<List<String>>(emptyList())
    val nextSchedules: StateFlow<List<String>> = _nextSchedules.asStateFlow()

    // Available directions (having schedules) for the current line/stop combination
    private val _availableDirections = MutableStateFlow<List<Int>>(emptyList())
    val availableDirections: StateFlow<List<Int>> = _availableDirections.asStateFlow()

    private val _favoriteLines = MutableStateFlow<Set<String>>(emptySet())
    val favoriteLines: StateFlow<Set<String>> = _favoriteLines.asStateFlow()
    private val _selectedLineName = MutableStateFlow<String?>(null)
    val selectedLineName: StateFlow<String?> = _selectedLineName.asStateFlow()

    // Traffic alerts state
    private val _trafficAlerts = MutableStateFlow<Map<String, List<com.pelotcl.app.data.model.TrafficAlert>>>(emptyMap())
    val trafficAlerts: StateFlow<Map<String, List<com.pelotcl.app.data.model.TrafficAlert>>> = _trafficAlerts.asStateFlow()

    // Connectivity and offline state
    private val connectivityObserver = ConnectivityObserver.getInstance(application.applicationContext)
    val offlineDataManager = OfflineDataManager.getInstance(application.applicationContext)

    private val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

    val offlineDataInfo: StateFlow<OfflineDataInfo> = offlineDataManager.offlineDataInfo

    // Alerts timestamp for staleness display
    private val _alertsTimestampMillis = MutableStateFlow<Long?>(null)
    val alertsTimestampMillis: StateFlow<Long?> = _alertsTimestampMillis.asStateFlow()
    private var trafficAlertsStreamJob: Job? = null
    private var lastTrafficSubscription: Set<String> = emptySet()

    // Vehicle positions state for live tracking
    private val vehiclePositionsRepository = VehiclePositionsRepository()
    private val _vehiclePositions = MutableStateFlow<List<SimpleVehiclePosition>>(emptyList())
    val vehiclePositions: StateFlow<List<SimpleVehiclePosition>> = _vehiclePositions.asStateFlow()
    private val _isLiveTrackingEnabled = MutableStateFlow(false)
    val isLiveTrackingEnabled: StateFlow<Boolean> = _isLiveTrackingEnabled.asStateFlow()
    private var vehiclePositionsJob: Job? = null

    // Global live map state (all vehicles across the network)
    private val _isGlobalLiveEnabled = MutableStateFlow(false)
    val isGlobalLiveEnabled: StateFlow<Boolean> = _isGlobalLiveEnabled.asStateFlow()
    private val _globalVehiclePositions = MutableStateFlow<List<SimpleVehiclePosition>>(emptyList())
    val globalVehiclePositions: StateFlow<List<SimpleVehiclePosition>> = _globalVehiclePositions.asStateFlow()
    private var globalLiveJob: Job? = null

    // Offline download job reference for cancellation support
    private var offlineDownloadJob: Job? = null

    // Preloading flags to avoid multiple reloads
    private var isPreloading: Boolean = false
    private var hasPreloaded: Boolean = false

    // === STRUCTURED CONCURRENCY: SupervisorJob for line detail operations ===
    // All line-detail coroutines (headsigns, directions, schedules, line loading) run under
    // this scope. Cancelling lineDetailJob cancels ALL children at once, preventing
    // accumulation during rapid navigation between lines.
    private var lineDetailJob = SupervisorJob()
    private var lineDetailScope = CoroutineScope(viewModelScope.coroutineContext + lineDetailJob)

    init {
        // Observe connectivity changes
        viewModelScope.launch {
            connectivityObserver.isOnline.collect { online ->
                val wasOffline = _isOffline.value
                _isOffline.value = !online
                if (!online) {
                    stopTrafficAlertsStreaming()
                } else if (wasOffline) {
                    startTrafficAlertsStreaming(forceRestart = true)
                }
            }
        }

        // Start non-blocking preload on creation to have lines, stops, and connection index ready
        preloadAllData()

        // Load favorites asynchronously (SharedPreferences read can be slow on some devices)
        viewModelScope.launch(Dispatchers.IO) {
            val favorites = favoritesRepository.getFavorites().map { it.uppercase() }.toSet()
            _favoriteLines.value = favorites
        }

        // Defer traffic alerts - not critical for initial display
        viewModelScope.launch {
            delay(1000) // Wait for UI to stabilize
            refreshTrafficAlerts()
            startTrafficAlertsStreaming(forceRestart = true)
        }

        // Load offline data info
        offlineDataManager.refreshInfo()
    }

    /**
     * Starts offline data download in viewModelScope so it survives screen navigation.
     * Using rememberCoroutineScope in the UI would cancel the download if the user leaves the screen.
     */
    fun startOfflineDownload() {
        offlineDownloadJob = viewModelScope.launch {
            offlineDataManager.downloadAllOfflineData()
        }
    }

    /**
     * Cancels the ongoing offline data download.
     * Already-saved partial data is preserved.
     */
    fun cancelOfflineDownload() {
        offlineDataManager.cancelDownload()
        offlineDownloadJob?.cancel()
        offlineDownloadJob = null
    }

    /**
     * Deletes all offline data from viewModelScope.
     */
    fun deleteOfflineData() {
        viewModelScope.launch {
            offlineDataManager.deleteAllOfflineData()
        }
    }

    /**
     * Search for stops by name using SQLite
     * This is the function called by the Search Bar in MainActivity
     */
    suspend fun searchStops(query: String): List<StationSearchResult> {
        return withContext(Dispatchers.IO) {
            schedulesRepository.searchStopsByName(query)
        }
    }

    /**
     * Search for lines by name
     * Returns matching line results from the currently loaded lines
     */
    /**
     * Search for lines by name using GTFS database
     * Returns matching line results from all available lines (including buses)
     */
    fun searchLines(query: String): List<LineSearchResult> {
        if (query.isBlank()) return emptyList()
        return schedulesRepository.searchLinesByName(query)
    }

    fun loadHeadsign(routeName: String) {
        lineDetailScope.launch {
            // GTFS uses "NAVI1" for Navigone while the app displays "NAV1"
            val gtfsRouteName = if (routeName.equals("NAV1", ignoreCase = true)) "NAVI1" else routeName
            val headsigns = schedulesRepository.getHeadsigns(gtfsRouteName)
            _headsigns.value = headsigns
        }
    }

    /**
     * Calculates directions that actually have schedules for a given stop.
     * Uses IDs present in _headsigns when available.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun computeAvailableDirections(lineName: String, stopName: String) {
        lineDetailScope.launch {
            // Check for school holidays and French public holidays separately
            val today = LocalDate.now()
            val isSchoolHoliday = holidayDetector.isSchoolHoliday(today)
            val isPublicHoliday = holidayDetector.isFrenchPublicHoliday(today)
            val gtfsLineName = if (lineName.equals("NAV1", ignoreCase = true)) "NAVI1" else lineName

            // Candidate directions list: those exposed by _headsigns otherwise 0 and 1 by default
            val candidateDirections = _headsigns.value.keys.ifEmpty { setOf(0, 1) }.toList().sorted()

            val available = mutableListOf<Int>()
            for (dir in candidateDirections) {
                try {
                    val schedules = schedulesRepository.getSchedules(gtfsLineName, stopName, dir, isSchoolHoliday, isPublicHoliday)
                    if (schedules.isNotEmpty()) {
                        available.add(dir)
                    }
                } catch (t: Throwable) {
                    Log.e("SchedulesDebug", "computeAvailableDirections: exception for dir $dir: ${t.message}")
                }
            }
            _availableDirections.value = available
        }
    }

    /**
     * Loads all schedules for a given direction, then updates the schedule states.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun loadSchedulesForDirection(lineName: String, stopName: String, directionId: Int) {
        lineDetailScope.launch {
            _allSchedules.value = emptyList()
            _nextSchedules.value = emptyList()

            // Check for school holidays and French public holidays separately
            val today = LocalDate.now()
            val isSchoolHoliday = holidayDetector.isSchoolHoliday(today)
            val isPublicHoliday = holidayDetector.isFrenchPublicHoliday(today)

            // The GTFS data uses NAVI1 for the Navigone, but the app displays NAV1
            val gtfsLineName = if (lineName.equals("NAV1", ignoreCase = true)) "NAVI1" else lineName

            val allSchedulesForDay = schedulesRepository.getSchedules(
                gtfsLineName,
                stopName,
                directionId,
                isSchoolHoliday,
                isPublicHoliday
            )
            _allSchedules.value = allSchedulesForDay

            if (allSchedulesForDay.isEmpty()) {
                Log.w("SchedulesDebug", "=== loadSchedulesForDirection END === No schedules found!")
                return@launch
            }

            try {
                val now = java.util.Calendar.getInstance()
                val currentHour = now.get(java.util.Calendar.HOUR_OF_DAY)
                val currentMinute = now.get(java.util.Calendar.MINUTE)

                val remainingToday = allSchedulesForDay.filter { time ->
                    val parts = time.split(":")
                    if (parts.size == 2) {
                        val hour = parts[0].toInt()
                        val minute = parts[1].toInt()
                        hour > currentHour || (hour == currentHour && minute >= currentMinute)
                    } else {
                        false
                    }
                }

                val nextThree = (remainingToday + allSchedulesForDay).take(3)

                _nextSchedules.value = nextThree

            } catch (e: Exception) {
                Log.e("SchedulesDebug", "Error filtering next schedules: ${e.message}")
            }
        }
    }

    /**
     * Cancels all pending line detail operations (headsign, directions, schedules, line load).
     * Call this when the user rapidly changes between lines/connections to prevent
     * accumulation of concurrent operations that can cause OutOfMemoryError.
     */
    fun cancelPendingLineOperations() {
        // Cancel the supervisor job — this cancels ALL children at once
        lineDetailJob.cancel()
        // Create a fresh supervisor so new operations can be launched
        lineDetailJob = SupervisorJob()
        lineDetailScope = CoroutineScope(viewModelScope.coroutineContext + lineDetailJob)
    }

    /**
     * Clears all line detail states (headsigns, schedules, directions).
     * Call this when switching to a new line to ensure fresh state and prevent
     * stale data from accumulating in memory.
     */
    fun clearLineDetailStates() {
        _headsigns.value = emptyMap()
        _allSchedules.value = emptyList()
        _nextSchedules.value = emptyList()
        _availableDirections.value = emptyList()
    }

    /**
     * Combined operation: cancel pending operations and clear states.
     * Use this when rapidly switching between lines.
     */
    fun resetLineDetailState() {
        cancelPendingLineOperations()
        clearLineDetailStates()
    }

    /**
     * Clears schedule-related states (schedules, directions) but keeps headsigns.
     * Call this when switching stops on the same line to ensure fresh schedule data
     * while preserving direction labels.
     */
    fun clearScheduleState() {
        // Cancel all line detail operations and recreate the scope
        // (headsigns will be reloaded when needed)
        cancelPendingLineOperations()
        _allSchedules.value = emptyList()
        _nextSchedules.value = emptyList()
        _availableDirections.value = emptyList()
    }

    /**
     * Toggles live vehicle tracking for the given line.
     */
    fun toggleLiveTracking(lineName: String) {
        if (_isLiveTrackingEnabled.value) {
            stopLiveTracking()
        } else {
            startLiveTracking(lineName)
        }
    }

    companion object {
        private val DEFAULT_ALERT_SUBSCRIPTION_LINES = setOf(
            "A", "B", "C", "D",
            "F1", "F2",
            "T1", "T2", "T3", "T4", "T5", "T6", "T7", "T8", "T9", "T10",
            "NAV1", "NAVI1",
            "RX"
        )
    }

    /**
     * Starts live vehicle tracking for the given line using SSE stream updates.
     */
    fun startLiveTracking(lineName: String) {
        // Don't start live tracking when offline
        if (_isOffline.value) {
            Log.w("TransportViewModel", "Cannot start live tracking while offline")
            return
        }
        // Mutually exclusive with global live
        if (_isGlobalLiveEnabled.value) {
            stopGlobalLive()
        }
        vehiclePositionsJob?.cancel()
        _isLiveTrackingEnabled.value = true

        vehiclePositionsJob = viewModelScope.launch {
            vehiclePositionsRepository.streamAllVehiclePositions().collect { result ->
                result.onSuccess { allPositions ->
                    _vehiclePositions.value = allPositions.filter {
                        it.lineName.equals(lineName, ignoreCase = true)
                    }
                }.onFailure {
                    Log.w("TransportViewModel", "Vehicle SSE error: ${it.message}")
                }
            }
        }
    }

    /**
     * Stops live vehicle tracking and clears vehicle positions.
     */
    fun stopLiveTracking() {
        vehiclePositionsJob?.cancel()
        vehiclePositionsJob = null
        _isLiveTrackingEnabled.value = false
        _vehiclePositions.value = emptyList()
    }

    /**
     * Toggles the global live map showing all vehicles across the network.
     * Mutually exclusive with per-line live tracking.
     */
    fun toggleGlobalLive() {
        if (_isGlobalLiveEnabled.value) {
            stopGlobalLive()
        } else {
            startGlobalLive()
        }
    }

    private fun startGlobalLive() {
        if (_isOffline.value) {
            Log.w("TransportViewModel", "Cannot start global live while offline")
            return
        }
        // Mutually exclusive with per-line tracking
        if (_isLiveTrackingEnabled.value) {
            stopLiveTracking()
        }

        globalLiveJob?.cancel()
        _isGlobalLiveEnabled.value = true

        globalLiveJob = viewModelScope.launch {
            vehiclePositionsRepository.streamAllVehiclePositions().collect { result ->
                result.onSuccess { positions ->
                    _globalVehiclePositions.value = positions
                }.onFailure {
                    Log.w("TransportViewModel", "Global live SSE error: ${it.message}")
                }
            }
        }
    }

    fun stopGlobalLive() {
        globalLiveJob?.cancel()
        globalLiveJob = null
        _isGlobalLiveEnabled.value = false
        _globalVehiclePositions.value = emptyList()
    }

    /**
     * Reloads all strong lines (metro/tram/funicular/navigone + RX)
     * and updates UI state. Used as fallback if a strong line is missing.
     */
    fun reloadStrongLines() {
        viewModelScope.launch {
            try {
                repository.getAllLines()
                    .onSuccess { collection ->
                        _uiState.value = TransportLinesUiState.Success(collection.features)
                    }
                    .onFailure { e ->
                        Log.w("TransportViewModel", "reloadStrongLines failed: ${e.message}")
                    }
            } catch (t: Throwable) {
                Log.e("TransportViewModel", "reloadStrongLines: exception ${t.message}")
            }
        }
    }

    // Stops cache to avoid reloading them each time
    private var cachedStops: List<StopFeature>? = null
    private var stopsLoadingJob: Job? = null

    // Lines loading job to prevent duplicate concurrent loads
    private var linesLoadingJob: Job? = null

    // Pre-calculated transfers index by stop name
    // Key = normalized stop name, Value = list of transfer lines
    private var connectionsIndex: Map<String, List<Connection>> = emptyMap()

    // Pre-calculated index of stops by line name for O(1) lookups
    // Key = line name (uppercase), Value = list of StopFeatures serving that line
    private var stopsByLineIndex: Map<String, List<StopFeature>> = emptyMap()
    private val stopsByLineIndexMutex = Mutex()

    // LruCache for line stops to avoid expensive geometric calculations
    // Key = "lineName|currentStopName", Value = list of LineStopInfo
    // Reduced from 30 to 15 entries to limit memory usage during rapid navigation
    private val lineStopsCache = LruCache<String, List<com.pelotcl.app.data.gtfs.LineStopInfo>>(15)

    // === OPTIMIZATION: Spatial grid index for fast bounding-box queries ===
    // Partitions stops into geographic cells for O(visible_cells) viewport queries
    val spatialGrid = SpatialGrid()

    // === OPTIMIZATION: Pre-computed GeoJSON cache for stops ===
    // Cached GeoJSON string for all stops (avoids re-computation on each map display)
    private var cachedStopsGeoJson: String? = null
    // Set of required icon names for the cached GeoJSON
    private var cachedRequiredIcons: Set<String>? = null
    // Cached set of used slot indices for the GeoJSON (avoids recalculation)
    private var cachedUsedSlots: Set<Int>? = null
    // Cheap hash of stops list to detect changes and invalidate cache (O(1) instead of O(n))
    private var cachedStopsHash: Long = 0L

    // === OPTIMIZATION: Pre-loaded icon bitmaps cache with memory management ===
    // LruCache with 10MB max size for bitmap icons, responds to memory pressure
    private val iconBitmapCache = object : LruCache<String, android.graphics.Bitmap>(10 * 1024 * 1024) {
        override fun sizeOf(key: String, value: android.graphics.Bitmap): Int {
            return value.allocationByteCount
        }
    }

    /**
     * Cheap O(1) hash for stop list identity check.
     * Uses size + first/last element hash instead of iterating all elements.
     * The list only changes via full replacement, never element-level mutation.
     */
    private fun cheapListHash(stops: List<StopFeature>): Long {
        if (stops.isEmpty()) return 0L
        return stops.size.toLong() * 31 +
               stops.first().hashCode().toLong() * 17 +
               stops.last().hashCode().toLong()
    }

    /**
     * Returns cached stops GeoJSON data if available and valid.
     * Returns null if cache is invalid or stops have changed.
     * The Triple contains (geoJson, requiredIcons, usedSlots).
     */
    fun getCachedStopsGeoJson(currentStops: List<StopFeature>): Triple<String, Set<String>, Set<Int>>? {
        val currentHash = cheapListHash(currentStops)
        return if (cachedStopsGeoJson != null && cachedRequiredIcons != null && cachedUsedSlots != null && currentHash == cachedStopsHash) {
            Triple(cachedStopsGeoJson!!, cachedRequiredIcons!!, cachedUsedSlots!!)
        } else {
            null
        }
    }

    /**
     * Caches the GeoJSON data for stops including used slots.
     */
    fun cacheStopsGeoJson(stops: List<StopFeature>, geoJson: String, requiredIcons: Set<String>, usedSlots: Set<Int>) {
        cachedStopsHash = cheapListHash(stops)
        cachedStopsGeoJson = geoJson
        cachedRequiredIcons = requiredIcons
        cachedUsedSlots = usedSlots
    }

    /**
     * Returns a cached icon bitmap by name, or null if not cached.
     * Direct LruCache access without creating a full snapshot copy.
     */
    fun getIconBitmap(name: String): android.graphics.Bitmap? = iconBitmapCache.get(name)

    /**
     * Checks if all the given icon names are present in the bitmap cache.
     */
    fun hasAllIcons(names: Set<String>): Boolean = names.all { iconBitmapCache.get(it) != null }

    /**
     * Caches a single icon bitmap for reuse using memory-aware LruCache.
     */
    fun cacheIconBitmap(name: String, bitmap: android.graphics.Bitmap) {
        iconBitmapCache.put(name, bitmap)
    }

    /**
     * Reduces bitmap cache size based on memory pressure level.
     * Call from Application.onTrimMemory() to respond to system memory pressure.
     * @param level The trim level from ComponentCallbacks2 (TRIM_MEMORY_*)
     */
    fun trimBitmapCache(level: Int) {
        when {
            level >= android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                iconBitmapCache.evictAll()
            }
            level >= android.content.ComponentCallbacks2.TRIM_MEMORY_MODERATE -> {
                iconBitmapCache.trimToSize(iconBitmapCache.maxSize() / 2)
            }
            level >= android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> {
                iconBitmapCache.trimToSize(iconBitmapCache.maxSize() * 3 / 4)
            }
        }
    }

    /**
     * Clears all cached data for performance optimization.
     * Call when data sources are updated.
     */
    @Suppress("unused") // Public API for cache invalidation when GTFS data is updated
    fun clearAllCaches() {
        lineStopsCache.evictAll()
        iconBitmapCache.evictAll()
        connectionsIndex = emptyMap()
        stopsByLineIndex = emptyMap()
        cachedStops = null
        // Clear GeoJSON cache
        cachedStopsGeoJson = null
        cachedRequiredIcons = null
        cachedUsedSlots = null
        cachedStopsHash = 0
        // Clear spatial grid
        spatialGrid.build(emptyList())
        // Clear BusIconHelper cache
        BusIconHelper.clearCache()
    }

    /**
     * Preloads lines and stops at launch, builds transfers index,
     * and populates StateFlows if possible. Does not block UI.
     * Uses 3-phase loading strategy for optimal UX:
     * - Phase 0 (instant): Show stale cache immediately if available
     * - Phase 1 (immediate): Critical UI data (lines, stops, SQLite warmup)
     * - Phase 2 (deferred): Heavy processing (connections index, Raptor preload)
     */
    private fun preloadAllData() {
        if (hasPreloaded || isPreloading) return
        isPreloading = true

        viewModelScope.launch {
            try {
                val startTime = System.currentTimeMillis()

                // ===== PHASE 0: Show stale cache instantly =====
                // This allows the UI to display immediately with cached data
                // while fresh data loads in the background
                val currentLinesState = _uiState.value
                if (currentLinesState !is TransportLinesUiState.Success && currentLinesState !is TransportLinesUiState.PartialSuccess) {
                    val staleResult = repository.getAllLinesStale()
                    staleResult?.onSuccess { featureCollection ->
                        if (featureCollection.features.isNotEmpty()) {
                            _uiState.value = TransportLinesUiState.Success(featureCollection.features)
                            Log.d("TransportViewModel", "Phase 0: Displayed ${featureCollection.features.size} lines from stale cache in ${System.currentTimeMillis() - startTime}ms")
                        }
                    }
                }

                // ===== PHASE 1: Critical UI data (parallel) =====
                val linesDeferred = async(Dispatchers.IO) {
                    // Always try to refresh from network for fresh data
                    repository.getAllLines()
                }

                val stopsDeferred = async(Dispatchers.IO) {
                    if (cachedStops == null || connectionsIndex.isEmpty()) {
                        repository.getAllStops()
                    } else null
                }

                // Warm up SQLite database in parallel (non-blocking)
                launch(Dispatchers.IO) {
                    schedulesRepository.warmupDatabase()
                }

                // Wait for critical data to complete
                val (linesResult, stopsResult) = awaitAll(linesDeferred, stopsDeferred)

                // Process lines result - update with fresh data
                @Suppress("UNCHECKED_CAST")
                (linesResult as? Result<com.pelotcl.app.data.model.FeatureCollection>)?.let { result ->
                    result.onSuccess { featureCollection ->
                        // Update with fresh data (may be same as stale cache if not expired)
                        _uiState.value = TransportLinesUiState.Success(featureCollection.features)
                        Log.d("TransportViewModel", "Phase 1: Refreshed with ${featureCollection.features.size} lines in ${System.currentTimeMillis() - startTime}ms")
                    }.onFailure { e ->
                        Log.w("TransportViewModel", "Preload: failed loading lines: ${e.message}")
                        // Keep stale cache if refresh failed and we had cached data
                        val linesAfterFailure = _uiState.value
                        if (linesAfterFailure !is TransportLinesUiState.Success && linesAfterFailure !is TransportLinesUiState.PartialSuccess) {
                            _uiState.value = TransportLinesUiState.Error(e.message ?: "Failed to load lines")
                        }
                    }
                }

                // Process stops result
                var stopsForIndexing: List<StopFeature>? = null
                @Suppress("UNCHECKED_CAST")
                (stopsResult as? Result<com.pelotcl.app.data.model.StopCollection>)?.let { result ->
                    result.onSuccess { stopCollection ->
                        cachedStops = stopCollection.features
                        stopsForIndexing = stopCollection.features
                        // Build spatial grid index for fast viewport queries
                        spatialGrid.build(stopCollection.features)
                        Log.d("TransportViewModel", "Spatial grid built with ${spatialGrid.size} stops")
                        // Publish stops state only if not already success
                        if (_stopsUiState.value !is TransportStopsUiState.Success) {
                            _stopsUiState.value = TransportStopsUiState.Success(stopCollection.features)
                        }
                    }.onFailure { e ->
                        Log.w("TransportViewModel", "Preload: failed loading stops: ${e.message}")
                    }
                }

                // ===== PHASE 2: Deferred heavy processing =====
                // Build connections index (can take time with many stops)
                stopsForIndexing?.let { stops ->
                    launch(Dispatchers.Default) {
                        buildConnectionsIndex(stops)
                    }
                }

                // Raptor initialization is handled by MainActivity to avoid duplicate init.
                // Here we only cleanup expired cache entries once Raptor is ready.
                launch(Dispatchers.IO) {
                    raptorRepository.cleanupExpiredCache()
                }

            } catch (t: Throwable) {
                Log.e("TransportViewModel", "Preload: unexpected exception: ${t.message}")
            } finally {
                hasPreloaded = true
                isPreloading = false
            }
        }
    }

    /**
     * Normalizes a station name to use it as an index key
     */
    private fun normalizeStopName(name: String): String {
        return name.filter { it.isLetter() }.lowercase()
    }

    /**
     * Normalizes a line name for comparison (handles NAVI1 -> NAV1)
     */
    private fun normalizeLineName(lineName: String): String {
        return when (lineName.uppercase()) {
            "NAVI1" -> "NAV1"
            else -> lineName.uppercase()
        }
    }

    /**
     * Normalizes a line name for alert matching (removes spaces and hyphens)
     */
    private fun normalizeLineForAlerts(line: String): String {
        val cleaned = line.uppercase()
            .replace(" ", "")
            .replace("-", "")
            .replace("TRAM", "")
            .replace("METRO", "")
            .trim()
        // Apply same normalization as normalizeLineName (e.g. NAVI1 → NAV1)
        return normalizeLineName(cleaned)
    }

    /**
     * Retrieves transfers for a given stop from the pre-calculated index
     * Ultra-fast method (O(1))
     */
    fun getConnectionsForStop(stopName: String, currentLine: String): List<Connection> {
        val normalized = normalizeStopName(stopName)
        val connections = connectionsIndex[normalized] ?: emptyList()
        // Exclude the current line (with normalization to handle NAVI1 vs NAV1)
        val normalizedCurrentLine = normalizeLineName(currentLine)
        return connections
            .filter { normalizeLineName(it.lineName) != normalizedCurrentLine }
            .sortedWith(compareBy<Connection> {
                when (it.transportType) {
                    com.pelotcl.app.utils.TransportType.METRO -> 1
                    com.pelotcl.app.utils.TransportType.TRAM -> 2
                    com.pelotcl.app.utils.TransportType.FUNICULAR -> 3
                    com.pelotcl.app.utils.TransportType.NAVIGONE -> 4
                    com.pelotcl.app.utils.TransportType.BUS -> 5
                    com.pelotcl.app.utils.TransportType.UNKNOWN -> 6
                }
            }.thenBy { it.lineName })
    }

    /**
     * Retrieves all stops (from cache if available, otherwise loads)
     * Synchronous method that immediately returns the cache or an empty list
     */
    fun getCachedStopsSync(): List<StopFeature> {
        return cachedStops ?: emptyList()
    }

    /**
     * Loads all stops in the background and caches them
     * Starts loading if not already in progress
     * Also builds the transfers index for ultra-fast access
     */
    fun preloadStops() {
        if (cachedStops != null || stopsLoadingJob?.isActive == true) {
            return // Already in cache or loading
        }

        stopsLoadingJob = viewModelScope.launch {
            repository.getAllStops()
                .onSuccess { stopCollection ->
                    cachedStops = stopCollection.features

                    // Build spatial grid and transfers index
                    spatialGrid.build(stopCollection.features)
                    buildConnectionsIndex(stopCollection.features)

                    _stopsUiState.value = TransportStopsUiState.Success(stopCollection.features)
                }
                .onFailure { exception ->
                    _stopsUiState.value = TransportStopsUiState.Error(
                        exception.message ?: "An error occurred while loading stops"
                    )
                }
        }
    }

    /**
     * Forces reload of the stops cache
     * Useful when incorrect data is detected
     */
    suspend fun reloadStopsCache() {
        repository.getAllStops()
            .onSuccess { stopCollection ->
                cachedStops = stopCollection.features
                spatialGrid.build(stopCollection.features)
                buildConnectionsIndex(stopCollection.features)
            }
            .onFailure { exception ->
                Log.e("TransportViewModel", "Error reloading stops cache: ${exception.message}")
            }
    }

    /**
     * Builds a transfers index for each stop
     * Allows O(1) access instead of scanning all stops each time
     * Uses prefix-based indexing for O(n * avg_bucket_size) instead of O(n * total_groups)
     */
    private suspend fun buildConnectionsIndex(allStops: List<StopFeature>) = withContext(Dispatchers.Default) {
        // Step 1: Group stops by approximate name to find a "canonical" name
        // Uses a prefix index (first 4 chars) to avoid scanning all groups for each stop
        val stopGroups = mutableMapOf<String, MutableList<StopFeature>>()
        val prefixIndex = mutableMapOf<String, MutableList<String>>()

        for (stop in allStops) {
            val normalizedName = normalizeStopName(stop.properties.nom)
            val prefix = if (normalizedName.length >= 4) normalizedName.substring(0, 4) else normalizedName

            // Only search candidates sharing the same prefix
            var foundGroup: String? = null
            val candidates = prefixIndex[prefix]
            if (candidates != null) {
                for (candidate in candidates) {
                    if (candidate.startsWith(normalizedName) || normalizedName.startsWith(candidate)) {
                        foundGroup = candidate
                        break
                    }
                }
            }

            if (foundGroup != null) {
                stopGroups[foundGroup]?.add(stop)
            } else {
                stopGroups[normalizedName] = mutableListOf(stop)
                prefixIndex.getOrPut(prefix) { mutableListOf() }.add(normalizedName)
            }
        }

        // Step 2: Build index using groups
        val index = mutableMapOf<String, List<Connection>>()
        for ((_, stopsInGroup) in stopGroups) {
            val allConnectionsForGroup = stopsInGroup
                .flatMap { stop -> ConnectionsHelper.parseAllConnections(stop.properties.desserte) }
                .distinctBy { it.lineName }

            // Associate this transfer group to all normalized names in the group
            val allNormalizedNamesInGroup = stopsInGroup.map { normalizeStopName(it.properties.nom) }.distinct()
            for (name in allNormalizedNamesInGroup) {
                index[name] = allConnectionsForGroup
            }
        }
        connectionsIndex = index

        // Also build the stops-by-line index for fast filtering in map operations
        buildStopsByLineIndex(allStops)
    }

    /**
     * Builds an index mapping line names to their stops for O(1) lookups.
     * Called after stops are loaded and when connections index is built.
     */
    private suspend fun buildStopsByLineIndex(allStops: List<StopFeature>) = withContext(Dispatchers.Default) {
        val index = mutableMapOf<String, MutableList<StopFeature>>()

        for (stop in allStops) {
            val lines = BusIconHelper.getAllLinesForStop(stop)
            for (line in lines) {
                val key = line.uppercase()
                index.getOrPut(key) { mutableListOf() }.add(stop)
            }
        }

        stopsByLineIndexMutex.withLock {
            stopsByLineIndex = index
        }
    }

    /**
     * Gets all stops serving a specific line in O(1) time.
     * Uses pre-computed index for fast access during map filtering.
     *
     * @param lineName The line name (case-insensitive)
     * @return List of StopFeatures serving that line, or empty list if not found
     */
    fun getStopsFeaturesForLine(lineName: String): List<StopFeature> {
        // Handle NAV1/NAVI1 normalization
        val searchKeys = if (lineName.equals("NAV1", ignoreCase = true)) {
            listOf("NAV1", "NAVI1")
        } else {
            listOf(lineName.uppercase())
        }

        // Return first match from index
        for (key in searchKeys) {
            stopsByLineIndex[key]?.let { return it }
        }
        return emptyList()
    }

    /**
     * Checks if the stops-by-line index is ready for use.
     */
    fun isStopsByLineIndexReady(): Boolean = stopsByLineIndex.isNotEmpty()

    /**
     * Loads all transport lines progressively with request coalescing.
     * Emits partial states as lines become available for better UX.
     * Prevents duplicate concurrent API calls when called multiple times.
     */
    fun loadAllLines() {
        // Skip if already loaded successfully or currently loading
        if (_uiState.value is TransportLinesUiState.Success) return
        if (linesLoadingJob?.isActive == true) return

        linesLoadingJob = viewModelScope.launch {
            _uiState.value = TransportLinesUiState.Loading

            try {
                repository.getAllLinesFlow().collect { progress ->
                    if (progress.isComplete) {
                        _uiState.value = TransportLinesUiState.Success(progress.lines)
                        Log.d("TransportViewModel", "Lines loading complete: ${progress.lines.size} lines")
                    } else {
                        _uiState.value = TransportLinesUiState.PartialSuccess(progress.lines, progress.source)
                        Log.d("TransportViewModel", "Lines partial load (${progress.source}): ${progress.lines.size} lines")
                    }
                }
            } catch (e: Exception) {
                Log.e("TransportViewModel", "Error loading lines: ${e.message}")
                // If we have partial data, keep it and log the error
                val currentState = _uiState.value
                if (currentState is TransportLinesUiState.PartialSuccess && currentState.lines.isNotEmpty()) {
                    // Convert partial to success with what we have
                    _uiState.value = TransportLinesUiState.Success(currentState.lines)
                    Log.w("TransportViewModel", "Keeping partial data due to error: ${currentState.lines.size} lines")
                } else {
                    _uiState.value = TransportLinesUiState.Error(
                        e.message ?: "An error occurred"
                    )
                }
            }
        }
    }

    /**
     * Toggle favorite status for a line and persist
     */
    fun toggleFavorite(lineName: String) {
        viewModelScope.launch {
            // Normalize to uppercase for consistency
            val normalized = lineName.uppercase()
            val current = _favoriteLines.value.toMutableSet()
            if (current.contains(normalized)) {
                current.remove(normalized)
            } else {
                current.add(normalized)
            }
            favoritesRepository.saveFavorites(current)
            _favoriteLines.value = current
            startTrafficAlertsStreaming(forceRestart = true)
        }
    }

    fun selectLine(lineName: String) {
        _selectedLineName.value = lineName
        startTrafficAlertsStreaming(forceRestart = true)
    }

    fun clearSelectedLine() {
        _selectedLineName.value = null
        startTrafficAlertsStreaming(forceRestart = true)
    }

    /**
     * Adds a specific line to already loaded lines (for on-demand bus lines)
     * Does not modify state if the line is already present
     */
    fun addLineToLoaded(lineName: String) {
        lineDetailScope.launch {
            val currentState = _uiState.value

            // Get current lines from Success or PartialSuccess state
            val currentLines = when (currentState) {
                is TransportLinesUiState.Success -> currentState.lines
                is TransportLinesUiState.PartialSuccess -> currentState.lines
                else -> {
                    Log.w("TransportViewModel", "Current state is not Success/PartialSuccess, cannot add line")
                    return@launch
                }
            }

            // Check if line is already loaded
            val isAlreadyLoaded = currentLines.any {
                lineName.equals(it.properties.ligne, ignoreCase = true)
            }

            if (isAlreadyLoaded) {
                return@launch // Line already present, do nothing
            }

            // Load the line from the API (or offline storage if offline)
            Log.d("TransportViewModel", "addLineToLoaded: loading $lineName (offline=${_isOffline.value})")
            repository.getLineByName(lineName, isOffline = _isOffline.value)
                .onSuccess { feature ->
                    if (feature != null) {
                        Log.d("TransportViewModel", "addLineToLoaded: got $lineName with ${feature.geometry.coordinates.size} segments, ${feature.geometry.coordinates.sumOf { it.size }} points")
                        // Add the new line to existing lines
                        val updatedLines = currentLines + feature
                        _uiState.value = TransportLinesUiState.Success(updatedLines)
                    } else {
                        Log.w("TransportViewModel", "getLineByName returned null for $lineName")
                    }
                }
                .onFailure { exception ->
                    // In case of error, don't change state (keep current lines)
                    Log.e("TransportViewModel", "Error loading line $lineName: ${exception.message}")
                }
        }
    }

    /**
     * Removes a specific line from loaded lines (to clean up temporary bus lines)
     */
    fun removeLineFromLoaded(lineName: String) {
        val currentState = _uiState.value

        // Get current lines from Success or PartialSuccess state
        val currentLines = when (currentState) {
            is TransportLinesUiState.Success -> currentState.lines
            is TransportLinesUiState.PartialSuccess -> currentState.lines
            else -> {
                Log.w("TransportViewModel", "Current state is not Success/PartialSuccess, cannot remove line")
                return
            }
        }

        // Filter to remove the line
        val updatedLines = currentLines.filter {
            !lineName.equals(it.properties.ligne, ignoreCase = true)
        }

        _uiState.value = TransportLinesUiState.Success(updatedLines)
    }

    /**
     * Retrieves stops served by a specific line, ordered according to the line's path
     * Uses GTFS stop_sequences table as primary source for ordering
     * Falls back to static cache (metros/trams) then geometric ordering
     * @param lineName Line name (ex: "86", "A", "T1")
     * @param currentStopName Current stop name to mark it (optional)
     * @param directionId Direction ID (0 or 1) for GTFS ordering, defaults to 0
     * @return List of stops with their transfers, ordered according to the line's route
     */
    fun getStopsForLine(lineName: String, currentStopName: String? = null, directionId: Int = 0): List<com.pelotcl.app.data.gtfs.LineStopInfo> {
        // Check LruCache first for ultra-fast repeated lookups
        val cacheKey = "$lineName|${currentStopName ?: ""}|$directionId"
        lineStopsCache.get(cacheKey)?.let { return it }

        // GTFS uses "NAVI1" for Navigone while the app displays "NAV1"
        val gtfsLineName = if (lineName.equals("NAV1", ignoreCase = true)) "NAVI1" else lineName

        // First, try to retrieve from GTFS stop_sequences table (most accurate)
        val gtfsSequences = schedulesRepository.getStopSequences(gtfsLineName, directionId)
        if (gtfsSequences.isNotEmpty()) {
            val allStops = getCachedStopsSync()
            // Build a map of normalized name -> official stop name and connections
            val normalizedToStop = allStops.associateBy { normalizeStopName(it.properties.nom) }

            val result = gtfsSequences.mapNotNull { (stationName, sequence) ->
                // Try to find the matching stop in allStops
                val normalizedGtfsName = normalizeStopName(stationName)
                val matchingStop = normalizedToStop[normalizedGtfsName]

                val officialName = matchingStop?.properties?.nom ?: stationName
                val connections = getConnectionsForStop(officialName, lineName)

                com.pelotcl.app.data.gtfs.LineStopInfo(
                    stopId = matchingStop?.properties?.id?.toString() ?: "gtfs_${lineName}_${sequence}",
                    stopName = officialName,
                    stopSequence = sequence,
                    isCurrentStop = currentStopName?.let {
                        normalizeStopName(officialName) == normalizeStopName(it)
                    } ?: false,
                    connections = connections.map { it.lineName }
                )
            }.distinctBy { normalizeStopName(it.stopName) }

            if (result.isNotEmpty()) {
                lineStopsCache.put(cacheKey, result)
                return result
            }
        }

        // Second, try to retrieve from static cache (for metros and trams)
        val cachedStops = com.pelotcl.app.data.gtfs.LineStopsCache.getLineStops(lineName, currentStopName)
        if (cachedStops != null) {
            // Align cache labels with official GTFS labels (strict comparison required DB side)
            val allStops = getCachedStopsSync()
            // Build set of official names served by the line (all directions)
            val officialStopsForLine: Set<String> = allStops.filter { stop ->
                val desserte = stop.properties.desserte
                desserte.split(',').any { part ->
                    val lineCode = part.split(':').first().trim()
                    lineCode.equals(lineName, ignoreCase = true) ||
                            (lineName.equals("NAV1", ignoreCase = true) && lineCode.equals("NAVI1", ignoreCase = true))
                }
            }.map { it.properties.nom }.toSet()

            // Index by normalized name -> exact official name
            val normalizedToOfficial = officialStopsForLine.associateBy { normalizeStopName(it) }

            // Replace cached name with official label if found
            // Then deduplicate by name (double platforms, writing variants), preserving order
            val mapped = cachedStops.map { stop ->
                val officialName = normalizedToOfficial[normalizeStopName(stop.stopName)] ?: stop.stopName
                val connections = getConnectionsForStop(officialName, lineName)
                stop.copy(
                    stopName = officialName,
                    connections = connections.map { it.lineName }
                )
            }
            val dedup = mapped.distinctBy { normalizeStopName(it.stopName) }
            val result = dedup.mapIndexed { index, stop ->
                stop.copy(
                    stopSequence = index + 1,
                    isCurrentStop = currentStopName?.let {
                        normalizeStopName(stop.stopName) == normalizeStopName(it)
                    } ?: stop.isCurrentStop
                )
            }
            // Cache the result for future lookups
            lineStopsCache.put(cacheKey, result)
            return result
        }

        // Get all stops from cache
        val allStops = getCachedStopsSync()

        // API uses NAVI1 but we display NAV1, so we need to search for both
        val searchNames = if (lineName.equals("NAV1", ignoreCase = true)) {
            listOf("NAV1", "NAVI1")
        } else {
            listOf(lineName)
        }

        // Filter stops that are served by this line
        val lineStops = allStops.filter { stop ->
            val desserte = stop.properties.desserte
            desserte.split(',').any { part ->
                val lineCode = part.split(':').first().trim()
                searchNames.any { searchName -> lineCode.equals(searchName, ignoreCase = true) }
            }
        }

        // Get all line traces to order stops
        val currentState = _uiState.value
        val currentLines = when (currentState) {
            is TransportLinesUiState.Success -> currentState.lines
            is TransportLinesUiState.PartialSuccess -> currentState.lines
            else -> null
        }

        if (currentLines != null) {
            // Get ALL traces of this line (may have multiple directions)
            val lineFeatures = currentLines.filter {
                lineName.equals(it.properties.ligne, ignoreCase = true)
            }

            if (lineFeatures.isNotEmpty()) {
                // Choose the longest trace (generally main/outbound direction)
                val mainTrace = lineFeatures.maxByOrNull { feature ->
                    feature.geometry.coordinates.sumOf { lineString -> lineString.size }
                }

                if (mainTrace != null) {
                    // The trace is a MultiLineString with multiple segments
                    // Find the longest segment (main trace)
                    val longestSegment = mainTrace.geometry.coordinates.maxByOrNull { segment ->
                        segment.size
                    } ?: emptyList()

                    // Determine main trace direction and convert it to letter (A or R)
                    // Be defensive: API may return null sens unexpectedly, avoid NPEs
                    val sensUpper = mainTrace.properties.sens?.uppercase() ?: ""
                    val mainDirection = when (sensUpper) {
                        "ALLER" -> "A"
                        "RETOUR" -> "R"
                        else -> sensUpper.take(1) // First character already uppercased or empty
                    }

                    // Filter stops to keep only those that match the main direction
                    val directionStops = lineStops.filter { stop ->
                        // Be defensive: desserte can be blank in some rare cases
                        val desserte = stop.properties.desserte
                        // Look for "86:A" or "86:R" (or "NAVI1:A" for NAV1) in the desserte
                        val matches = desserte.split(",").any { line ->
                            val trimmed = line.trim()
                            // Check all search names with the direction
                            val result = if (mainDirection.isNotEmpty()) {
                                searchNames.any { searchName ->
                                    trimmed.equals("$searchName:$mainDirection", ignoreCase = true)
                                }
                            } else {
                                // If direction unknown, accept any entry for the searchName
                                searchNames.any { searchName ->
                                    // e.g. "86:A" or "86:R" or just "86"
                                    trimmed.startsWith(searchName, ignoreCase = true)
                                }
                            }
                            result
                        }
                        matches
                    }

                    // For each stop, find its position on the main segment
                    val stopsWithPosition = directionStops.map { stop ->
                        val stopCoords = stop.geometry.coordinates

                        // Find the closest point on the segment and its index
                        val closestPointIndex = longestSegment.withIndex().minByOrNull { (_, coord) ->
                            sqrt(
                                (coord[0] - stopCoords[0]).pow(2.0) +
                                        (coord[1] - stopCoords[1]).pow(2.0)
                            )
                        }?.index ?: 0

                        Pair(stop, closestPointIndex)
                    }

                    // Sort stops according to their position on the trace
                    val orderedStops = stopsWithPosition
                        .sortedBy { (_, traceIndex) -> traceIndex }
                        .map { (stop, _) -> stop }

                    // Deduplicate by normalized name (some lines have double platforms)
                    val dedupOrdered = orderedStops.distinctBy { normalizeStopName(it.properties.nom) }

                    // Convert to LineStopInfo
                    val result = dedupOrdered.mapIndexed { index, stop ->
                        val connections = getConnectionsForStop(stop.properties.nom, lineName)
                        com.pelotcl.app.data.gtfs.LineStopInfo(
                            stopId = stop.properties.id.toString(),
                            stopName = stop.properties.nom,
                            stopSequence = index + 1,
                            isCurrentStop = currentStopName?.let {
                                normalizeStopName(stop.properties.nom) == normalizeStopName(it)
                            } ?: false,
                            connections = connections.map { it.lineName }
                        )
                    }
                    // Cache the result for future lookups
                    lineStopsCache.put(cacheKey, result)
                    return result
                }
            }
        }

        // Fallback: if no trace found, at least remove duplicates
        val uniqueStops = lineStops.distinctBy { stop ->
            normalizeStopName(stop.properties.nom)
        }

        val result = uniqueStops.mapIndexed { index, stop ->
            val connections = getConnectionsForStop(stop.properties.nom, lineName)
            com.pelotcl.app.data.gtfs.LineStopInfo(
                stopId = stop.properties.id.toString(),
                stopName = stop.properties.nom,
                stopSequence = index + 1,
                isCurrentStop = currentStopName?.let {
                    normalizeStopName(stop.properties.nom) == normalizeStopName(it)
                } ?: false,
                connections = connections.map { it.lineName }
            )
        }
        // Cache the result for future lookups
        lineStopsCache.put(cacheKey, result)
        return result
    }

    /**
     * Retrieves the list of all available lines (names only)
     * by extracting lines from all loaded stops
     */
    fun getAllAvailableLines(): List<String> {
        // First, try to extract from loaded lines
        val linesFromFeatures = when (val currentState = _uiState.value) {
            is TransportLinesUiState.Success -> {
                currentState.lines
                    .map { it.properties.ligne.split(':').first().trim() }
                    .distinct()
            }
            is TransportLinesUiState.PartialSuccess -> {
                currentState.lines
                    .map { it.properties.ligne.split(':').first().trim() }
                    .distinct()
            }
            else -> emptyList()
        }

        // Then, extract from all stops (to get ALL lines, including buses)
        val linesFromStops = getCachedStopsSync()
            .flatMap { stop ->
                val desserte = stop.properties.desserte
                desserte.split(',').map { it.split(':').first().trim() }
            }
            .distinct()

        // Combine and deduplicate using normalized names (e.g. NAVI1 & NAV1 → keep first seen)
        // Prefer names from Features (GeoJSON) since they're used for API lookups
        return (linesFromFeatures + linesFromStops)
            .groupBy { normalizeLineName(it) }
            .map { (_, variants) -> variants.first() }
            .filter { it.isNotEmpty() && !it.equals("TS", ignoreCase = true) }
            .sortedWith(compareBy(
                // Sort by type first (Metro, Funicular, Navigone, Tram, then the rest)
                { line ->
                    when {
                        line.uppercase() in setOf("A", "B", "C", "D") -> 0 // Metros first
                        line.uppercase().startsWith("F") -> 1 // Funiculars
                        line.uppercase().startsWith("NAV") -> 2 // Navigone
                        line.uppercase().startsWith("T") && !line.uppercase().startsWith("TB") -> 3 // Trams
                        else -> 4 // The rest (buses)
                    }
                },
                // Then by name (numeric if possible, otherwise alphabetical)
                { line ->
                    line.toIntOrNull() ?: Int.MAX_VALUE
                },
                { line -> line }
            ))
    }

    /**
     * Traffic Alerts Management
     */

    private fun buildTrafficSubscriptionLines(): Set<String> {
        val selected = _selectedLineName.value
            ?.trim()
            ?.uppercase()
            ?.takeIf { it.isNotEmpty() }

        return buildSet {
            addAll(DEFAULT_ALERT_SUBSCRIPTION_LINES)
            addAll(_favoriteLines.value.map { it.uppercase() })
            if (selected != null) add(selected)
        }.take(50).toSet()
    }

    private fun startTrafficAlertsStreaming(forceRestart: Boolean = false) {
        if (_isOffline.value) return

        val subscription = buildTrafficSubscriptionLines()
        if (!forceRestart && subscription == lastTrafficSubscription && trafficAlertsStreamJob?.isActive == true) {
            return
        }

        stopTrafficAlertsStreaming()
        lastTrafficSubscription = subscription

        trafficAlertsStreamJob = viewModelScope.launch {
            trafficAlertsRepository.streamTrafficAlerts(subscription.toList()).collect { result ->
                result.onSuccess { push ->
                    applyTrafficAlertPush(push)
                }.onFailure {
                    Log.w("TransportViewModel", "Traffic WS event error: ${it.message}")
                }
            }
        }
    }

    private fun stopTrafficAlertsStreaming() {
        trafficAlertsStreamJob?.cancel()
        trafficAlertsStreamJob = null
        lastTrafficSubscription = emptySet()
    }

    private fun applyTrafficAlertPush(push: TrafficAlertPush) {
        val normalizedLine = normalizeLineForAlerts(push.line)
        val current = _trafficAlerts.value.toMutableMap()
        val existingForLine = current[normalizedLine].orEmpty()

        val updatedForLine = existingForLine
            .filterNot { sameAlertIdentity(it, push.alert) }
            .plus(push.alert)

        current[normalizedLine] = updatedForLine
        _trafficAlerts.value = current
        _alertsTimestampMillis.value = System.currentTimeMillis()
    }

    private fun sameAlertIdentity(
        first: com.pelotcl.app.data.model.TrafficAlert,
        second: com.pelotcl.app.data.model.TrafficAlert
    ): Boolean {
        return first.alertNumber == second.alertNumber &&
                first.lineCode.equals(second.lineCode, ignoreCase = true) &&
                first.title == second.title &&
                first.message == second.message
    }

    /**
     * Gets traffic status (alert count and last update)
     */
    fun getTrafficStatus() = viewModelScope.launch {
        trafficAlertsRepository.getTrafficStatus()
    }

    /**
     * Gets all traffic alerts
     */
    fun getAllTrafficAlerts() = viewModelScope.launch {
        trafficAlertsRepository.getTrafficAlerts()
    }

    /**
     * Gets alerts for a specific line (uses cached state first)
     */
    fun getAlertsForLine(lineCode: String): List<com.pelotcl.app.data.model.TrafficAlert> {
        val normalizedLineCode = normalizeLineForAlerts(lineCode)
        val alerts = _trafficAlerts.value[normalizedLineCode] ?: emptyList()
        if (alerts.isEmpty() && _trafficAlerts.value.isNotEmpty()) {
            // Log known keys to help debug mapping
            Log.d("AlertCheck", "No alerts for $normalizedLineCode. Available keys: ${_trafficAlerts.value.keys.take(5)}...")
        }
        return alerts
    }

    /**
     * Gets the most severe alert for a specific line (uses cached state first)
     */
    fun getMostSevereAlertForLine(lineCode: String): com.pelotcl.app.data.model.TrafficAlert? {
        val alerts = getAlertsForLine(lineCode)
        return if (alerts.isEmpty()) {
            null
        } else {
            // Find the alert with the highest severity (lowest severityLevel value)
            alerts.minByOrNull { it.severityLevel }
        }
    }

    /**
     * Gets the alert severity for a specific line (or null if no alerts)
     */
    fun getAlertSeverityForLine(lineCode: String): com.pelotcl.app.data.model.AlertSeverity? {
        val alert = getMostSevereAlertForLine(lineCode)
        val severity = alert?.let {
            com.pelotcl.app.data.model.AlertSeverity.fromSeverityType(it.severityType, it.severityLevel)
        }
        Log.d("AlertCheck", "getAlertSeverityForLine($lineCode) -> normalized: ${normalizeLineForAlerts(lineCode)}, hasAlert: ${alert != null}, severity: ${severity?.name}")
        return severity
    }

    /**
     * Refreshes traffic alerts cache and updates state
     */
    fun refreshTrafficAlerts() = viewModelScope.launch {
        try {
            val result = trafficAlertsRepository.getTrafficAlerts()
            if (result.isSuccess) {
                val alerts = result.getOrDefault(emptyList())
                Log.d("AlertCheck", "Fetched ${alerts.size} alerts from repository")
                // Group alerts by line code (normalized for robust matching)
                val alertsByLine = alerts.groupBy { alert: com.pelotcl.app.data.model.TrafficAlert ->
                    val normalized = normalizeLineForAlerts(alert.lineCode)
                    Log.d("AlertCheck", "Mapping alert for ${alert.lineCode} to $normalized")
                    normalized
                }
                _trafficAlerts.value = alertsByLine
                _alertsTimestampMillis.value = trafficAlertsRepository.getAlertsTimestampMillis()
                Log.d("AlertCheck", "Updated _trafficAlerts with ${alertsByLine.size} unique normalized lines")
            } else {
                Log.e("AlertCheck", "Failed to fetch alerts: ${result.exceptionOrNull()?.message}")
            }
        } catch (e: Exception) {
            Log.e("TransportViewModel", "Error refreshing traffic alerts", e)
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopTrafficAlertsStreaming()
        stopLiveTracking()
        stopGlobalLive()
    }
}
