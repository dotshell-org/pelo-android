package com.pelotcl.app.generic.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pelotcl.app.generic.data.model.Favorite
import com.pelotcl.app.generic.data.model.SimpleVehiclePosition
import com.pelotcl.app.generic.data.model.StopFeature
import com.pelotcl.app.generic.data.network.TransportApi
import com.pelotcl.app.generic.service.TransportServiceProvider
import com.pelotcl.app.generic.data.repository.TransportRepository
import com.pelotcl.app.generic.data.repository.online.TrafficAlertsRepository
import com.pelotcl.app.generic.data.repository.online.VehiclePositionsRepository
import com.pelotcl.app.generic.data.repository.offline.FavoritesRepository
import com.pelotcl.app.generic.data.repository.offline.SchedulesRepository
import com.pelotcl.app.generic.data.offline.OfflineDataManager
import com.pelotcl.app.generic.data.offline.OfflineDataInfo
import com.pelotcl.app.generic.data.model.LineStopInfo
import com.pelotcl.app.generic.data.model.TrafficAlert
import com.pelotcl.app.generic.data.model.AlertSeverity
import com.pelotcl.app.generic.data.repository.itinerary.RaptorRepository
import com.pelotcl.app.specific.data.mapper.StopMapper
import com.pelotcl.app.generic.ui.components.search.LineSearchResult
import com.pelotcl.app.generic.ui.components.search.StationSearchResult
import com.pelotcl.app.generic.ui.theme.AccentColor
import com.pelotcl.app.utils.HolidayDetector
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class LineSection(
    val lineName: String,
    val startStop: String,
    val endStop: String
)

/**
 * ViewModel principal pour la gestion des données de transport
 * Utilise TransportServiceProvider pour accéder aux services
 */
class TransportViewModel(private val context: Context) : ViewModel() {

    companion object {
        private const val TAG = "TransportViewModel"
        // Set to false for production builds to skip string formatting overhead in hot paths
        private const val DEBUG_LOGGING = false
    }

    private val transportApi: TransportApi = TransportServiceProvider.getTransportApi()
    private val vehiclePositionsService = TransportServiceProvider.getVehiclePositionsService()
    private val lineRules = TransportServiceProvider.getTransportLineRules()
    internal val transportRepository: TransportRepository = TransportRepository(context)
    private val trafficAlertsRepository = TrafficAlertsRepository(transportApi, context)
    private val vehiclePositionsRepository = VehiclePositionsRepository(vehiclePositionsService)
    private val schedulesRepository = SchedulesRepository.getInstance(context)
    private val holidayDetector by lazy { HolidayDetector(context.applicationContext) }
    private var vehiclePositionsJob: Job? = null
    private var globalLiveJob: Job? = null
    private val favoritesRepository = FavoritesRepository(context)
    val raptorRepository = RaptorRepository.getInstance(context)
    val offlineDataManager = OfflineDataManager(transportApi, context)
    private val transportCache by lazy { com.pelotcl.app.specific.data.cache.TransportCacheImpl(context) }
    private val offlineRepository by lazy { com.pelotcl.app.generic.data.offline.OfflineRepository(context) }
    private val _linesState = MutableStateFlow<TransportLinesState>(TransportLinesState.Loading)

    // Compatibilité avec PlanScreen.kt qui utilise TransportLinesUiState
    private val _uiState = MutableStateFlow<TransportLinesUiState>(TransportLinesUiState.Loading)
    val uiState: StateFlow<TransportLinesUiState> = _uiState.asStateFlow()
    
    // État pour les alertes trafic
    private val _alertsState = MutableStateFlow<TrafficAlertsState>(TrafficAlertsState.Loading)

    // État pour les arrêts de transport
    private val _stopsUiState = MutableStateFlow<TransportStopsUiState>(TransportStopsUiState.Loading)
    val stopsUiState: StateFlow<TransportStopsUiState> = _stopsUiState.asStateFlow()

    // Alertes trafic (Flow pour LinesBottomSheet)
    private val _trafficAlerts = MutableStateFlow<List<TrafficAlert>>(emptyList())
    val trafficAlerts: StateFlow<List<TrafficAlert>> = _trafficAlerts.asStateFlow()

    private val _alertsTimestampMillis = MutableStateFlow<Long?>(null)
    val alertsTimestampMillis: StateFlow<Long?> = _alertsTimestampMillis.asStateFlow()

    // Positions des véhicules (PlanScreen)
    private val _vehiclePositions = MutableStateFlow<List<SimpleVehiclePosition>>(emptyList())
    val vehiclePositions: StateFlow<List<SimpleVehiclePosition>> = _vehiclePositions.asStateFlow()

    private val _globalVehiclePositions = MutableStateFlow<List<SimpleVehiclePosition>>(emptyList())
    val globalVehiclePositions: StateFlow<List<SimpleVehiclePosition>> = _globalVehiclePositions.asStateFlow()

    private val _isLiveTrackingEnabled = MutableStateFlow(false)
    val isLiveTrackingEnabled: StateFlow<Boolean> = _isLiveTrackingEnabled.asStateFlow()

    private val _isGlobalLiveEnabled = MutableStateFlow(false)
    val isGlobalLiveEnabled: StateFlow<Boolean> = _isGlobalLiveEnabled.asStateFlow()

    private val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

    // États pour LineDetailsBottomSheet
    private val _headsigns = MutableStateFlow<Map<Int, String>>(emptyMap())
    val headsigns: StateFlow<Map<Int, String>> = _headsigns.asStateFlow()

    private val _allSchedules = MutableStateFlow<List<String>>(emptyList())
    val allSchedules: StateFlow<List<String>> = _allSchedules.asStateFlow()

    private val _nextSchedules = MutableStateFlow<List<String>>(emptyList())
    val nextSchedules: StateFlow<List<String>> = _nextSchedules.asStateFlow()

    private val _availableDirections = MutableStateFlow<List<Int>>(emptyList())
    val availableDirections: StateFlow<List<Int>> = _availableDirections.asStateFlow()

    // Favoris (anciens)
    private val _favoriteStops = MutableStateFlow<Set<String>>(emptySet())
    val favoriteStops: StateFlow<Set<String>> = _favoriteStops.asStateFlow()

    // Favoris utilisateur (nouveaux)
    private val _userFavorites = MutableStateFlow<List<Favorite>>(emptyList())
    val userFavorites: StateFlow<List<Favorite>> = _userFavorites.asStateFlow()

    private val _selectedLineName = MutableStateFlow<String?>(null)
    val selectedLineName: StateFlow<String?> = _selectedLineName.asStateFlow()

    private val _offlineDataInfo = MutableStateFlow(OfflineDataInfo())
    val offlineDataInfo: StateFlow<OfflineDataInfo> = _offlineDataInfo.asStateFlow()

    // Cache for expensive line aggregation used by LinesBottomSheet.
    private var cachedAvailableLines: List<String> = emptyList()
    private var cachedAvailableLinesUiState: TransportLinesUiState? = null
    private var cachedAvailableLinesStopsState: TransportStopsUiState? = null

