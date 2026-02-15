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
    val mapTilesDownloaded: Boolean = false,
    val busLinesCount: Int = 0
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
    private val busDir = File(offlineDir, "bus").also { it.mkdirs() }
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

    /**
     * Saves bus lines grouped by line name into individual files (bus/C5.json.gz, bus/44.json.gz, etc.)
     * to avoid loading all 10k features at once (OOM risk).
     */
    suspend fun saveBusLines(lines: List<Feature>) = withContext(Dispatchers.IO) {
        // Clean old single-file format if it exists
        File(offlineDir, FILE_BUS_LINES).delete()
        // Clean old per-line files
        busDir.listFiles()?.forEach { it.delete() }
        // Group by line name and save each separately
        val grouped = lines.groupBy { it.properties.ligne.uppercase() }
        for ((lineName, features) in grouped) {
            val safeFileName = lineName.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".json.gz"
            writeCompressedTo(File(busDir, safeFileName), features)
        }
        Log.d(TAG, "Saved ${grouped.size} bus line files (${lines.size} total features)")
    }

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

    /**
     * Loads a single bus line by name from offline storage.
     * Only reads one small file instead of all 10k features.
     * Falls back to legacy single-file format if per-line file not found.
     */
    suspend fun loadBusLineByName(lineName: String): List<Feature>? = withContext(Dispatchers.IO) {
        try {
            // Try per-line file first (new format)
            val safeFileName = lineName.uppercase().replace(Regex("[^A-Za-z0-9_-]"), "_") + ".json.gz"
            val file = File(busDir, safeFileName)
            if (file.exists()) {
                val jsonString = GZIPInputStream(FileInputStream(file).buffered()).use { gzip ->
                    gzip.bufferedReader(Charsets.UTF_8).readText()
                }
                return@withContext json.decodeFromString<List<Feature>>(jsonString)
            }

            // Fallback: migrate legacy single-file format if it exists
            val legacyFile = File(offlineDir, FILE_BUS_LINES)
            if (legacyFile.exists()) {
                Log.d(TAG, "Migrating legacy bus_lines.json.gz to per-line format...")
                try {
                    val allBusJson = GZIPInputStream(FileInputStream(legacyFile).buffered()).use { gzip ->
                        gzip.bufferedReader(Charsets.UTF_8).readText()
                    }
                    val allBus = json.decodeFromString<List<Feature>>(allBusJson)
                    // Save as per-line files
                    val grouped = allBus.groupBy { it.properties.ligne.uppercase() }
                    for ((name, features) in grouped) {
                        val safeName = name.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".json.gz"
                        val perLineFile = File(busDir, safeName)
                        val perLineJson = json.encodeToString(features)
                        GZIPOutputStream(FileOutputStream(perLineFile).buffered()).use { gzip ->
                            gzip.write(perLineJson.toByteArray(Charsets.UTF_8))
                        }
                    }
                    // Delete legacy file after successful migration
                    legacyFile.delete()
                    Log.d(TAG, "Migration complete: ${grouped.size} bus line files created")

                    // Return the requested line
                    return@withContext grouped[lineName.uppercase()]
                } catch (oom: OutOfMemoryError) {
                    Log.e(TAG, "OOM during legacy migration, deleting legacy file", oom)
                    legacyFile.delete()
                    null
                }
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Error reading bus line $lineName", e)
            null
        }
    }

    /**
     * Returns list of all available offline bus line names (without loading the actual data).
     */
    fun getAvailableBusLineNames(): List<String> {
        return busDir.listFiles()
            ?.filter { it.name.endsWith(".json.gz") }
            ?.map { it.name.removeSuffix(".json.gz") }
            ?: emptyList()
    }

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
     * Loads non-bus offline lines (metro + tram + navigone + trambus + rx).
     * Bus lines are NOT included to avoid OOM — use loadBusLineByName() instead.
     */
    suspend fun loadAllLines(): List<Feature> {
        val metro = loadMetroLines() ?: emptyList()
        val tram = loadTramLines() ?: emptyList()
        val navigone = loadNavigoneLines() ?: emptyList()
        val trambus = loadTrambusLines() ?: emptyList()
        val rx = loadRxLines() ?: emptyList()
        return metro + tram + navigone + trambus + rx
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

        val busCount = busDir.listFiles()?.count { it.name.endsWith(".json.gz") } ?: 0

        return OfflineDataInfo(
            isAvailable = hasData,
            lastDownloadTimestamp = lastDownload,
            totalSizeBytes = if (hasData) calculateTotalSize() else 0L,
            mapTilesDownloaded = mapTiles,
            busLinesCount = busCount
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
        busDir.listFiles()?.forEach { it.delete() }
        offlineDir.listFiles()?.forEach {
            if (it.isDirectory) it.deleteRecursively() else it.delete()
        }
        prefs.edit()
            .remove(KEY_LAST_DOWNLOAD)
            .remove(KEY_DATA_VERSION)
            .apply()
    }

    // ===== INTERNAL =====

    private fun calculateTotalSize(): Long {
        val mainSize = offlineDir.listFiles()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L
        val busSize = busDir.listFiles()?.sumOf { it.length() } ?: 0L
        // Include MapLibre offline tiles database (stored by MapLibre in filesDir)
        val mapLibreDb = File(context.filesDir, "mbgl-offline.db")
        val mapTilesSize = if (mapLibreDb.exists()) mapLibreDb.length() else 0L
        return mainSize + busSize + mapTilesSize
    }

    private suspend inline fun <reified T> writeCompressed(fileName: String, data: T) =
        writeCompressedTo(File(offlineDir, fileName), data)

    private suspend inline fun <reified T> writeCompressedTo(file: File, data: T) =
        withContext(Dispatchers.IO) {
            try {
                val jsonString = json.encodeToString(data)
                GZIPOutputStream(FileOutputStream(file).buffered()).use { gzip ->
                    gzip.write(jsonString.toByteArray(Charsets.UTF_8))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error writing to ${file.name}", e)
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
