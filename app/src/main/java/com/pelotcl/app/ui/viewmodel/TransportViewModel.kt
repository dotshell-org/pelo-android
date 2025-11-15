package com.pelotcl.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pelotcl.app.data.model.Feature
import com.pelotcl.app.data.model.StopFeature
import com.pelotcl.app.data.repository.TransportRepository
import com.pelotcl.app.utils.ConnectionsHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
class TransportViewModel : ViewModel() {
    
    private val repository = TransportRepository()
    
    private val _uiState = MutableStateFlow<TransportLinesUiState>(TransportLinesUiState.Loading)
    val uiState: StateFlow<TransportLinesUiState> = _uiState.asStateFlow()
    
    private val _stopsUiState = MutableStateFlow<TransportStopsUiState>(TransportStopsUiState.Loading)
    val stopsUiState: StateFlow<TransportStopsUiState> = _stopsUiState.asStateFlow()
    
    // Cache des arrêts pour éviter de les recharger à chaque fois
    private var cachedStops: List<StopFeature>? = null
    private var stopsLoadingJob: kotlinx.coroutines.Job? = null
    
    // Index pré-calculé des correspondances par nom d'arrêt
    // Clé = nom d'arrêt normalisé, Valeur = liste des lignes en correspondance
    private var connectionsIndex: Map<String, List<String>> = emptyMap()
    
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
    fun getConnectionsForStop(stopName: String, currentLine: String): List<String> {
        val normalized = normalizeStopName(stopName)
        val connections = connectionsIndex[normalized] ?: emptyList()
        // Exclure la ligne actuelle
        return connections.filter { it != currentLine }
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
            return // Déjà en cache ou en cours de chargement
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
     * Construit un index des correspondances pour chaque arrêt
     * Permet un accès O(1) au lieu de parcourir tous les arrêts à chaque fois
     */
    private fun buildConnectionsIndex(allStops: List<StopFeature>) {
        val index = mutableMapOf<String, MutableSet<String>>()
        
        // Parcourir tous les arrêts une seule fois
        for (stop in allStops) {
            val normalized = normalizeStopName(stop.properties.nom)
            val connections = ConnectionsHelper.parseMetroFunicularAndTramConnections(stop.properties.desserte)
            
            // Ajouter les correspondances à l'index
            if (connections.isNotEmpty()) {
                index.getOrPut(normalized) { mutableSetOf() }.addAll(connections)
            }
        }
        
        // Convertir en Map immuable avec des listes triées
        connectionsIndex = index.mapValues { (_, connections) ->
            connections.sortedWith(compareBy { line ->
                when {
                    line in listOf("A", "B", "C", "D") -> line[0].code
                    line.startsWith("F") -> 100 + line.substring(1).toIntOrNull()!! 
                    line.startsWith("T") -> 200 + line.substring(1).toIntOrNull()!!
                    else -> 9999
                }
            })
        }
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
        viewModelScope.launch {
            val currentState = _uiState.value
            
            // Ne rien faire si on n'est pas dans un état Success
            if (currentState !is TransportLinesUiState.Success) {
                return@launch
            }
            
            // Vérifier si la ligne est déjà chargée
            val isAlreadyLoaded = currentState.lines.any { 
                it.properties.ligne.equals(lineName, ignoreCase = true) 
            }
            
            if (isAlreadyLoaded) {
                return@launch // Ligne déjà présente, ne rien faire
            }
            
            // Charger la ligne depuis l'API
            repository.getLineByName(lineName)
                .onSuccess { feature ->
                    if (feature != null) {
                        // Ajouter la nouvelle ligne aux lignes existantes
                        val updatedLines = currentState.lines + feature
                        _uiState.value = TransportLinesUiState.Success(updatedLines)
                    }
                }
                .onFailure { exception ->
                    // En cas d'erreur, ne pas changer l'état (garder les lignes actuelles)
                    android.util.Log.e("TransportViewModel", "Erreur lors du chargement de la ligne $lineName: ${exception.message}")
                }
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
}
