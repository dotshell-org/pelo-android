package com.pelotcl.app.data.graph

import android.util.Log
import java.util.*

/**
 * Service pour calculer les chemins les plus courts avec l'algorithme de Dijkstra
 */
class DijkstraService(private val graph: GraphData) {
    
    companion object {
        private const val TAG = "DijkstraService"
    }
    
    // Structure de graphe optimisée pour Dijkstra
    private val adjacencyList: List<MutableList<Pair<Int, Int>>> by lazy {
        buildAdjacencyList()
    }
    
    /**
     * Construit la liste d'adjacence depuis les edges
     */
    private fun buildAdjacencyList(): List<MutableList<Pair<Int, Int>>> {
        val adjList = List(graph.nodes.size) { mutableListOf<Pair<Int, Int>>() }
        
        graph.edges.forEach { edge ->
            adjList[edge.fromIndex].add(Pair(edge.toIndex, edge.weight))
        }
        
        return adjList
    }
    
    /**
     * Trouve le chemin le plus court entre deux nœuds
     * Retourne null si aucun chemin n'existe
     */
    fun findShortestPath(fromIndex: Int, toIndex: Int): RoutePath? {
        if (fromIndex == toIndex) {
            return RoutePath(emptyList(), 0, 0)
        }
        
        val distances = IntArray(graph.nodes.size) { Int.MAX_VALUE }
        val previous = IntArray(graph.nodes.size) { -1 }
        val visited = BooleanArray(graph.nodes.size) { false }
        
        distances[fromIndex] = 0
        
        // Priority queue: (distance, nodeIndex)
        val queue = PriorityQueue<Pair<Int, Int>>(compareBy { it.first })
        queue.offer(Pair(0, fromIndex))
        
        while (queue.isNotEmpty()) {
            val (currentDist, currentNode) = queue.poll()
            
            if (visited[currentNode]) continue
            visited[currentNode] = true
            
            // Si on a atteint la destination, on peut s'arrêter
            if (currentNode == toIndex) {
                break
            }
            
            // Explorer les voisins
            adjacencyList[currentNode].forEach { (neighbor, weight) ->
                if (!visited[neighbor]) {
                    val newDist = currentDist + weight
                    if (newDist < distances[neighbor]) {
                        distances[neighbor] = newDist
                        previous[neighbor] = currentNode
                        queue.offer(Pair(newDist, neighbor))
                    }
                }
            }
        }
        
        // Reconstruire le chemin
        if (distances[toIndex] == Int.MAX_VALUE) {
            return null // Pas de chemin trouvé
        }
        
        val path = mutableListOf<Int>()
        var current = toIndex
        while (current != -1) {
            path.add(0, current)
            current = previous[current]
        }
        
        // Convertir le chemin en segments
        val segments = buildSegments(path)
        
        return RoutePath(
            segments = segments,
            totalDuration = distances[toIndex],
            totalCost = distances[toIndex]
        )
    }
    
    /**
     * Trouve les K chemins les plus courts (variations)
     * Utilise une variante de Yen's algorithm pour trouver de vrais chemins alternatifs
     */
    fun findKShortestPaths(fromIndex: Int, toIndex: Int, k: Int = 3): List<RoutePath> {
        val paths = mutableListOf<RoutePath>()
        
        // Premier chemin: le plus court
        val shortestPath = findShortestPath(fromIndex, toIndex)
        if (shortestPath != null) {
            paths.add(shortestPath)
        }
        
        if (paths.isEmpty()) return paths
        
        // Trouver des chemins alternatifs en évitant progressivement les edges du chemin précédent
        while (paths.size < k) {
            val lastPath = paths.last()
            val alternativePath = findAlternativePathAvoidingEdges(fromIndex, toIndex, paths)
            
            if (alternativePath == null || alternativePath.totalDuration >= lastPath.totalDuration * 1.5) {
                // Si le chemin alternatif est trop long ou n'existe pas, essayer via des nœuds intermédiaires
                val viaIntermediate = findPathViaIntermediate(fromIndex, toIndex, paths)
                if (viaIntermediate != null) {
                    paths.add(viaIntermediate)
                } else {
                    break // Plus de chemins alternatifs trouvés
                }
            } else {
                paths.add(alternativePath)
            }
        }
        
        return paths.take(k)
    }
    