    // Cache alert line index to avoid O(lines * alerts) recomputation in UI.
    private var cachedAlertIndexSource: List<TrafficAlert>? = null
    private var cachedAlertSeverityByLine: Map<String, AlertSeverity> = emptyMap()

    // Cache alerts grouped by line for O(1) lookup in getAlertsForLine()
    private var cachedAlertsByLineSource: List<TrafficAlert>? = null
    private var cachedAlertsByLine: Map<String, List<TrafficAlert>> = emptyMap()
    
    init {
        // Load favorites first (synchronous SharedPrefs read, instant)
        loadFavorites()
        // Fire all async loads in parallel — each launches its own coroutine
        loadTransportLines()
        loadStops()
        loadTrafficAlerts()
    }
    
    /**
     * Charge les lignes de transport
     */
    fun loadTransportLines() {
        viewModelScope.launch {
            _linesState.value = TransportLinesState.Loading
            _uiState.value = TransportLinesUiState.Loading
            try {
                val result = transportRepository.getAllLines()
                result.onSuccess { lines ->
                    _linesState.value = TransportLinesState.Success(lines)
                    _uiState.value = TransportLinesUiState.Success(lines.features.orEmpty())
                    
                    // Save lines to cache for future use
                    val cache = transportCache
                    val metroLines = lines.features.filter { 
                        it.properties.transportType == "METRO" || 
                        it.properties.transportType == "FUNICULAR"
                    }
                    val tramLines = lines.features.filter { 
                        it.properties.transportType == "TRAM"
                    }
                    
                    if (metroLines.isNotEmpty()) {
                        cache.saveMetroLines(metroLines)
                    }
                    if (tramLines.isNotEmpty()) {
                        cache.saveTramLines(tramLines)
                    }
                }.onFailure { error ->
                    _linesState.value = TransportLinesState.Error(error.message ?: "Unknown error")
                    _uiState.value = TransportLinesUiState.Error(error.message ?: "Unknown error")
                }
            } catch (e: Exception) {
                _linesState.value = TransportLinesState.Error(e.message ?: "Unknown error")
                _uiState.value = TransportLinesUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Charge tous les arrêts
     */
    fun loadStops() {
        viewModelScope.launch {
            _stopsUiState.value = TransportStopsUiState.Loading
            val cache = transportCache

            // Try to load from offline cache first
            val offlineStops = runCatching {
                withContext(Dispatchers.IO) { offlineRepository.loadStops() }
            }.getOrNull().orEmpty()

            if (DEBUG_LOGGING) Log.d(TAG, "Loaded ${offlineStops.size} stops from offline cache")
            if (offlineStops.isNotEmpty()) {
                val sampleSize = minOf(10, offlineStops.size)
                val emptyDesserteCount = offlineStops.take(sampleSize).count { it.properties.desserte.isBlank() }
                val unknownCount = offlineStops.take(sampleSize).count { it.properties.desserte.equals("UNKNOWN", ignoreCase = true) }
                if (DEBUG_LOGGING) Log.d(TAG, "Offline cache analysis (sample=$sampleSize): ${emptyDesserteCount} empty, ${unknownCount} UNKNOWN")
                
                // If most stops have empty desserte, force refresh from WFS
                if (emptyDesserteCount > sampleSize * 0.8) {
                    Log.w("TransportViewModel", "Offline cache has mostly empty desserte - forcing refresh from WFS")
                    // Clear the cache and force fresh data load
                    withContext(Dispatchers.IO) {
                        try {
                            File(context.filesDir, "offline_data/stops.json.gz").delete()
                            if (DEBUG_LOGGING) Log.d(TAG, "Cleared stale stops cache")
                        } catch (e: Exception) {
                            Log.e("TransportViewModel", "Failed to clear cache: ${e.message}")
                        }
                    }
                    // Now load fresh data
                    return@launch loadStops()
                }
            }

            // If offline data is empty, try to get stops from cache
            val cachedStops = runCatching {
                withContext(Dispatchers.IO) { cache.getStops() }
            }.getOrNull().orEmpty()
            
            if (DEBUG_LOGGING) Log.d(TAG, "Loaded ${cachedStops.size} stops from TransportCache")
            
            // Determine base stops with clear priority: offline > cache > WFS
            val baseStops: List<StopFeature> = when {
                offlineStops.isNotEmpty() -> {
                    if (DEBUG_LOGGING) Log.d(TAG, "Using offline cache (${offlineStops.size} stops)")
                    offlineStops
                }
                cachedStops.isNotEmpty() -> {
                    if (DEBUG_LOGGING) Log.d(TAG, "Using TransportCache (${cachedStops.size} stops)")
                    cachedStops
                }
                else -> {
                    // Both caches empty - load from WFS API
                    if (DEBUG_LOGGING) Log.d(TAG, "Both offline cache and TransportCache are empty, loading from WFS API")
                    val wfsResult = runCatching {
                        withContext(Dispatchers.IO) { transportApi.getTransportStops() }
                    }
                    wfsResult.onFailure { error ->
                        Log.e("TransportViewModel", "Failed to load transport stops from WFS API: ${error.message}", error)
                        _stopsUiState.value = TransportStopsUiState.Error(
                            error.message ?: "Unable to load transport stops"
                        )
                        return@launch
                    }
                    val features = wfsResult.getOrNull()?.features
                    if (features.isNullOrEmpty()) {
                        Log.e("TransportViewModel", "WFS API returned empty or null features")
                        _stopsUiState.value = TransportStopsUiState.Error(
                            "No transport stops available from server"
                        )
                        return@launch
                    }
                    if (DEBUG_LOGGING) Log.d(TAG, "WFS API returned ${features.size} stop features")
                    features
                }
            }

            if (baseStops.isEmpty()) {
                _stopsUiState.value = TransportStopsUiState.Error("No transport stops available")
                return@launch
            }

            // WFS "desserte" is not guaranteed to be present/accurate across versions.
            // To keep line/stop matching working (map + bottom sheets), we enrich stops
            // using the local Raptor/GTFS dataset when most stops have empty desserte.
            val (enrichedStops, didEnrich) = withContext(Dispatchers.Default) {
                fun hasStrongToken(desserte: String): Boolean {
                    if (desserte.isBlank()) return false
                    val strongTokens = setOf("A", "B", "C", "D", "F1", "F2", "RX")
                    val entries = desserte.split(",")
                    for (entry in entries) {
                        val token = entry.trim().substringBefore(":").trim()
                        if (token.isEmpty()) continue
                        val up = token.uppercase()
                        if (up in strongTokens) return true
                        if (up.startsWith("T")) return true // includes TBxx
                        if (up.startsWith("NAV")) return true // NAV1 / NAVI1
                    }
                    return false
                }

                // Only check a small sample of stops to determine if enrichment is needed
                val sampleSize = minOf(50, baseStops.size)
                val sampleStops = baseStops.take(sampleSize) // Take first stops for consistency
                val nonBlankCount = sampleStops.count { it.properties.desserte.isNotBlank() }
                val ratioNonBlank = nonBlankCount.toDouble() / sampleSize.toDouble()
                val strongStopCount = sampleStops.count { it.properties.desserte.isNotBlank() && hasStrongToken(it.properties.desserte) }
                
                val unknownCount = sampleStops.count { it.properties.desserte.equals("UNKNOWN", ignoreCase = true) }
                val emptyCount = sampleStops.count { it.properties.desserte.isBlank() }

                if (DEBUG_LOGGING) {
                    Log.d(TAG, "Stop desserte analysis (sample=$sampleSize):")
                    Log.d(TAG, "  Non-blank: $nonBlankCount (${ratioNonBlank * 100}%)")
                    Log.d(TAG, "  Strong stops: $strongStopCount")
                    Log.d(TAG, "  UNKNOWN: $unknownCount")
                    Log.d(TAG, "  Empty: $emptyCount")
                }
                
                // Enrichment conditions:
                // 1. Original condition: very few stops have desserte (legacy case)
                // 2. Many stops have "UNKNOWN" desserte (WFS data quality issue)
                // 3. Few strong stops relative to total (indicates missing bus data)
                val shouldEnrich = strongStopCount == 0 && ratioNonBlank < 0.1 || 
                                 unknownCount > sampleSize * 0.3 ||
                                 (strongStopCount < sampleSize * 0.2 && unknownCount > sampleSize * 0.2)

                if (!shouldEnrich) {
                    baseStops to false
                } else {
                    // Enrich ALL stops that need it, regardless of count
                    // This ensures metro/tram/funicular stops are properly enriched
                    val desserteCache = HashMap<String, String>(512)
                    val stopsNeedingEnrichment = baseStops.filter { it.properties.desserte.isBlank() }
                    
                    if (DEBUG_LOGGING) Log.d(TAG, "Enriching ${stopsNeedingEnrichment.size} stops that have empty desserte")
                    
                    val enriched = stopsNeedingEnrichment.mapNotNull { stop ->
                        val name = stop.properties.nom
                        if (name.isBlank()) {
                            stop
                        } else {
                            val desserte = desserteCache.getOrPut(name) {
                                schedulesRepository.getDesserteForStop(name).orEmpty()
                            }
                            if (DEBUG_LOGGING) Log.d(TAG, "Enriching stop '$name': WFS desserte='' -> Raptor desserte='$desserte'")
                            if (desserte.isBlank()) {
                                null // Skip stops with no desserte from Raptor
                            } else {
                                stop.copy(properties = stop.properties.copy(desserte = desserte))
                            }
                        }
                    }
                    
                    if (DEBUG_LOGGING) Log.d(TAG, "Successfully enriched ${enriched.size} stops, ${stopsNeedingEnrichment.size - enriched.size} stops have no Raptor data")
                    
                    // Merge enriched stops back with original stops (keep original if not enriched)
                    val stopMap = baseStops.associateBy { it.properties.nom }.toMutableMap()
                    enriched.forEach { enrichedStop ->
                        stopMap[enrichedStop.properties.nom] = enrichedStop
                    }
                    
                    stopMap.values.toList() to (enriched.isNotEmpty())
                }
            }

            // Enrich stop names from Raptor/GTFS by matching coordinates
            val stopsWithNames = withContext(Dispatchers.Default) {
                val stopsNeedingNameEnrichment = enrichedStops.filter {
                    it.properties.nom.startsWith("Arret ") ||
                    it.properties.nom.contains("Arrondissement")
                }

                if (stopsNeedingNameEnrichment.isNotEmpty()) {
                    val raptorStopsWithCoords = raptorRepository.getAllStopsWithCoords()

                    // Build spatial hash grid for O(1) nearest-neighbor lookup
                    // Key = (latBucket, lonBucket) truncated to ~100m cells
                    val gridCellSize = 0.001 // ~100m in degrees
                    val spatialGrid = HashMap<Long, MutableList<com.pelotcl.app.generic.data.repository.itinerary.RaptorStopWithCoords>>()
                    for (raptorStop in raptorStopsWithCoords) {
                        val latBucket = (raptorStop.lat / gridCellSize).toLong()
                        val lonBucket = (raptorStop.lon / gridCellSize).toLong()
                        val key = latBucket * 1_000_000L + lonBucket
                        spatialGrid.getOrPut(key) { mutableListOf() }.add(raptorStop)
                    }

                    var enrichedCount = 0
                    val result = enrichedStops.map { stop ->
                        if (stop.properties.nom.startsWith("Arret ") || stop.properties.nom.contains("Arrondissement")) {
                            val coords = stop.geometry.coordinates
                            if (coords.size >= 2) {
                                val wfsLon = coords[0]
                                val wfsLat = coords[1]
                                val latBucket = (wfsLat / gridCellSize).toLong()
                                val lonBucket = (wfsLon / gridCellSize).toLong()

                                // Search current cell + 8 neighbors for closest stop
                                var bestStop: com.pelotcl.app.generic.data.repository.itinerary.RaptorStopWithCoords? = null
                                var bestDistSq = Double.MAX_VALUE
                                for (dLat in -1L..1L) {
                                    for (dLon in -1L..1L) {
                                        val key = (latBucket + dLat) * 1_000_000L + (lonBucket + dLon)
                                        spatialGrid[key]?.forEach { raptorStop ->
                                            val latDiff = wfsLat - raptorStop.lat
                                            val lonDiff = wfsLon - raptorStop.lon
                                            val distSq = latDiff * latDiff + lonDiff * lonDiff
                                            if (distSq < bestDistSq) {
                                                bestDistSq = distSq
                                                bestStop = raptorStop
                                            }
                                        }
                                    }
                                }

                                // Only use match if within ~50 meters (0.0005 degrees)
                                val matched = bestStop
                                if (matched != null && bestDistSq < 0.0005 * 0.0005) {
                                    enrichedCount++
                                    stop.copy(properties = stop.properties.copy(nom = matched.name))
                                } else {
                                    stop
                                }
                            } else {
                                stop
                            }
                        } else {
                            stop
                        }
                    }
                    if (DEBUG_LOGGING) Log.d(TAG, "Enriched $enrichedCount stop names by coordinates")
                    result
                } else {
                    enrichedStops
                }
            }

            _stopsUiState.value = TransportStopsUiState.Success(stopsWithNames)

            // Analyze loaded stops to understand data quality
            analyzeLoadedStops(stopsWithNames)

            // Warm offline storage and cache for next launches.
            // Always save to both stores when we have valid data, regardless of source.
            // Failures are logged but do not break the UI.
            if (stopsWithNames.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    try {
                        offlineRepository.saveStops(stopsWithNames)
                        if (DEBUG_LOGGING) Log.d(TAG, "Saved ${stopsWithNames.size} stops to offline storage")
                    } catch (e: Exception) {
                        Log.e("TransportViewModel", "Failed to save to offline storage: ${e.message}", e)
                    }
                    try {
                        cache.saveStops(stopsWithNames)
                        if (DEBUG_LOGGING) Log.d(TAG, "Saved ${stopsWithNames.size} stops to TransportCache")
                    } catch (e: Exception) {
                        Log.e("TransportViewModel", "Failed to save to TransportCache: ${e.message}", e)
                    }
                }
            } else {
                Log.e("TransportViewModel", "No stops to cache (stopsWithNames is empty)")
            }
        }
    }

    /**
     * Charge les favoris
     */
    fun loadFavorites() {
        _favoriteStops.value = favoritesRepository.getFavoriteStops()
        _userFavorites.value = favoritesRepository.getUserFavorites()
    }

    fun addUserFavorite(name: String, iconName: String, stopName: String) {
        val newFavorite = Favorite(
            id = favoritesRepository.generateFavoriteId(),
            name = name,
            iconName = iconName,
            stopName = stopName
        )
        if (favoritesRepository.addFavorite(newFavorite)) {
            loadFavorites()
        }
    }

    fun removeUserFavorite(favoriteId: String) {
        if (favoritesRepository.removeFavorite(favoriteId)) {
            loadFavorites()
        }
    }

    fun toggleFavoriteStop(stopName: String) {
        favoritesRepository.toggleFavoriteStop(stopName)
        loadFavorites()
    }

    fun getConnectionsForStop(stopName: String, lineName: String): Flow<List<LineSearchResult>> {
        return flow {
            val rawDesserte = schedulesRepository.getDesserteForStop(stopName)
            if (DEBUG_LOGGING) Log.d(TAG, "getConnectionsForStop($stopName): raw desserte = '$rawDesserte'")
            val lines = rawDesserte.orEmpty()
                .split(",")
                .mapNotNull { token ->
                    val line = token.trim().substringBefore(":").trim()
                    line.takeIf { it.isNotEmpty() && !it.equals(lineName, ignoreCase = true) }
                }
                .distinctBy { it.uppercase() }
            if (DEBUG_LOGGING) Log.d(TAG, "getConnectionsForStop($stopName): parsed ${lines.size} lines: $lines")
            emit(lines.map { LineSearchResult(it) })
        }
    }

    private fun analyzeLoadedStops(stops: List<StopFeature>) {
        if (!DEBUG_LOGGING) return
        if (stops.isEmpty()) {
            Log.w(TAG, "analyzeLoadedStops: no stops to analyze")
            return
        }
        val sample = stops.take(100)
        val withName = sample.count { it.properties.nom.isNotBlank() }
        val emptyName = sample.count { it.properties.nom.isBlank() }
        val withDesserte = sample.count { it.properties.desserte.isNotBlank() }
        val emptyDesserte = sample.count { it.properties.desserte.isBlank() }
        val sampleStops = sample.filter { it.properties.nom.isNotBlank() }.take(5)
        Log.d(TAG, "Loaded stops analysis (first 100):")
        Log.d(TAG, "  With name: $withName, empty name: $emptyName")
        Log.d(TAG, "  With desserte: $withDesserte, empty desserte: $emptyDesserte")
        sampleStops.forEach { stop ->
            Log.d(TAG, "  Sample stop: nom='${stop.properties.nom}', desserte='${stop.properties.desserte}'")
        }
    }

    suspend fun getNextDeparturesForStop(
        stopName: String,
        lines: List<String>
    ): List<StopDeparturePreview> = withContext(Dispatchers.Default) {
        if (DEBUG_LOGGING) Log.d(TAG, "getNextDeparturesForStop($stopName, $lines)")
        if (stopName.isBlank() || lines.isEmpty()) {
            if (DEBUG_LOGGING) Log.d(TAG, "getNextDeparturesForStop: early return - blank stopName or empty lines")
            return@withContext emptyList()
        }

        val today = LocalDate.now()
        val isSchoolHoliday = holidayDetector.isSchoolHoliday(today)
        val isPublicHoliday = holidayDetector.isFrenchPublicHoliday(today)
        val now = java.util.Calendar.getInstance()
        val nowMinutes = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)

        lines.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.uppercase() }
            .flatMap { line ->
                val headsigns = schedulesRepository.getHeadsigns(line)
                val directions = headsigns.keys.ifEmpty { setOf(0, 1) }
                directions.asSequence().mapNotNull { directionId ->
                    val schedules = schedulesRepository.getSchedules(
                        lineName = line,
                        stopName = stopName,
                        directionId = directionId,
                        isSchoolHoliday = isSchoolHoliday,
                        isPublicHoliday = isPublicHoliday
                    )
                    val nextDeparture = pickNextDeparture(schedules, nowMinutes) ?: return@mapNotNull null
                    StopDeparturePreview(
                        lineName = line,
                        directionId = directionId,
                        directionName = headsigns[directionId] ?: "Direction ${directionId + 1}",
                        nextDeparture = nextDeparture
                    )
                }
            }
            .sortedWith(
                compareBy(
                    { parseTimeToMinutes(it.nextDeparture) ?: Int.MAX_VALUE },
                    { it.lineName },
                    { it.directionId }
                )
            )
            .toList()
            .also { result ->
                if (DEBUG_LOGGING) Log.d(TAG, "getNextDeparturesForStop($stopName): returning ${result.size} departures")
                if (result.isEmpty()) {
                    Log.w("TransportViewModel", "No departures found for stop '$stopName' with lines $lines")
                    if (DEBUG_LOGGING) Log.d(TAG, "Raptor assets available: ${schedulesRepository.javaClass.kotlin}")
                }
            }
    }

