package com.pelotcl.app.data.offline

import android.content.Context
import android.util.Log
import com.pelotcl.app.data.model.Feature
import com.pelotcl.app.data.model.StopFeature
import com.pelotcl.app.data.model.TrafficAlert
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Metadata about the offline data download.
 */
data class OfflineDataInfo(
    val isAvailable: Boolean = false,
    val lastDownloadTimestamp: Long = 0L,
    val totalSizeBytes: Long = 0L,
    val mapTilesDownloaded: Boolean = false
)

/**
 * Dedicated persistent storage for offline data.
 * Uses filesDir (not cacheDir) so data is NOT purged by the OS.
 */
class OfflineRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val offlineDir = File(context.filesDir, "offline_data").also { it.mkdirs() }
    private val prefs = context.getSharedPreferences("offline_data_meta", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "OfflineRepository"

        // File names
        private const val FILE_METRO_LINES = "metro_lines.json.gz"
        private const val FILE_TRAM_LINES = "tram_lines.json.gz"
        private const val FILE_BUS_LINES = "bus_lines.json.gz"
        private const val FILE_NAVIGONE_LINES = "navigone_lines.json.gz"
        private const val FILE_TRAMBUS_LINES = "trambus_lines.json.gz"
        private const val FILE_RX_LINES = "rx_lines.json.gz"
        private const val FILE_STOPS = "stops.json.gz"
        private const val FILE_TRAFFIC_ALERTS = "traffic_alerts.json.gz"

        // Prefs keys
        private const val KEY_LAST_DOWNLOAD = "last_download_timestamp"
        private const val KEY_MAP_TILES_DOWNLOADED = "map_tiles_downloaded"
        private const val KEY_DATA_VERSION = "offline_data_version"
        private const val DATA_VERSION = 1

        @Volatile
        private var INSTANCE: OfflineRepository? = null

        fun getInstance(context: Context): OfflineRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: OfflineRepository(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }

    // ===== SAVE METHODS =====

    suspend fun saveMetroLines(lines: List<Feature>) =
        writeCompressed(FILE_METRO_LINES, lines)

    suspend fun saveTramLines(lines: List<Feature>) =
        writeCompressed(FILE_TRAM_LINES, lines)

    suspend fun saveBusLines(lines: List<Feature>) =
        writeCompressed(FILE_BUS_LINES, lines)

    suspend fun saveNavigoneLines(lines: List<Feature>) =
        writeCompressed(FILE_NAVIGONE_LINES, lines)

    suspend fun saveTrambusLines(lines: List<Feature>) =
        writeCompressed(FILE_TRAMBUS_LINES, lines)

    suspend fun saveRxLines(lines: List<Feature>) =
        writeCompressed(FILE_RX_LINES, lines)

    suspend fun saveStops(stops: List<StopFeature>) =
        writeCompressed(FILE_STOPS, stops)

    suspend fun saveTrafficAlerts(alerts: List<TrafficAlert>) =
        writeCompressed(FILE_TRAFFIC_ALERTS, alerts)

    // ===== LOAD METHODS =====

    suspend fun loadMetroLines(): List<Feature>? =
        readCompressed(FILE_METRO_LINES)

    suspend fun loadTramLines(): List<Feature>? =
        readCompressed(FILE_TRAM_LINES)

    suspend fun loadBusLines(): List<Feature>? =
        readCompressed(FILE_BUS_LINES)

    suspend fun loadNavigoneLines(): List<Feature>? =
        readCompressed(FILE_NAVIGONE_LINES)

    suspend fun loadTrambusLines(): List<Feature>? =
        readCompressed(FILE_TRAMBUS_LINES)

    suspend fun loadRxLines(): List<Feature>? =
        readCompressed(FILE_RX_LINES)

    suspend fun loadStops(): List<StopFeature>? =
        readCompressed(FILE_STOPS)

    suspend fun loadTrafficAlerts(): List<TrafficAlert>? =
        readCompressed(FILE_TRAFFIC_ALERTS)

    /**
     * Loads ALL offline lines (metro + tram + bus + navigone + trambus + rx) merged.
     */
    suspend fun loadAllLines(): List<Feature> {
        val metro = loadMetroLines() ?: emptyList()
        val tram = loadTramLines() ?: emptyList()
        val bus = loadBusLines() ?: emptyList()
        val navigone = loadNavigoneLines() ?: emptyList()
        val trambus = loadTrambusLines() ?: emptyList()
        val rx = loadRxLines() ?: emptyList()
        return metro + tram + bus + navigone + trambus + rx
    }

    // ===== METADATA =====

    fun markDownloadComplete() {
        prefs.edit()
            .putLong(KEY_LAST_DOWNLOAD, System.currentTimeMillis())
            .putInt(KEY_DATA_VERSION, DATA_VERSION)
            .apply()
    }

    fun setMapTilesDownloaded(downloaded: Boolean) {
        prefs.edit().putBoolean(KEY_MAP_TILES_DOWNLOADED, downloaded).apply()
    }

    fun getOfflineDataInfo(): OfflineDataInfo {
        val lastDownload = prefs.getLong(KEY_LAST_DOWNLOAD, 0L)
        val mapTiles = prefs.getBoolean(KEY_MAP_TILES_DOWNLOADED, false)
        val hasData = lastDownload > 0L && offlineDir.listFiles()?.isNotEmpty() == true

        return OfflineDataInfo(
            isAvailable = hasData,
            lastDownloadTimestamp = lastDownload,
            totalSizeBytes = if (hasData) calculateTotalSize() else 0L,
            mapTilesDownloaded = mapTiles
        )
    }

    fun isOfflineDataAvailable(): Boolean {
        return prefs.getLong(KEY_LAST_DOWNLOAD, 0L) > 0L &&
                offlineDir.listFiles()?.isNotEmpty() == true
    }

    fun getLastDownloadTimestamp(): Long {
        return prefs.getLong(KEY_LAST_DOWNLOAD, 0L)
    }

    /**
     * Deletes all offline data files (not map tiles, which are managed by MapLibre).
     */
    suspend fun deleteOfflineData() = withContext(Dispatchers.IO) {
        offlineDir.listFiles()?.forEach { it.delete() }
        prefs.edit()
            .remove(KEY_LAST_DOWNLOAD)
            .remove(KEY_DATA_VERSION)
            .apply()
    }

    // ===== INTERNAL =====

    private fun calculateTotalSize(): Long {
        return offlineDir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    private suspend inline fun <reified T> writeCompressed(fileName: String, data: T) =
        withContext(Dispatchers.IO) {
            try {
                val file = File(offlineDir, fileName)
                val jsonString = json.encodeToString(data)
                GZIPOutputStream(FileOutputStream(file).buffered()).use { gzip ->
                    gzip.write(jsonString.toByteArray(Charsets.UTF_8))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error writing to $fileName", e)
            }
        }

    private suspend inline fun <reified T> readCompressed(fileName: String): T? =
        withContext(Dispatchers.IO) {
            try {
                val file = File(offlineDir, fileName)
                if (file.exists()) {
                    val jsonString = GZIPInputStream(FileInputStream(file).buffered()).use { gzip ->
                        gzip.bufferedReader(Charsets.UTF_8).readText()
                    }
                    json.decodeFromString<T>(jsonString)
                } else null
            } catch (e: Exception) {
                Log.e(TAG, "Error reading from $fileName", e)
                null
            }
        }
}