    /**
     * Trouve un chemin alternatif en évitant certains edges des chemins existants
     */
    private fun findAlternativePathAvoidingEdges(
        fromIndex: Int,
        toIndex: Int,
        existingPaths: List<RoutePath>
    ): RoutePath? {
        // Collecter les edges à éviter (tous les edges du dernier chemin)
        val edgesToAvoid = mutableSetOf<Pair<Int, Int>>()
        existingPaths.lastOrNull()?.segments?.forEach { segment ->
            edgesToAvoid.add(Pair(segment.fromStopIndex, segment.toStopIndex))
        }
        
        // Dijkstra en évitant ces edges
        val distances = IntArray(graph.nodes.size) { Int.MAX_VALUE }
        val previous = IntArray(graph.nodes.size) { -1 }
        val visited = BooleanArray(graph.nodes.size) { false }
        
        distances[fromIndex] = 0
        val queue = PriorityQueue<Pair<Int, Int>>(compareBy { it.first })
        queue.offer(Pair(0, fromIndex))
        
        while (queue.isNotEmpty()) {
            val (currentDist, currentNode) = queue.poll()
            
            if (visited[currentNode]) continue
            visited[currentNode] = true
            
            if (currentNode == toIndex) {
                break
            }
            
            adjacencyList[currentNode].forEach { (neighbor, weight) ->
                if (!visited[neighbor] && !edgesToAvoid.contains(Pair(currentNode, neighbor))) {
                    val newDist = currentDist + weight
                    if (newDist < distances[neighbor]) {
                        distances[neighbor] = newDist
                        previous[neighbor] = currentNode
                        queue.offer(Pair(newDist, neighbor))
                    }
                }
            }
        }
        
        if (distances[toIndex] == Int.MAX_VALUE) {
            return null
        }
        
        val path = mutableListOf<Int>()
        var current = toIndex
        while (current != -1) {
            path.add(0, current)
            current = previous[current]
        }
        
        val segments = buildSegments(path)
        return RoutePath(
            segments = segments,
            totalDuration = distances[toIndex],
            totalCost = distances[toIndex]
        )
    }
    
    /**
     * Trouve un chemin via un nœud intermédiaire qui n'est pas dans les chemins existants
     */
    private fun findPathViaIntermediate(
        fromIndex: Int,
        toIndex: Int,
        existingPaths: List<RoutePath>
    ): RoutePath? {
        // Collecter tous les nœuds des chemins existants
        val usedNodes = existingPaths.flatMap { path ->
            path.segments.flatMap { listOf(it.fromStopIndex, it.toStopIndex) }
        }.toSet()
        
        // Essayer quelques nœuds intermédiaires aléatoires
        val candidates = graph.nodes
            .mapIndexed { index, _ -> index }
            .filter { it != fromIndex && it != toIndex && !usedNodes.contains(it) }
            .shuffled()
            .take(20)
        
        for (intermediate in candidates) {
            val path1 = findShortestPath(fromIndex, intermediate)
            val path2 = findShortestPath(intermediate, toIndex)
            
            if (path1 != null && path2 != null) {
                val combinedPath = RoutePath(
                    segments = path1.segments + path2.segments,
                    totalDuration = path1.totalDuration + path2.totalDuration,
                    totalCost = path1.totalCost + path2.totalCost
                )
                
                // Vérifier que ce chemin est différent
                val isDifferent = existingPaths.none { existingPath ->
                    existingPath.segments.size == combinedPath.segments.size &&
                    existingPath.segments.zip(combinedPath.segments).all { (a, b) ->
                        a.fromStopIndex == b.fromStopIndex && a.toStopIndex == b.toStopIndex
                    }
                }
                
                if (isDifferent) {
                    return combinedPath
                }
            }
        }
        
        return null
    }
    
