package com.pelotcl.app.data.graph

/**
 * Modèles de données pour le graphe de transport
 */

data class GraphNode(
    val id: String,
    val name: String,
    val x: Double, // longitude
    val y: Double, // latitude
    val modes: List<String>,
    val boardingCost: Double
)

data class GraphEdge(
    val fromIndex: Int,
    val toIndex: Int,
    val weight: Int
)

data class GraphData(
    val metadata: GraphMetadata,
    val nodes: List<GraphNode>,
    val edges: List<GraphEdge>
)

data class GraphMetadata(
    val period: String,
    val nodeCount: Int,
    val edgeCount: Int
)

/**
 * Représente un segment d'un itinéraire (un trajet sur une ligne)
 */
data class RouteSegment(
    val fromStop: String,
    val toStop: String,
    val lineName: String? = null,
    val direction: String? = null,
    val duration: Int, // en secondes
    val intermediateStops: List<String> = emptyList(), // Arrêts intermédiaires sur ce segment
    val fromStopIndex: Int, // Index du nœud de départ dans le graphe
    val toStopIndex: Int // Index du nœud d'arrivée dans le graphe
)

/**
 * Représente un chemin complet d'un arrêt à un autre
 */
data class RoutePath(
    val segments: List<RouteSegment>,
    val totalDuration: Int,
    val totalCost: Int
)

/**
 * Résultat de recherche d'arrêt
 */
data class StopSearchResult(
    val nodeIndex: Int,
    val stopName: String,
    val distance: Double? = null // distance depuis la position GPS en mètres
)

