package com.pelotcl.app.data.offline

import android.content.Context
import android.util.Log
import com.pelotcl.app.data.api.RetrofitInstance
import com.pelotcl.app.data.model.Feature
import com.pelotcl.app.data.model.Geometry
import com.pelotcl.app.data.model.TransportLineProperties
import com.pelotcl.app.utils.withRetry
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Overall download state for all offline data.
 */
sealed class OfflineDownloadState {
    data object Idle : OfflineDownloadState()
    data class Downloading(val progress: Float, val stepDescription: String) : OfflineDownloadState()
    data object Complete : OfflineDownloadState()
    data class Error(val message: String) : OfflineDownloadState()
}

/**
 * Orchestrates the download of all offline data:
 * transport lines (including ALL bus lines), stops, traffic alerts, and map tiles.
 */
class OfflineDataManager(private val context: Context) {

    companion object {
        private const val TAG = "OfflineDataManager"

        // Weight of each step in the overall progress (total = 1.0)
        private const val WEIGHT_METRO_TRAM = 0.05f
        private const val WEIGHT_NAVIGONE_TRAMBUS = 0.03f
        private const val WEIGHT_BUS = 0.10f
        private const val WEIGHT_RX = 0.02f
        private const val WEIGHT_STOPS = 0.05f
        private const val WEIGHT_ALERTS = 0.02f
        private const val WEIGHT_MAP_TILES = 0.73f

        @Volatile
        private var INSTANCE: OfflineDataManager? = null

        fun getInstance(context: Context): OfflineDataManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: OfflineDataManager(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }

    private val api = RetrofitInstance.api
    private val offlineRepository = OfflineRepository.getInstance(context)
    private val offlineMapManager = OfflineMapManager.getInstance(context)

    private val _downloadState = MutableStateFlow<OfflineDownloadState>(OfflineDownloadState.Idle)
    val downloadState: StateFlow<OfflineDownloadState> = _downloadState.asStateFlow()

    private val _offlineDataInfo = MutableStateFlow(offlineRepository.getOfflineDataInfo())
    val offlineDataInfo: StateFlow<OfflineDataInfo> = _offlineDataInfo.asStateFlow()

    fun refreshInfo() {
        _offlineDataInfo.value = offlineRepository.getOfflineDataInfo()
    }

    fun isOfflineDataAvailable(): Boolean = offlineRepository.isOfflineDataAvailable()

    /**
     * Downloads all offline data sequentially.
     * Each step updates the progress flow.
     */
    suspend fun downloadAllOfflineData() {
        if (_downloadState.value is OfflineDownloadState.Downloading) return

        withContext(Dispatchers.IO) {
            try {
                var cumulativeProgress = 0f

                // Step 1: Metro + Tram lines
                _downloadState.value = OfflineDownloadState.Downloading(cumulativeProgress, "Lignes de métro et tram...")
                try {
                    val metro = withRetry(maxRetries = 2, initialDelayMs = 1000) {
                        api.getTransportLines()
                    }
                    offlineRepository.saveMetroLines(metro.features)

                    val tram = withRetry(maxRetries = 2, initialDelayMs = 1000) {
                        api.getTramLines()
                    }
                    offlineRepository.saveTramLines(tram.features)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to download metro/tram lines", e)
                    _downloadState.value = OfflineDownloadState.Error("Échec du téléchargement des lignes de métro/tram: ${e.message}")
                    return@withContext
                }
                cumulativeProgress += WEIGHT_METRO_TRAM

                // Step 2: Navigone + Trambus
                _downloadState.value = OfflineDownloadState.Downloading(cumulativeProgress, "Lignes Navigone et Trambus...")
                try {
                    val navigone = withRetry(maxRetries = 2, initialDelayMs = 1000) {
                        api.getNavigoneLines()
                    }
                    offlineRepository.saveNavigoneLines(navigone.features)

                    val trambus = withRetry(maxRetries = 2, initialDelayMs = 1000) {
                        api.getTrambusLines()
                    }
                    offlineRepository.saveTrambusLines(trambus.features)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to download navigone/trambus lines (non-critical)", e)
                    // Non-critical, continue
                }
                cumulativeProgress += WEIGHT_NAVIGONE_TRAMBUS

                // Step 3: ALL bus lines (the big one)
                _downloadState.value = OfflineDownloadState.Downloading(cumulativeProgress, "Toutes les lignes de bus...")
                try {
                    val bus = withRetry(maxRetries = 2, initialDelayMs = 2000) {
                        api.getBusLines()
                    }
                    offlineRepository.saveBusLines(bus.features)
                } catch (e: OutOfMemoryError) {
                    Log.e(TAG, "OutOfMemoryError downloading bus lines", e)
                    _downloadState.value = OfflineDownloadState.Error("Mémoire insuffisante pour télécharger les lignes de bus")
                    return@withContext
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to download bus lines", e)
                    _downloadState.value = OfflineDownloadState.Error("Échec du téléchargement des lignes de bus: ${e.message}")
                    return@withContext
                }
                cumulativeProgress += WEIGHT_BUS

                // Step 4: Rhônexpress
                _downloadState.value = OfflineDownloadState.Downloading(cumulativeProgress, "Ligne Rhônexpress...")
                try {
                    val rxFeatures = fetchRhonexpressFeatures()
                    if (rxFeatures.isNotEmpty()) {
                        offlineRepository.saveRxLines(rxFeatures)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to download RX line (non-critical)", e)
                }
                cumulativeProgress += WEIGHT_RX

                // Step 5: All stops
                _downloadState.value = OfflineDownloadState.Downloading(cumulativeProgress, "Tous les arrêts...")
                try {
                    val stops = withRetry(maxRetries = 2, initialDelayMs = 1000) {
                        api.getTransportStops()
                    }
                    offlineRepository.saveStops(stops.features)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to download stops", e)
                    _downloadState.value = OfflineDownloadState.Error("Échec du téléchargement des arrêts: ${e.message}")
                    return@withContext
                }
                cumulativeProgress += WEIGHT_STOPS

                // Step 6: Traffic alerts
                _downloadState.value = OfflineDownloadState.Downloading(cumulativeProgress, "Alertes trafic...")
                try {
                    val alertsResponse = withRetry(maxRetries = 2, initialDelayMs = 500) {
                        api.getTrafficAlerts()
                    }
                    if (alertsResponse.success && alertsResponse.alerts.isNotEmpty()) {
                        offlineRepository.saveTrafficAlerts(alertsResponse.alerts)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to download traffic alerts (non-critical)", e)
                }
                cumulativeProgress += WEIGHT_ALERTS

                // Mark data download as complete
                offlineRepository.markDownloadComplete()

                // Step 7: Map tiles (the longest step)
                _downloadState.value = OfflineDownloadState.Downloading(cumulativeProgress, "Tuiles de carte (peut prendre plusieurs minutes)...")

                // Start map tile download and monitor progress
                offlineMapManager.startDownload()

                // Monitor map tile download progress
                offlineMapManager.downloadState.collect { mapState ->
                    when (mapState) {
                        is MapTilesDownloadState.Downloading -> {
                            val totalProgress = cumulativeProgress + (mapState.progress * WEIGHT_MAP_TILES)
                            _downloadState.value = OfflineDownloadState.Downloading(
                                totalProgress.coerceIn(0f, 1f),
                                "Tuiles de carte (${(mapState.progress * 100).toInt()}%)..."
                            )
                        }
                        is MapTilesDownloadState.Complete -> {
                            offlineRepository.setMapTilesDownloaded(true)
                            _downloadState.value = OfflineDownloadState.Complete
                            _offlineDataInfo.value = offlineRepository.getOfflineDataInfo()
                            return@collect
                        }
                        is MapTilesDownloadState.Error -> {
                            // Map tiles failed but data is still downloaded
                            Log.e(TAG, "Map tiles download failed: ${mapState.message}")
                            _downloadState.value = OfflineDownloadState.Complete
                            _offlineDataInfo.value = offlineRepository.getOfflineDataInfo()
                            return@collect
                        }
                        is MapTilesDownloadState.Idle -> { /* wait */ }
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error during offline download", e)
                _downloadState.value = OfflineDownloadState.Error("Erreur inattendue: ${e.message}")
            }
        }
    }

    /**
     * Deletes all offline data (files + map tiles).
     */
    suspend fun deleteAllOfflineData() {
        withContext(Dispatchers.IO) {
            offlineRepository.deleteOfflineData()
            offlineMapManager.deleteOfflineRegions {
                offlineRepository.setMapTilesDownloaded(false)
            }
            _downloadState.value = OfflineDownloadState.Idle
            _offlineDataInfo.value = offlineRepository.getOfflineDataInfo()
        }
    }

    /**
     * Fetches Rhônexpress features from WFS API (replicating TransportRepository logic).
     */
    private suspend fun fetchRhonexpressFeatures(): List<Feature> {
        fun mapJsonToFeatures(json: JsonObject): List<Feature> {
            val featuresArray: JsonArray = json.getAsJsonArray("features") ?: return emptyList()
            val result = mutableListOf<Feature>()

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
                        coordinatesElement.asJsonArray.map { lineArr ->
                            lineArr.asJsonArray.map { coord ->
                                val pair = coord.asJsonArray
                                listOf(pair[0].asDouble, pair[1].asDouble)
                            }
                        }
                    }
                    "LineString" -> {
                        listOf(coordinatesElement.asJsonArray.map { coord ->
                            val pair = coord.asJsonArray
                            listOf(pair[0].asDouble, pair[1].asDouble)
                        })
                    }
                    else -> continue
                }

                result.add(Feature(
                    type = "Feature",
                    id = "rx_$id",
                    geometry = Geometry(type = "MultiLineString", coordinates = multiLineCoordinates),
                    geometryName = null,
                    properties = TransportLineProperties(
                        ligne = "RX", codeTrace = "RX-$gid", codeLigne = "RX",
                        typeTrace = "", nomTrace = "Rhônexpress",
                        sens = "ALLER",
                        origine = "Gare Part-Dieu Villette",
                        destination = "Aéroport St Exupéry -RX",
                        nomOrigine = "Gare Part-Dieu Villette",
                        nomDestination = "Aéroport St Exupéry -RX",
                        familleTransport = "TRAM",
                        dateDebut = "", dateFin = null,
                        codeTypeLigne = "TRAM", nomTypeLigne = "Tramway",
                        pmr = true, codeTriLigne = "RX", nomVersion = "",
                        lastUpdate = "", lastUpdateFme = "",
                        gid = gid, couleur = "#E30613"
                    ),
                    bbox = null
                ))
            }
            return result
        }

        return try {
            val primary = mapJsonToFeatures(api.getRhonexpressRaw())
            if (primary.isNotEmpty()) primary
            else mapJsonToFeatures(api.getRhonexpressRaw(srsName = "EPSG:4171"))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch Rhônexpress: ${e.message}")
            emptyList()
        }
    }
}
