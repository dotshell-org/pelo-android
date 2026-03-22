package com.pelotcl.app.specific

import com.pelotcl.app.generic.data.network.MapStyleConfig
import com.pelotcl.app.generic.data.network.MapStyleCategory
import com.pelotcl.app.generic.data.network.MapStyleData

/**
 * Lyon-specific implementation of MapStyleConfig
 * Provides map style configurations for the Lyon area
 */
class MapStyleConfigImpl : MapStyleConfig {
    
    override fun getStandardMapStyles(): List<MapStyleData> {
        return listOf(
            MapStyleData(
                key = "positron",
                displayName = "Clair",
                styleUrl = "https://tiles.openfreemap.org/styles/positron",
                category = MapStyleCategory.STANDARD
            ),
            MapStyleData(
                key = "dark_matter",
                displayName = "Sombre",
                styleUrl = "https://tiles.openfreemap.org/styles/dark",
                category = MapStyleCategory.STANDARD
            ),
            MapStyleData(
                key = "bright",
                displayName = "OSM",
                styleUrl = "https://tiles.openfreemap.org/styles/bright",
                category = MapStyleCategory.STANDARD
            ),
            MapStyleData(
                key = "liberty",
                displayName = "3D",
                styleUrl = "https://tiles.openfreemap.org/styles/liberty",
                category = MapStyleCategory.STANDARD
            )
        )
    }
    
    override fun getSatelliteMapStyle(): MapStyleData {
        return MapStyleData(
            key = "satellite",
            displayName = "Vue satellite",
            styleUrl = "asset://satellite.json",
            category = MapStyleCategory.SATELLITE
        )
    }
    
    override fun getMapStyleByKey(key: String): MapStyleData? {
        val allStyles = getStandardMapStyles() + getSatelliteMapStyle()
        return allStyles.find { it.key == key }
    }
    
    override fun getDefaultMapStyle(): MapStyleData {
        return getStandardMapStyles().first()
    }
}