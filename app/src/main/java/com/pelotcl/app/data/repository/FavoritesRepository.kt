package com.pelotcl.app.data.repository

import android.content.Context

/**
 * Very small wrapper around SharedPreferences to store favorite lines as a Set<String>
 */
class FavoritesRepository(private val context: Context) {
    private val prefs by lazy { context.getSharedPreferences("pelo_prefs", Context.MODE_PRIVATE) }
    private val KEY_FAVORITES = "favorites_lines"

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
}
