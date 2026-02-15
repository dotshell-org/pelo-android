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
import com.pelotcl.app.data.offline.OfflineRepository
import com.pelotcl.app.utils.withRetry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

/**
 * Repository for managing transport line data
 */
class TransportRepository(context: Context? = null) {

    private val api = RetrofitInstance.api
    private val cache = context?.let { TransportCache.getInstance(it) }
    private val offlineRepo = context?.let { OfflineRepository.getInstance(it) }
    
    /**
     * Fetches all transport lines (metro, funicular, tram, and navigone ONLY)
     * Bus lines are NOT loaded by default to avoid overloading the phone
     * To load a specific bus line, use getLineByName()
     * Uses cache to improve performance and parallel loading for faster startup
     */
    suspend fun getAllLines(): Result<FeatureCollection> {
        return withContext(kotlinx.coroutines.Dispatchers.Default) {
            try {
                // Try to load all line types from cache first
                val cachedMetro = cache?.getMetroLines()
                val cachedTram = cache?.getTramLines()
                val cachedNavigone = cache?.getNavigoneLines()
                val cachedTrambus = cache?.getTrambusLines()

                val metroFuniculaire: FeatureCollection
                val trams: FeatureCollection
                var navigoneFeatures: List<Feature>
                var trambusFeatures: List<Feature>

                // Load metro and tram (from cache or API)
                if (cachedMetro != null && cachedTram != null) {
                    // Cache hit: use cached data
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
                    // Cache miss: load from API with retry on transient failures
                    metroFuniculaire = withRetry(maxRetries = 2, initialDelayMs = 1000) {
                        api.getTransportLines()
                    }
                    trams = withRetry(maxRetries = 2, initialDelayMs = 1000) {
                        api.getTramLines()
                    }

                    // Save to cache
                    cache?.saveMetroLines(metroFuniculaire.features)
                    cache?.saveTramLines(trams.features)
                }

                // Load Navigone (from cache or API with retry)
                navigoneFeatures = cachedNavigone ?: run {
                    try {
                        val navigone = withRetry(maxRetries = 2, initialDelayMs = 1000) {
                            api.getNavigoneLines()
                        }
                        cache?.saveNavigoneLines(navigone.features)
                        navigone.features
                    } catch (e: Exception) {
                        android.util.Log.w("TransportRepository", "Failed to load navigone lines: ${e.message}")
                        emptyList()
                    }
                }

                // Load Trambus (from cache or API with retry)
                trambusFeatures = cachedTrambus ?: run {
                    try {
                        val trambus = withRetry(maxRetries = 2, initialDelayMs = 1000) {
                            api.getTrambusLines()
                        }
                        cache?.saveTrambusLines(trambus.features)
                        trambus.features
                    } catch (e: Exception) {
                        android.util.Log.w("TransportRepository", "Failed to load trambus lines: ${e.message}")
                        emptyList()
                    }
                }

                // Fetch Rhônexpress (RX) - small data, always from API
                // Run in parallel with any remaining API calls using async
                val rxFeatures: List<Feature> = try {
                    fetchRhonexpressFromWfs()
                } catch (e: Exception) {
                    android.util.Log.w("TransportRepository", "Failed to load Rhônexpress from WFS: ${e.message}")
                    emptyList()
                }

                // Merge metro/funicular, trams, navigone, trambus and RX (NOT buses)
                val allFeatures = (metroFuniculaire.features + trams.features + navigoneFeatures + trambusFeatures + rxFeatures)

                // Group by code_trace and keep only the first of each group (outbound direction)
                val uniqueLines = allFeatures
                    .groupBy { it.properties.codeTrace }
                    .map { (_, features) -> features.first() }

                val filteredCollection = metroFuniculaire.copy(
                    features = uniqueLines,
                    numberReturned = uniqueLines.size,
                    totalFeatures = (metroFuniculaire.totalFeatures ?: 0) + (trams.totalFeatures ?: 0) + navigoneFeatures.size + trambusFeatures.size,
                    numberMatched = (metroFuniculaire.numberMatched ?: 0) + (trams.numberMatched ?: 0) + navigoneFeatures.size + trambusFeatures.size
                )

                Result.success(filteredCollection)
            } catch (e: Exception) {
                // Fallback to offline repository if available
                val offlineLines = offlineRepo?.loadAllLines()
                if (!offlineLines.isNullOrEmpty()) {
                    val uniqueLines = offlineLines
                        .groupBy { it.properties.codeTrace }
                        .map { (_, features) -> features.first() }
                    android.util.Log.d("TransportRepository", "Using offline data: ${uniqueLines.size} lines")
                    Result.success(FeatureCollection(
                        type = "FeatureCollection",
                        features = uniqueLines,
                        totalFeatures = uniqueLines.size,
                        numberMatched = uniqueLines.size,
                        numberReturned = uniqueLines.size
                    ))
                } else {
                    Result.failure(e)
                }
            }
        }
    }

