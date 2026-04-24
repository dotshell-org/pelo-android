package com.pelotcl.app.generic.data.repository.offline.mapstyle

import android.content.Context
import androidx.core.content.edit
import com.pelotcl.app.generic.data.network.mapstyle.MapStyleConfig
import com.pelotcl.app.generic.data.network.mapstyle.MapStyleData

/**
 * Repository for managing map style preferences using SharedPreferences.
 */
class MapStyleRepository(
    private val context: Context,
    private val mapStyleConfig: MapStyleConfig
) {
    private val prefs by lazy {
        context.getSharedPreferences("pelo_map_prefs", Context.MODE_PRIVATE)
    }

    private val keyMapStyle = "selected_map_style"

    /**
     * Get the currently selected map style.
     * Defaults to config's default style if no style is saved.
     */
    fun getSelectedStyle(): MapStyleData {
        val styleKey = prefs.getString(keyMapStyle, mapStyleConfig.getDefaultMapStyle().key)
        return mapStyleConfig.getMapStyleByKey(styleKey ?: mapStyleConfig.getDefaultMapStyle().key)
            ?: mapStyleConfig.getDefaultMapStyle()
    }

    /**
     * Save the selected map style.
     */
    fun saveSelectedStyle(style: MapStyleData) {
        prefs.edit { putString(keyMapStyle, style.key) }
    }

    /**
     * Get the effective style considering offline state.
     * If offline and the selected style is not downloaded, falls back to a downloaded style.
     */
    fun getEffectiveStyle(isOffline: Boolean, downloadedStyles: Set<String>): MapStyleData {
        val selected = getSelectedStyle()
        if (!isOffline) return selected
        if (selected.key in downloadedStyles) return selected
        return downloadedStyles.firstOrNull()?.let { mapStyleConfig.getMapStyleByKey(it) }
            ?: mapStyleConfig.getDefaultMapStyle()
    }

}
