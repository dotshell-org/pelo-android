package com.pelotcl.app.data.repository

import android.content.Context

/**
 * Enum representing available map styles.
 * Each style has a unique key, display name, and tile URL.
 */
enum class MapStyle(
    val key: String,
    val displayName: String,
    val styleUrl: String,
    val category: MapStyleCategory
) {
    // Standard styles
    POSITRON(
        key = "positron",
        displayName = "Positron (Clair)",
        styleUrl = "https://tiles.openfreemap.org/styles/positron",
        category = MapStyleCategory.STANDARD
    ),
    DARK_MATTER(
        key = "dark_matter",
        displayName = "Dark Matter (Sombre)",
        styleUrl = "https://tiles.openfreemap.org/styles/dark",
        category = MapStyleCategory.STANDARD
    ),
    BRIGHT(
        key = "bright",
        displayName = "OSM Bright (Détaillé)",
        styleUrl = "https://tiles.openfreemap.org/styles/bright",
        category = MapStyleCategory.STANDARD
    ),
    LIBERTY(
        key = "liberty",
        displayName = "Liberty (3D)",
        styleUrl = "https://tiles.openfreemap.org/styles/liberty",
        category = MapStyleCategory.STANDARD
    ),
    
    // Satellite style - Using ESRI World Imagery with local style file
    SATELLITE(
        key = "satellite",
        displayName = "Vue satellite",
        styleUrl = "asset://satellite.json",
        category = MapStyleCategory.SATELLITE
    );

    companion object {
        fun fromKey(key: String): MapStyle {
            return entries.find { it.key == key } ?: POSITRON
        }
        
        fun getByCategory(category: MapStyleCategory): List<MapStyle> {
            return entries.filter { it.category == category }
        }
    }
}

/**
 * Categories for map styles
 */
enum class MapStyleCategory(val displayName: String) {
    STANDARD("Cartes standard"),
    SATELLITE("Vue satellite")
}

/**
 * Repository for managing map style preferences using SharedPreferences.
 */
class MapStyleRepository(private val context: Context) {
    private val prefs by lazy { 
        context.getSharedPreferences("pelo_map_prefs", Context.MODE_PRIVATE) 
    }
    
    private val KEY_MAP_STYLE = "selected_map_style"

    /**
     * Get the currently selected map style.
     * Defaults to POSITRON if no style is saved.
     */
    fun getSelectedStyle(): MapStyle {
        val styleKey = prefs.getString(KEY_MAP_STYLE, MapStyle.POSITRON.key)
        return MapStyle.fromKey(styleKey ?: MapStyle.POSITRON.key)
    }

    /**
     * Save the selected map style.
     */
    fun saveSelectedStyle(style: MapStyle) {
        prefs.edit().putString(KEY_MAP_STYLE, style.key).apply()
    }

    /**
     * Get all available map styles.
     */
    fun getAllStyles(): List<MapStyle> {
        return MapStyle.entries.toList()
    }
    
    /**
     * Get all available map style categories.
     */
    fun getAllCategories(): List<MapStyleCategory> {
        return MapStyleCategory.entries.toList()
    }
    
    /**
     * Get styles by category.
     */
    fun getStylesByCategory(category: MapStyleCategory): List<MapStyle> {
        return MapStyle.getByCategory(category)
    }

    /**
     * Get the effective style considering offline state.
     * If offline and the selected style is not downloaded, falls back to a downloaded style.
     */
    fun getEffectiveStyle(isOffline: Boolean, downloadedStyles: Set<String>): MapStyle {
        val selected = getSelectedStyle()
        if (!isOffline) return selected
        if (selected.key in downloadedStyles) return selected
        return downloadedStyles.firstOrNull()?.let { MapStyle.fromKey(it) } ?: MapStyle.POSITRON
    }
}
