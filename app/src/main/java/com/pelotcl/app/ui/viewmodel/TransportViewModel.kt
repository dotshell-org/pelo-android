package com.pelotcl.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pelotcl.app.data.model.Feature
import com.pelotcl.app.data.model.StopFeature
import com.pelotcl.app.data.repository.TransportRepository
import com.pelotcl.app.utils.Connection
import com.pelotcl.app.utils.ConnectionsHelper
import com.pelotcl.app.utils.TransportType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * État de l'UI pour les lignes de transport
 */
sealed class TransportLinesUiState {
    object Loading : TransportLinesUiState()
    data class Success(val lines: List<Feature>) : TransportLinesUiState()
    data class Error(val message: String) : TransportLinesUiState()
}

/**
 * État de l'UI pour les arrêts de transport
 */
sealed class TransportStopsUiState {
    object Loading : TransportStopsUiState()
    data class Success(val stops: List<StopFeature>) : TransportStopsUiState()
    data class Error(val message: String) : TransportStopsUiState()
}

/**
 * ViewModel pour gérer les données des lignes de transport
 */
class TransportViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository = TransportRepository(application.applicationContext)
    
    private val _uiState = MutableStateFlow<TransportLinesUiState>(TransportLinesUiState.Loading)
    val uiState: StateFlow<TransportLinesUiState> = _uiState.asStateFlow()
    
    private val _stopsUiState = MutableStateFlow<TransportStopsUiState>(TransportStopsUiState.Loading)
    val stopsUiState: StateFlow<TransportStopsUiState> = _stopsUiState.asStateFlow()
    
    // Stops cache to avoid reloading them each time
    private var cachedStops: List<StopFeature>? = null
    private var stopsLoadingJob: kotlinx.coroutines.Job? = null
    
    // Pre-calculated transfers index by stop name
    // Key = normalized stop name, Value = list des lignes en correspondance
    private var connectionsIndex: Map<String, List<com.pelotcl.app.utils.Connection>> = emptyMap()

    /**
     * Normalise un nom de station pour l'utiliser comme clé d'index
     */
    private fun normalizeStopName(name: String): String {
        return name.filter { it.isLetter() }.lowercase()
    }
    
    /**
     * Récupère les correspondances pour un arrêt donné depuis l'index pré-calculé
     * Méthode ultra-rapide (O(1))
     */
    fun getConnectionsForStop(stopName: String, currentLine: String): List<com.pelotcl.app.utils.Connection> {
        val normalized = normalizeStopName(stopName)
        val connections = connectionsIndex[normalized] ?: emptyList()
        // Exclure la ligne actuelle
        return connections.filter { it.lineName != currentLine }
    }

    /**
     * Récupère tous les arrêts (depuis le cache si disponible, sinon charge)
     * Méthode synchrone qui retourne immédiatement le cache ou une liste vide
     */
    fun getCachedStopsSync(): List<StopFeature> {
        return cachedStops ?: emptyList()
    }
    
    /**
     * Charge tous les arrêts en arrière-plan et les met en cache
     * Démarre le chargement si pas déjà en cours
     * Construit également l'index des correspondances pour un accès ultra-rapide
     */
    fun preloadStops() {
        if (cachedStops != null || stopsLoadingJob?.isActive == true) {
            return // Already in cache or loading
        }
        
        stopsLoadingJob = viewModelScope.launch {
            repository.getAllStops()
                .onSuccess { stopCollection ->
                    cachedStops = stopCollection.features
                    
                    // Construire l'index des correspondances
                    buildConnectionsIndex(stopCollection.features)
                    
                    _stopsUiState.value = TransportStopsUiState.Success(stopCollection.features)
                }
                .onFailure { exception ->
                    _stopsUiState.value = TransportStopsUiState.Error(
                        exception.message ?: "Une erreur est survenue lors du chargement des arrêts"
                    )
                }
        }
    }
    
    /**
     * Force le rechargement du cache des arrêts
     * Utile quand on détecte des données incorrectes
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
     * Construit un index des correspondances pour chaque arrêt
     * Permet un accès O(1) au lieu de parcourir tous les arrêts each time
     */
    private fun buildConnectionsIndex(allStops: List<StopFeature>) {
        // Step 1: Group stops by approximate name pour trouver un nom "canonique"
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
     * Charge toutes les lignes de transport
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
                        exception.message ?: "Une erreur est survenue"
                    )
                }
        }
    }
    
    /**
     * Charge une ligne spécifique par son nom
     */
    fun loadLineByName(lineName: String) {
        viewModelScope.launch {
            _uiState.value = TransportLinesUiState.Loading
            repository.getLineByName(lineName)
                .onSuccess { feature ->
                    _uiState.value = if (feature != null) {
                        TransportLinesUiState.Success(listOf(feature))
                    } else {
                        TransportLinesUiState.Error("Ligne $lineName non trouvée")
                    }
                }
                .onFailure { exception ->
                    _uiState.value = TransportLinesUiState.Error(
                        exception.message ?: "Une erreur est survenue"
                    )
                }
        }
    }
    
    /**
     * Ajoute une ligne spécifique aux lignes déjà chargées (pour les lignes de bus à la demande)
     * Ne modifie pas l'état si la ligne est déjà présente
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
                it.properties.ligne.equals(lineName, ignoreCase = true) 
            }
            
            if (isAlreadyLoaded) {
                android.util.Log.d("TransportViewModel", "Line $lineName is already loaded, skipping")
                return@launch // Line already present, do nothing
            }
            
            android.util.Log.d("TransportViewModel", "Loading line $lineName from API...")
            
            // Charger la ligne depuis l'API
            repository.getLineByName(lineName)
                .onSuccess { feature ->
                    if (feature != null) {
                        android.util.Log.d("TransportViewModel", "Successfully loaded line $lineName, adding to map")
                        // Ajouter la nouvelle ligne aux lignes existantes
                        val updatedLines = currentState.lines + feature
                        _uiState.value = TransportLinesUiState.Success(updatedLines)
                    } else {
                        android.util.Log.w("TransportViewModel", "getLineByName returned null for $lineName")
                    }
                }
                .onFailure { exception ->
                    // In case of error, don't change state (garder les lignes actuelles)
                    android.util.Log.e("TransportViewModel", "Erreur lors du chargement de la ligne $lineName: ${exception.message}")
                }
        }
    }
    
    /**
     * Retire une ligne spécifique des lignes chargées (pour nettoyer les lignes de bus temporaires)
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
        
        // Filtrer pour retirer la ligne
        val updatedLines = currentState.lines.filter { 
            !it.properties.ligne.equals(lineName, ignoreCase = true)
        }
        
        val afterCount = updatedLines.size
        android.util.Log.d("TransportViewModel", "Removed line $lineName. Before: $beforeCount lines, After: $afterCount lines")
        
        _uiState.value = TransportLinesUiState.Success(updatedLines)
    }
    
    /**
     * Récupère les arrêts desservis par une ligne spécifique, ordonnés selon le tracé de la ligne
     * @param lineName Nom de la ligne (ex: "86", "A", "T1")
     * @param currentStopName Nom de l'arrêt actuel pour le marquer (optionnel)
     * @return Liste d'arrêts avec leurs correspondances, ordonnés selon le parcours de la ligne
     */
    fun getStopsForLine(lineName: String, currentStopName: String? = null): List<com.pelotcl.app.data.gtfs.LineStopInfo> {
        // First, try to retrieve from cache (for metros and trams)
        val cachedStops = com.pelotcl.app.data.gtfs.LineStopsCache.getLineStops(lineName, currentStopName)
        if (cachedStops != null) {
            android.util.Log.d("TransportViewModel", "Found $lineName in cache with ${cachedStops.size} stops")
            // Add transfers for each stop
            return cachedStops.map { stop ->
                val connections = getConnectionsForStop(stop.stopName, lineName)
                val filteredConnections = connections.filter {
                    it.transportType == TransportType.METRO ||
                    it.transportType == TransportType.TRAM ||
                    it.transportType == TransportType.FUNICULAR
                }
                stop.copy(connections = filteredConnections.map { it.lineName })
            }
        }
        
        // Get all stops from cache
        val allStops = getCachedStopsSync()
        
        android.util.Log.d("TransportViewModel", "getStopsForLine: line=$lineName, total stops=${allStops.size}")
        
        // Filter stops that are served par cette ligne
        val lineStops = allStops.filter { stop ->
            val desserte = stop.properties.desserte
            desserte.split(',').any { part ->
                part.split(':').first().trim().equals(lineName, ignoreCase = true)
            }
        }
        
        android.util.Log.d("TransportViewModel", "Found ${lineStops.size} stops for line $lineName (with duplicates)")
        
        // Get all line traces to order stops
        val currentState = _uiState.value
        if (currentState is TransportLinesUiState.Success) {
            // Get ALL traces of this line (may have multiple directions)
            val lineFeatures = currentState.lines.filter { 
                it.properties.ligne.equals(lineName, ignoreCase = true) 
            }
            
            android.util.Log.d("TransportViewModel", "Found ${lineFeatures.size} traces for line $lineName")
            
            if (lineFeatures.isNotEmpty()) {
                // Choose the longest trace (generally main/outbound direction)
                val mainTrace = lineFeatures.maxByOrNull { feature ->
                    feature.geometry.coordinates.sumOf { lineString -> lineString.size }
                }
                
                if (mainTrace != null) {
                    // The trace is a MultiLineString avec plusieurs segments
                    // Find the longest segment (main trace)
                    val longestSegment = mainTrace.geometry.coordinates.maxByOrNull { segment ->
                        segment.size
                    } ?: emptyList()
                    
                    android.util.Log.d("TransportViewModel", "Main trace has ${mainTrace.geometry.coordinates.size} segments")
                    android.util.Log.d("TransportViewModel", "Longest segment (${mainTrace.properties.sens}) has ${longestSegment.size} points")
                    
                    // Determine main trace direction et le convertir en lettre (A ou R)
                    val mainDirection = when (mainTrace.properties.sens.uppercase()) {
                        "ALLER" -> "A"
                        "RETOUR" -> "R"
                        else -> mainTrace.properties.sens.take(1).uppercase() // First character uppercase
                    }
                    
                    android.util.Log.d("TransportViewModel", "Filtering stops for direction code: $mainDirection")
                    
                    // Afficher quelques exemples de desserte pour debug
                    lineStops.take(5).forEach { stop ->
                        android.util.Log.d("TransportViewModel", "  Stop '${stop.properties.nom}': desserte='${stop.properties.desserte}'")
                    }
                    
                    // Filter stops to keep only those that match au sens principal
                    val directionStops = lineStops.filter { stop ->
                        val desserte = stop.properties.desserte
                        // Chercher "86:A" ou "86:R" dans la desserte
                        val matches = desserte.split(",").any { line ->
                            val trimmed = line.trim()
                            val result = trimmed.equals("$lineName:$mainDirection", ignoreCase = true)
                            if (result) {
                                android.util.Log.d("TransportViewModel", "  ✓ Stop '${stop.properties.nom}' matches (found '$trimmed')")
                            }
                            result
                        }
                        matches
                    }
                    
                    android.util.Log.d("TransportViewModel", "Found ${directionStops.size} stops for direction $mainDirection")
                    
                    // For each stop, find its position sur le segment principal
                    val stopsWithPosition = directionStops.map { stop ->
                        val stopCoords = stop.geometry.coordinates
                        
                        // Trouver le point le plus proche du segment et son index
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
                    
                    // Convertir en LineStopInfo
                    return orderedStops.mapIndexed { index, stop ->
                        val connections = getConnectionsForStop(stop.properties.nom, lineName)
                        val filteredConnections = connections.filter {
                            it.transportType == TransportType.METRO ||
                            it.transportType == TransportType.TRAM ||
                            it.transportType == TransportType.FUNICULAR
                        }
                        com.pelotcl.app.data.gtfs.LineStopInfo(
                            stopId = stop.properties.id.toString(),
                            stopName = stop.properties.nom,
                            stopSequence = index + 1,
                            isCurrentStop = currentStopName?.let {
                                normalizeStopName(stop.properties.nom) == normalizeStopName(it)
                            } ?: false,
                            connections = filteredConnections.map { it.lineName }
                        )
                    }
                }
            }
        }
        
        // Fallback: if no trace found, supprimer au moins les doublons
        val uniqueStops = lineStops.distinctBy { stop ->
            normalizeStopName(stop.properties.nom)
        }
        
        android.util.Log.d("TransportViewModel", "Fallback: returning ${uniqueStops.size} unique stops without ordering")
        
        return uniqueStops.mapIndexed { index, stop ->
            val connections = getConnectionsForStop(stop.properties.nom, lineName)
            val filteredConnections = connections.filter {
                it.transportType == TransportType.METRO ||
                it.transportType == TransportType.TRAM ||
                it.transportType == TransportType.FUNICULAR
            }
            com.pelotcl.app.data.gtfs.LineStopInfo(
                stopId = stop.properties.id.toString(),
                stopName = stop.properties.nom,
                stopSequence = index + 1,
                isCurrentStop = currentStopName?.let {
                    normalizeStopName(stop.properties.nom) == normalizeStopName(it)
                } ?: false,
                connections = filteredConnections.map { it.lineName }
            )
        }
    }

    /**
     * Charge tous les arrêts de transport
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
                        exception.message ?: "Une erreur est survenue lors du chargement des arrêts"
                    )
                }
        }
    }
    
    /**
     * Récupère la liste de toutes les lignes disponibles (noms uniquement)
     * en extrayant les lignes depuis tous les arrêts chargés
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
        
        // Then, extract from all stops (pour avoir TOUTES les lignes, y compris les bus)
        val linesFromStops = getCachedStopsSync()
            .flatMap { stop ->
                val desserte = stop.properties.desserte
                desserte.split(',').map { it.split(':').first().trim() }
            }
            .distinct()
        
        // Combiner et trier
        return (linesFromFeatures + linesFromStops)
            .distinct()
            .filter { it.isNotEmpty() } // Filtrer les lignes vides
            .sortedWith(compareBy(
                // Sort by type first (Metro, Funicular, Tram, then the rest)
                { line ->
                    when {
                        line.uppercase() in setOf("A", "B", "C", "D") -> 0 // Metros first
                        line.uppercase().startsWith("F") -> 1 // Funiculaires
                        line.uppercase().startsWith("T") && !line.uppercase().startsWith("TB") -> 2 // Trams
                        else -> 3 // Le reste (bus)
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
     * Force le rechargement des données depuis l'API (ignore le cache)
     */
    fun refreshAllData() {
        viewModelScope.launch {
            _uiState.value = TransportLinesUiState.Loading
            _stopsUiState.value = TransportStopsUiState.Loading
            
            // Vider le cache et recharger
            repository.clearCache()
            
            // Recharger les lignes
            repository.getAllLines()
                .onSuccess { featureCollection ->
                    _uiState.value = TransportLinesUiState.Success(featureCollection.features)
                }
                .onFailure { exception ->
                    _uiState.value = TransportLinesUiState.Error(
                        exception.message ?: "Une erreur est survenue"
                    )
                }
            
            // Reload stops
            repository.getAllStops()
                .onSuccess { stopCollection ->
                    cachedStops = stopCollection.features
                    buildConnectionsIndex(stopCollection.features)
                    _stopsUiState.value = TransportStopsUiState.Success(stopCollection.features)
                }
                .onFailure { exception ->
                    _stopsUiState.value = TransportStopsUiState.Error(
                        exception.message ?: "Une erreur est survenue lors du chargement des arrêts"
                    )
                }
        }
    }
}
