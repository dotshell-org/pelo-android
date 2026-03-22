package com.pelotcl.app.generic.data.repository

import android.content.Context
import android.util.Log
import com.google.gson.JsonObject
import com.pelotcl.app.generic.data.network.TransportApi
import com.pelotcl.app.generic.service.TransportServiceProvider
import com.pelotcl.app.generic.data.cache.TransportCache
import com.pelotcl.app.generic.data.model.Feature
import com.pelotcl.app.generic.data.model.FeatureCollection
import com.pelotcl.app.generic.data.model.Geometry
import com.pelotcl.app.generic.data.model.TransportLineProperties
import com.pelotcl.app.generic.data.offline.OfflineRepository
import com.pelotcl.app.utils.withRetry
import java.text.Normalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for managing transport line data
 * Uses TransportServiceProvider for dependency access
 */
class TransportRepository(context: Context? = null) {

    private val transportApi: TransportApi = TransportServiceProvider.getTransportApi()
    private val cache = context?.let { TransportCache(it) }
    private val offlineRepo = context?.let { OfflineRepository(it) }

    /**
     * Fetches all transport lines (metro, funicular, tram, and navigone ONLY)
     * Bus lines are NOT loaded by default to avoid overloading the phone
     * To load a specific bus line, use getLineByName()
     * Uses cache to improve performance and parallel loading for faster startup
     */
    suspend fun getAllLines(): Result<FeatureCollection> {
        return withContext(Dispatchers.IO) {
            try {
                val cacheNeedsRefresh = cache?.needsCacheRefresh() ?: true

                // Try to load all line types from cache first
                val cachedMetro = cache?.getMetroLines()
                val cachedTram = cache?.getTramLines()
                val cachedNavigone = cache?.getNavigoneLines()
                val cachedTrambus = cache?.getTrambusLines()

                val metroFuniculaire: FeatureCollection
                val trams: FeatureCollection

                // Load metro and tram (from cache or API)
                if (cachedMetro != null && cachedTram != null && !cacheNeedsRefresh) {
                    // Cache hit with fresh data: use cached data
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
                    // Cache miss or expired: load from API with retry on transient failures
                    metroFuniculaire = withRetry(maxRetries = 2, initialDelayMs = 1000) {
                        transportApi.getMetroLines()
                    }
                    trams = withRetry(maxRetries = 2, initialDelayMs = 1000) {
                        transportApi.getTramLines()
                    }

                    // Save to cache
                    cache?.saveMetroLines(metroFuniculaire.features)
                    cache?.saveTramLines(trams.features)
                }

                // Load Navigone (from cache or API with retry)
                val navigoneFeatures: List<Feature> =
                    if (cachedNavigone != null && cachedNavigone.isNotEmpty() && !cacheNeedsRefresh) {
                        cachedNavigone
                    } else run {
                    try {
                        val navigone = withRetry(maxRetries = 2, initialDelayMs = 1000) {
                            transportApi.getNavigoneLines()
                        }
                        cache?.saveNavigoneLines(navigone.features)
                        navigone.features
                    } catch (e: Exception) {
                        android.util.Log.w(
                            "TransportRepository",
                            "Failed to load navigone lines: ${e.message}"
                        )
                        emptyList()
                    }
                }

                // Load Trambus (from cache or API with retry)
                val trambusFeatures: List<Feature> =
                    if (cachedTrambus != null && cachedTrambus.isNotEmpty() && !cacheNeedsRefresh) {
                        cachedTrambus
                    } else run {
                    try {
                        val trambus = withRetry(maxRetries = 2, initialDelayMs = 1000) {
                            transportApi.getTrambusLines()
                        }
                        cache?.saveTrambusLines(trambus.features)
                        trambus.features
                    } catch (e: Exception) {
                        android.util.Log.w(
                            "TransportRepository",
                            "Failed to load trambus lines: ${e.message}"
                        )
                        emptyList()
                    }
                }

                // Fetch Rhônexpress (RX) - small data, always from API
                // Run in parallel with any remaining API calls using async
                val rxFeatures: List<Feature> = try {
                    fetchRhonexpressFromWfs()
                } catch (e: Exception) {
                    android.util.Log.w(
                        "TransportRepository",
                        "Failed to load Rhônexpress from WFS: ${e.message}"
                    )
                    emptyList()
                }

                // Merge metro/funicular, trams, navigone, trambus and RX (NOT buses)
                val allFeatures =
                    (metroFuniculaire.features + trams.features + navigoneFeatures + trambusFeatures + rxFeatures)

                // Group by code_trace and keep only the first of each group (outbound direction)
                val uniqueLines = allFeatures
                    .groupBy { it.properties.codeTrace }
                    .map { (_, features) -> features.first() }

                val filteredCollection = metroFuniculaire.copy(
                    features = uniqueLines,
                    numberReturned = uniqueLines.size,
                    totalFeatures = (metroFuniculaire.totalFeatures ?: 0) + (trams.totalFeatures
                        ?: 0) + navigoneFeatures.size + trambusFeatures.size + rxFeatures.size,
                    numberMatched = (metroFuniculaire.numberMatched ?: 0) + (trams.numberMatched
                        ?: 0) + navigoneFeatures.size + trambusFeatures.size + rxFeatures.size
                )

                Result.success(filteredCollection)
            } catch (e: Exception) {
                // Fallback to offline repository if available
                val offlineLines = offlineRepo?.loadAllLines()
                if (!offlineLines.isNullOrEmpty()) {
                    val uniqueLines = offlineLines
                        .groupBy { it.properties.codeTrace }
                        .map { (_, features) -> features.first() }

                    Result.success(
                        FeatureCollection(
                            type = "FeatureCollection",
                            features = uniqueLines
                        )
                    )
                } else {
                    Result.failure(e)
                }
            }
        }
    }

