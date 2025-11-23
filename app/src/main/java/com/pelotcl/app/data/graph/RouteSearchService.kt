package com.pelotcl.app.data.graph

import android.content.Context
import android.util.Log

/**
 * Service pour rechercher des itinéraires entre arrêts
 */
class RouteSearchService(context: Context) {
    
    private val graphRepository = GraphRepository(context)
    private var dijkstraService: DijkstraService? = null
    
    companion object {
        private const val TAG = "RouteSearchService"
    }
    
    init {
        // Charger le graphe au démarrage
        graphRepository.loadCurrentGraph()?.let {
            dijkstraService = DijkstraService(it)
        }
    }
    
    /**
     * Recherche des arrêts par nom
     */
    fun searchStops(query: String): List<StopSearchResult> {
        return graphRepository.searchStops(query)
    }
    
    /**
     * Trouve l'arrêt le plus proche d'une position GPS
     */
    fun findNearestStop(latitude: Double, longitude: Double): StopSearchResult? {
        return graphRepository.findNearestStop(latitude, longitude)
    }
    
    /**
     * Calcule l'itinéraire entre deux arrêts
     * @param fromStopIndex Index de l'arrêt de départ
     * @param toStopIndex Index de l'arrêt d'arrivée
     * @return L'itinéraire trouvé ou null
     */
    fun findRoute(fromStopIndex: Int, toStopIndex: Int): RoutePath? {
        val service = dijkstraService ?: run {
            // Recharger le graphe si nécessaire
            graphRepository.loadCurrentGraph()?.let {
                dijkstraService = DijkstraService(it)
            } ?: run {
                Log.e(TAG, "No graph loaded")
                return null
            }
            dijkstraService!!
        }
        
        return service.findShortestPath(fromStopIndex, toStopIndex)
    }
    
    /**
     * Recharge le graphe si nécessaire (par exemple après un changement d'heure)
     */
    fun refreshGraph() {
        graphRepository.loadCurrentGraph()?.let {
            dijkstraService = DijkstraService(it)
        }
    }
}

