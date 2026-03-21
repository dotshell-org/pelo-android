package com.pelotcl.app.core.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pelotcl.app.core.data.model.Favorite
import com.pelotcl.app.core.data.model.Feature
import com.pelotcl.app.core.data.model.SimpleVehiclePosition
import com.pelotcl.app.core.data.model.StopFeature
import com.pelotcl.app.core.data.network.TransportApi
import com.pelotcl.app.core.data.network.TransportConfig
import com.pelotcl.app.core.service.TransportServiceProvider
import com.pelotcl.app.data.repository.TransportRepository
import com.pelotcl.app.data.repository.online.TrafficAlertsRepository
import com.pelotcl.app.data.repository.online.VehiclePositionsRepository
import com.pelotcl.app.data.repository.offline.FavoritesRepository
import com.pelotcl.app.data.repository.offline.SchedulesRepository
import com.pelotcl.app.core.data.offline.OfflineDataManager
import com.pelotcl.app.core.data.offline.OfflineDataInfo
import com.pelotcl.app.core.data.model.LineStopInfo
import com.pelotcl.app.core.data.model.TrafficAlert
import com.pelotcl.app.core.data.model.AlertSeverity
import com.pelotcl.app.data.repository.itinerary.RaptorRepository
import com.pelotcl.app.core.ui.components.LineSearchResult
import com.pelotcl.app.core.ui.components.StationSearchResult
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

/**
 * ViewModel principal pour la gestion des données de transport
 * Utilise TransportServiceProvider pour accéder aux services
 */
class TransportViewModel(private val context: Context) : ViewModel() {

    private val transportConfig: TransportConfig = TransportServiceProvider.getTransportConfig()
    private val transportApi: TransportApi = TransportServiceProvider.getTransportApi()
    private val transportRepository: TransportRepository
    private val trafficAlertsRepository = TrafficAlertsRepository(transportApi, context)
    private val vehiclePositionsRepository = VehiclePositionsRepository()
    private val schedulesRepository = SchedulesRepository.getInstance(context)
    private val holidayDetector by lazy { HolidayDetector(context.applicationContext) }
    private var vehiclePositionsJob: Job? = null
    private var globalLiveJob: Job? = null

    init {
        transportRepository = TransportRepository(context)
    }
    private val favoritesRepository = FavoritesRepository(context)
    val raptorRepository = RaptorRepository.getInstance(context)
    val offlineDataManager = OfflineDataManager(transportApi, context)
    private val _linesState = MutableStateFlow<TransportLinesState>(TransportLinesState.Loading)
    val linesState: StateFlow<TransportLinesState> = _linesState.asStateFlow()

    // Compatibilité avec PlanScreen.kt qui utilise TransportLinesUiState
    private val _uiState = MutableStateFlow<TransportLinesUiState>(TransportLinesUiState.Loading)
    val uiState: StateFlow<TransportLinesUiState> = _uiState.asStateFlow()
    
    // État pour les alertes trafic
    private val _alertsState = MutableStateFlow<TrafficAlertsState>(TrafficAlertsState.Loading)
    val alertsState: StateFlow<TrafficAlertsState> = _alertsState.asStateFlow()

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
    
    init {
        loadTransportLines()
        loadTrafficAlerts()
        loadStops()
        loadFavorites()
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
                    _uiState.value = TransportLinesUiState.Success(lines.features)
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
            val offlineRepository = com.pelotcl.app.core.data.offline.OfflineRepository(context)

            val offlineStops = runCatching {
                withContext(Dispatchers.IO) { offlineRepository.loadStops() }
            }.getOrNull().orEmpty()

            if (offlineStops.isNotEmpty()) {
                _stopsUiState.value = TransportStopsUiState.Success(offlineStops)
                return@launch
            }

            val onlineStops = runCatching {
                withContext(Dispatchers.IO) { transportApi.getTransportStops().features }
            }.getOrElse { error ->
                _stopsUiState.value =
                    TransportStopsUiState.Error(error.message ?: "Unable to load transport stops")
                return@launch
            }

            if (onlineStops.isEmpty()) {
                _stopsUiState.value =
                    TransportStopsUiState.Error("No transport stops available")
                return@launch
            }

            _stopsUiState.value = TransportStopsUiState.Success(onlineStops)

            // Warm offline storage for next launches. Failure here should not break UI.
            runCatching {
                withContext(Dispatchers.IO) { offlineRepository.saveStops(onlineStops) }
            }.onFailure { e ->
                Log.w("TransportViewModel", "Failed to cache stops offline: ${e.message}")
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
            iconColor = Favorite.DEFAULT_COLORS.first(),
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
            val lines = schedulesRepository.getDesserteForStop(stopName).orEmpty()
                .split(",")
                .mapNotNull { token ->
                    val line = token.trim().substringBefore(":").trim()
                    line.takeIf { it.isNotEmpty() && !it.equals(lineName, ignoreCase = true) }
                }
                .distinctBy { it.uppercase() }
            emit(lines.map { LineSearchResult(it) })
        }
    }

