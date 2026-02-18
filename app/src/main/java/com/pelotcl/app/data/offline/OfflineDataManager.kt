package com.pelotcl.app.data.offline

import android.content.Context
import android.util.Log
import com.pelotcl.app.data.api.RetrofitInstance
import com.pelotcl.app.data.model.Feature
import com.pelotcl.app.data.model.Geometry
import com.pelotcl.app.data.model.TransportLineProperties
import com.pelotcl.app.data.repository.MapStyle
import com.pelotcl.app.utils.withRetry
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
    private val schedulesRepository = com.pelotcl.app.data.gtfs.SchedulesRepository.getInstance(context)

    private val _downloadState = MutableStateFlow<OfflineDownloadState>(OfflineDownloadState.Idle)
    val downloadState: StateFlow<OfflineDownloadState> = _downloadState.asStateFlow()

    private val _offlineDataInfo = MutableStateFlow(offlineRepository.getOfflineDataInfo())
    val offlineDataInfo: StateFlow<OfflineDataInfo> = _offlineDataInfo.asStateFlow()

    fun refreshInfo() {
        _offlineDataInfo.value = offlineRepository.getOfflineDataInfo()
    }

    fun isOfflineDataAvailable(): Boolean = offlineRepository.isOfflineDataAvailable()

    /**
     * Cancels an ongoing download.
     * Already-saved data is preserved (partial data is still useful offline).
     * The coroutine Job must also be cancelled externally by TransportViewModel.
     */
    fun cancelDownload() {
        offlineMapManager.cancelDownload()
        _downloadState.value = OfflineDownloadState.Idle
        _offlineDataInfo.value = offlineRepository.getOfflineDataInfo()
    }

    /**
     * Downloads all offline data sequentially.
     * Each step updates the progress flow.
     */
    suspend fun downloadAllOfflineData() {
        if (_downloadState.value is OfflineDownloadState.Downloading) return

        withContext(Dispatchers.IO) {
            try {
                var cumulativeProgress = 0f
                val dataWeight = WEIGHT_METRO_TRAM + WEIGHT_NAVIGONE_TRAMBUS + WEIGHT_RX + WEIGHT_STOPS + WEIGHT_ALERTS

                // ============================================================
                // BATCH 1: Parallel API calls for all non-bus data
                // All calls are independent (different API endpoints, different files).
                // ============================================================
                _downloadState.value = OfflineDownloadState.Downloading(0f, "Téléchargement des données...")

                // Launch all API calls in parallel. Non-critical calls have try/catch
                // inside async so they return null on failure instead of cancelling others.
                data class BatchResults(
                    val metroFeatures: List<Feature>?,
                    val tramFeatures: List<Feature>?,
                    val navigoneFeatures: List<Feature>?,
                    val trambusFeatures: List<Feature>?,
                    val rxFeatures: List<Feature>?,
                    val stopsFeatures: List<com.pelotcl.app.data.model.StopFeature>?,
                    val alertsResponse: com.pelotcl.app.data.model.TrafficAlertsResponse?
                )

                val batchResults: BatchResults
                try {
                    batchResults = coroutineScope {
                        val metroDeferred = async {
                            withRetry(maxRetries = 2, initialDelayMs = 1000) { api.getTransportLines() }
                        }
                        val tramDeferred = async {
                            withRetry(maxRetries = 2, initialDelayMs = 1000) { api.getTramLines() }
                        }
                        val navigoneDeferred = async {
                            try { withRetry(maxRetries = 2, initialDelayMs = 1000) { api.getNavigoneLines() } }
                            catch (e: Exception) { Log.w(TAG, "Failed to download navigone (non-critical)", e); null }
                        }
                        val trambusDeferred = async {
                            try { withRetry(maxRetries = 2, initialDelayMs = 1000) { api.getTrambusLines() } }
                            catch (e: Exception) { Log.e(TAG, "Failed to download trambus (non-critical)", e); null }
                        }
                        val rxDeferred = async {
                            try { fetchRhonexpressFeatures() }
                            catch (e: Exception) { Log.w(TAG, "Failed to download RX (non-critical)", e); null }
                        }
                        val stopsDeferred = async {
                            withRetry(maxRetries = 2, initialDelayMs = 1000) { api.getTransportStops() }
                        }
                        val alertsDeferred = async {
                            try { withRetry(maxRetries = 2, initialDelayMs = 500) { api.getTrafficAlerts() } }
                            catch (e: Exception) { Log.w(TAG, "Failed to download alerts (non-critical)", e); null }
                        }

                        // Await all — critical calls (metro, tram, stops) will throw on failure
                        val metro = metroDeferred.await()
                        val tram = tramDeferred.await()
                        val navigone = navigoneDeferred.await()
                        val trambus = trambusDeferred.await()
                        val rx = rxDeferred.await()
                        val stops = stopsDeferred.await()
                        val alerts = alertsDeferred.await()

                        BatchResults(
                            metroFeatures = metro.features,
                            tramFeatures = tram.features,
                            navigoneFeatures = navigone?.features,
                            trambusFeatures = trambus?.features,
                            rxFeatures = rx,
                            stopsFeatures = stops.features,
                            alertsResponse = alerts
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to download critical data (metro/tram/stops)", e)
                    _downloadState.value = OfflineDownloadState.Error("Échec du téléchargement: ${e.message}")
                    return@withContext
                }

                // Save all results — each writes a different file, no conflicts
                _downloadState.value = OfflineDownloadState.Downloading(0.02f, "Sauvegarde des données...")

                offlineRepository.saveMetroLines(batchResults.metroFeatures ?: emptyList())
                offlineRepository.saveTramLines(batchResults.tramFeatures ?: emptyList())

                batchResults.navigoneFeatures?.let { features ->
                    offlineRepository.saveNavigoneLines(features)
                    Log.d(TAG, "Navigone: saved ${features.size} features")
                }

                batchResults.trambusFeatures?.let { features ->
                    Log.d(TAG, "Trambus API returned ${features.size} features: ${features.map { it.properties.ligne }.distinct()}")
                    if (features.isNotEmpty()) {
                        offlineRepository.saveTrambusLines(features)
                        val verifyLoad = offlineRepository.loadTrambusLines()
                        Log.d(TAG, "Trambus: verify after save = ${verifyLoad?.size ?: "NULL (write failed!)"} features")
                    }
                }

                batchResults.rxFeatures?.let { features ->
                    if (features.isNotEmpty()) offlineRepository.saveRxLines(features)
                }

                offlineRepository.saveStops(batchResults.stopsFeatures ?: emptyList())

                batchResults.alertsResponse?.let { response ->
                    if (response.success && response.alerts.isNotEmpty()) {
                        offlineRepository.saveTrafficAlerts(response.alerts)
                    }
                }

                cumulativeProgress = dataWeight
                Log.d(TAG, "Batch 1 complete: all non-bus data downloaded and saved")

                // ============================================================
                // BATCH 2: Bus lines — sequential pages (OOM protection)
                // ============================================================
                _downloadState.value = OfflineDownloadState.Downloading(cumulativeProgress, "Lignes de bus...")
                try {
                    offlineRepository.clearBusLines()
                    val busLikeNames = schedulesRepository.getAllBusLikeRouteNames()
                    val busNameBySafe = busLikeNames.associateBy {
                        it.uppercase().replace(Regex("[^A-Za-z0-9_-]"), "_")
                    }
                    val pageSize = 500
                    var startIndex = 0
                    var totalDownloaded = 0
                    var hasMore = true
                    val rescuedTrambus = mutableListOf<Feature>()

                    Log.d(TAG, "Starting paginated bus download (pageSize=$pageSize)")

                    while (hasMore) {
                        System.gc()
                        Log.d(TAG, "Fetching bus page: startIndex=$startIndex, count=$pageSize")
                        val page = withRetry(maxRetries = 2, initialDelayMs = 1000) {
                            api.getBusLines(startIndex = startIndex, count = pageSize)
                        }
                        val features = page.features
                        Log.d(TAG, "Bus page response: ${features.size} features, totalFeatures=${page.totalFeatures}, numberMatched=${page.numberMatched}, numberReturned=${page.numberReturned}")

                        if (features.isNotEmpty()) {
                            val trambusInPage = features.filter { it.properties.ligne.uppercase().startsWith("TB") }
                            val busFeatures = features.filter { !it.properties.ligne.uppercase().startsWith("TB") }

                            if (trambusInPage.isNotEmpty()) {
                                rescuedTrambus.addAll(trambusInPage)
                                Log.d(TAG, "Rescued ${trambusInPage.size} trambus features from bus page")
                            }

                            if (busFeatures.isNotEmpty()) {
                                offlineRepository.saveBusLinesPage(busFeatures)
                                totalDownloaded += busFeatures.size
                            }

                            startIndex += features.size
                            Log.d(TAG, "Bus page saved: ${busFeatures.size} bus features (total: $totalDownloaded), trambus rescued: ${rescuedTrambus.size}")
                            _downloadState.value = OfflineDownloadState.Downloading(
                                cumulativeProgress + WEIGHT_BUS * (totalDownloaded.toFloat() / 10000f).coerceAtMost(0.95f),
                                "Lignes de bus ($totalDownloaded)..."
                            )
                        } else {
                            startIndex += pageSize
                        }
                        hasMore = features.size >= pageSize
                        Log.d(TAG, "hasMore=$hasMore (features.size=${features.size} >= pageSize=$pageSize)")
                    }

                    // Save rescued trambus if batch 1 failed to download them
                    if (rescuedTrambus.isNotEmpty()) {
                        val existingTrambus = offlineRepository.loadTrambusLines()
                        if (existingTrambus.isNullOrEmpty()) {
                            offlineRepository.saveTrambusLines(rescuedTrambus)
                            Log.d(TAG, "Saved ${rescuedTrambus.size} rescued trambus features (batch 1 had failed)")
                        } else {
                            Log.d(TAG, "Trambus already saved from batch 1 (${existingTrambus.size} features), skipping rescued ones")
                        }
                    }

                    var busFiles = offlineRepository.getAvailableBusLineNames()
                    Log.d(TAG, "Bus download complete: $totalDownloaded features total, ${busFiles.size} line files on disk: ${busFiles.take(10)}")

                    // Fallback: fetch missing lines in parallel batches of 5
                    if (busNameBySafe.isNotEmpty()) {
                        val busFilesSafe = busFiles.map { it.uppercase() }.toSet()
                        val missingSafe = busNameBySafe.keys - busFilesSafe
                        if (missingSafe.isNotEmpty()) {
                            Log.w(TAG, "Bulk bus download missing ${missingSafe.size} lines, starting per-line fallback (batched)")
                            var done = 0
                            val total = missingSafe.size
                            val missingList = missingSafe.toList()

                            for (batch in missingList.chunked(5)) {
                                coroutineScope {
                                    batch.map { safeName ->
                                        async {
                                            val lineName = busNameBySafe[safeName] ?: return@async
                                            try {
                                                val cqlFilter = "ligne='${lineName.replace("'", "''")}'"
                                                val page = withRetry(maxRetries = 2, initialDelayMs = 1000) {
                                                    api.getBusLineByName(cqlFilter = cqlFilter, count = 200)
                                                }
                                                if (page.features.isNotEmpty()) {
                                                    offlineRepository.saveBusLinesPage(page.features)
                                                }
                                            } catch (e: Exception) {
                                                Log.w(TAG, "Fallback bus fetch failed for $lineName: ${e.message}")
                                            }
                                        }
                                    }.awaitAll()
                                }
                                done += batch.size
                                _downloadState.value = OfflineDownloadState.Downloading(
                                    cumulativeProgress + WEIGHT_BUS * (done.toFloat() / total).coerceAtMost(0.95f),
                                    "Lignes de bus ($done/$total)..."
                                )
                            }
                            busFiles = offlineRepository.getAvailableBusLineNames()
                            Log.d(TAG, "Bus fallback complete: ${busFiles.size} line files on disk")
                        }
                    }
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

                // Mark data download as complete
                offlineRepository.markDownloadComplete()
                // Log offline info right after marking complete (before map tiles)
                val infoAfterData = offlineRepository.getOfflineDataInfo()
                Log.d(TAG, "After data download: busLinesCount=${infoAfterData.busLinesCount}, totalSize=${infoAfterData.totalSizeBytes}, isAvailable=${infoAfterData.isAvailable}")
                _offlineDataInfo.value = infoAfterData

                // Step 7: Map tiles for each selected style
                val selectedStyleKeys = offlineRepository.getSelectedMapStyles()
                val stylesToDownload = selectedStyleKeys.mapNotNull { key ->
                    MapStyle.entries.find { it.key == key }
                }.filter { it.styleUrl.startsWith("http") } // Exclude asset:// styles (e.g. Satellite)

                val completedStyles = mutableSetOf<String>()
                val weightPerStyle = if (stylesToDownload.isNotEmpty()) WEIGHT_MAP_TILES / stylesToDownload.size else WEIGHT_MAP_TILES

                for ((styleIndex, style) in stylesToDownload.withIndex()) {
                    val regionName = OfflineMapManager.regionNameForStyle(style.key)
                    val styleBaseProgress = cumulativeProgress + (styleIndex * weightPerStyle)
                    _downloadState.value = OfflineDownloadState.Downloading(
                        styleBaseProgress,
                        "Tuiles ${style.displayName} (${styleIndex + 1}/${stylesToDownload.size})..."
                    )

                    // Reset to Idle before starting so first{} doesn't see stale Complete
                    offlineMapManager.resetState()
                    offlineMapManager.startDownload(style.styleUrl, regionName)

                    // Monitor progress in a child coroutine, wait for terminal state
                    coroutineScope {
                        val progressJob = launch {
                            offlineMapManager.downloadState.collect { mapState ->
                                if (mapState is MapTilesDownloadState.Downloading) {
                                    val totalProgress = styleBaseProgress + (mapState.progress * weightPerStyle)
                                    _downloadState.value = OfflineDownloadState.Downloading(
                                        totalProgress.coerceIn(0f, 1f),
                                        "Tuiles ${style.displayName} (${(mapState.progress * 100).toInt()}%)..."
                                    )
                                }
                            }
                        }

                        // Wait for terminal state (Complete or Error)
                        val terminalState = offlineMapManager.downloadState.first { state ->
                            state is MapTilesDownloadState.Complete || state is MapTilesDownloadState.Error
                        }
                        progressJob.cancel()

                        when (terminalState) {
                            is MapTilesDownloadState.Complete -> {
                                completedStyles.add(style.key)
                                Log.d(TAG, "Map tiles for ${style.key} complete")
                            }
                            is MapTilesDownloadState.Error -> {
                                Log.e(TAG, "Map tiles for ${style.key} failed: ${terminalState.message}")
                            }
                            else -> {}
                        }
                    }
                }

                // Also keep any previously downloaded styles that are still selected
                val previouslyDownloaded = offlineRepository.getDownloadedMapStyles()
                val allDownloaded = completedStyles + previouslyDownloaded.filter { it in selectedStyleKeys }
                offlineRepository.setDownloadedMapStyles(allDownloaded)
                _downloadState.value = OfflineDownloadState.Complete
                _offlineDataInfo.value = offlineRepository.getOfflineDataInfo()

            } catch (e: CancellationException) {
                Log.d(TAG, "Download cancelled by user")
                offlineMapManager.cancelDownload()
                _downloadState.value = OfflineDownloadState.Idle
                _offlineDataInfo.value = offlineRepository.getOfflineDataInfo()
                throw e
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
                offlineRepository.setDownloadedMapStyles(emptySet())
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
