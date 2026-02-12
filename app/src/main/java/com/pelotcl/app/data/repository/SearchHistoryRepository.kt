package com.pelotcl.app.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Model for a search history item that can be either a stop or a line
 */
data class SearchHistoryItem(
    val query: String,
    val type: SearchType,
    val lines: List<String> = emptyList(), // For stops: the lines serving the stop; For lines: empty
    val timestamp: Long = System.currentTimeMillis()
)

enum class SearchType {
    STOP,
    LINE
}

/**
 * Repository for managing search history using SharedPreferences.
 * Stores recent searches for quick access.
 */
class SearchHistoryRepository(context: Context) {
    private val prefs = context.getSharedPreferences("pelo_search_history", Context.MODE_PRIVATE)
    private val gson = Gson()
    
    companion object {
        private const val KEY_SEARCH_HISTORY = "search_history"
        private const val MAX_HISTORY_SIZE = 10
    }
    
    /**
     * Get the search history ordered by most recent first
     */
    fun getSearchHistory(): List<SearchHistoryItem> {
        val json = prefs.getString(KEY_SEARCH_HISTORY, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<SearchHistoryItem>>() {}.type
            gson.fromJson<List<SearchHistoryItem>>(json, type)
                .sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Add a search item to history. If the item already exists, it updates its timestamp.
     * Keeps only the most recent MAX_HISTORY_SIZE items.
     */
    fun addToHistory(item: SearchHistoryItem) {
        val history = getSearchHistory().toMutableList()
        
        // Remove existing entry with same query and type (case-insensitive)
        history.removeAll { 
            it.query.equals(item.query, ignoreCase = true) && it.type == item.type 
        }
        
        // Add new item at the beginning
        history.add(0, item.copy(timestamp = System.currentTimeMillis()))
        
        // Keep only MAX_HISTORY_SIZE items
        val trimmedHistory = history.take(MAX_HISTORY_SIZE)
        
        // Save to preferences
        val json = gson.toJson(trimmedHistory)
        prefs.edit().putString(KEY_SEARCH_HISTORY, json).apply()
    }
    
    /**
     * Remove a specific item from history
     */
    fun removeFromHistory(query: String, type: SearchType) {
        val history = getSearchHistory().toMutableList()
        history.removeAll { 
            it.query.equals(query, ignoreCase = true) && it.type == type 
        }
        val json = gson.toJson(history)
        prefs.edit().putString(KEY_SEARCH_HISTORY, json).apply()
    }
    
    /**
     * Clear all search history
     */
    fun clearHistory() {
        prefs.edit().remove(KEY_SEARCH_HISTORY).apply()
    }
}