    /**
     * Loads a single line geometry by line name, including non-strong lines.
     * This is used to add weak lines on demand without loading the whole bus dataset.
     */
    suspend fun getLineByName(lineName: String): Result<List<Feature>> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val normalized = Normalizer.normalize(lineName.trim(), Normalizer.Form.NFD)
                    .replace("\\p{Mn}+".toRegex(), "")
                    .uppercase()
                val aliases = when (normalized) {
                    "NAV1", "NAVI1" -> listOf("NAV1", "NAVI1")
                    "RHONEXPRESS", "RHONEXPRES", "RX" -> listOf("RX")
                    else -> listOf(normalized)
                }

                val collected = mutableListOf<Feature>()

                // RX has its own dedicated endpoint.
                if (aliases.size == 1 && aliases.first() == "RX") {
                    return@runCatching fetchRhonexpressFromWfs()
                        .groupBy { it.properties.codeTrace }
                        .map { (_, features) -> features.first() }
                }

                val typeNames = listOf(
                    "sytral:tcl_sytral.tcllignebus_2_0_0",
                    "sytral:tcl_sytral.tcllignemf_2_0_0",
                    "sytral:tcl_sytral.tcllignetram_2_0_0",
                    "sytral:tcl_sytral.tcllignefluv"
                )

                aliases.forEach { alias ->
                    if (alias == "RX") return@forEach
                    val escapedAlias = alias.replace("'", "''")
                    val cqlFilter = "ligne = '$escapedAlias'"
                    typeNames.forEach { typename ->
                        val features = transportApi.getBusLineByName(
                            typename = typename,
                            cqlFilter = cqlFilter
                        ).features
                        collected += features
                    }
                }

                collected
                    .groupBy { it.properties.codeTrace }
                    .map { (_, features) -> features.first() }
            }
        }
    }

    private suspend fun fetchRhonexpressFromWfs(): List<Feature> {
        fun mapJsonToFeatures(json: JsonObject): List<Feature> {
            val featuresArray = json.getAsJsonArray("features") ?: return emptyList()
            val result = mutableListOf<Feature>()

            for (featureElement in featuresArray) {
                val featureObject = featureElement.asJsonObject
                val id = featureObject.get("id")?.asString ?: "rx-${System.nanoTime()}"
                val properties = featureObject.getAsJsonObject("properties") ?: JsonObject()
                val gid = properties.get("gid")?.asInt ?: 0
                val geometryObject = featureObject.getAsJsonObject("geometry") ?: continue
                val geometryType = geometryObject.get("type")?.asString ?: "LineString"
                val coordinatesElement = geometryObject.get("coordinates")

                val coordinates: List<List<List<Double>>> = when (geometryType) {
                    "MultiLineString" -> {
                        coordinatesElement.asJsonArray.map { lineArray ->
                            lineArray.asJsonArray.map { coordinate ->
                                val pair = coordinate.asJsonArray
                                listOf(pair[0].asDouble, pair[1].asDouble)
                            }
                        }
                    }

                    "LineString" -> {
                        listOf(coordinatesElement.asJsonArray.map { coordinate ->
                            val pair = coordinate.asJsonArray
                            listOf(pair[0].asDouble, pair[1].asDouble)
                        })
                    }

                    else -> continue
                }

                result.add(
                    Feature(
                        type = "Feature",
                        id = "rx_$id",
                        geometry = Geometry(
                            type = "MultiLineString",
                            coordinates = coordinates
                        ),
                        geometryName = null,
                        properties = TransportLineProperties(
                            ligne = "RX",
                            codeTrace = "RX-$gid",
                            codeLigne = "RX",
                            typeTrace = "",
                            nomTrace = "Rhônexpress",
                            sens = "ALLER",
                            origine = "Gare Part-Dieu Villette",
                            destination = "Aéroport St Exupéry -RX",
                            nomOrigine = "Gare Part-Dieu Villette",
                            nomDestination = "Aéroport St Exupéry -RX",
                            familleTransport = "TRAM",
                            dateDebut = "",
                            dateFin = null,
                            codeTypeLigne = "TRAM",
                            nomTypeLigne = "Tramway",
                            lastUpdate = "",
                            lastUpdateFme = "",
                            gid = gid,
                            couleur = "#E30613"
                        ),
                        bbox = null
                    )
                )
            }

            return result
        }

        return try {
            mapJsonToFeatures(
                transportApi.getSpecialLineRaw(
                    typename = "sytral:tcl_sytral.tclrhonexpress"
                )
            )
        } catch (e: Exception) {
            Log.w("TransportRepository", "Failed to fetch Rhônexpress: ${e.message}")
            emptyList()
        }
    }
}