    suspend fun searchStops(query: String): List<StationSearchResult> =
        schedulesRepository.searchStopsByName(query)

    fun searchLines(query: String): List<LineSearchResult> {
        return schedulesRepository.searchLinesByName(query)
    }

    fun getAlertsForLine(lineName: String): List<TrafficAlert> {
        val alerts = trafficAlerts.value
        if (alerts !== cachedAlertsByLineSource) {
            // Rebuild index: group distinct alerts by each line token they affect
            val index = mutableMapOf<String, MutableList<TrafficAlert>>()
            alerts.distinctBy { it.alertNumber }.forEach { alert ->
                extractAlertLineTokens(alert).forEach { token ->
                    index.getOrPut(token) { mutableListOf() }.add(alert)
                }
            }
            // Pre-sort each list by severity for consistent output
            index.values.forEach { list -> list.sortBy { it.severityLevel } }
            cachedAlertsByLine = index
            cachedAlertsByLineSource = alerts
        }
        val target = normalizeLineToken(lineName)
        return cachedAlertsByLine[target] ?: emptyList()
    }

    fun getAllAvailableLines(): List<String> {
        val currentUiState = uiState.value
        val currentStopsState = stopsUiState.value

        if (currentUiState === cachedAvailableLinesUiState &&
            currentStopsState === cachedAvailableLinesStopsState
        ) {
            return cachedAvailableLines
        }

        val linesFromLoadedFeatures = when (currentUiState) {
            is TransportLinesUiState.Success -> currentUiState.lines.map { it.properties.lineName }
            is TransportLinesUiState.PartialSuccess -> currentUiState.lines.map { it.properties.lineName }
            else -> emptyList()
        }

        val linesFromStops = when (currentStopsState) {
            is TransportStopsUiState.Success -> currentStopsState.stops
                .asSequence()
                .flatMap { stop -> parseLineCodesFromDesserte(stop.properties.desserte).asSequence() }
                .toList()

            else -> emptyList()
        }

        val aggregated = (linesFromLoadedFeatures + linesFromStops)
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.uppercase() }
            .toList()

