package com.pelotcl.app.data.repository

import android.content.Context

/**
 * Very small wrapper around SharedPreferences to store favorite lines as a Set<String>
 */
class FavoritesRepository(private val context: Context) {
    private val prefs by lazy { context.getSharedPreferences("pelo_prefs", Context.MODE_PRIVATE) }
    private val KEY_FAVORITES = "favorites_lines"
    private val KEY_FAVORITE_STOPS = "favorites_stops"
    private val KEY_STOP_DESSERTE_PREFIX = "stop_desserte_"

    fun getFavorites(): Set<String> {
        return prefs.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()
    }

    fun saveFavorites(favorites: Set<String>) {
        prefs.edit().putStringSet(KEY_FAVORITES, favorites).apply()
    }

    fun toggleFavorite(line: String): Boolean {
        val normalized = line.uppercase()
        val favorites = getFavorites().toMutableSet()
        val changed = if (favorites.contains(normalized)) {
            favorites.remove(normalized)
            true
        } else {
            favorites.add(normalized)
            true
        }
        // only save if changed
        saveFavorites(favorites)
        return !favorites.contains(normalized) // return true if it was removed, false otherwise? Keep simple and return true
    }

    // --- Favorite stops ---

    fun getFavoriteStops(): Set<String> {
        return prefs.getStringSet(KEY_FAVORITE_STOPS, emptySet()) ?: emptySet()
    }

    fun saveFavoriteStops(favorites: Set<String>) {
        prefs.edit().putStringSet(KEY_FAVORITE_STOPS, favorites).apply()
    }

    fun toggleFavoriteStop(stopName: String, desserte: String? = null): Boolean {
        val favorites = getFavoriteStops().toMutableSet()
        if (favorites.contains(stopName)) {
            favorites.remove(stopName)
            // Clean up desserte when removing
            prefs.edit().remove(KEY_STOP_DESSERTE_PREFIX + stopName).apply()
        } else {
            favorites.add(stopName)
            // Store desserte alongside stop name
            if (!desserte.isNullOrEmpty()) {
                prefs.edit().putString(KEY_STOP_DESSERTE_PREFIX + stopName, desserte).apply()
            }
        }
        saveFavoriteStops(favorites)
        return favorites.contains(stopName)
    }

    fun isFavoriteStop(stopName: String): Boolean {
        return getFavoriteStops().contains(stopName)
    }

    fun getDesserteForStop(stopName: String): String? {
        return prefs.getString(KEY_STOP_DESSERTE_PREFIX + stopName, null)
    }

    fun saveDesserteForStop(stopName: String, desserte: String) {
        prefs.edit().putString(KEY_STOP_DESSERTE_PREFIX + stopName, desserte).apply()
    }
}
