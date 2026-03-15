package com.pelotcl.app.data.repository

import android.content.Context
import androidx.core.content.edit

/**
 * Very small wrapper around SharedPreferences to store favorite lines as a Set<String>
 */
class FavoritesRepository(private val context: Context) {
    private val prefs by lazy { context.getSharedPreferences("pelo_prefs", Context.MODE_PRIVATE) }
    private val keyFavorites = "favorites_lines"
    private val keyFavoriteStops = "favorites_stops"
    private val keyStopDessertePrefix = "stop_desserte_"

    fun getFavorites(): Set<String> {
        return prefs.getStringSet(keyFavorites, emptySet()) ?: emptySet()
    }

    fun saveFavorites(favorites: Set<String>) {
        prefs.edit { putStringSet(keyFavorites, favorites) }
    }

    // --- Favorite stops ---

    fun getFavoriteStops(): Set<String> {
        return prefs.getStringSet(keyFavoriteStops, emptySet()) ?: emptySet()
    }

    fun saveFavoriteStops(favorites: Set<String>) {
        prefs.edit { putStringSet(keyFavoriteStops, favorites)}
    }

    fun toggleFavoriteStop(stopName: String, desserte: String? = null): Boolean {
        val favorites = getFavoriteStops().toMutableSet()
        if (favorites.contains(stopName)) {
            favorites.remove(stopName)
            // Clean up desserte when removing
            prefs.edit { remove(keyStopDessertePrefix + stopName)}
        } else {
            favorites.add(stopName)
            // Store desserte alongside stop name
            if (!desserte.isNullOrEmpty()) {
                prefs.edit { putString(keyStopDessertePrefix + stopName, desserte)}
            }
        }
        saveFavoriteStops(favorites)
        return favorites.contains(stopName)
    }

    fun getDesserteForStop(stopName: String): String? {
        return prefs.getString(keyStopDessertePrefix + stopName, null)
    }

    fun saveDesserteForStop(stopName: String, desserte: String) {
        prefs.edit { putString(keyStopDessertePrefix + stopName, desserte)}
    }
}
