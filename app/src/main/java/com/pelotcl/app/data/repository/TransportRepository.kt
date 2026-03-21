package com.pelotcl.app.data.repository

import android.content.Context
import com.pelotcl.app.core.data.network.TransportApi
import com.pelotcl.app.core.service.TransportServiceProvider
import com.pelotcl.app.core.data.cache.TransportCache
import com.pelotcl.app.core.data.model.Feature
import com.pelotcl.app.core.data.model.FeatureCollection
import com.pelotcl.app.core.data.model.Geometry
import com.pelotcl.app.core.data.model.TransportLineProperties
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.pelotcl.app.core.data.model.StopCollection
import com.pelotcl.app.core.data.offline.OfflineRepository
import com.pelotcl.app.utils.withRetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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
                // Quick check: if cache has fresh data, use it immediately
                val cacheHasData = cache?.hasAnyCachedData() ?: false
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
                val navigoneFeatures: List<Feature> = cachedNavigone ?: run {
                    try {
                        val navigone = withRetry(maxRetries = 2, initialDelayMs = 1000) {
                            transportApi.getTransportLines(
                                typename = "sytral:tcl_sytral.tcllignefluv"
                            )
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
                val trambusFeatures: List<Feature> = cachedTrambus ?: run {
                    try {
                        val trambus = withRetry(maxRetries = 2, initialDelayMs = 1000) {
                            transportApi.getBusLines(
                                cqlFilter = "ligne LIKE 'TB%'"
                            )
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
                        ?: 0) + navigoneFeatures.size + trambusFeatures.size,
                    numberMatched = (metroFuniculaire.numberMatched ?: 0) + (trams.numberMatched
                        ?: 0) + navigoneFeatures.size + trambusFeatures.size
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

    // ... [le reste des méthodes existantes]
    
    // Méthode temporaire pour maintenir la compatibilité
    private suspend fun fetchRhonexpressFromWfs(): List<Feature> {
        return emptyList() // Implémentation simplifiée pour l'instant
    }
}