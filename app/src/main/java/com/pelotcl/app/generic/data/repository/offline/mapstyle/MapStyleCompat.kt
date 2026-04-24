package com.pelotcl.app.generic.data.repository.offline.mapstyle

import com.pelotcl.app.generic.data.network.mapstyle.MapStyleCategory
import com.pelotcl.app.generic.data.network.mapstyle.MapStyleData
import com.pelotcl.app.generic.data.network.mapstyle.MapStyleConfig

/**
 * Object containing predefined map styles for compatibility
 */
object MapStyleCompat {
    val POSITRON = MapStyleData(
        key = "positron",
        displayName = "Clair",
        styleUrl = "https://tiles.openfreemap.org/styles/positron",
        category = MapStyleCategory.STANDARD
    )

    val DARK_MATTER = MapStyleData(
        key = "dark_matter",
        displayName = "Sombre",
        styleUrl = "https://tiles.openfreemap.org/styles/dark",
        category = MapStyleCategory.STANDARD
    )

    /**
     * Compatibility function to replace MapStyle.getByCategory()
     */
    fun getByCategory(category: MapStyleCategory, config: MapStyleConfig): List<MapStyleData> {
        return when (category) {
            MapStyleCategory.STANDARD -> config.getStandardMapStyles()
            MapStyleCategory.SATELLITE -> listOf(config.getSatelliteMapStyle())
        }
    }
}