    /**
     * Data class representing a partial load result
     */
    data class LinesLoadProgress(
        val lines: List<Feature>,
        val isComplete: Boolean,
        val source: String // For debugging: "cache_metro_tram", "navigone", "trambus", "rhonexpress"
    )

    /**
     * Fetches all transport lines progressively using a Flow.
     * Emits lines as they become available (cache first, then API data).
     * This allows the UI to display lines progressively for better UX.
     */
    fun getAllLinesFlow(): Flow<LinesLoadProgress> = flow {
        val loadedCodeTraces = mutableSetOf<String>()
        val allLoadedFeatures = mutableListOf<Feature>()

        fun addUniqueFeatures(features: List<Feature>): List<Feature> {
            val newFeatures = features.filter { feature ->
                val codeTrace = feature.properties.codeTrace
                if (codeTrace !in loadedCodeTraces) {
                    loadedCodeTraces.add(codeTrace)
                    true
                } else {
                    false
                }
            }
            allLoadedFeatures.addAll(newFeatures)
            return allLoadedFeatures.toList()
        }

        // Step 1: Load metro and tram from cache first (fastest)
        val cachedMetro = cache?.getMetroLines()
        val cachedTram = cache?.getTramLines()

        if (cachedMetro != null && cachedTram != null) {
            val cacheFeatures = addUniqueFeatures(cachedMetro + cachedTram)
            emit(LinesLoadProgress(cacheFeatures, isComplete = false, source = "cache_metro_tram"))
        } else {
            // No cache, load from API with retry on transient failures
            try {
                val metroFuniculaire = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    withRetry(maxRetries = 2, initialDelayMs = 1000) { api.getTransportLines() }
                }
                cache?.saveMetroLines(metroFuniculaire.features)

                val trams = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    withRetry(maxRetries = 2, initialDelayMs = 1000) { api.getTramLines() }
                }
                cache?.saveTramLines(trams.features)

                val apiFeatures = addUniqueFeatures(metroFuniculaire.features + trams.features)
                emit(LinesLoadProgress(apiFeatures, isComplete = false, source = "api_metro_tram"))
            } catch (e: Exception) {
                android.util.Log.e("TransportRepository", "Failed to load metro/tram: ${e.message}")
            }
        }

        // Step 2: Load Navigone (from cache or API with retry)
        val cachedNavigone = cache?.getNavigoneLines()
        val navigoneFeatures = if (cachedNavigone != null) {
            cachedNavigone
        } else {
            try {
                val navigone = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    withRetry(maxRetries = 2, initialDelayMs = 1000) { api.getNavigoneLines() }
                }
                cache?.saveNavigoneLines(navigone.features)
                navigone.features
            } catch (e: Exception) {
                android.util.Log.w("TransportRepository", "Failed to load navigone lines: ${e.message}")
                emptyList()
            }
        }
        if (navigoneFeatures.isNotEmpty()) {
            val updatedFeatures = addUniqueFeatures(navigoneFeatures)
            emit(LinesLoadProgress(updatedFeatures, isComplete = false, source = "navigone"))
        }

        // Step 3: Load Trambus (from cache or API with retry)
        val cachedTrambus = cache?.getTrambusLines()
        val trambusFeatures = if (cachedTrambus != null) {
            cachedTrambus
        } else {
            try {
                val trambus = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    withRetry(maxRetries = 2, initialDelayMs = 1000) { api.getTrambusLines() }
                }
                cache?.saveTrambusLines(trambus.features)
                trambus.features
            } catch (e: Exception) {
                android.util.Log.w("TransportRepository", "Failed to load trambus lines: ${e.message}")
                emptyList()
            }
        }
        if (trambusFeatures.isNotEmpty()) {
            val updatedFeatures = addUniqueFeatures(trambusFeatures)
            emit(LinesLoadProgress(updatedFeatures, isComplete = false, source = "trambus"))
        }

        // Step 4: Load Rhônexpress (always from API, small data)
        try {
            val rxFeatures = withContext(kotlinx.coroutines.Dispatchers.IO) {
                fetchRhonexpressFromWfs()
            }
            if (rxFeatures.isNotEmpty()) {
                val updatedFeatures = addUniqueFeatures(rxFeatures)
                emit(LinesLoadProgress(updatedFeatures, isComplete = false, source = "rhonexpress"))
            }
        } catch (e: Exception) {
            android.util.Log.w("TransportRepository", "Failed to load Rhônexpress: ${e.message}")
        }

        // Final emission: mark as complete
        emit(LinesLoadProgress(allLoadedFeatures.toList(), isComplete = true, source = "complete"))
    }

    /**
     * Récupère et mappe la couche Rhônexpress (RX) vers nos modèles internes
     */
    private suspend fun fetchRhonexpressFromWfs(): List<Feature> {
        fun mapJsonToFeatures(json: JsonObject): List<Feature> {
            val featuresArray: JsonArray = json.getAsJsonArray("features") ?: return emptyList()

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

            return result
        }

        val primary = try {
            mapJsonToFeatures(RetrofitInstance.api.getRhonexpressRaw())
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
                RetrofitInstance.api.getRhonexpressRaw(srsName = "EPSG:4171")
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
    suspend fun getLineByName(lineName: String, isOffline: Boolean = false): Result<Feature?> {
        // Fast path: when offline, go directly to caches and offline storage (no network retries)
        if (isOffline) {
            return getLineByNameOffline(lineName)
        }

        return try {
            // Try to load from cache
            val cachedMetro = cache?.getMetroLines()
            val cachedTram = cache?.getTramLines()

            val priorityFeatures: List<Feature>

            if (cachedMetro != null && cachedTram != null) {
                // Cache hit: use cached data
                priorityFeatures = cachedMetro + cachedTram
            } else {
                // Cache miss: load from API
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
            val line = (priorityFeatures + navigoneFeatures).firstOrNull {
                it.properties.ligne.equals(
                    lineName,
                    ignoreCase = true
                )
            }

            // If found, return it
            if (line != null) {
                return Result.success(line)
            }

            // Otherwise, search in buses using CQL filter (downloads only the requested line)
            val cachedBus = cache?.getBusLines()
            val busLine = if (cachedBus != null) {
                // Cache hit for buses
                cachedBus.firstOrNull { it.properties.ligne.equals(lineName, ignoreCase = true) }
            } else {
                // Use CQL filter to fetch only the requested line from the API
                // This avoids downloading all 10,000 bus features
                // Retry on transient network failures (up to 2 retries)
                try {
                    val cqlFilter = "ligne='$lineName'"
                    val bus = withRetry(maxRetries = 2, initialDelayMs = 1000) {
                        api.getBusLineByName(cqlFilter = cqlFilter)
                    }
                    bus.features.firstOrNull {
                        it.properties.ligne.equals(lineName, ignoreCase = true)
                    }
                } catch (e: Exception) {
                    android.util.Log.w("TransportRepository", "CQL filter search failed for $lineName: ${e.message}")
                    // Fallback to offline per-line file (avoids OOM from loading all 10k bus features)
                    offlineRepo?.loadBusLineByName(lineName)?.firstOrNull {
                        it.properties.ligne.equals(lineName, ignoreCase = true)
                    }
                }
            }

            Result.success(busLine)
        } catch (e: Exception) {
            // Fallback to offline repository (per-line file for bus, or non-bus lines)
            getLineByNameOffline(lineName)
        }
    }

    /**
     * Loads a line exclusively from local caches and offline storage (no network calls).
     * Used when the device is known to be offline to avoid retry delays.
     */
    private suspend fun getLineByNameOffline(lineName: String): Result<Feature?> {
        return try {
            // Check stale TransportCache first (metro/tram/navigone may still be in memory/disk)
            val cachedMetro = cache?.getMetroLinesStale()
            val cachedTram = cache?.getTramLinesStale()
            val cachedNavigone = cache?.getNavigoneLinesStale()
            val cachedTrambus = cache?.getTrambusLinesStale()

            val cachedLine = listOfNotNull(cachedMetro, cachedTram, cachedNavigone, cachedTrambus)
                .flatten()
                .firstOrNull { it.properties.ligne.equals(lineName, ignoreCase = true) }
            if (cachedLine != null) {
                return Result.success(cachedLine)
            }

            // Try bus from offline per-line file
            val offlineBus = offlineRepo?.loadBusLineByName(lineName)
            android.util.Log.d("TransportRepository", "Offline bus for $lineName: ${offlineBus?.size ?: "null"} features")
            val offlineBusLine = offlineBus?.firstOrNull {
                it.properties.ligne.equals(lineName, ignoreCase = true)
            }
            if (offlineBusLine != null) {
                android.util.Log.d("TransportRepository", "Offline bus $lineName: geom type=${offlineBusLine.geometry.type}, coords=${offlineBusLine.geometry.coordinates.size} segments")
                return Result.success(offlineBusLine)
            }

            // Try non-bus offline lines (trambus/rx/etc.)
            val allOffline = offlineRepo?.loadAllLines()
            val offlineAny = allOffline?.firstOrNull {
                it.properties.ligne.equals(lineName, ignoreCase = true)
            }
            android.util.Log.d("TransportRepository", "Offline allLines for $lineName: ${if (offlineAny != null) "found" else "NOT found"}")
            Result.success(offlineAny)
        } catch (e: Exception) {
            android.util.Log.e("TransportRepository", "Offline getLineByName failed for $lineName", e)
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
        return withContext(kotlinx.coroutines.Dispatchers.Default) {
            try {
                // Try to load from cache
                val cachedStops = cache?.getStops()

                val response: StopCollection

                if (cachedStops != null) {
                    // Cache hit: use cached data
                    response = StopCollection(
                        type = "FeatureCollection",
                        features = cachedStops,
                        totalFeatures = cachedStops.size,
                        numberMatched = cachedStops.size,
                        numberReturned = cachedStops.size
                    )
                } else {
                    // Cache miss: load from API with retry on transient failures
                    response = withRetry(maxRetries = 2, initialDelayMs = 1000) {
                        api.getTransportStops()
                    }
                }

                // No need to load navigone stops separately - they are in the main API with code NAVI1
                // (which will be normalized to NAV1 in BusIconHelper)

                val allStopsFeatures = response.features

                // Intelligently filter tram, trambus, and rhonexpress stops:
                // Group by name and line, then keep :A priority, otherwise :R
                val lineRegex = Regex(".*\\b(T\\d+|TB\\d+|RX):[AR]\\b.*")
                val stopsToDedup = allStopsFeatures.filter { stop ->
                    stop.properties.desserte.matches(lineRegex)
                }
                val otherStops = allStopsFeatures.filterNot { stop ->
                    stop.properties.desserte.matches(lineRegex)
                }

                val lineNameRegex = Regex("\\b(T\\d+|TB\\d+|RX):[AR]\\b")
                val groupedStops = stopsToDedup.groupBy { stop ->
                    val match = lineNameRegex.find(stop.properties.desserte)
                    // Group by stop name and the matched line name (e.g., T1, TB11, RX)
                    "${stop.properties.nom}_${match?.groupValues?.get(1)}"
                }

                val dedupedStops = groupedStops.values.map { stops ->
                    stops.firstOrNull { it.properties.desserte.contains(":A") }
                        ?: stops.first()
                }

                // Combine deduplicated stops with other stops
                val stopsWithoutDuplicates = dedupedStops + otherStops

                // Filter metro and funicular stops to avoid duplicates by platform
                // For transfers, we merge all services into one
                val filteredStops = stopsWithoutDuplicates.groupBy { stop ->
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
                // Fallback to offline repository if available
                val offlineStops = offlineRepo?.loadStops()
                if (!offlineStops.isNullOrEmpty()) {
                    android.util.Log.d("TransportRepository", "Using offline stops: ${offlineStops.size}")
                    Result.success(StopCollection(
                        type = "FeatureCollection",
                        features = offlineStops,
                        totalFeatures = offlineStops.size,
                        numberMatched = offlineStops.size,
                        numberReturned = offlineStops.size
                    ))
                } else {
                    Result.failure(e)
                }
            }
        }
    }

    /**
     * Returns cached lines immediately (even if stale/expired).
     * Use this for instant UI display, then call getAllLines() to refresh.
     * Returns null if no cache exists at all.
     */
    suspend fun getAllLinesStale(): Result<FeatureCollection>? {
        return withContext(kotlinx.coroutines.Dispatchers.Default) {
            try {
                // Try to load stale cache data (even if expired)
                val cachedMetro = cache?.getMetroLinesStale()
                val cachedTram = cache?.getTramLinesStale()

                // If no cache at all, try offline repository
                if (cachedMetro == null || cachedTram == null) {
                    val offlineLines = offlineRepo?.loadAllLines()
                    if (!offlineLines.isNullOrEmpty()) {
                        val uniqueLines = offlineLines
                            .groupBy { it.properties.codeTrace }
                            .map { (_, features) -> features.first() }
                        return@withContext Result.success(FeatureCollection(
                            type = "FeatureCollection",
                            features = uniqueLines,
                            totalFeatures = uniqueLines.size,
                            numberMatched = uniqueLines.size,
                            numberReturned = uniqueLines.size
                        ))
                    }
                    return@withContext null
                }

                val cachedNavigone = cache.getNavigoneLinesStale() ?: emptyList()
                val cachedTrambus = cache.getTrambusLinesStale() ?: emptyList()

                // Build collection from stale cache
                val allFeatures = cachedMetro + cachedTram + cachedNavigone + cachedTrambus

                // Group by code_trace and keep only the first of each group
                val uniqueLines = allFeatures
                    .groupBy { it.properties.codeTrace }
                    .map { (_, features) -> features.first() }

                val collection = FeatureCollection(
                    type = "FeatureCollection",
                    features = uniqueLines,
                    totalFeatures = uniqueLines.size,
                    numberMatched = uniqueLines.size,
                    numberReturned = uniqueLines.size
                )

                Result.success(collection)
            } catch (e: Exception) {
                android.util.Log.w("TransportRepository", "getAllLinesStale failed: ${e.message}")
                null
            }
        }
    }

    /**
     * Check if cache needs refresh (has data but expired).
     */
    fun needsCacheRefresh(): Boolean {
        return cache?.needsRefresh() ?: false
    }

    /**
     * Check if any cached data is available.
     */
    fun hasAnyCachedData(): Boolean {
        return cache?.hasAnyCachedData() ?: false
    }
}
