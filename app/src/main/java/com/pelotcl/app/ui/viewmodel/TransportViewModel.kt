package com.pelotcl.app.ui.viewmodel

import android.app.Application
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pelotcl.app.data.model.Feature
import com.pelotcl.app.data.model.StopFeature
import com.pelotcl.app.data.repository.TransportRepository
import com.pelotcl.app.ui.components.StationSearchResult
import com.pelotcl.app.utils.Connection
import com.pelotcl.app.data.repository.FavoritesRepository
import com.pelotcl.app.utils.ConnectionsHelper
import com.pelotcl.app.utils.HolidayDetector
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * UI state for transport lines
 */
sealed class TransportLinesUiState {
    object Loading : TransportLinesUiState()
    data class Success(val lines: List<Feature>) : TransportLinesUiState()
    data class Error(val message: String) : TransportLinesUiState()
}

/**
 * UI state for transport stops
 */
sealed class TransportStopsUiState {
    object Loading : TransportStopsUiState()
    data class Success(val stops: List<StopFeature>) : TransportStopsUiState()
    data class Error(val message: String) : TransportStopsUiState()
}

/**
 * ViewModel to manage transport line data and searches
 */
class TransportViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TransportRepository(application.applicationContext)

    private val _uiState = MutableStateFlow<TransportLinesUiState>(TransportLinesUiState.Loading)
    val uiState: StateFlow<TransportLinesUiState> = _uiState.asStateFlow()

    private val _stopsUiState = MutableStateFlow<TransportStopsUiState>(TransportStopsUiState.Loading)
    val stopsUiState: StateFlow<TransportStopsUiState> = _stopsUiState.asStateFlow()

    private val schedulesRepository = com.pelotcl.app.data.gtfs.SchedulesRepository(application.applicationContext)
    private val holidayDetector = HolidayDetector(application.applicationContext)
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

    // Preloading flags to avoid multiple reloads
    private var isPreloading: Boolean = false
    private var hasPreloaded: Boolean = false

    init {
        // Start non-blocking preload on creation to have lines, stops, and connection index ready
        preloadAllData()
        // Load favorites
        _favoriteLines.value = favoritesRepository.getFavorites().map { it.uppercase() }.toSet()
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

    fun loadHeadsigns(routeName: String) {
        viewModelScope.launch {
            // GTFS uses "NAVI1" for Navigone while the app displays "NAV1"
            val gtfsRouteName = if (routeName.equals("NAV1", ignoreCase = true)) "NAVI1" else routeName
            _headsigns.value = schedulesRepository.getHeadsigns(gtfsRouteName)
        }
    }

    /**
     * Calculates directions that actually have schedules for a given stop.
     * Uses IDs present in _headsigns when available.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun computeAvailableDirections(lineName: String, stopName: String) {
        viewModelScope.launch {
            // Determine if it is a school holiday
            val isTodayHoliday = holidayDetector.isSchoolHoliday(java.time.LocalDate.now())
            val gtfsLineName = if (lineName.equals("NAV1", ignoreCase = true)) "NAVI1" else lineName

            // Candidate directions list: those exposed by _headsigns otherwise 0 and 1 by default
            val candidateDirections = _headsigns.value.keys.ifEmpty { setOf(0, 1) }.toList().sorted()

            val available = mutableListOf<Int>()
            for (dir in candidateDirections) {
                try {
                    val schedules = schedulesRepository.getSchedules(gtfsLineName, stopName, dir, isTodayHoliday)
                    if (schedules.isNotEmpty()) {
                        available.add(dir)
                    }
                } catch (t: Throwable) {
                    // Ignore error for this dir, consider it unavailable
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
        viewModelScope.launch {
            _allSchedules.value = emptyList()
            _nextSchedules.value = emptyList()

            android.util.Log.d("NavigoneDebug", "loadSchedulesForDirection called with lineName: '$lineName'")

            // Determine if today is a school holiday
            val isTodayHoliday = holidayDetector.isSchoolHoliday(LocalDate.now())
            android.util.Log.d("NavigoneDebug", "Is today a school holiday? $isTodayHoliday")

            // The GTFS data uses NAVI1 for the Navigone, but the app displays NAV1
            val gtfsLineName = if (lineName.equals("NAV1", ignoreCase = true)) "NAVI1" else lineName

            android.util.Log.d("NavigoneDebug", "Translated lineName to gtfsLineName: '$gtfsLineName'")

            val allSchedulesForDay = schedulesRepository.getSchedules(gtfsLineName, stopName, directionId, isTodayHoliday)
            _allSchedules.value = allSchedulesForDay

            if (allSchedulesForDay.isEmpty()) {
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
                android.util.Log.e("TransportViewModel", "Error filtering next schedules: ${e.message}")
            }
        }
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
                        val rxCount = collection.features.count { it.properties.ligne.equals("RX", ignoreCase = true) }
                        android.util.Log.d("TransportViewModel", "reloadStrongLines: loaded ${collection.features.size} features; RX count=$rxCount")
                        _uiState.value = TransportLinesUiState.Success(collection.features)
                    }
                    .onFailure { e ->
                        android.util.Log.w("TransportViewModel", "reloadStrongLines failed: ${e.message}")
                    }
            } catch (t: Throwable) {
                android.util.Log.e("TransportViewModel", "reloadStrongLines: exception ${t.message}")
            }
        }
    }

    // Stops cache to avoid reloading them each time
    private var cachedStops: List<StopFeature>? = null
    private var stopsLoadingJob: kotlinx.coroutines.Job? = null

    // Pre-calculated transfers index by stop name
    // Key = normalized stop name, Value = list of transfer lines
    private var connectionsIndex: Map<String, List<com.pelotcl.app.utils.Connection>> = emptyMap()

    /**
     * Preloads lines and stops at launch, builds transfers index,
     * and populates StateFlows if possible. Does not block UI.
     */
    private fun preloadAllData() {
        if (hasPreloaded || isPreloading) return
        isPreloading = true

        viewModelScope.launch {
            try {
                // 1) Load lines if not already loaded
                if (_uiState.value !is TransportLinesUiState.Success) {
                    repository.getAllLines()
                        .onSuccess { featureCollection ->
                            _uiState.value = TransportLinesUiState.Success(featureCollection.features)
                        }
                        .onFailure { e ->
                            android.util.Log.w("TransportViewModel", "Preload: failed loading lines: ${e.message}")
                        }
                }

                // 2) Load all stops, cache them and build transfers index
                if (cachedStops == null || connectionsIndex.isEmpty()) {
                    repository.getAllStops()
                        .onSuccess { stopCollection ->
                            cachedStops = stopCollection.features
                            buildConnectionsIndex(stopCollection.features)
                            // Publish stops state only if not already success
                            if (_stopsUiState.value !is TransportStopsUiState.Success) {
                                _stopsUiState.value = TransportStopsUiState.Success(stopCollection.features)
                            }
                        }
                        .onFailure { e ->
                            android.util.Log.w("TransportViewModel", "Preload: failed loading stops: ${e.message}")
                        }
                }
            } catch (t: Throwable) {
                android.util.Log.e("TransportViewModel", "Preload: unexpected exception: ${t.message}")
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
     * Retrieves transfers for a given stop from the pre-calculated index
     * Ultra-fast method (O(1))
     */
    fun getConnectionsForStop(stopName: String, currentLine: String): List<com.pelotcl.app.utils.Connection> {
        val normalized = normalizeStopName(stopName)
        val connections = connectionsIndex[normalized] ?: emptyList()
        // Exclude the current line (with normalization to handle NAVI1 vs NAV1)
        val normalizedCurrentLine = normalizeLineName(currentLine)
        return connections.filter { normalizeLineName(it.lineName) != normalizedCurrentLine }
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

                    // Build transfers index
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
                buildConnectionsIndex(stopCollection.features)
                android.util.Log.d("TransportViewModel", "Stops cache reloaded: ${stopCollection.features.size} stops")
            }
            .onFailure { exception ->
                android.util.Log.e("TransportViewModel", "Error reloading stops cache: ${exception.message}")
            }
    }

    /**
     * Builds a transfers index for each stop
     * Allows O(1) access instead of scanning all stops each time
     */
    private fun buildConnectionsIndex(allStops: List<StopFeature>) {
        // Step 1: Group stops by approximate name to find a "canonical" name
        val stopGroups = mutableMapOf<String, MutableList<StopFeature>>()
        val canonicalNames = mutableMapOf<String, String>()

        for (stop in allStops) {
            val normalizedName = normalizeStopName(stop.properties.nom)
            var foundGroup = false
            for (key in stopGroups.keys) {
                if (key.startsWith(normalizedName) || normalizedName.startsWith(key)) {
                    stopGroups[key]?.add(stop)
                    canonicalNames[normalizedName] = key
                    foundGroup = true
                    break
                }
            }
            if (!foundGroup) {
                stopGroups[normalizedName] = mutableListOf(stop)
                canonicalNames[normalizedName] = normalizedName
            }
        }

        // Step 2: Build index using groups
        val index = mutableMapOf<String, List<Connection>>()
        for ((canonicalName, stopsInGroup) in stopGroups) {
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
    }

    /**
     * Loads all transport lines
     */
    fun loadAllLines() {
        viewModelScope.launch {
            _uiState.value = TransportLinesUiState.Loading
            repository.getAllLines()
                .onSuccess { featureCollection ->
                    _uiState.value = TransportLinesUiState.Success(featureCollection.features)
                }
                .onFailure { exception ->
                    _uiState.value = TransportLinesUiState.Error(
                        exception.message ?: "An error occurred"
                    )
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
        }
    }

    fun isFavorite(lineName: String): Boolean {
        return _favoriteLines.value.contains(lineName.uppercase())
    }

    fun selectLine(lineName: String, currentStationName: String = "") {
        _selectedLineName.value = lineName
    }

    fun clearSelectedLine() {
        _selectedLineName.value = null
    }

    /**
     * Loads a specific line by its name
     */
    fun loadLineByName(lineName: String) {
        viewModelScope.launch {
            _uiState.value = TransportLinesUiState.Loading
            repository.getLineByName(lineName)
                .onSuccess { feature ->
                    _uiState.value = if (feature != null) {
                        TransportLinesUiState.Success(listOf(feature))
                    } else {
                        TransportLinesUiState.Error("Line $lineName not found")
                    }
                }
                .onFailure { exception ->
                    _uiState.value = TransportLinesUiState.Error(
                        exception.message ?: "An error occurred"
                    )
                }
        }
    }

    /**
     * Adds a specific line to already loaded lines (for on-demand bus lines)
     * Does not modify state if the line is already present
     */
    fun addLineToLoaded(lineName: String) {
        android.util.Log.d("TransportViewModel", "addLineToLoaded called for: $lineName")

        viewModelScope.launch {
            val currentState = _uiState.value

            // Do nothing if not in Success state
            if (currentState !is TransportLinesUiState.Success) {
                android.util.Log.w("TransportViewModel", "Current state is not Success, cannot add line")
                return@launch
            }

            // Check if line is already loaded
            val isAlreadyLoaded = currentState.lines.any {
                lineName.equals(it.properties.ligne, ignoreCase = true)
            }

            if (isAlreadyLoaded) {
                android.util.Log.d("TransportViewModel", "Line $lineName is already loaded, skipping")
                return@launch // Line already present, do nothing
            }

            android.util.Log.d("TransportViewModel", "Loading line $lineName from API...")

            // Load the line from the API
            repository.getLineByName(lineName)
                .onSuccess { feature ->
                    if (feature != null) {
                        android.util.Log.d("TransportViewModel", "Successfully loaded line $lineName, adding to map")
                        // Add the new line to existing lines
                        val updatedLines = currentState.lines + feature
                        _uiState.value = TransportLinesUiState.Success(updatedLines)
                    } else {
                        android.util.Log.w("TransportViewModel", "getLineByName returned null for $lineName")
                    }
                }
                .onFailure { exception ->
                    // In case of error, don't change state (keep current lines)
                    android.util.Log.e("TransportViewModel", "Error loading line $lineName: ${exception.message}")
                }
        }
    }

    /**
     * Removes a specific line from loaded lines (to clean up temporary bus lines)
     */
    fun removeLineFromLoaded(lineName: String) {
        android.util.Log.d("TransportViewModel", "removeLineFromLoaded called for: $lineName")

        val currentState = _uiState.value

        // Do nothing if not in Success state
        if (currentState !is TransportLinesUiState.Success) {
            android.util.Log.w("TransportViewModel", "Current state is not Success, cannot remove line")
            return
        }

        val beforeCount = currentState.lines.size

        // Filter to remove the line
        val updatedLines = currentState.lines.filter {
            !lineName.equals(it.properties.ligne, ignoreCase = true)
        }

        val afterCount = updatedLines.size
        android.util.Log.d("TransportViewModel", "Removed line $lineName. Before: $beforeCount lines, After: $afterCount lines")

        _uiState.value = TransportLinesUiState.Success(updatedLines)
    }

    /**
     * Retrieves stops served by a specific line, ordered according to the line's path
     * @param lineName Line name (ex: "86", "A", "T1")
     * @param currentStopName Current stop name to mark it (optional)
     * @return List of stops with their transfers, ordered according to the line's route
     */
    fun getStopsForLine(lineName: String, currentStopName: String? = null): List<com.pelotcl.app.data.gtfs.LineStopInfo> {
        // First, try to retrieve from cache (for metros and trams)
        val cachedStops = com.pelotcl.app.data.gtfs.LineStopsCache.getLineStops(lineName, currentStopName)
        if (cachedStops != null) {
            android.util.Log.d("TransportViewModel", "Found $lineName in cache with ${cachedStops.size} stops")
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
            return dedup.mapIndexed { index, stop ->
                stop.copy(
                    stopSequence = index + 1,
                    isCurrentStop = currentStopName?.let {
                        normalizeStopName(stop.stopName) == normalizeStopName(it)
                    } ?: stop.isCurrentStop
                )
            }
        }

        // Get all stops from cache
        val allStops = getCachedStopsSync()

        android.util.Log.d("TransportViewModel", "getStopsForLine: line=$lineName, total stops=${allStops.size}")

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

        android.util.Log.d("TransportViewModel", "Found ${lineStops.size} stops for line $lineName (with duplicates)")

        // Get all line traces to order stops
        val currentState = _uiState.value
        if (currentState is TransportLinesUiState.Success) {
            // Get ALL traces of this line (may have multiple directions)
            val lineFeatures = currentState.lines.filter {
                lineName.equals(it.properties.ligne, ignoreCase = true)
            }

            android.util.Log.d("TransportViewModel", "Found ${lineFeatures.size} traces for line $lineName")

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

                    android.util.Log.d("TransportViewModel", "Main trace has ${mainTrace.geometry.coordinates.size} segments")
                    android.util.Log.d("TransportViewModel", "Longest segment (${mainTrace.properties.sens}) has ${longestSegment.size} points")

                    // Determine main trace direction and convert it to letter (A or R)
                    // Be defensive: API may return null sens unexpectedly, avoid NPEs
                    val sensUpper = try {
                        mainTrace.properties.sens.uppercase()
                    } catch (e: Exception) {
                        android.util.Log.w("TransportViewModel", "properties.sens is null or invalid for line $lineName (codeTrace=${mainTrace.properties.codeTrace}) — falling back to unknown direction")
                        ""
                    }
                    val mainDirection = when (sensUpper) {
                        "ALLER" -> "A"
                        "RETOUR" -> "R"
                        else -> sensUpper.take(1) // First character already uppercased or empty
                    }

                    android.util.Log.d("TransportViewModel", "Filtering stops for direction code: $mainDirection")

                    // Display a few desserte examples for debug
                    lineStops.take(5).forEach { stop ->
                        android.util.Log.d("TransportViewModel", "  Stop '${stop.properties.nom}': desserte='${stop.properties.desserte}'")
                    }

                    // Filter stops to keep only those that match the main direction
                    val directionStops = lineStops.filter { stop ->
                        // Be defensive: desserte can be null/blank in some rare cases
                        val desserte = try { stop.properties.desserte } catch (e: Exception) { "" }
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
                            if (result) {
                                android.util.Log.d("TransportViewModel", "  ✓ Stop '${stop.properties.nom}' matches (found '$trimmed')")
                            }
                            result
                        }
                        matches
                    }

                    android.util.Log.d("TransportViewModel", "Found ${directionStops.size} stops for direction $mainDirection")

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

                    android.util.Log.d("TransportViewModel", "Ordered stops by trace position: ${orderedStops.map { it.properties.nom }}")

                    // Deduplicate by normalized name (some lines have double platforms)
                    val dedupOrdered = orderedStops.distinctBy { normalizeStopName(it.properties.nom) }

                    // Convert to LineStopInfo
                    return dedupOrdered.mapIndexed { index, stop ->
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
                }
            }
        }

        // Fallback: if no trace found, at least remove duplicates
        val uniqueStops = lineStops.distinctBy { stop ->
            normalizeStopName(stop.properties.nom)
        }

        android.util.Log.d("TransportViewModel", "Fallback: returning ${uniqueStops.size} unique stops without ordering")

        return uniqueStops.mapIndexed { index, stop ->
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
    }

    /**
     * Loads all transport stops
     */
    fun loadAllStops() {
        viewModelScope.launch {
            _stopsUiState.value = TransportStopsUiState.Loading
            repository.getAllStops()
                .onSuccess { stopCollection ->
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
     * Retrieves the list of all available lines (names only)
     * by extracting lines from all loaded stops
     */
    fun getAllAvailableLines(): List<String> {
        // First, try to extract from loaded lines
        val linesFromFeatures = when (val currentState = _uiState.value) {
            is TransportLinesUiState.Success -> {
                currentState.lines
                    .map { it.properties.ligne }
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

        // Combine and sort
        return (linesFromFeatures + linesFromStops)
            .distinct()
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
}