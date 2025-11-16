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
     * Normalise un nom de station pour la comparaison
     * Garde uniquement les lettres et convertit en minuscules
     * Ex: "Saxe - Gambetta" devient "saxegambetta"
     */
    private fun normalizeStationName(name: String): String {
        return name.filter { it.isLetter() }.lowercase()
    }
    
    /**
     * Normalise les mots d'un nom de station en gérant les abréviations
     * Ex: "L." devient "l", "Louis" devient "louis"
     * Filtre aussi les mots génériques comme "gare" qui peuvent être omis
     */
    private fun normalizeWords(name: String): List<String> {
        val ignoredWords = setOf("gare", "station", "arret")
        
        return name.split(Regex("[\\s\\-,.]+"))
            .filter { it.isNotEmpty() }
            .map { word -> 
                // Retirer les points et garder uniquement les lettres
                word.filter { it.isLetter() }.lowercase()
            }
            .filter { it.isNotEmpty() && it !in ignoredWords }
    }
    
    /**
     * Compare deux noms de station de manière flexible
     * Gère les abréviations : "L. Pradel" match avec "Louis Pradel"
     * Gère aussi les abréviations de mots : "Cat." match avec "Cathédrale"
     */
    private fun stationNamesMatch(name1: String, name2: String): Boolean {
        // First try simple comparison
        val normalized1 = normalizeStationName(name1)
        val normalized2 = normalizeStationName(name2)
        
        if (normalized1 == normalized2) {
            return true
        }
        
        // Then try with separated words to handle abbreviations
        val words1 = normalizeWords(name1)
        val words2 = normalizeWords(name2)
        
        // If one of the two names has fewer words, it might be an abbreviated version
        if (words1.size != words2.size) {
            val shorter = if (words1.size < words2.size) words1 else words2
            val longer = if (words1.size < words2.size) words2 else words1
            
            // Check if all words from the short version match with the long version
            // Either exactly or as beginning of word (for abbreviations)
            // Also handles compound words (e.g. "partdieu" matches "part" + "dieu")
            var shorterIndex = 0
            var longerIndex = 0
            
            while (shorterIndex < shorter.size && longerIndex < longer.size) {
                val shortWord = shorter[shorterIndex]
                val longWord = longer[longerIndex]
                
                // Exact match
                if (shortWord == longWord) {
                    shorterIndex++
                    longerIndex++
                }
                // Long word starts with short word (e.g. "cat" matches "cathedrale")
                // Minimum 3 letters to avoid false positives
                else if (shortWord.length >= 3 && longWord.startsWith(shortWord)) {
                    shorterIndex++
                    longerIndex++
                }
                // Short word is an initial of long word (e.g. "l" matches "louis")
                else if (shortWord.length == 1 && longWord.startsWith(shortWord)) {
                    shorterIndex++
                    longerIndex++
                }
                // Short word might be a compound word (e.g. "partdieu" matches "part" + "dieu")
                // Try to match short word with multiple consecutive long words
                else if (shortWord.length > longWord.length) {
                    var combinedWord = longWord
                    var tempIndex = longerIndex + 1
                    var matched = false
                    
                    // Try to combine consecutive words to see if we get the short word
                    while (tempIndex < longer.size && combinedWord.length < shortWord.length) {
                        combinedWord += longer[tempIndex]
                        if (combinedWord == shortWord) {
                            // Match found!
                            longerIndex = tempIndex + 1
                            shorterIndex++
                            matched = true
                            break
                        }
                        tempIndex++
                    }
                    
                    if (!matched) {
                        // No match, advance in long version
                        longerIndex++
                    }
                }
                // Maybe the long word has no equivalent in the short version
                else {
                    longerIndex++
                }
            }
            
            // If we matched all words from the short version, it's good
            return shorterIndex == shorter.size
        }
        
        // Otherwise, check that all words match in order
        if (words1.size == words2.size) {
            return words1.zip(words2).all { (w1, w2) ->
                w1 == w2 || 
                (w1.length == 1 && w2.startsWith(w1)) ||
                (w2.length == 1 && w1.startsWith(w2)) ||
                (w1.length >= 3 && w2.startsWith(w1)) ||
                (w2.length >= 3 && w1.startsWith(w2))
            }
        }
        
        return false
    }
    
    /**
     * Charge toutes les routes depuis routes.txt
     */
    fun loadRoutes(): Map<String, GtfsRoute> {
        if (routesCacheLoaded) return routesCache
        
        routesCache.clear()
        
        try {
            val inputStream = context.assets.open("data/gtfstcl/routes.txt")
            val reader = BufferedReader(InputStreamReader(inputStream))
            
            // Read header
            val header = reader.readLine()?.split(",") ?: return routesCache
            
            // Read lines
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
            
            // Read header
            val header = reader.readLine()?.split(",") ?: return stopsCache
            
            // Read lines
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
            stationNamesMatch(it.stopName, stopName)
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
        // For now, return simulated data car le parsing du fichier
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
        
        // Generate schedules every 5-10 minutes
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
     * Récupère tous les arrêts d'une ligne in order
     * @param lineName Nom de la ligne
     * @param direction Direction (0 ou 1)
     * @param currentStopName Nom de l'arrêt actuel (pour le marquer)
     * @return Liste des arrêts in order de passage
     */
    fun getLineStops(
        lineName: String,
        direction: Int = 0,
        currentStopName: String? = null
    ): List<LineStopInfo> {
        android.util.Log.d("GtfsParser", "getLineStops called for line: $lineName, direction: $direction, currentStop: $currentStopName")
        
        // First, try to retrieve from cache
        val cachedStops = LineStopsCache.getLineStops(lineName, currentStopName)
        if (cachedStops != null) {
            android.util.Log.d("GtfsParser", "Found $lineName in cache with ${cachedStops.size} stops")
            return cachedStops
        }
        
        val routes = loadRoutes()
        android.util.Log.d("GtfsParser", "Loaded ${routes.size} routes. Available keys: ${routes.keys.take(10)}")
        
        val stops = loadStops()
        
        val route = routes[lineName]
        if (route == null) {
            android.util.Log.w("GtfsParser", "Route '$lineName' not found in routes map. Returning empty list.")
            return emptyList()
        }
        android.util.Log.d("GtfsParser", "Found route: ${route.routeShortName} - ${route.routeLongName}")
        
        // Find a trip of this line with specified direction
        val tripId = findTripForRoute(route.routeId, direction)
        if (tripId == null) {
            android.util.Log.w("GtfsParser", "No trip found for route ${route.routeId} with direction $direction. Generating mock stops.")
            return generateMockLineStops(lineName, currentStopName)
        }
        android.util.Log.d("GtfsParser", "Found trip ID: $tripId")
        
        val lineStops = mutableListOf<LineStopInfo>()
        val stopIds = mutableSetOf<String>() // To avoid duplicates
        
        try {
            val inputStream = context.assets.open("data/gtfstcl/stop_times.txt")
            val reader = BufferedReader(InputStreamReader(inputStream))
            
            // Read header
            reader.readLine()
            
            var lineCount = 0
            val maxLinesToRead = 500000 // Limit reading to avoid blocking trop longtemps
            
            // Read lines and collect stops for this trip
            reader.useLines { lines ->
                for (line in lines) {
                    if (lineCount++ > maxLinesToRead) break
                    
                    val values = parseCsvLine(line)
                    if (values.size >= 9 && values[0] == tripId) {
                        val stopId = values[3]
                        
                        // Avoid duplicates
                        if (stopIds.contains(stopId)) continue
                        stopIds.add(stopId)
                        
                        val stopSequence = values[4].toIntOrNull() ?: 0
                        val stop = stops[stopId]
                        
                        if (stop != null) {
                            lineStops.add(LineStopInfo(
                                stopId = stopId,
                                stopName = stop.stopName,
                                stopSequence = stopSequence,
                                isCurrentStop = currentStopName?.let { stationNamesMatch(stop.stopName, it) } ?: false
                            ))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // If no stops found, use simulated data
        if (lineStops.isEmpty()) {
            android.util.Log.w("GtfsParser", "No stops found in stop_times.txt for trip $tripId. Generating mock stops.")
            return generateMockLineStops(lineName, currentStopName)
        }
        
        android.util.Log.d("GtfsParser", "Successfully loaded ${lineStops.size} stops for line $lineName")
        return lineStops.sortedBy { it.stopSequence }
    }
    
    /**
     * Génère des arrêts simulés pour une ligne
     */
    private fun generateMockLineStops(lineName: String, currentStopName: String?): List<LineStopInfo> {
        val routes = loadRoutes()
        val route = routes[lineName] ?: run {
            android.util.Log.w("GtfsParser", "No route found for line $lineName when generating mock stops")
            return emptyList()
        }
        
        // Extract stops from long name de la route
        val routeParts = route.routeLongName.split(" - ")
        
        val mockStops = routeParts.mapIndexed { index, stopName ->
            LineStopInfo(
                stopId = "mock_${index}",
                stopName = stopName.trim(),
                stopSequence = index + 1,
                isCurrentStop = currentStopName?.let { stationNamesMatch(stopName.trim(), it) } ?: false
            )
        }
        
        android.util.Log.d("GtfsParser", "Generated ${mockStops.size} mock stops for line $lineName: ${mockStops.map { it.stopName }}")
        return mockStops
    }
    
    /**
     * Trouve la route associée à un trip
     */
    private fun findRouteForTrip(tripId: String): GtfsRoute? {
        try {
            val inputStream = context.assets.open("data/gtfstcl/trips.txt")
            val reader = BufferedReader(InputStreamReader(inputStream))
            
            // Read header
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
            
            // Read header
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
            
            // Read header
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
