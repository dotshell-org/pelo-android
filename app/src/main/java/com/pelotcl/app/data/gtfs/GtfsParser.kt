package com.pelotcl.app.data.gtfs

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

/**
 * Parser pour les fichiers GTFS stockés dans les assets
 */
class GtfsParser(private val context: Context) {
    
    private val routesCache = mutableMapOf<String, GtfsRoute>()
    private val stopsCache = mutableMapOf<String, GtfsStop>()
    private var routesCacheLoaded = false
    private var stopsCacheLoaded = false
    
    /**
     * Charge toutes les routes depuis routes.txt
     */
    fun loadRoutes(): Map<String, GtfsRoute> {
        if (routesCacheLoaded) return routesCache
        
        routesCache.clear()
        
        try {
            val inputStream = context.assets.open("data/gtfstcl/routes.txt")
            val reader = BufferedReader(InputStreamReader(inputStream))
            
            // Lire l'en-tête
            val header = reader.readLine()?.split(",") ?: return routesCache
            
            // Lire les lignes
            reader.useLines { lines ->
                lines.forEach { line ->
                    val values = parseCsvLine(line)
                    if (values.size >= 8) {
                        val route = GtfsRoute(
                            routeId = values[0],
                            agencyId = values[1],
                            routeShortName = values[2],
                            routeLongName = values[3],
                            routeDesc = values[4],
                            routeType = values[5].toIntOrNull() ?: 0,
                            routeColor = values[6],
                            routeTextColor = values[7]
                        )
                        routesCache[route.routeShortName] = route
                    }
                }
            }
            routesCacheLoaded = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return routesCache
    }
    
    /**
     * Charge tous les arrêts depuis stops.txt
     */
    fun loadStops(): Map<String, GtfsStop> {
        if (stopsCacheLoaded) return stopsCache
        
        stopsCache.clear()
        
        try {
            val inputStream = context.assets.open("data/gtfstcl/stops.txt")
            val reader = BufferedReader(InputStreamReader(inputStream))
            
            // Lire l'en-tête
            val header = reader.readLine()?.split(",") ?: return stopsCache
            
            // Lire les lignes
            reader.useLines { lines ->
                lines.forEach { line ->
                    val values = parseCsvLine(line)
                    if (values.size >= 10) {
                        val stop = GtfsStop(
                            stopId = values[0],
                            stopCode = values[1],
                            stopName = values[2],
                            stopLat = values[3].toDoubleOrNull() ?: 0.0,
                            stopLon = values[4].toDoubleOrNull() ?: 0.0,
                            zoneId = values[5],
                            locationType = values[6].toIntOrNull() ?: 0,
                            parentStation = values[7],
                            stopTimezone = values[8],
                            wheelchairBoarding = values[9].toIntOrNull() ?: 0
                        )
                        stopsCache[stop.stopId] = stop
                    }
                }
            }
            stopsCacheLoaded = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return stopsCache
    }
    
    /**
     * Trouve le stop_id GTFS correspondant à un nom d'arrêt TCL
     */
    fun findStopIdByName(stopName: String): String? {
        val stops = loadStops()
        return stops.values.find { 
            it.stopName.equals(stopName, ignoreCase = true) 
        }?.stopId
    }
    
    /**
     * Récupère les prochains départs pour un arrêt spécifique
     * @param stopName Nom de l'arrêt TCL
     * @param lineName Nom de la ligne (optionnel, null pour toutes les lignes)
     * @param maxResults Nombre maximum de résultats
     * @return Liste des prochains départs triés par heure
     */
    fun getNextDepartures(
        stopName: String,
        lineName: String? = null,
        maxResults: Int = 10
    ): List<StopDeparture> {
        // Pour le moment, retourner des données simulées car le parsing du fichier
        // stop_times.txt (>50MB) est trop lent
        return generateMockDepartures(lineName, maxResults)
    }
    
    /**
     * Génère des départs simulés pour la démo
     */
    private fun generateMockDepartures(lineName: String?, maxResults: Int): List<StopDeparture> {
        val routes = loadRoutes()
        val route = lineName?.let { routes[it] }
        
        if (route == null) return emptyList()
        
        val now = Calendar.getInstance()
        val departures = mutableListOf<StopDeparture>()
        
        // Générer des horaires toutes les 5-10 minutes
        val intervals = listOf(5, 8, 12, 15, 18)
        var minutesToAdd = 2
        
        repeat(maxResults) {
            val departureTime = Calendar.getInstance().apply {
                add(Calendar.MINUTE, minutesToAdd)
            }
            
            departures.add(StopDeparture(
                lineName = route.routeShortName,
                destination = route.routeLongName.split(" - ").lastOrNull() ?: "Terminus",
                departureTime = String.format(
                    "%02d:%02d:00",
                    departureTime.get(Calendar.HOUR_OF_DAY),
                    departureTime.get(Calendar.MINUTE)
                ),
                routeColor = route.routeColor,
                routeTextColor = route.routeTextColor
            ))
            
            minutesToAdd += intervals.getOrElse(it) { 10 }
        }
        
        return departures
    }
    
    /**
     * Récupère tous les arrêts d'une ligne dans l'ordre
     * @param lineName Nom de la ligne
     * @param direction Direction (0 ou 1)
     * @param currentStopName Nom de l'arrêt actuel (pour le marquer)
     * @return Liste des arrêts dans l'ordre de passage
     */
    fun getLineStops(
        lineName: String,
        direction: Int = 0,
        currentStopName: String? = null
    ): List<LineStopInfo> {
        // D'abord, essayer de récupérer depuis le cache
        val cachedStops = LineStopsCache.getLineStops(lineName, currentStopName)
        if (cachedStops != null) {
            return cachedStops
        }
        
        val routes = loadRoutes()
        val stops = loadStops()
        
        val route = routes[lineName] ?: return emptyList()
        
        // Trouver un trip de cette ligne avec la direction spécifiée
        val tripId = findTripForRoute(route.routeId, direction) ?: return generateMockLineStops(lineName, currentStopName)
        
        val lineStops = mutableListOf<LineStopInfo>()
        val stopIds = mutableSetOf<String>() // Pour éviter les doublons
        
        try {
            val inputStream = context.assets.open("data/gtfstcl/stop_times.txt")
            val reader = BufferedReader(InputStreamReader(inputStream))
            
            // Lire l'en-tête
            reader.readLine()
            
            var lineCount = 0
            val maxLinesToRead = 500000 // Limiter la lecture pour éviter de bloquer trop longtemps
            
            // Lire les lignes et collecter les arrêts pour ce trip
            reader.useLines { lines ->
                for (line in lines) {
                    if (lineCount++ > maxLinesToRead) break
                    
                    val values = parseCsvLine(line)
                    if (values.size >= 9 && values[0] == tripId) {
                        val stopId = values[3]
                        
                        // Éviter les doublons
                        if (stopIds.contains(stopId)) continue
                        stopIds.add(stopId)
                        
                        val stopSequence = values[4].toIntOrNull() ?: 0
                        val stop = stops[stopId]
                        
                        if (stop != null) {
                            lineStops.add(LineStopInfo(
                                stopId = stopId,
                                stopName = stop.stopName,
                                stopSequence = stopSequence,
                                isCurrentStop = stop.stopName.equals(currentStopName, ignoreCase = true)
                            ))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // Si on n'a pas trouvé d'arrêts, utiliser des données simulées
        if (lineStops.isEmpty()) {
            return generateMockLineStops(lineName, currentStopName)
        }
        
        return lineStops.sortedBy { it.stopSequence }
    }
    
    /**
     * Génère des arrêts simulés pour une ligne
     */
    private fun generateMockLineStops(lineName: String, currentStopName: String?): List<LineStopInfo> {
        val routes = loadRoutes()
        val route = routes[lineName] ?: return emptyList()
        
        // Extraire les arrêts depuis le nom long de la route
        val routeParts = route.routeLongName.split(" - ")
        
        return routeParts.mapIndexed { index, stopName ->
            LineStopInfo(
                stopId = "mock_${index}",
                stopName = stopName.trim(),
                stopSequence = index + 1,
                isCurrentStop = stopName.trim().equals(currentStopName, ignoreCase = true)
            )
        }
    }
    
    /**
     * Trouve la route associée à un trip
     */
    private fun findRouteForTrip(tripId: String): GtfsRoute? {
        try {
            val inputStream = context.assets.open("data/gtfstcl/trips.txt")
            val reader = BufferedReader(InputStreamReader(inputStream))
            
            // Lire l'en-tête
            reader.readLine()
            
            reader.useLines { lines ->
                lines.forEach { line ->
                    val values = parseCsvLine(line)
                    if (values.size >= 3 && values[2] == tripId) {
                        val routeId = values[0]
                        return routesCache.values.find { it.routeId == routeId }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
    
    /**
     * Trouve la destination d'un trip
     */
    private fun findTripDestination(tripId: String): String {
        try {
            val inputStream = context.assets.open("data/gtfstcl/trips.txt")
            val reader = BufferedReader(InputStreamReader(inputStream))
            
            // Lire l'en-tête
            reader.readLine()
            
            reader.useLines { lines ->
                lines.forEach { line ->
                    val values = parseCsvLine(line)
                    if (values.size >= 4 && values[2] == tripId) {
                        return values[3] // trip_headsign
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "Destination inconnue"
    }
    
    /**
     * Trouve un trip pour une route et direction données
     */
    private fun findTripForRoute(routeId: String, direction: Int): String? {
        try {
            val inputStream = context.assets.open("data/gtfstcl/trips.txt")
            val reader = BufferedReader(InputStreamReader(inputStream))
            
            // Lire l'en-tête
            reader.readLine()
            
            reader.useLines { lines ->
                lines.forEach { line ->
                    val values = parseCsvLine(line)
                    if (values.size >= 6 && values[0] == routeId && values[5].toIntOrNull() == direction) {
                        return values[2] // trip_id
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
    
    /**
     * Convertit une chaîne de temps HH:MM:SS en secondes depuis minuit
     */
    private fun timeToSeconds(time: String): Int {
        val parts = time.split(":")
        if (parts.size != 3) return 0
        
        val hours = parts[0].toIntOrNull() ?: 0
        val minutes = parts[1].toIntOrNull() ?: 0
        val seconds = parts[2].toIntOrNull() ?: 0
        
        return hours * 3600 + minutes * 60 + seconds
    }
    
    /**
     * Parse une ligne CSV en gérant les virgules dans les guillemets
     */
    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val currentField = StringBuilder()
        var inQuotes = false
        
        for (char in line) {
            when {
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    result.add(currentField.toString())
                    currentField.clear()
                }
                else -> currentField.append(char)
            }
        }
        result.add(currentField.toString())
        
        return result
    }
}