    suspend fun getNextDeparturesForStop(
        stopName: String,
        lines: List<String>
    ): List<StopDeparturePreview> = withContext(Dispatchers.Default) {
        if (stopName.isBlank() || lines.isEmpty()) return@withContext emptyList()

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
                compareBy<StopDeparturePreview>(
                    { parseTimeToMinutes(it.nextDeparture) ?: Int.MAX_VALUE },
                    { it.lineName },
                    { it.directionId }
                )
            )
            .toList()
    }

    suspend fun searchStops(query: String): List<StationSearchResult> =
        schedulesRepository.searchStopsByName(query)

    fun searchLines(query: String): List<LineSearchResult> {
        return schedulesRepository.searchLinesByName(query)
    }

    fun getAlertsForLine(lineName: String): List<TrafficAlert> {
        return trafficAlerts.value
            .asSequence()
            .filter { alertAffectsLine(it, lineName) }
            .distinctBy { it.alertNumber }
            .sortedBy { it.severityLevel }
            .toList()
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
            is TransportLinesUiState.Success -> currentUiState.lines.map { it.properties.ligne }
            is TransportLinesUiState.PartialSuccess -> currentUiState.lines.map { it.properties.ligne }
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

        val stopsByNormalizedName = filteredStops
            .groupBy { normalizeStopName(it.properties.nom) }

        val usedStopNames = mutableSetOf<String>()
        val orderedStops = stopSequences.mapNotNull { (stopNameFromGtfs, sequence) ->
            val normalizedName = normalizeStopName(stopNameFromGtfs)
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
                .filter { normalizeStopName(it.properties.nom) !in usedStopNames }
                .sortedBy { it.properties.nom.uppercase() }
                .mapIndexed { index, stop ->
                    val normalizedName = normalizeStopName(stop.properties.nom)
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
            .distinctBy { normalizeStopName(it.properties.nom) }
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

    fun getAlertSeverityForLine(lineName: String): AlertSeverity? {
        val token = normalizeLineToken(lineName)
        if (token.isEmpty() || !isLikelyLineToken(token)) return null
        return getOrBuildAlertSeverityIndex()[token]
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
        // Implementation here
    }

    fun removeLineFromLoaded(lineName: String) {
        // Implementation here
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
                    _vehiclePositions.value = allPositions.filter {
                        it.lineName.equals(lineName, ignoreCase = true)
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
        val token = raw.uppercase()
            .replace(" ", "")
            .replace("-", "")
            .replace("_", "")
            .replace("LIGNES", "")
            .replace("LIGNE", "")
            .replace("TRAM", "")
            .replace("METRO", "")
            .trim()
        return when {
            token.contains("RHONEXPRESS") -> "RX"
            else -> token
        }
    }

    private fun canonicalRouteName(raw: String): String {
        val token = raw.trim().uppercase()
        return when (token) {
            "NAVI1" -> "NAV1"
            else -> token
        }
    }

    private fun resolveScheduleRouteName(raw: String): String {
        val normalized = raw.trim().uppercase()
        val candidates = when (normalized) {
            "NAVI1", "NAV1" -> listOf("NAVI1", "NAV1")
            else -> listOf(normalized)
        }

        val routeNames = schedulesRepository.getAllRouteNames()
        val routeNamesUpper = routeNames.map { it.uppercase() }.toSet()
        val matched = candidates.firstOrNull { it in routeNamesUpper }
        return matched ?: canonicalRouteName(raw)
    }

    private fun areEquivalentRouteNames(first: String, second: String): Boolean {
        return canonicalRouteName(first) == canonicalRouteName(second)
    }

    private fun isLikelyLineToken(token: String): Boolean {
        if (token.isBlank()) return false
        return token in setOf("A", "B", "C", "D", "F1", "F2", "RX") ||
                token.matches(Regex("^TB\\d{1,2}[A-Z]?$")) ||
                token.matches(Regex("^T\\d{1,2}[A-Z]?$")) ||
                token.matches(Regex("^C\\d{1,2}[A-Z]?$")) ||
                token.matches(Regex("^NAVI\\d{1,2}$")) ||
                token.matches(Regex("^JD\\d{1,3}$")) ||
                token.matches(Regex("^GE\\d{1,2}$")) ||
                token.matches(Regex("^PL\\d{1,2}$")) ||
                token.matches(Regex("^ZI\\d{1,2}$")) ||
                token.matches(Regex("^S\\d{1,2}$")) ||
                token.matches(Regex("^N\\d{1,2}$")) ||
                token.matches(Regex("^\\d{1,3}[A-Z]?$"))
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

        val lineCodeTokens = parseAlertTokens(alert.lineCode)
        val lineNameTokens = parseAlertTokens(alert.lineName)
        val shouldUseObjectList = alert.objectType.contains("ligne", ignoreCase = true) ||
                alert.objectType.contains("line", ignoreCase = true) ||
                alert.objectType.contains("route", ignoreCase = true) ||
                (lineCodeTokens.isEmpty() && lineNameTokens.isEmpty())

        val tokens = buildSet {
            addAll(lineCodeTokens)
            addAll(lineNameTokens)
            if (shouldUseObjectList) {
                addAll(parseAlertTokens(alert.objectList))
            }
            addAll(parseLineMentionsFromText(alert.title))
            addAll(parseLineMentionsFromText(alert.message))
        }

        return target in tokens
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

    private fun parseLineCodesFromDesserte(desserte: String): List<String> {
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

    
    /**
     * Obtient la configuration de transport
     */
    fun getTransportConfig(): TransportConfig = transportConfig
    
    /**
     * Obtient le repository de transport
     */
    fun getTransportRepository(): TransportRepository = transportRepository
    
    /**
     * Obtient le repository des alertes trafic
     */
    fun getTrafficAlertsRepository(): TrafficAlertsRepository = trafficAlertsRepository
}

/**
 * États pour les lignes de transport
 */
sealed class TransportLinesState {
    object Loading : TransportLinesState()
    data class Success(val lines: com.pelotcl.app.core.data.model.FeatureCollection) : TransportLinesState()
    data class Error(val message: String) : TransportLinesState()
}

/**
 * États pour les alertes trafic
 */
sealed class TrafficAlertsState {
    object Loading : TrafficAlertsState()
    data class Success(val alerts: List<com.pelotcl.app.core.data.model.TrafficAlert>) : TrafficAlertsState()
    data class Error(val message: String) : TrafficAlertsState()
}

/**
 * États pour les lignes de transport (Compatibilité PlanScreen)
 */
sealed class TransportLinesUiState {
    object Loading : TransportLinesUiState()
    data class Success(val lines: List<com.pelotcl.app.core.data.model.Feature>) : TransportLinesUiState()
    data class PartialSuccess(val lines: List<com.pelotcl.app.core.data.model.Feature>) : TransportLinesUiState()
    data class Error(val message: String) : TransportLinesUiState()
}

/**
 * États pour les arrêts de transport (Compatibilité PlanScreen)
 */
sealed class TransportStopsUiState {
    object Loading : TransportStopsUiState()
    data class Success(val stops: List<com.pelotcl.app.core.data.model.StopFeature>) : TransportStopsUiState()
    data class Error(val message: String) : TransportStopsUiState()
}
