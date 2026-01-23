package com.pelotcl.app.utils

import java.text.Normalizer

/**
 * Utilities for search functionality
 */
object SearchUtils {
    /**
     * Normalize a string for fuzzy search matching:
     * - Removes accents/diacritics
     * - Converts to lowercase
     * - Replaces multiple spaces with single space
     * - Trims leading/trailing spaces
     * 
     * This allows flexible matching:
     * - "Saint Denis" matches "Saint-Denis"
     * - "PERRIERE" matches "Perrière"
     * - "élysée" matches "Elysee"
     */
    fun normalizeForSearch(text: String): String {
        // Remove diacritics/accents
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace("\\p{M}".toRegex(), "")
        
        // Lowercase and normalize whitespace
        return normalized.lowercase()
            .replace("\\s+".toRegex(), " ")
            .trim()
    }
    
    /**
     * Check if a text contains a query with fuzzy matching
     * (case-insensitive, accent-insensitive, space-to-dash flexible)
     * Supports multi-word queries: all words must appear in the text
     * Optimized to minimize allocations and string operations
     */
    fun fuzzyContains(text: String, query: String): Boolean {
        if (query.isEmpty()) return true
        if (text.isEmpty()) return false
        
        val normalizedText = normalizeForSearch(text)
        val normalizedQuery = normalizeForSearch(query)
        
        // Check if query has multiple words
        val queryWords = normalizedQuery.split(" ").filter { it.isNotEmpty() }
        
        if (queryWords.size > 1) {
            // Multi-word: all words must appear in text (in any order)
            // Also check with hyphens and spaces swapped
            val textWithSpaces = normalizedText.replace('-', ' ')
            val textWithHyphens = normalizedText.replace(' ', '-')
            
            return queryWords.all { word ->
                normalizedText.contains(word) || 
                textWithSpaces.contains(word) || 
                textWithHyphens.contains(word)
            }
        }
        
        // Single word: try direct match first (most common case)
        if (normalizedText.contains(normalizedQuery)) {
            return true
        }
        
        // Only do expensive operations if direct match failed
        // Check if we need to handle hyphens/spaces at all
        if (!normalizedText.contains('-') && !normalizedQuery.contains(' ') &&
            !normalizedText.contains(' ') && !normalizedQuery.contains('-')) {
            return false // No hyphens or spaces to worry about
        }
        
        // Try with hyphens replaced by spaces in text
        if (normalizedText.contains('-')) {
            val textWithSpaces = normalizedText.replace('-', ' ')
            if (textWithSpaces.contains(normalizedQuery)) {
                return true
            }
        }
        
        // Try with spaces replaced by hyphens in query
        if (normalizedQuery.contains(' ')) {
            val queryWithHyphens = normalizedQuery.replace(' ', '-')
            if (normalizedText.contains(queryWithHyphens)) {
                return true
            }
        }
        
        return false
    }
    
    /**
     * Check if a text starts with a query with fuzzy matching
     * Optimized to minimize allocations and string operations
     */
    fun fuzzyStartsWith(text: String, query: String): Boolean {
        if (query.isEmpty()) return true
        if (text.isEmpty()) return false
        
        val normalizedText = normalizeForSearch(text)
        val normalizedQuery = normalizeForSearch(query)
        
        // Try direct match (most common case)
        if (normalizedText.startsWith(normalizedQuery)) {
            return true
        }
        
        // Only do expensive operations if needed
        if (!normalizedText.contains('-') && !normalizedQuery.contains(' ') &&
            !normalizedText.contains(' ') && !normalizedQuery.contains('-')) {
            return false
        }
        
        // Try with hyphens replaced by spaces
        if (normalizedText.contains('-')) {
            val textWithSpaces = normalizedText.replace('-', ' ')
            if (textWithSpaces.startsWith(normalizedQuery)) {
                return true
            }
        }
        
        // Try with spaces replaced by hyphens in query
        if (normalizedQuery.contains(' ')) {
            val queryWithHyphens = normalizedQuery.replace(' ', '-')
            if (normalizedText.startsWith(queryWithHyphens)) {
                return true
            }
        }
        
        return false
    }
}
