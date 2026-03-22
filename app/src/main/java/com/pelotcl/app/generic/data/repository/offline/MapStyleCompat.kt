package com.pelotcl.app.generic.data.repository.offline

import com.pelotcl.app.generic.data.network.MapStyleCategory
import com.pelotcl.app.generic.data.network.MapStyleData

/**
 * Compatibility extensions to maintain backward compatibility with MapStyle enum
 */

// Typealias for easier migration
typealias MapStyle = MapStyleData

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

    val BRIGHT = MapStyleData(
        key = "bright",
        displayName = "OSM",
        styleUrl = "https://tiles.openfreemap.org/styles/bright",
        category = MapStyleCategory.STANDARD
    )

    val LIBERTY = MapStyleData(
        key = "liberty",
        displayName = "3D",
        styleUrl = "https://tiles.openfreemap.org/styles/liberty",
        category = MapStyleCategory.STANDARD
    )

    val SATELLITE = MapStyleData(
        key = "satellite",
        displayName = "Vue satellite",
        styleUrl = "asset://satellite.json",
        category = MapStyleCategory.SATELLITE
    )

    /**
     * Compatibility function to replace MapStyle.fromKey()
     */
    fun fromKey(key: String, config: com.pelotcl.app.generic.data.network.MapStyleConfig): MapStyleData {
        return config.getMapStyleByKey(key) ?: POSITRON
    }

    /**
     * Compatibility function to replace MapStyle.getByCategory()
     */
    fun getByCategory(category: MapStyleCategory, config: com.pelotcl.app.generic.data.network.MapStyleConfig): List<MapStyleData> {
        return when (category) {
            MapStyleCategory.STANDARD -> config.getStandardMapStyles()
            MapStyleCategory.SATELLITE -> listOf(config.getSatelliteMapStyle())
        }
    }
}