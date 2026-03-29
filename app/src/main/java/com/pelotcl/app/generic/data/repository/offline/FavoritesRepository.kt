package com.pelotcl.app.generic.data.repository.offline

import android.content.Context
import androidx.core.content.edit
import com.google.gson.reflect.TypeToken
import com.pelotcl.app.generic.data.GsonProvider
import com.pelotcl.app.generic.data.model.Favorite

/**
 * Repository for managing favorites - both the new user-created favorites
 * and the legacy favorite stops system (kept for migration)
 */
class FavoritesRepository(private val context: Context) {
    private val prefs by lazy { context.getSharedPreferences("pelo_prefs", Context.MODE_PRIVATE) }
    private val gson = GsonProvider.instance

    private val keyFavoriteStops = "favorites_stops"
    private val keyStopDessertePrefix = "stop_desserte_"

    // New keys for the updated favorites system
    private val keyUserFavorites = "user_favorites_v2"

    fun getFavoriteStops(): Set<String> {
        return prefs.getStringSet(keyFavoriteStops, emptySet()) ?: emptySet()
    }

    fun saveFavoriteStops(favorites: Set<String>) {
        prefs.edit { putStringSet(keyFavoriteStops, favorites) }
    }

    fun toggleFavoriteStop(stopName: String, desserte: String? = null): Boolean {
        val favorites = getFavoriteStops().toMutableSet()
        val isAdding = !favorites.contains(stopName)

        if (isAdding) favorites.add(stopName) else favorites.remove(stopName)

        // Single batch edit for all SharedPreferences changes
        prefs.edit {
            putStringSet(keyFavoriteStops, favorites)
            if (isAdding && !desserte.isNullOrEmpty()) {
                putString(keyStopDessertePrefix + stopName, desserte)
            } else if (!isAdding) {
                remove(keyStopDessertePrefix + stopName)
            }
        }
        return isAdding
    }

    fun getDesserteForStop(stopName: String): String? {
        return prefs.getString(keyStopDessertePrefix + stopName, null)
    }

    // === New favorites system ===

    /**
     * Get all user-created favorites
     */
    fun getUserFavorites(): List<Favorite> {
        val json = prefs.getString(keyUserFavorites, null)
        return if (json != null) {
            try {
                val type = object : TypeToken<List<Favorite>>() {}.type
                gson.fromJson(json, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    /**
     * Save the list of user-created favorites
     */
    fun saveUserFavorites(favorites: List<Favorite>) {
        val json = gson.toJson(favorites)
        prefs.edit { putString(keyUserFavorites, json) }
    }

    /**
     * Add a new favorite
     */
    fun addFavorite(favorite: Favorite): Boolean {
        val favorites = getUserFavorites().toMutableList()
        // Check if a favorite with the same name already exists
        if (favorites.any { it.name.equals(favorite.name, ignoreCase = true) }) {
            return false // Favorite with this name already exists
        }

        favorites.add(favorite)
        saveUserFavorites(favorites)
        return true
    }

    /**
     * Remove a favorite by ID
     */
    fun removeFavorite(favoriteId: String): Boolean {
        val favorites = getUserFavorites().toMutableList()
        val initialSize = favorites.size
        favorites.removeAll { it.id == favoriteId }
        saveUserFavorites(favorites)
        return favorites.size < initialSize
    }

    /**
     * Generate a unique ID for a new favorite
     */
    fun generateFavoriteId(): String {
        return "fav_" + System.currentTimeMillis().toString()
    }
}