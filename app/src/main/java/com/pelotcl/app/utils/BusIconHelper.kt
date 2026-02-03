package com.pelotcl.app.utils

import android.util.LruCache
import com.pelotcl.app.data.model.StopFeature

/**
 * Utility to determine appropriate icons for bus stops
 */
object BusIconHelper {

    /**
     * Cache for parsed desserte strings to avoid repeated parsing.
     * Key: desserte string, Value: list of normalized line names
     * Size: 500 entries (sufficient for frequently accessed stops)
     */
    private val desserteCache = LruCache<String, List<String>>(500)

    /**
     * Extracts the first bus line from a stop and returns the corresponding drawable name
     *
     * @param stopFeature The transport stop
     * @return The drawable name (without the .xml extension) or null if no line found
     */
    fun getIconNameForStop(stopFeature: StopFeature): String? {
        val lines = getAllLinesForStop(stopFeature)
        if (lines.isEmpty()) return null
        val firstLine = lines.first()
        return getDrawableNameForLine(firstLine)
    }

    /**
     * Returns all lines serving a stop (line names).
     * Results are cached by desserte string to avoid repeated parsing.
     */
    fun getAllLinesForStop(stopFeature: StopFeature): List<String> {
        val desserte = stopFeature.properties.desserte

        // Check cache first
        desserteCache.get(desserte)?.let { return it }

        // Parse and cache
        val result = parseDesserte(desserte).map { normalizeLineName(it) }
        desserteCache.put(desserte, result)
        return result
    }

    /**
     * Clears the desserte cache. Call when data source changes or under memory pressure.
     */
    fun clearCache() {
        desserteCache.evictAll()
    }

    /**
     * Trims the cache under memory pressure.
     * @param level The trim memory level from ComponentCallbacks2
     */
    fun trimCache(level: Int) {
        when {
            level >= android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                desserteCache.evictAll()
            }
            level >= android.content.ComponentCallbacks2.TRIM_MEMORY_MODERATE -> {
                desserteCache.trimToSize(desserteCache.maxSize() / 2)
            }
        }
    }
    
    /**
     * Normalizes line names to match icon names
     * NAVI1 -> NAV1
     */
    private fun normalizeLineName(lineName: String): String {
        return when (lineName.uppercase()) {
            "NAVI1" -> "NAV1"
            else -> lineName
        }
    }

    /**
     * Returns the drawable names for all lines at a stop, in order
     */
    fun getAllDrawableNamesForStop(stopFeature: StopFeature): List<String> {
        return getAllLinesForStop(stopFeature).map { getDrawableNameForLine(it) }
    }
    
    /**
     * Converts a line name to a drawable name (public version)
     * Normalizes line names and converts to drawable format
     * 
     * @param lineName The line name (ex: "212", "C17", "A", "NAVI1")
     * @return The corresponding drawable name (ex: "_212", "c17", "a", "nav1")
     */
    fun getDrawableNameForLineName(lineName: String): String {
        val normalized = normalizeLineName(lineName)
        return getDrawableNameForLine(normalized)
    }
    
    /**
     * Parses the desserte string to extract the list of lines.
     * Handled cases:
     *  - "5:A,86:A,JD844:R" -> ["5", "86", "JD844"] (buses with directions)
     *  - "A:A,D:A" -> ["A", "D"] (metros A and D, :A = outbound direction)
     *  - "F1:A,F2:A" -> ["F1", "F2"] (funiculars)
     *  - "M:A:B" -> ["M", "B"] (bus M with multiple destinations, ignore :A/:R)
     *  - "C17:22:31" (old format) -> ["C17", "22", "31"]
     * 
     * IMPORTANT: Don't confuse ":A" (outbound direction) with metro line A
     */
    private fun parseDesserte(desserte: String): List<String> {
        if (desserte.isBlank()) return emptyList()
        
        // If string contains commas, each entry represents a line with direction (e.g.: 5:A or A:A)
        val entries = desserte.split(",")
        val rawLines: List<String> = if (entries.size > 1) {
            // Format with commas: "5:A,86:A" or "A:A,D:A"
            entries.mapNotNull { part ->
                val trimmed = part.trim()
                if (trimmed.isEmpty()) null else {
                    // Extract the line (before the first ":")
                    trimmed.substringBefore(":").trim()
                }
            }.filter { it.isNotEmpty() }
        } else {
            // No comma: old or simple format
            // E.g.: "M:A:B" or "C17:22:31" or "A:A" (single stop)
            val tokens = desserte.split(":")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            if (tokens.isEmpty()) return emptyList()
            
            // For a single entry without comma like "A:A" (metro A outbound direction),
            // we only keep the first token
            if (tokens.size == 2) {
                val first = tokens[0]
                val second = tokens[1]
                // If the second token is "A" or "R" (direction), keep only the first
                if (second.uppercase() == "A" || second.uppercase() == "R") {
                    listOf(first)
                } else {
                    // Otherwise it might be an old format with multiple lines
                    tokens
                }
            } else if (tokens.size > 2) {
                // Format like "M:A:B" -> keep M and B, ignore A/R which are directions
                val first = tokens.first()
                val rest = tokens.drop(1).filter { t ->
                    val up = t.uppercase()
                    // Don't keep outbound/return directions alone
                    up != "A" && up != "R"
                }
                listOf(first) + rest
            } else {
                // Single token, no ":", keep as is
                tokens
            }
        }

        // Deduplicate while preserving order, case-insensitive comparison
        val seen = HashSet<String>()
        val unique = ArrayList<String>(rawLines.size)
        rawLines.forEach { line ->
            val key = line.uppercase()
            if (seen.add(key)) {
                unique.add(line)
            }
        }
        return unique
    }
    
    /**
     * Converts a line name to a drawable name
     * Lines composed only of digits are prefixed with an underscore
     * 
     * @param lineName The line name (ex: "212", "C17", "A")
     * @return The corresponding drawable name (ex: "_212", "c17", "a")
     */
    private fun getDrawableNameForLine(lineName: String): String {
        if (lineName.isBlank()) {
            return ""
        }
        
        // Check if line is composed only of digits
        val isNumericOnly = lineName.all { it.isDigit() }
        
        return if (isNumericOnly) {
            // Prefix with underscore for numeric lines
            "_$lineName"
        } else {
            // Convert to lowercase for other lines
            lineName.lowercase()
        }
    }
    
    /**
     * Checks if a drawable exists for a given line
     * Note: This function should be extended to check for actual file existence
     * 
     * @param lineName The line name
     * @return true if a drawable should exist, false otherwise
     */
    fun hasIconForLine(lineName: String): Boolean {
        if (lineName.isBlank()) {
            return false
        }
        
        // For now, assume all lines have an icon
        // This logic can be extended as needed
        return true
    }
}