    /**
     * Trouve des chemins alternatifs en essayant différents nœuds intermédiaires
     */
    private fun findAlternativePaths(
        fromIndex: Int,
        toIndex: Int,
        existingPaths: List<RoutePath>,
        maxPaths: Int
    ): List<RoutePath> {
        val alternativePaths = mutableListOf<RoutePath>()
        val existingNodeSets = existingPaths.map { path ->
            path.segments.flatMap { listOf(it.fromStop, it.toStop) }.toSet()
        }
        
        // Essayer quelques nœuds intermédiaires proches
        val intermediateCandidates = graph.nodes
            .mapIndexed { index, _ -> index }
            .filter { it != fromIndex && it != toIndex }
            .shuffled()
            .take(10) // Limiter pour la performance
        
        for (intermediate in intermediateCandidates) {
            if (alternativePaths.size >= maxPaths - existingPaths.size) break
            
            // Vérifier que ce nœud n'est pas déjà dans tous les chemins existants
            val isInAllPaths = existingNodeSets.all { it.contains(graph.nodes[intermediate].name) }
            if (isInAllPaths) continue
            
            // Chercher un chemin via ce nœud intermédiaire
            val path1 = findShortestPath(fromIndex, intermediate)
            val path2 = findShortestPath(intermediate, toIndex)
            
            if (path1 != null && path2 != null) {
                val combinedPath = RoutePath(
                    segments = path1.segments + path2.segments,
                    totalDuration = path1.totalDuration + path2.totalDuration,
                    totalCost = path1.totalCost + path2.totalCost
                )
                
                // Vérifier que ce chemin est différent des existants
                val isDifferent = existingPaths.none { existingPath ->
                    existingPath.segments.size == combinedPath.segments.size &&
                    existingPath.segments.zip(combinedPath.segments).all { (a, b) ->
                        a.fromStop == b.fromStop && a.toStop == b.toStop
                    }
                }
                
                if (isDifferent) {
                    alternativePaths.add(combinedPath)
                }
            }
        }
        
        return alternativePaths.sortedBy { it.totalDuration }
    }
    
    /**
     * Construit les segments de route à partir d'un chemin de nœuds
     * Regroupe les segments consécutifs qui utilisent probablement la même ligne
     */
    private fun buildSegments(path: List<Int>): List<RouteSegment> {
        if (path.size < 2) return emptyList()
        
        val segments = mutableListOf<RouteSegment>()
        var currentSegmentStart = 0
        
        for (i in 0 until path.size - 1) {
            val fromNode = graph.nodes[path[i]]
            val toNode = graph.nodes[path[i + 1]]
            
            // Trouver le poids de l'edge entre ces deux nœuds
            val edge = graph.edges.find { 
                it.fromIndex == path[i] && it.toIndex == path[i + 1]
            }
            
            // Détecter si c'est un changement de ligne (basé sur le poids ou les modes)
            val isTransfer = edge?.weight ?: 0 > 300 // Transferts prennent généralement plus de temps
            
            if (isTransfer && i > currentSegmentStart) {
                // Créer un segment pour la partie précédente
                val segmentFromNode = graph.nodes[path[currentSegmentStart]]
                val segmentToNode = graph.nodes[path[i]]
                val intermediateStops = (currentSegmentStart + 1 until i).map { 
                    graph.nodes[path[it]].name 
                }
                
                segments.add(
                    RouteSegment(
                        fromStop = segmentFromNode.name,
                        toStop = segmentToNode.name,
                        lineName = null, // Sera enrichi plus tard
                        direction = null,
                        duration = calculateSegmentDuration(path, currentSegmentStart, i),
                        intermediateStops = intermediateStops,
                        fromStopIndex = path[currentSegmentStart],
                        toStopIndex = path[i]
                    )
                )
                
                // Nouveau segment commence après le transfert
                currentSegmentStart = i
            }
        }
        
        // Ajouter le dernier segment
        if (currentSegmentStart < path.size - 1) {
            val segmentFromNode = graph.nodes[path[currentSegmentStart]]
            val segmentToNode = graph.nodes[path[path.size - 1]]
            val intermediateStops = (currentSegmentStart + 1 until path.size - 1).map { 
                graph.nodes[path[it]].name 
            }
            
            segments.add(
                RouteSegment(
                    fromStop = segmentFromNode.name,
                    toStop = segmentToNode.name,
                    lineName = null,
                    direction = null,
                    duration = calculateSegmentDuration(path, currentSegmentStart, path.size - 1),
                    intermediateStops = intermediateStops,
                    fromStopIndex = path[currentSegmentStart],
                    toStopIndex = path[path.size - 1]
                )
            )
        }
        
        return segments
    }
    
    /**
     * Calcule la durée totale d'un segment
     */
    private fun calculateSegmentDuration(path: List<Int>, fromIndex: Int, toIndex: Int): Int {
        var totalDuration = 0
        for (i in fromIndex until toIndex) {
            val edge = graph.edges.find { 
                it.fromIndex == path[i] && it.toIndex == path[i + 1]
            }
            totalDuration += edge?.weight ?: 0
        }
        return totalDuration
    }
}

