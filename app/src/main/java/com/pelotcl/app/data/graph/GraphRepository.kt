package com.pelotcl.app.data.graph

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.InputStream
import java.util.Calendar

/**
 * Repository pour charger et gérer les graphes de transport
 */
class GraphRepository(private val context: Context) {
    
    private val gson = Gson()
    private var currentGraph: GraphData? = null
    private val graphCache = mutableMapOf<String, GraphData>()
    
    companion object {
        private const val TAG = "GraphRepository"
        
        // Mapping des horaires aux fichiers
        private val TIME_PERIODS = mapOf(
            "morning_peak" to (7 to 9),
            "day_offpeak" to (9 to 16),
            "evening_peak" to (16 to 19),
            "evening" to (19 to 23),
            "late_night" to (23 to 26)
        )
    }
    
    /**
     * Détermine quel fichier de graphe utiliser selon l'heure actuelle
     */
    private fun getGraphFileNameForCurrentTime(): String {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        
        return when {
            hour >= 7 && hour < 9 -> "network_morning_peak.json"
            hour >= 9 && hour < 16 -> "network_day_offpeak.json"
            hour >= 16 && hour < 19 -> "network_evening_peak.json"
            hour >= 19 && hour < 23 -> "network_evening.json"
            else -> "network_late_night.json" // 23h-2h
        }
    }
    
    /**
     * Charge un graphe depuis un fichier JSON
     */
    fun loadGraph(fileName: String): GraphData? {
        // Vérifier le cache
        if (graphCache.containsKey(fileName)) {
            return graphCache[fileName]
        }
        
        return try {
            val inputStream: InputStream = context.assets.open("databases/$fileName")
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val jsonObject = JsonParser.parseString(jsonString).asJsonObject
            
            val metadataObj = jsonObject.getAsJsonObject("metadata")
            val metadata = GraphMetadata(
                period = metadataObj.get("period").asString,
                nodeCount = metadataObj.get("node_count").asInt,
                edgeCount = metadataObj.get("edge_count").asInt
            )
            
            val nodesArray = jsonObject.getAsJsonArray("nodes")
            val nodes = mutableListOf<GraphNode>()
            for (i in 0 until nodesArray.size()) {
                val node = nodesArray[i].asJsonObject
                val modesArray = node.getAsJsonArray("modes")
                val modes = mutableListOf<String>()
                for (j in 0 until modesArray.size()) {
                    modes.add(modesArray[j].asString)
                }
                nodes.add(
                    GraphNode(
                        id = node.get("id").asString,
                        name = node.get("name").asString,
                        x = node.get("x").asDouble,
                        y = node.get("y").asDouble,
                        modes = modes,
                        boardingCost = node.get("boarding_cost").asDouble
                    )
                )
            }
            
            val edgesArray = jsonObject.getAsJsonArray("edges")
            val edges = mutableListOf<GraphEdge>()
            for (i in 0 until edgesArray.size()) {
                val arr = edgesArray[i].asJsonArray
                edges.add(
                    GraphEdge(
                        fromIndex = arr[0].asInt,
                        toIndex = arr[1].asInt,
                        weight = arr[2].asInt
                    )
                )
            }
            
            val graphData = GraphData(metadata, nodes, edges)
            graphCache[fileName] = graphData
            Log.d(TAG, "Loaded graph: $fileName with ${nodes.size} nodes and ${edges.size} edges")
            graphData
        } catch (e: Exception) {
            Log.e(TAG, "Error loading graph $fileName", e)
            null
        }
    }
    
    /**
     * Charge le graphe approprié pour l'heure actuelle
     */
    fun loadCurrentGraph(): GraphData? {
        val fileName = getGraphFileNameForCurrentTime()
        val graph = loadGraph(fileName)
        currentGraph = graph
        return graph
    }
    
    /**
     * Obtient le graphe actuellement chargé
     */
    fun getCurrentGraph(): GraphData? = currentGraph
    
    /**
     * Recherche des arrêts par nom
     */
    fun searchStops(query: String, limit: Int = 20): List<StopSearchResult> {
        val graph = getCurrentGraph() ?: return emptyList()
        val queryLower = query.lowercase().trim()
        
        if (queryLower.isEmpty()) return emptyList()
        
        return graph.nodes
            .mapIndexed { index, node ->
                StopSearchResult(
                    nodeIndex = index,
                    stopName = node.name
                )
            }
            .filter { it.stopName.lowercase().contains(queryLower) }
            .take(limit)
    }
    
    /**
     * Trouve l'arrêt le plus proche d'une position GPS
     */
    fun findNearestStop(latitude: Double, longitude: Double): StopSearchResult? {
        val graph = getCurrentGraph() ?: return null
        
        var nearestIndex = -1
        var minDistance = Double.MAX_VALUE
        
        graph.nodes.forEachIndexed { index, node ->
            val distance = calculateDistance(
                latitude, longitude,
                node.y, node.x
            )
            if (distance < minDistance) {
                minDistance = distance
                nearestIndex = index
            }
        }
        
        return if (nearestIndex >= 0) {
            StopSearchResult(
                nodeIndex = nearestIndex,
                stopName = graph.nodes[nearestIndex].name,
                distance = minDistance
            )
        } else null
    }
    
    /**
     * Calcule la distance entre deux points GPS (formule de Haversine)
     * Retourne la distance en mètres
     */
    private fun calculateDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val R = 6371000.0 // Rayon de la Terre en mètres
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return R * c
    }
}

