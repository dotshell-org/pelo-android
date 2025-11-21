package com.pelotcl.app.data.repository

import android.content.Context
import com.pelotcl.app.data.api.RetrofitInstance
import com.pelotcl.app.data.cache.TransportCache
import com.pelotcl.app.data.model.Feature
import com.pelotcl.app.data.model.FeatureCollection
import com.pelotcl.app.data.model.Geometry
import com.pelotcl.app.data.model.TransportLineProperties
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.pelotcl.app.data.model.StopCollection

/**
 * Repository for managing transport line data
 */
class TransportRepository(context: Context? = null) {
    
    private val api = RetrofitInstance.api
    private val cache = context?.let { TransportCache.getInstance(it) }
    
    /**
     * Fetches all transport lines (metro, funicular, tram, and navigone ONLY)
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
            var navigone: FeatureCollection
            
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

            // Load navigone lines (NAVI1 - always loaded like metro/tram)
            try {
                navigone = api.getNavigoneLines()
                android.util.Log.d("TransportRepository", "Loaded ${navigone.features.size} navigone lines")
            } catch (e: Exception) {
                android.util.Log.w("TransportRepository", "Failed to load navigone lines: ${e.message}")
                navigone = FeatureCollection(
                    type = "FeatureCollection",
                    features = emptyList(),
                    totalFeatures = 0,
                    numberMatched = 0,
                    numberReturned = 0
                )
            }

            // Fetch Rhônexpress (RX) from dedicated WFS layer and map to our Feature model
            val rxFeatures: List<Feature> = try {
                fetchRhonexpressFromWfs()
            } catch (e: Exception) {
                android.util.Log.w("TransportRepository", "Failed to load Rhônexpress from WFS: ${e.message}")
                emptyList()
            }

            // Merge metro/funicular, trams, navigone and RX (NOT buses)
            val allFeatures = (metroFuniculaire.features + trams.features + navigone.features + rxFeatures)

            // Log tram lines before grouping
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
                totalFeatures = (metroFuniculaire.totalFeatures ?: 0) + (trams.totalFeatures ?: 0) + (navigone.totalFeatures ?: 0),
                numberMatched = (metroFuniculaire.numberMatched ?: 0) + (trams.numberMatched ?: 0) + (navigone.numberMatched ?: 0)
            )

            // Log loaded lines
            val lineNames = uniqueLines.map { it.properties.ligne }.sorted()
            android.util.Log.d("TransportRepository", "Loaded lines: $lineNames")
            android.util.Log.d("TransportRepository", "Metro/Funicular count: ${metroFuniculaire.features.size}")
            android.util.Log.d("TransportRepository", "Tram count: ${trams.features.size}")
            android.util.Log.d("TransportRepository", "Navigone count: ${navigone.features.size}")
            android.util.Log.d("TransportRepository", "Total unique lines: ${uniqueLines.size}")
            if (rxFeatures.isNotEmpty()) {
                android.util.Log.d("TransportRepository", "Included Rhônexpress features: ${rxFeatures.size}")
            }

            Result.success(filteredCollection)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Récupère et mappe la couche Rhônexpress (RX) vers nos modèles internes
     */
    private suspend fun fetchRhonexpressFromWfs(): List<Feature> {
        fun mapJsonToFeatures(json: JsonObject, srs: String): List<Feature> {
            val featuresArray: JsonArray = json.getAsJsonArray("features") ?: return emptyList()
            android.util.Log.d("TransportRepository", "Rhônexpress(WFS $srs): raw features=${featuresArray.size()}")

            val result = mutableListOf<Feature>()
            var minLon = Double.POSITIVE_INFINITY
            var minLat = Double.POSITIVE_INFINITY
            var maxLon = Double.NEGATIVE_INFINITY
            var maxLat = Double.NEGATIVE_INFINITY

            for (elem in featuresArray) {
                val featObj = elem.asJsonObject

                val id = featObj.get("id")?.asString ?: "rx-${System.nanoTime()}"
                val properties = featObj.getAsJsonObject("properties") ?: JsonObject()
                val gid = properties.get("gid")?.asInt ?: kotlin.math.abs(id.hashCode())

                val geomObj = featObj.getAsJsonObject("geometry") ?: continue
                val geomType = geomObj.get("type")?.asString ?: "LineString"
                val coordinatesElement = geomObj.get("coordinates")

                val multiLineCoordinates: List<List<List<Double>>> = when (geomType) {
                    "MultiLineString" -> {
                        val outer = coordinatesElement.asJsonArray
                        outer.map { lineArr ->
                            lineArr.asJsonArray.map { coord ->
                                val pair = coord.asJsonArray
                                val lon = pair[0].asDouble
                                val lat = pair[1].asDouble
                                if (!lon.isNaN() && !lat.isNaN()) {
                                    minLon = kotlin.math.min(minLon, lon)
                                    maxLon = kotlin.math.max(maxLon, lon)
                                    minLat = kotlin.math.min(minLat, lat)
                                    maxLat = kotlin.math.max(maxLat, lat)
                                }
                                listOf(lon, lat)
                            }
                        }
                    }
                    "LineString" -> {
                        val line = coordinatesElement.asJsonArray.map { coord ->
                            val pair = coord.asJsonArray
                            val lon = pair[0].asDouble
                            val lat = pair[1].asDouble
                            if (!lon.isNaN() && !lat.isNaN()) {
                                minLon = kotlin.math.min(minLon, lon)
                                maxLon = kotlin.math.max(maxLon, lon)
                                minLat = kotlin.math.min(minLat, lat)
                                maxLat = kotlin.math.max(maxLat, lat)
                            }
                            listOf(lon, lat)
                        }
                        listOf(line)
                    }
                    else -> continue
                }

                val geometry = Geometry(
                    type = "MultiLineString",
                    coordinates = multiLineCoordinates
                )

                val transportProps = TransportLineProperties(
                    ligne = "RX",
                    codeTrace = "RX-$gid",
                    codeLigne = "RX",
                    typeTrace = properties.get("type_trace")?.asString ?: "",
                    nomTrace = properties.get("nom_trace")?.asString ?: "Rhônexpress",
                    sens = properties.get("sens")?.asString ?: "ALLER",
                    origine = properties.get("origine")?.asString ?: "Gare Part-Dieu Villette",
                    destination = properties.get("destination")?.asString ?: "Aéroport St Exupéry -RX",
                    nomOrigine = properties.get("nom_origine")?.asString ?: "Gare Part-Dieu Villette",
                    nomDestination = properties.get("nom_destination")?.asString ?: "Aéroport St Exupéry -RX",
                    familleTransport = "TRAM",
                    dateDebut = properties.get("date_debut")?.asString ?: "",
                    dateFin = properties.get("date_fin")?.asString,
                    codeTypeLigne = properties.get("code_type_ligne")?.asString ?: "TRAM",
                    nomTypeLigne = properties.get("nom_type_ligne")?.asString ?: "Tramway",
                    pmr = properties.get("pmr")?.asBoolean ?: true,
                    codeTriLigne = properties.get("code_tri_ligne")?.asString ?: "RX",
                    nomVersion = properties.get("nom_version")?.asString ?: "",
                    lastUpdate = properties.get("last_update")?.asString ?: "",
                    lastUpdateFme = properties.get("last_update_fme")?.asString ?: "",
                    gid = gid,
                    couleur = properties.get("couleur")?.asString ?: "#E30613"
                )

                result.add(
                    Feature(
                        type = "Feature",
                        id = "rx_$id",
                        geometry = geometry,
                        geometryName = null,
                        properties = transportProps,
                        bbox = null
                    )
                )
            }

            if (result.isNotEmpty()) {
                android.util.Log.d(
                    "TransportRepository",
                    "Rhônexpress(WFS $srs): bbox lon[$minLon,$maxLon] lat[$minLat,$maxLat]"
                )
            }
            return result
        }

        android.util.Log.d("TransportRepository", "Fetching Rhônexpress from WFS (primary SRS=EPSG:4326)…")
        val primary = try {
            mapJsonToFeatures(RetrofitInstance.api.getRhonexpressRaw(), "EPSG:4326")
        } catch (t: Throwable) {
            android.util.Log.w("TransportRepository", "Rhônexpress primary fetch failed: ${t.message}")
            emptyList()
        }

        if (primary.isNotEmpty()) return primary

        android.util.Log.w(
            "TransportRepository",
            "Rhônexpress(WFS): 0 feature with EPSG:4326, retrying with EPSG:4171…"
        )
        val secondary = try {
            mapJsonToFeatures(
                RetrofitInstance.api.getRhonexpressRaw(srsName = "EPSG:4171"),
                "EPSG:4171"
            )
        } catch (t: Throwable) {
            android.util.Log.w("TransportRepository", "Rhônexpress secondary fetch failed: ${t.message}")
            emptyList()
        }

        if (secondary.isNotEmpty()) return secondary

        // Fallback ultime: construire une géométrie simple à partir des arrêts connus si disponibles
        android.util.Log.w(
            "TransportRepository",
            "Rhônexpress(WFS): empty results on both SRS. Building static fallback from stops if possible."
        )

        return try {
            val stops = RetrofitInstance.api.getTransportStops()
            val wanted = listOf(
                "Gare Part-Dieu Villette",
                "Vaulx-en-Velin La Soie",
                "Meyzieu Z.i.",
                "Aéroport St Exupéry -RX"
            )

            val found = wanted.mapNotNull { wantedName ->
                stops.features.firstOrNull { it.properties.nom.equals(wantedName, ignoreCase = true) }?.geometry?.coordinates
            }

            if (found.size >= 2) {
                val line = found.map { coords -> listOf(coords[0], coords[1]) } // ensure [lon,lat]
                val geometry = Geometry(
                    type = "MultiLineString",
                    coordinates = listOf(line)
                )
                val props = TransportLineProperties(
                    ligne = "RX",
                    codeTrace = "RX-fallback",
                    codeLigne = "RX",
                    typeTrace = "",
                    nomTrace = "Rhônexpress (fallback)",
                    sens = "ALLER",
                    origine = wanted.first(),
                    destination = wanted.last(),
                    nomOrigine = wanted.first(),
                    nomDestination = wanted.last(),
                    familleTransport = "TRAM",
                    dateDebut = "",
                    dateFin = null,
                    codeTypeLigne = "TRAM",
                    nomTypeLigne = "Tramway",
                    pmr = true,
                    codeTriLigne = "RX",
                    nomVersion = "",
                    lastUpdate = "",
                    lastUpdateFme = "",
                    gid = -1,
                    couleur = "#E30613"
                )
                android.util.Log.w(
                    "TransportRepository",
                    "Using static fallback geometry for RX built from ${found.size} stops"
                )
                listOf(
                    Feature(
                        type = "Feature",
                        id = "rx_fallback",
                        geometry = geometry,
                        geometryName = null,
                        properties = props,
                        bbox = null
                    )
                )
            } else {
                android.util.Log.w(
                    "TransportRepository",
                    "Fallback from stops failed (found ${found.size} of ${wanted.size}). RX will be absent."
                )
                emptyList()
            }
        } catch (t: Throwable) {
            android.util.Log.w(
                "TransportRepository",
                "Stops-based fallback failed: ${t.message}. RX will be absent."
            )
            emptyList()
        }
    }
    
