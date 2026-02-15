package com.pelotcl.app.data.offline

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition

/**
 * State of the map tiles download.
 */
sealed class MapTilesDownloadState {
    data object Idle : MapTilesDownloadState()
    data class Downloading(val progress: Float) : MapTilesDownloadState()
    data object Complete : MapTilesDownloadState()
    data class Error(val message: String) : MapTilesDownloadState()
}

/**
 * Manages offline map tile downloads for the Lyon TCL region using MapLibre OfflineManager.
 */
class OfflineMapManager(private val context: Context) {

    companion object {
        private const val TAG = "OfflineMapManager"
        private const val REGION_NAME = "pelo_lyon_tcl"

        // Lyon metropolitan area bounding box (covers the full TCL network)
        private val LYON_BOUNDS = LatLngBounds.Builder()
            .include(LatLng(45.55, 4.65))  // Southwest
            .include(LatLng(45.95, 5.10))  // Northeast
            .build()

        // Zoom range: 8 (regional overview) to 16 (street-level detail)
        private const val MIN_ZOOM = 8.0
        private const val MAX_ZOOM = 16.0

        // Positron style URL used for offline tiles
        private const val STYLE_URL = "https://tiles.openfreemap.org/styles/positron"

        // Pixel ratio
        private const val PIXEL_RATIO = 1.0f

        @Volatile
        private var INSTANCE: OfflineMapManager? = null

        fun getInstance(context: Context): OfflineMapManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: OfflineMapManager(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }

    private val _downloadState = MutableStateFlow<MapTilesDownloadState>(MapTilesDownloadState.Idle)
    val downloadState: StateFlow<MapTilesDownloadState> = _downloadState.asStateFlow()

    private var currentRegion: OfflineRegion? = null

    /**
     * Starts downloading map tiles for the Lyon region.
     * The style URL must be a remote URL (not asset://), so we use the Positron remote URL.
     */
    fun startDownload() {
        _downloadState.value = MapTilesDownloadState.Downloading(0f)

        val definition = OfflineTilePyramidRegionDefinition(
            STYLE_URL,
            LYON_BOUNDS,
            MIN_ZOOM,
            MAX_ZOOM,
            PIXEL_RATIO
        )

        val metadata = REGION_NAME.toByteArray(Charsets.UTF_8)

        OfflineManager.getInstance(context).createOfflineRegion(
            definition,
            metadata,
            object : OfflineManager.CreateOfflineRegionCallback {
                override fun onCreate(offlineRegion: OfflineRegion) {
                    currentRegion = offlineRegion
                    Log.d(TAG, "Offline region created, starting download")

                    offlineRegion.setObserver(object : OfflineRegion.OfflineRegionObserver {
                        override fun onStatusChanged(status: OfflineRegionStatus) {
                            val completedResources = status.completedResourceCount
                            val requiredResources = status.requiredResourceCount

                            if (requiredResources > 0) {
                                val progress = completedResources.toFloat() / requiredResources.toFloat()
                                _downloadState.value = MapTilesDownloadState.Downloading(progress.coerceIn(0f, 1f))
                            }

                            if (status.isComplete) {
                                Log.d(TAG, "Offline region download complete: $completedResources resources")
                                _downloadState.value = MapTilesDownloadState.Complete
                            }
                        }

                        override fun onError(error: OfflineRegionError) {
                            Log.e(TAG, "Offline region error: ${error.reason} - ${error.message}")
                            _downloadState.value = MapTilesDownloadState.Error(
                                error.message
                            )
                        }

                        override fun mapboxTileCountLimitExceeded(limit: Long) {
                            Log.w(TAG, "Tile count limit exceeded: $limit")
                            _downloadState.value = MapTilesDownloadState.Error(
                                "Limite de tuiles atteinte ($limit)"
                            )
                        }
                    })

                    offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
                }

                override fun onError(error: String) {
                    Log.e(TAG, "Error creating offline region: $error")
                    _downloadState.value = MapTilesDownloadState.Error(error)
                }
            }
        )
    }

    /**
     * Cancels the current download.
     */
    fun cancelDownload() {
        currentRegion?.setDownloadState(OfflineRegion.STATE_INACTIVE)
        _downloadState.value = MapTilesDownloadState.Idle
    }

    /**
     * Deletes all offline map regions for Pelo.
     */
    fun deleteOfflineRegions(onComplete: () -> Unit = {}) {
        OfflineManager.getInstance(context).listOfflineRegions(
            object : OfflineManager.ListOfflineRegionsCallback {
                override fun onList(offlineRegions: Array<OfflineRegion>?) {
                    if (offlineRegions.isNullOrEmpty()) {
                        onComplete()
                        return
                    }

                    var remaining = offlineRegions.size
                    for (region in offlineRegions) {
                        val regionName = try {
                            region.metadata.toString(Charsets.UTF_8)
                        } catch (e: Exception) { null }

                        if (regionName == REGION_NAME) {
                            region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                                override fun onDelete() {
                                    Log.d(TAG, "Offline region deleted")
                                    remaining--
                                    if (remaining <= 0) onComplete()
                                }

                                override fun onError(error: String) {
                                    Log.e(TAG, "Error deleting offline region: $error")
                                    remaining--
                                    if (remaining <= 0) onComplete()
                                }
                            })
                        } else {
                            remaining--
                            if (remaining <= 0) onComplete()
                        }
                    }
                }

                override fun onError(error: String) {
                    Log.e(TAG, "Error listing offline regions: $error")
                    onComplete()
                }
            }
        )
    }

    /**
     * Checks if an offline region already exists.
     */
    fun checkExistingRegion(callback: (Boolean) -> Unit) {
        OfflineManager.getInstance(context).listOfflineRegions(
            object : OfflineManager.ListOfflineRegionsCallback {
                override fun onList(offlineRegions: Array<OfflineRegion>?) {
                    val exists = offlineRegions?.any { region ->
                        try {
                            region.metadata.toString(Charsets.UTF_8) == REGION_NAME
                        } catch (e: Exception) { false }
                    } ?: false
                    callback(exists)
                }

                override fun onError(error: String) {
                    Log.e(TAG, "Error checking offline regions: $error")
                    callback(false)
                }
            }
        )
    }
}