        cachedAvailableLinesUiState = currentUiState
        cachedAvailableLinesStopsState = currentStopsState
        cachedAvailableLines = aggregated
        return aggregated
    }

    fun getStopsForLine(lineName: String, currentStopName: String? = null, directionId: Int? = null): List<LineStopInfo> {
        val state = stopsUiState.value
        if (state !is TransportStopsUiState.Success) return emptyList()

        val effectiveDirection = directionId ?: 0
        val effectiveLineName = resolveScheduleRouteName(lineName)
        val stopSequences = schedulesRepository.getStopSequences(effectiveLineName, effectiveDirection)
        val stopSequenceByName = stopSequences
            .associate { (stopNameFromGtfs, sequence) -> stopNameFromGtfs.uppercase() to sequence }

        val filteredStops = state.stops.filter { stop ->
            parseLineCodesFromDesserte(stop.properties.desserte)
                .any { areEquivalentRouteNames(it, lineName) }
        }

        if (filteredStops.isEmpty()) return emptyList()

        // Pre-compute normalized names once to avoid redundant normalizeStopName() calls
        val normalizedNameCache = HashMap<String, String>(filteredStops.size)
        val stopsByNormalizedName = filteredStops
            .groupBy { stop ->
                normalizedNameCache.getOrPut(stop.properties.nom) { normalizeStopName(stop.properties.nom) }
            }

        val usedStopNames = mutableSetOf<String>()
        val orderedStops = stopSequences.mapNotNull { (stopNameFromGtfs, sequence) ->
            val normalizedName = normalizedNameCache.getOrPut(stopNameFromGtfs) { normalizeStopName(stopNameFromGtfs) }
            if (!usedStopNames.add(normalizedName)) return@mapNotNull null

            val stopFeature = stopsByNormalizedName[normalizedName]?.firstOrNull()
            val displayStopName = stopFeature?.properties?.nom ?: stopNameFromGtfs

            LineStopInfo(
                stopId = stopFeature?.id ?: "${lineName.uppercase()}_${effectiveDirection}_$sequence",
                stopName = displayStopName,
                stopSequence = sequence,
                isCurrentStop = displayStopName.equals(currentStopName, ignoreCase = true)
            )
        }

        if (orderedStops.isNotEmpty()) {
            val missingStops = filteredStops
                .asSequence()
                .filter { normalizedNameCache.getOrPut(it.properties.nom) { normalizeStopName(it.properties.nom) } !in usedStopNames }
                .sortedBy { it.properties.nom.uppercase() }
                .mapIndexed { index, stop ->
                    val normalizedName = normalizedNameCache[stop.properties.nom]!!
                    usedStopNames.add(normalizedName)

                    LineStopInfo(
                        stopId = stop.id,
                        stopName = stop.properties.nom,
                        stopSequence = stopSequenceByName[stop.properties.nom.uppercase()]
                            ?: (orderedStops.size + index + 1),
                        isCurrentStop = stop.properties.nom.equals(currentStopName, ignoreCase = true)
                    )
                }
                .toList()

            return orderedStops + missingStops
        }

        return filteredStops
            .asSequence()
            .sortedBy { it.properties.nom.uppercase() }
            .distinctBy { normalizedNameCache.getOrPut(it.properties.nom) { normalizeStopName(it.properties.nom) } }
            .mapIndexed { index, stop ->
                LineStopInfo(
                    stopId = stop.id,
                    stopName = stop.properties.nom,
                    stopSequence = index + 1,
                    isCurrentStop = stop.properties.nom.equals(currentStopName, ignoreCase = true)
                )
            }
            .toList()
    }

    fun loadHeadsign(lineName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _headsigns.value = schedulesRepository.getHeadsigns(resolveScheduleRouteName(lineName))
        }
    }

    fun computeAvailableDirections(lineName: String, stopName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (lineName.isBlank() || stopName.isBlank()) {
                _availableDirections.value = emptyList()
                return@launch
            }

            val today = LocalDate.now()
            val isSchoolHoliday = holidayDetector.isSchoolHoliday(today)
            val isPublicHoliday = holidayDetector.isFrenchPublicHoliday(today)
            val candidateDirections = _headsigns.value.keys.ifEmpty { setOf(0, 1) }.toList().sorted()

            val available = candidateDirections.filter { directionId ->
                runCatching {
                    schedulesRepository.getSchedules(
                        lineName = resolveScheduleRouteName(lineName),
                        stopName = stopName,
                        directionId = directionId,
                        isSchoolHoliday = isSchoolHoliday,
                        isPublicHoliday = isPublicHoliday
                    )
                }.getOrDefault(emptyList()).isNotEmpty()
            }
            _availableDirections.value = available
        }
    }

    fun loadSchedulesForDirection(lineName: String, stopName: String, directionId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            _allSchedules.value = emptyList()
            _nextSchedules.value = emptyList()

            if (lineName.isBlank() || stopName.isBlank()) return@launch

            val today = LocalDate.now()
            val isSchoolHoliday = holidayDetector.isSchoolHoliday(today)
            val isPublicHoliday = holidayDetector.isFrenchPublicHoliday(today)

            val allSchedulesForDay = schedulesRepository.getSchedules(
                lineName = resolveScheduleRouteName(lineName),
                stopName = stopName,
                directionId = directionId,
                isSchoolHoliday = isSchoolHoliday,
                isPublicHoliday = isPublicHoliday
            )
            _allSchedules.value = allSchedulesForDay
            if (allSchedulesForDay.isEmpty()) return@launch

            val now = java.util.Calendar.getInstance()
            val nowMinutes = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)
            val ordered = allSchedulesForDay.map { it.trim() }.filter { it.isNotEmpty() }.distinct()

            val nextThree = (
                ordered.filter { schedule ->
                    val minutes = parseTimeToMinutes(schedule) ?: return@filter false
                    minutes >= nowMinutes
                } + ordered
            ).take(3)

            _nextSchedules.value = nextThree
        }
    }

    fun getAlertSeverityMapForLines(lineNames: List<String>): Map<String, AlertSeverity> {
        if (lineNames.isEmpty()) return emptyMap()
        val severityByLine = getOrBuildAlertSeverityIndex()
        if (severityByLine.isEmpty()) return emptyMap()

        return lineNames
            .asSequence()
            .mapNotNull { lineName ->
                val token = normalizeLineToken(lineName)
                if (token.isEmpty() || !isLikelyLineToken(token)) return@mapNotNull null
                severityByLine[token]?.let { severity ->
                    lineName.uppercase() to severity
                }
            }
            .toMap()
    }

    private fun getOrBuildAlertSeverityIndex(): Map<String, AlertSeverity> {
        val alerts = trafficAlerts.value
        if (alerts === cachedAlertIndexSource) return cachedAlertSeverityByLine

        val index = mutableMapOf<String, AlertSeverity>()
        alerts
            .asSequence()
            .distinctBy { it.alertNumber }
            .forEach { alert ->
                val severity = AlertSeverity.fromSeverityType(alert.severityType, alert.severityLevel)
                extractAlertLineTokens(alert).forEach { token ->
                    val existing = index[token]
                    if (existing == null || severity.level < existing.level) {
                        index[token] = severity
                    }
                }
            }

        cachedAlertIndexSource = alerts
        cachedAlertSeverityByLine = index
        return index
    }

    private fun extractAlertLineTokens(alert: TrafficAlert): Set<String> {
        val lineCodeTokens = parseAlertTokens(alert.lineCode)
        val lineNameTokens = parseAlertTokens(alert.lineName)
        val shouldUseObjectList = alert.objectType.contains("ligne", ignoreCase = true) ||
                alert.objectType.contains("line", ignoreCase = true) ||
                alert.objectType.contains("route", ignoreCase = true) ||
                (lineCodeTokens.isEmpty() && lineNameTokens.isEmpty())

        return buildSet {
            addAll(lineCodeTokens)
            addAll(lineNameTokens)
            if (shouldUseObjectList) {
                addAll(parseAlertTokens(alert.objectList))
            }
            addAll(parseLineMentionsFromText(alert.title))
            addAll(parseLineMentionsFromText(alert.message))
        }
    }

    fun selectLine(lineName: String) {
        _selectedLineName.value = lineName
    }

    fun clearSelectedLine() {
        _selectedLineName.value = null
    }

    fun addLineToLoaded(lineName: String) {
        val requestedLine = lineName.trim()
        if (requestedLine.isEmpty()) return

        viewModelScope.launch {
            val currentLines = when (val state = _uiState.value) {
                is TransportLinesUiState.Success -> state.lines
                is TransportLinesUiState.PartialSuccess -> state.lines
                else -> emptyList()
            }

            if (currentLines.any { areEquivalentRouteNames(it.properties.lineName, requestedLine) }) {
                return@launch
            }

            transportRepository.getLineByName(requestedLine)
                .onSuccess { loadedFeatures ->
                    if (loadedFeatures.isEmpty()) {
                        Log.w("TransportViewModel", "No geometry found for line $requestedLine")
                        return@onSuccess
                    }

                    val merged = (currentLines + loadedFeatures)
                        .groupBy { it.properties.traceCode }
                        .map { (_, features) -> features.first() }

                    _uiState.value = TransportLinesUiState.Success(merged)
                    invalidateAvailableLinesCache()
                }
                .onFailure { error ->
                    Log.w(
                        "TransportViewModel",
                        "Failed to load line geometry for $requestedLine: ${error.message}"
                    )
                }
        }
    }

    fun removeLineFromLoaded(lineName: String) {
        val requestedLine = lineName.trim()
        if (requestedLine.isEmpty()) return

        val currentLines = when (val state = _uiState.value) {
            is TransportLinesUiState.Success -> state.lines
            is TransportLinesUiState.PartialSuccess -> state.lines
            else -> return
        }

        val filtered = currentLines.filterNot {
            areEquivalentRouteNames(it.properties.lineName, requestedLine)
        }

        if (filtered.size != currentLines.size) {
            _uiState.value = TransportLinesUiState.Success(filtered)
            invalidateAvailableLinesCache()
        }
    }

    fun loadAllLines() {
        loadTransportLines()
    }

    fun preloadStops() {
        loadStops()
    }

    fun reloadStrongLines() {
        loadTransportLines()
    }

    fun startLiveTracking(lineName: String) {
        if (_isGlobalLiveEnabled.value) {
            stopGlobalLive()
        }
        vehiclePositionsJob?.cancel()
        _isLiveTrackingEnabled.value = true
        _vehiclePositions.value = emptyList()

        vehiclePositionsJob = viewModelScope.launch {
            vehiclePositionsRepository.streamAllVehiclePositions().collect { result ->
                result.onSuccess { allPositions ->
                    val requested = lineName.trim()
                    val requestedNormalized = lineRules.normalizeForComparison(requested)

                    _vehiclePositions.value = allPositions.filter {
                        lineRules.normalizeForComparison(it.lineName) == requestedNormalized
                    }
                }.onFailure {
                    Log.w("TransportViewModel", "Vehicle live stream error: ${it.message}")
                }
            }
        }
    }

    fun stopLiveTracking() {
        vehiclePositionsJob?.cancel()
        vehiclePositionsJob = null
        _isLiveTrackingEnabled.value = false
        _vehiclePositions.value = emptyList()
    }

    fun stopGlobalLive() {
        globalLiveJob?.cancel()
        globalLiveJob = null
        _isGlobalLiveEnabled.value = false
        _globalVehiclePositions.value = emptyList()
    }

    fun toggleGlobalLive() {
        if (_isGlobalLiveEnabled.value) {
            stopGlobalLive()
            return
        }

        if (_isLiveTrackingEnabled.value) {
            stopLiveTracking()
        }
        _isGlobalLiveEnabled.value = true
        _globalVehiclePositions.value = emptyList()

        globalLiveJob = viewModelScope.launch {
            vehiclePositionsRepository.streamAllVehiclePositions().collect { result ->
                result.onSuccess { allPositions ->
                    _globalVehiclePositions.value = allPositions
                }.onFailure {
                    Log.w("TransportViewModel", "Global live stream error: ${it.message}")
                }
            }
        }
    }

    fun hasAllIcons(iconNames: Collection<String>): Boolean {
        return false
    }

    fun getIconBitmap(iconName: String): android.graphics.Bitmap? {
        return null
    }

    fun cacheIconBitmap(iconName: String, bitmap: android.graphics.Bitmap) {
        // Implementation here
    }

    fun clearScheduleState() {
        _allSchedules.value = emptyList()
        _nextSchedules.value = emptyList()
    }

    fun getStopsFeaturesForLine(lineName: String): List<StopFeature> {
        val state = stopsUiState.value
        if (state is TransportStopsUiState.Success) {
            return state.stops.filter { stop ->
                parseLineCodesFromDesserte(stop.properties.desserte)
                    .any { areEquivalentRouteNames(it, lineName) }
            }
        }
        return emptyList()
    }

    fun isStopsByLineIndexReady(): Boolean {
        return stopsUiState.value is TransportStopsUiState.Success
    }

    fun startOfflineDownload() {
        viewModelScope.launch {
            offlineDataManager.downloadAllOfflineData()
        }
    }

    fun cancelOfflineDownload() {
        offlineDataManager.cancelDownload()
    }

    fun reloadStopsCache() {
        loadStops()
    }

    fun resetLineDetailState() {
        _headsigns.value = emptyMap()
        _allSchedules.value = emptyList()
        _nextSchedules.value = emptyList()
        _availableDirections.value = emptyList()
    }
    
    /**
     * Charge les alertes trafic
     */
    fun loadTrafficAlerts() {
        viewModelScope.launch {
            _alertsState.value = TrafficAlertsState.Loading
            try {
                val result = trafficAlertsRepository.getTrafficAlerts()
                result.onSuccess { alerts ->
                    _alertsState.value = TrafficAlertsState.Success(alerts)
                    _trafficAlerts.value = alerts
                    _alertsTimestampMillis.value = System.currentTimeMillis()
                }.onFailure { error ->
                    _alertsState.value = TrafficAlertsState.Error(error.message ?: "Unknown error")
                }
            } catch (e: Exception) {
                _alertsState.value = TrafficAlertsState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun normalizeLineToken(raw: String): String {
        return lineRules.normalizeAlertToken(raw)
    }

    private fun canonicalRouteName(raw: String): String {
        return lineRules.canonicalRouteName(raw)
    }

    private fun invalidateAvailableLinesCache() {
        cachedAvailableLines = emptyList()
        cachedAvailableLinesUiState = null
        cachedAvailableLinesStopsState = null
    }

    private fun resolveScheduleRouteName(raw: String): String {
        val candidates = lineRules.equivalentRouteNames(raw)

        val routeNames = schedulesRepository.getAllRouteNames()
        val routeNamesUpper = routeNames.map { it.uppercase() }.toSet()
        val matched = candidates.firstOrNull { it in routeNamesUpper }
        return matched ?: canonicalRouteName(raw)
    }

    private fun areEquivalentRouteNames(first: String, second: String): Boolean {
        return canonicalRouteName(first) == canonicalRouteName(second)
    }

    private fun isLikelyLineToken(token: String): Boolean {
        return lineRules.isLikelyLineToken(token)
    }

    private fun parseLineMentionsFromText(raw: String): Set<String> {
        if (raw.isBlank()) return emptySet()

        val matchedSegments = Regex("(?i)\\blignes?\\b([^.!?\\n\\r]*)")
            .findAll(raw)
            .map { match -> match.groupValues.getOrNull(1).orEmpty() }
            .toList()

        return matchedSegments
            .flatMap { segment -> parseAlertTokens(segment) }
            .toSet()
    }

    private fun parseAlertTokens(raw: String): Set<String> {
        return raw
            .split(',', ';', '|', ' ', ':', '/', '-', '\n', '\t')
            .map { normalizeLineToken(it) }
            .filter { isLikelyLineToken(it) }
            .toSet()
    }

    private fun alertAffectsLine(alert: TrafficAlert, lineName: String): Boolean {
        val target = normalizeLineToken(lineName)
        if (target.isEmpty() || !isLikelyLineToken(target)) return false

        // Check if the line is directly referenced in the alert's primary fields
        val lineCodeTokens = parseAlertTokens(alert.lineCode)
        val lineNameTokens = parseAlertTokens(alert.lineName)
        
        // Only use objectList if the alert type specifically indicates it's about lines/routes
        val shouldUseObjectList = alert.objectType.contains("ligne", ignoreCase = true) ||
                alert.objectType.contains("line", ignoreCase = true) ||
                alert.objectType.contains("route", ignoreCase = true)

        val primaryTokens = buildSet {
            addAll(lineCodeTokens)
            addAll(lineNameTokens)
            if (shouldUseObjectList) {
                addAll(parseAlertTokens(alert.objectList))
            }
        }

        // If we found the line in primary fields, it definitely affects this line
        if (target in primaryTokens) {
            return true
        }

        // Only check text fields if no primary fields matched
        // This prevents false positives from mentions in message text
        if (lineCodeTokens.isEmpty() && lineNameTokens.isEmpty() && !shouldUseObjectList) {
            val textTokens = buildSet {
                addAll(parseLineMentionsFromText(alert.title))
                addAll(parseLineMentionsFromText(alert.message))
            }
            return target in textTokens
        }

        return false
    }

    private fun parseTimeToMinutes(rawTime: String): Int? {
        val clean = if (rawTime.count { it == ':' } >= 2) rawTime.substringBeforeLast(":") else rawTime
        val parts = clean.split(":")
        if (parts.size < 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        if (minute !in 0..59) return null
        return (hour * 60) + minute
    }

    private fun pickNextDeparture(schedules: List<String>, currentMinutes: Int): String? {
        val unique = schedules.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (unique.isEmpty()) return null
        return unique.firstOrNull { time ->
            val minutes = parseTimeToMinutes(time) ?: return@firstOrNull false
            minutes >= currentMinutes
        } ?: unique.first()
    }

    fun parseLineCodesFromDesserte(desserte: String): List<String> {
        return desserte
            .split(",")
            .mapNotNull { token ->
                val line = token.trim().substringBefore(":").trim()
                line.takeIf { it.isNotEmpty() }
            }
    }

    private fun normalizeStopName(stopName: String): String {
        return stopName
            .trim()
            .replace(Regex("\\s+"), " ")
            .uppercase()
    }

    override fun onCleared() {
        vehiclePositionsJob?.cancel()
        globalLiveJob?.cancel()
        super.onCleared()
    }

    data class StopDeparturePreview(
        val lineName: String,
        val directionId: Int,
        val directionName: String,
        val nextDeparture: String
    )

    fun generateBezierCurve(start: List<Double>, end: List<Double>): List<List<Double>> {
        val points = mutableListOf<List<Double>>()
        points.add(start)
        
        val midX = (start[0] + end[0]) / 2
        val midY = (start[1] + end[1]) / 2
        
        val dx = end[0] - start[0]
        val dy = end[1] - start[1]
        
        val length = kotlin.math.sqrt(dx * dx + dy * dy)
        val perpx = -dy / length * length * 0.1
        val perpy = dx / length * length * 0.1
        
        val controlX = midX + perpx
        val controlY = midY + perpy
        
        val segments = 20
        for (i in 1..segments) {
            val t = i.toDouble() / segments
            val x = (1-t)*(1-t)*start[0] + 2*(1-t)*t*controlX + t*t*end[0]
            val y = (1-t)*(1-t)*start[1] + 2*(1-t)*t*controlY + t*t*end[1]
            points.add(listOf(x, y))
        }
        
        points.add(end)
        return points
    }

    /**
     * Find a stop by coordinates when ID-based matching fails.
     * This handles cases where Raptor and WFS use different GTFS datasets with different IDs.
     * Uses spatial proximity to find the closest stop within a reasonable distance threshold.
     */
    private fun findStopByCoordinates(
        stops: List<StopFeature>,
        targetLat: Double,
        targetLon: Double,
        stopId: String
    ): StopFeature? {
        val thresholdDistance = 0.0002 // ~20 meters in degrees
        var closestStop: StopFeature? = null
        var minDistance = Double.MAX_VALUE
        
        for (stop in stops) {
            val stopCoord = stop.geometry.coordinates
            if (stopCoord.size < 2) continue
            
            val stopLon = stopCoord[0]
            val stopLat = stopCoord[1]
            
            // Calculate squared distance to avoid sqrt computation
            val latDiff = targetLat - stopLat
            val lonDiff = targetLon - stopLon
            val distanceSq = latDiff * latDiff + lonDiff * lonDiff
            
            if (distanceSq < minDistance) {
                minDistance = distanceSq
                closestStop = stop
            }
        }
        
        if (closestStop != null && minDistance < thresholdDistance * thresholdDistance) {
            Log.d("TransportViewModel", "Found stop by coordinates for $stopId: ${closestStop.properties.nom} (distance: ${kotlin.math.sqrt(minDistance)} degrees)")
            return closestStop
        } else {
            Log.w("TransportViewModel", "No close stop found by coordinates for $stopId (min distance: ${if (closestStop == null) "none" else kotlin.math.sqrt(minDistance)} degrees)")
            return null
        }
    }

    internal fun sectionLinesBetweenStops(
        lines: List<com.pelotcl.app.generic.data.model.Feature>,
        startStopId: String,
        endStopId: String,
        leg: com.pelotcl.app.generic.data.repository.itinerary.JourneyLeg
    ): List<com.pelotcl.app.generic.data.model.Feature> {
        val sectionedLines = mutableListOf<com.pelotcl.app.generic.data.model.Feature>()

        val stopsState = stopsUiState.value
        if (stopsState !is TransportStopsUiState.Success) {
            Log.e("TransportViewModel", "Stops not loaded yet")
            return sectionedLines
        }
        
        val stops = stopsState.stops

        Log.d("TransportViewModel", "Looking for stops: startStopId=$startStopId, endStopId=$endStopId, total stops=${stops.size}")
        
        // JourneyLeg uses GTFS stop IDs (numeric strings), but StopFeature.id uses GID format
        // We need to compare with StopProperties.id which contains the GTFS ID
        val startStop = stops.find { it.properties.id.toString() == startStopId }
        val endStop = stops.find { it.properties.id.toString() == endStopId }
        
        // If ID-based matching fails, try coordinate-based matching as fallback
        // This can happen when Raptor and WFS use different GTFS datasets with different IDs
        val finalStartStop = startStop ?: findStopByCoordinates(stops, leg.fromLat, leg.fromLon, startStopId)
        val finalEndStop = endStop ?: findStopByCoordinates(stops, leg.toLat, leg.toLon, endStopId)
        
        // Log which matching method was used
        val startMatchMethod = if (startStop != null) "ID" else if (finalStartStop != null) "coordinates" else "failed"
        val endMatchMethod = if (endStop != null) "ID" else if (finalEndStop != null) "coordinates" else "failed"
        Log.d("TransportViewModel", "Stop matching: start=$startMatchMethod, end=$endMatchMethod")
        
        if (finalStartStop == null || finalEndStop == null) {
            Log.e("TransportViewModel", "Stops not found: startStopId=$startStopId, endStopId=$endStopId")
            // Debug: list some stop GTFS IDs to see what format they have
            if (stops.isNotEmpty()) {
                val sampleStops = stops.take(5)
                Log.d("TransportViewModel", "Sample GTFS stop IDs: ${sampleStops.joinToString { "${it.properties.id} (${it.properties.nom})" }}")
                Log.d("TransportViewModel", "Sample GID stop IDs: ${sampleStops.joinToString { "${it.id} (${it.properties.nom})" }}")
            }
            return sectionedLines
        }
        
        Log.d("TransportViewModel", "Found stops: ${finalStartStop.properties.nom} (GTFS: ${finalStartStop.properties.id}, GID: ${finalStartStop.id}) -> ${finalEndStop.properties.nom} (GTFS: ${finalEndStop.properties.id}, GID: ${finalEndStop.id})")
        
        for (line in lines) {
            val lineGeometry = line.geometry
            if (lineGeometry is com.pelotcl.app.generic.data.model.Geometry) {
                val coordinates = lineGeometry.coordinates
                val firstLine = coordinates.firstOrNull() ?: continue
                
                val startCoord = listOf(finalStartStop.geometry.coordinates[0], finalStartStop.geometry.coordinates[1])
                val endCoord = listOf(finalEndStop.geometry.coordinates[0], finalEndStop.geometry.coordinates[1])
                
                fun findClosestPointIndex(targetCoord: List<Double>): Int {
                    var minDistance = Double.MAX_VALUE
                    var bestIndex = -1
                    
                    for (i in firstLine.indices) {
                        val coord = firstLine[i]
                        val distance = kotlin.math.sqrt(
                            (coord[0] - targetCoord[0]) * (coord[0] - targetCoord[0]) +
                                    (coord[1] - targetCoord[1]) * (coord[1] - targetCoord[1])
                        )
                        
                        if (distance < minDistance) {
                            minDistance = distance
                            bestIndex = i
                        }
                    }
                    
                    return bestIndex
                }
                
                var startIndex = findClosestPointIndex(startCoord)
                var endIndex = findClosestPointIndex(endCoord)
                
                if (startIndex != -1 && endIndex != -1) {
                    val initialLength = kotlin.math.abs(endIndex - startIndex)
                    
                    if (initialLength < 10) {
                        val extendBy = 5
                        
                        startIndex = if (startIndex > extendBy) {
                            startIndex - extendBy
                        } else {
                            0
                        }
                        
                        endIndex = if (endIndex < firstLine.size - extendBy - 1) {
                            endIndex + extendBy
                        } else {
                            firstLine.size - 1
                        }
                    }
                }
                
                if (startIndex != -1 && endIndex != -1) {
                    var sectionCoordinates: List<List<Double>> = if (startIndex < endIndex) {
                        firstLine.subList(startIndex, endIndex + 1)
                    } else {
                        firstLine.subList(endIndex, startIndex + 1).reversed()
                    }
                    
                    // Replace first and last points with exact stop positions
                    if (sectionCoordinates.isNotEmpty()) {
                        // Replace first point with exact start stop position
                        if (sectionCoordinates.size == 1) {
                            sectionCoordinates = listOf(startCoord, endCoord)
                        } else {
                            sectionCoordinates = sectionCoordinates.toMutableList().apply {
                                // Replace first point with exact start position
                                if (this.isNotEmpty()) {
                                    this[0] = startCoord
                                }
                                // Replace last point with exact end position
                                if (this.size > 1) {
                                    this[this.size - 1] = endCoord
                                }
                            }
                        }
                    }
                    
                    if (sectionCoordinates.size > 2) {
                        val sectionedLine = line.copy(
                            geometry = com.pelotcl.app.generic.data.model.Geometry(
                                type = line.geometry.type,
                                coordinates = listOf(sectionCoordinates)
                            )
                        )
                        sectionedLines.add(sectionedLine)
                    }
                }
            }
        }
        return sectionedLines
    }

}

/**
 * États pour les lignes de transport
 */
sealed class TransportLinesState {
    object Loading : TransportLinesState()
    data class Success(val lines: com.pelotcl.app.generic.data.model.FeatureCollection) : TransportLinesState()
    data class Error(val message: String) : TransportLinesState()
}

/**
 * États pour les alertes trafic
 */
sealed class TrafficAlertsState {
    object Loading : TrafficAlertsState()
    data class Success(val alerts: List<TrafficAlert>) : TrafficAlertsState()
    data class Error(val message: String) : TrafficAlertsState()
}

/**
 * États pour les lignes de transport (Compatibilité PlanScreen)
 */
sealed class TransportLinesUiState {
    object Loading : TransportLinesUiState()
    data class Success(val lines: List<com.pelotcl.app.generic.data.model.Feature>) : TransportLinesUiState()
    data class PartialSuccess(val lines: List<com.pelotcl.app.generic.data.model.Feature>) : TransportLinesUiState()
    data class Error(val message: String) : TransportLinesUiState()
}

/**
 * États pour les arrêts de transport (Compatibilité PlanScreen)
 */
sealed class TransportStopsUiState {
    object Loading : TransportStopsUiState()
    data class Success(val stops: List<StopFeature>) : TransportStopsUiState()
    data class Error(val message: String) : TransportStopsUiState()
}