    /**
     * Fetches a specific line by name (outbound direction only)
     * Searches first in metro/funicular/tram/navigone, then in buses if not found
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

            // Try navigone lines too (NAVI1)
            val navigoneFeatures: List<Feature> = try {
                val navigone = api.getNavigoneLines()
                navigone.features
            } catch (e: Exception) {
                android.util.Log.w("TransportRepository", "Failed to load navigone lines: ${e.message}")
                emptyList()
            }

            // Search first in metro/funicular/tram/navigone
            val line = (priorityFeatures + navigoneFeatures)
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
                
                // Debug: check if any stops serve NAV1
                val navStops = response.features.filter { 
                    it.properties.desserte.contains("NAV", ignoreCase = true) 
                }
                android.util.Log.d("TransportRepository", "Found ${navStops.size} stops with NAV in desserte")
                navStops.take(5).forEach { stop ->
                    android.util.Log.d("TransportRepository", "NAV stop: ${stop.properties.nom}, desserte: ${stop.properties.desserte}")
                }
            }
            
            // No need to load navigone stops separately - they are in the main API with code NAVI1
            // (which will be normalized to NAV1 in BusIconHelper)
            
            val allStopsFeatures = response.features
            
            // Intelligently filter tram stops:
            // Group by name and tram line, then keep :A priority, otherwise :R
            val tramStopsGrouped = allStopsFeatures
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
            
            // Keep all non-tram stops (including navigone stops)
            val nonTramStops = allStopsFeatures.filter { stop ->
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
