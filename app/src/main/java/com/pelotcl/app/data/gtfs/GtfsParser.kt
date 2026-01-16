package com.pelotcl.app.data.gtfs

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Calendar

/**
 * Parser for GTFS files stored in assets
 */
class GtfsParser(private val context: Context) {
    
    private val routesCache = mutableMapOf<String, GtfsRoute>()
    private var routesCacheLoaded = false

    /**
     * Loads the routes from routes.txt
     */
    fun loadRoutes(): Map<String, GtfsRoute> {
        if (routesCacheLoaded) return routesCache
        
        routesCache.clear()
        
        try {
            val inputStream = context.assets.open("data/gtfstcl/routes.txt")
            val reader = BufferedReader(InputStreamReader(inputStream))
            
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
     * Récupère les prochains départs pour un arrêt spécifique
     * @param lineName Nom de la ligne (optionnel, null pour toutes les lignes)
     * @param maxResults Nombre maximum de résultats
     * @return Liste des prochains départs triés par heure
     */
    fun getNextDepartures(
        lineName: String? = null,
        maxResults: Int = 10
    ): List<StopDeparture> {
        return generateMockDepartures(lineName, maxResults)
    }
    
    /**
     * Génère des départs simulés pour la démo
     */
    private fun generateMockDepartures(lineName: String?, maxResults: Int): List<StopDeparture> {
        val routes = loadRoutes()
        val route = lineName?.let { routes[it] }
        
        if (route == null) return emptyList()

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
                    "fr",
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