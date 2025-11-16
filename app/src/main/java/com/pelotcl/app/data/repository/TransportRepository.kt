package com.pelotcl.app.data.repository

import android.content.Context
import com.pelotcl.app.data.api.RetrofitInstance
import com.pelotcl.app.data.cache.TransportCache
import com.pelotcl.app.data.model.Feature
import com.pelotcl.app.data.model.FeatureCollection
import com.pelotcl.app.data.model.StopCollection

/**
 * Repository for managing transport line data
 */
class TransportRepository(context: Context? = null) {
    
    private val api = RetrofitInstance.api
    private val cache = context?.let { TransportCache.getInstance(it) }
    
    /**
     * Fetches all transport lines (metro, funicular and tram ONLY)
     * Bus lines are NOT loaded by default to avoid overloading the phone
     * To load a specific bus line, use getLineByName()
     * Uses cache to improve performance
     */
    suspend fun getAllLines(): Result<FeatureCollection> {
        return try {
            // Try to load from cache
            val cachedMetro = cache?.getMetroLines()
            val cachedTram = cache?.getTramLines()
            
            val metroFuniculaire: FeatureCollection
            val trams: FeatureCollection
            
            if (cachedMetro != null && cachedTram != null) {
                // Cache hit: use cached data
                android.util.Log.d("TransportRepository", "Cache HIT: Loading lines from cache")
                metroFuniculaire = FeatureCollection(
                    type = "FeatureCollection",
                    features = cachedMetro,
                    totalFeatures = cachedMetro.size,
                    numberMatched = cachedMetro.size,
                    numberReturned = cachedMetro.size
                )
                trams = FeatureCollection(
                    type = "FeatureCollection",
                    features = cachedTram,
                    totalFeatures = cachedTram.size,
                    numberMatched = cachedTram.size,
                    numberReturned = cachedTram.size
                )
            } else {
                // Cache miss: load from API
                android.util.Log.d("TransportRepository", "Cache MISS: Loading lines from API")
                metroFuniculaire = api.getTransportLines()
                trams = api.getTramLines()
                
                // Save to cache
                cache?.saveMetroLines(metroFuniculaire.features)
                cache?.saveTramLines(trams.features)
            }

            // Merge only metro/funicular and trams (NOT buses)
            val allFeatures = (metroFuniculaire.features + trams.features)

            // Log des lignes de tram avant grouping
            val tramLines = trams.features.map { it.properties.ligne }.distinct().sorted()
            android.util.Log.d("TransportRepository", "Tram lines from API: $tramLines")
            android.util.Log.d("TransportRepository", "Total tram features before grouping: ${trams.features.size}")
            
            // Log code_trace for T1
            val t1Features = trams.features.filter { it.properties.ligne.equals("T1", ignoreCase = true) }
            android.util.Log.d("TransportRepository", "T1 features count: ${t1Features.size}")
            t1Features.forEach { feature ->
                android.util.Log.d("TransportRepository", "T1 code_trace: ${feature.properties.codeTrace}, sens: ${feature.properties.sens}")
            }

            // Group by code_trace and keep only the first of each group (outbound direction)
            val uniqueLines = allFeatures
                .groupBy { it.properties.codeTrace }
                .map { (_, features) -> features.first() }

            val filteredCollection = metroFuniculaire.copy(
                features = uniqueLines,
                numberReturned = uniqueLines.size,
                totalFeatures = (metroFuniculaire.totalFeatures ?: 0) + (trams.totalFeatures ?: 0),
                numberMatched = (metroFuniculaire.numberMatched ?: 0) + (trams.numberMatched ?: 0)
            )

            // Log loaded lines
            val lineNames = uniqueLines.map { it.properties.ligne }.sorted()
            android.util.Log.d("TransportRepository", "Loaded lines: $lineNames")
            android.util.Log.d("TransportRepository", "Metro/Funicular count: ${metroFuniculaire.features.size}")
            android.util.Log.d("TransportRepository", "Tram count: ${trams.features.size}")
            android.util.Log.d("TransportRepository", "Total unique lines: ${uniqueLines.size}")

            Result.success(filteredCollection)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Fetches a specific line by name (outbound direction only)
     * Searches first in metro/funicular/tram, then in buses if not found
     * This allows loading bus lines only on demand
     * Uses cache to improve performance
     */
    suspend fun getLineByName(lineName: String): Result<Feature?> {
        return try {
            // Try to load from cache
            val cachedMetro = cache?.getMetroLines()
            val cachedTram = cache?.getTramLines()
            
            val priorityFeatures: List<Feature>
            
            if (cachedMetro != null && cachedTram != null) {
                // Cache hit: use cached data
                android.util.Log.d("TransportRepository", "Cache HIT: Loading line $lineName from cache")
                priorityFeatures = cachedMetro + cachedTram
            } else {
                // Cache miss: load from API
                android.util.Log.d("TransportRepository", "Cache MISS: Loading line $lineName from API")
                val metroFuniculaire = api.getTransportLines()
                val trams = api.getTramLines()
                priorityFeatures = metroFuniculaire.features + trams.features
                
                // Save to cache
                cache?.saveMetroLines(metroFuniculaire.features)
                cache?.saveTramLines(trams.features)
            }

            // Search first in metro/funicular/tram
            val line = priorityFeatures
                .filter { it.properties.ligne.equals(lineName, ignoreCase = true) }
                .firstOrNull()
            
            // If found, return it
            if (line != null) {
                return Result.success(line)
            }
            
            // Otherwise, search in buses (on-demand loading)
            val cachedBus = cache?.getBusLines()
            val busLine = if (cachedBus != null) {
                // Cache hit for buses
                android.util.Log.d("TransportRepository", "Cache HIT: Loading bus line $lineName from cache")
                cachedBus.filter { it.properties.ligne.equals(lineName, ignoreCase = true) }
                    .firstOrNull()
            } else {
                // Cache miss: load all buses from API
                android.util.Log.d("TransportRepository", "Cache MISS: Loading all bus lines from API")
                try {
                    val bus = api.getBusLines()
                    // Save to cache (memory only, not on disk)
                    cache?.saveBusLines(bus.features)
                    
                    bus.features
                        .filter { it.properties.ligne.equals(lineName, ignoreCase = true) }
                        .firstOrNull()
                } catch (e: OutOfMemoryError) {
                    android.util.Log.e("TransportRepository", "OutOfMemoryError loading bus lines, trying to find line without caching", e)
                    // In case of OutOfMemoryError, just load the requested line without caching everything
                    val bus = api.getBusLines()
                    bus.features
                        .filter { it.properties.ligne.equals(lineName, ignoreCase = true) }
                        .firstOrNull()
                }
            }
            
            Result.success(busLine)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Fetches all transport stops
     * Filters duplicates for metros and funiculars (keeps only one stop per station)
     * Transfer stations (multiple lines) are displayed stacked like buses
     * Intelligently filters tram stops: keeps outbound stops, but also inbound stops
     * that don't have an outbound equivalent (certain terminals)
     * Uses cache to improve performance
     */
    suspend fun getAllStops(): Result<StopCollection> {
        return try {
            // Try to load from cache
            val cachedStops = cache?.getStops()
            
            val response: StopCollection
            
            if (cachedStops != null) {
                // Cache hit: use cached data
                android.util.Log.d("TransportRepository", "Cache HIT: Loading stops from cache (${cachedStops.size} stops)")
                response = StopCollection(
                    type = "FeatureCollection",
                    features = cachedStops,
                    totalFeatures = cachedStops.size,
                    numberMatched = cachedStops.size,
                    numberReturned = cachedStops.size
                )
            } else {
                // Cache miss: load from API
                android.util.Log.d("TransportRepository", "Cache MISS: Loading stops from API")
                response = api.getTransportStops()
            }
            
            // Intelligently filter tram stops:
            // Group by name and tram line, then keep :A priority, otherwise :R
            val tramStopsGrouped = response.features
                .filter { stop -> 
                    stop.properties.desserte.matches(Regex(".*\\bT\\d+:[AR]\\b.*"))
                }
                .groupBy { stop ->
                    // Group by stop name and tram line number
                    val tramLineMatch = Regex("\\bT(\\d+):[AR]\\b").find(stop.properties.desserte)
                    if (tramLineMatch != null) {
                        "${stop.properties.nom}_T${tramLineMatch.groupValues[1]}"
                    } else {
                        stop.id.toString()
                    }
                }
            
            // For each group, keep :A priority, otherwise :R
            val dedupedTramStops = tramStopsGrouped.values.map { stops ->
                stops.firstOrNull { it.properties.desserte.contains(":A") } 
                    ?: stops.first()
            }
            
            // Keep all non-tram stops
            val nonTramStops = response.features.filter { stop ->
                !stop.properties.desserte.matches(Regex(".*\\bT\\d+:[AR]\\b.*"))
            }
            
            // Combine deduplicated tram stops with other stops
            val stopsWithoutTramRetour = dedupedTramStops + nonTramStops
            
            // Filter metro and funicular stops to avoid duplicates by platform
            // For transfers, we merge all services into one
            val filteredStops = stopsWithoutTramRetour.groupBy { stop ->
                val desserte = stop.properties.desserte
                
                // Strict metro detection: service starts with A:, B:, C: or D:
                // Strict funicular detection: service starts with F1: or F2:
                // This excludes buses that have these lines later in their service
                val isMetro = desserte.matches(Regex("^[ABCD]:.*"))
                val isFunicular = desserte.matches(Regex("^F[12]:.*"))
                
                if (isMetro || isFunicular) {
                    // Group only by station name so that all lines
                    // at the same station (transfers) are displayed stacked at the same place
                    stop.properties.nom
                } else {
                    // For others (bus, tram), keep each stop unique
                    stop.id
                }
            }.map { (_, stops) ->
                if (stops.size == 1) {
                    // Single stop for this group, return as is
                    stops.first()
                } else {
                    // Multiple stops (transfer): merge services
                    val baseStop = stops.firstOrNull { it.properties.desserte.contains(":A") } ?: stops.first()
                    
                    // Collect all unique lines from all stops at this station
                    val allDessertes = stops.map { it.properties.desserte }.toSet()
                    
                    // Merge services into a single string with commas
                    // Ex: "A:A" + "D:A" -> "A:A,D:A"
                    val mergedDesserte = allDessertes.joinToString(",")
                    
                    // Create a new stop with the merged service
                    baseStop.copy(
                        properties = baseStop.properties.copy(
                            desserte = mergedDesserte
                        )
                    )
                }
            }
            
            val filteredCollection = response.copy(
                features = filteredStops,
                numberReturned = filteredStops.size
            )
            
            // Save to cache only if it wasn't already cached
            if (cachedStops == null) {
                try {
                    cache?.saveStops(filteredCollection.features)
                } catch (e: OutOfMemoryError) {
                    android.util.Log.e("TransportRepository", "OutOfMemoryError saving stops to cache, continuing without cache", e)
                    // Continue without cache in case of memory error
                }
            }
            
            Result.success(filteredCollection)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Forces reloading of stops from the API and updates the cache
     */
    suspend fun refreshStops(): Result<StopCollection> {
        return try {
            android.util.Log.d("TransportRepository", "Forcing refresh of stops from API")
            cache?.clearStops()
            getAllStops()
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Forces reloading of lines from the API and updates the cache
     */
    suspend fun refreshLines(): Result<FeatureCollection> {
        return try {
            android.util.Log.d("TransportRepository", "Forcing refresh of lines from API")
            cache?.clearLines()
            getAllLines()
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Clears the entire cache
     */
    suspend fun clearCache() {
        cache?.clearAll()
    }
}
