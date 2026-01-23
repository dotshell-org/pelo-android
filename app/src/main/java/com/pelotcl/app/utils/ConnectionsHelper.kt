package com.pelotcl.app.utils

import com.pelotcl.app.data.model.StopFeature

enum class TransportType {
    BUS,
    METRO,
    TRAM,
    FUNICULAR,
    NAVIGONE,
    UNKNOWN
}

fun getTransportType(lineName: String): TransportType {
    return when {
        lineName.matches(Regex("T[1-7]")) -> TransportType.TRAM
        lineName in listOf("A", "B", "C", "D") -> TransportType.METRO
        lineName in listOf("F1", "F2") -> TransportType.FUNICULAR
        lineName.uppercase().startsWith("NAV") -> TransportType.NAVIGONE
        // Bus lines can be numbers, "C" followed by numbers, or other letters.
        // It's simpler to consider everything else as a bus by default.
        else -> TransportType.BUS
    }
}

data class Connection(val lineName: String, val transportType: TransportType)

/**
 * Utility to manage transport transfers
 */
object ConnectionsHelper {
    
    /**
     * Normalizes a station name for comparison
     * Keeps only letters and converts to lowercase
     */
    private fun normalizeStationName(name: String): String {
        return name.filter { it.isLetter() }.lowercase()
    }
    
    /**
     * Normalizes words of a station name handling abbreviations
     */
    private fun normalizeWords(name: String): List<String> {
        val ignoredWords = setOf("gare", "station", "arret")
        
        return name.split(Regex("[\\s\\-,.]+"))
            .filter { it.isNotEmpty() }
            .map { word -> 
                word.filter { it.isLetter() }.lowercase()
            }
            .filter { it.isNotEmpty() && it !in ignoredWords }
    }
    
    /**
     * Compares two station names flexibly
     * Handles abbreviations: "L. Pradel" matches "Louis Pradel"
     */
    private fun stationNamesMatch(name1: String, name2: String): Boolean {
        // First try simple comparison
        val normalized1 = normalizeStationName(name1)
        val normalized2 = normalizeStationName(name2)
        
        if (normalized1 == normalized2) {
            return true
        }
        
        // Then try with separated words to handle abbreviations
        val words1 = normalizeWords(name1)
        val words2 = normalizeWords(name2)
        
        // If one of the two names has fewer words, it might be an abbreviated version
        if (words1.size != words2.size) {
            val shorter = if (words1.size < words2.size) words1 else words2
            val longer = if (words1.size < words2.size) words2 else words1
            
            var shorterIndex = 0
            var longerIndex = 0
            
            while (shorterIndex < shorter.size && longerIndex < longer.size) {
                val shortWord = shorter[shorterIndex]
                val longWord = longer[longerIndex]
                
                when {
                    shortWord == longWord -> {
                        shorterIndex++
                        longerIndex++
                    }
                    shortWord.length >= 3 && longWord.startsWith(shortWord) -> {
                        shorterIndex++
                        longerIndex++
                    }
                    shortWord.length == 1 && longWord.startsWith(shortWord) -> {
                        shorterIndex++
                        longerIndex++
                    }
                    shortWord.length > longWord.length -> {
                        var combinedWord = longWord
                        var tempIndex = longerIndex + 1
                        var matched = false
                        
                        while (tempIndex < longer.size && combinedWord.length < shortWord.length) {
                            combinedWord += longer[tempIndex]
                            if (combinedWord == shortWord) {
                                longerIndex = tempIndex + 1
                                shorterIndex++
                                matched = true
                                break
                            }
                            tempIndex++
                        }
                        
                        if (!matched) {
                            longerIndex++
                        }
                    }
                    else -> longerIndex++
                }
            }
            
            return shorterIndex == shorter.size
        }
        
        // Check that all words match in order
        if (words1.size == words2.size) {
            return words1.zip(words2).all { (w1, w2) ->
                w1 == w2 || 
                (w1.length == 1 && w2.startsWith(w1)) ||
                (w2.length == 1 && w1.startsWith(w2)) ||
                (w1.length >= 3 && w2.startsWith(w1)) ||
                (w2.length >= 3 && w1.startsWith(w2))
            }
        }
        
        return false
    }
    
    /**
     * Parses the desserte field and extracts metro lines (A, B, C, D),
     * funicular (F1, F2), tram (T1-T7) and navigone (NAVI1)
     * 
     * Desserte field format:
     * - "A:A" = Metro A outbound direction (:A is the direction, not the line!)
     * - "5:A,86:A" = Buses 5 and 86 outbound direction
     * - "A:A,D:A" = Metros A and D
     * - "F1:A,F2:A" = Funiculars F1 and F2
     * - "T1:A,T2:A" = Trams T1 and T2
     * - "NAVI1:A" = Navigone NAVI1
     * 
     * IMPORTANT: Don't confuse ":A" (outbound direction) with metro line A
     * 
     * @param desserte The stop's desserte field
     * @return List of metro, funicular, tram and navigone lines
     */
    fun parseMetroFunicularAndTramConnections(desserte: String): List<String> {
        val connections = mutableSetOf<String>()
        
        if (desserte.isBlank()) return emptyList()
        
        // If string contains commas, each entry represents a line with direction
        val entries = desserte.split(",")
        
        for (entry in entries) {
            val trimmed = entry.trim()
            if (trimmed.isEmpty()) continue
            
            // Extract the line (before the first ":")
            val lineName = trimmed.substringBefore(":").trim()
            
            // Check if it's a metro line (A, B, C, D)
            if (lineName in listOf("A", "B", "C", "D")) {
                connections.add(lineName)
            }
            
            // Check if it's a funicular line (F1, F2)
            if (lineName in listOf("F1", "F2")) {
                connections.add(lineName)
            }
            
            // Check if it's a tram line (T1 to T7)
            if (lineName.matches(Regex("T[1-7]"))) {
                connections.add(lineName)
            }
            
            // Check if it's a navigone line (NAV1, NAV2, etc.)
            if (lineName.uppercase().startsWith("NAV")) {
                connections.add(lineName.uppercase())
            }
        }
        
        // Sort for consistent order: A, B, C, D, F1, F2, T1-T7, NAV1+
        return connections.sortedWith(compareBy { line ->
            when {
                line in listOf("A", "B", "C", "D") -> line[0].code
                line.startsWith("F") -> 100 + (line.substring(1).toIntOrNull() ?: 0)
                line.startsWith("T") -> 200 + (line.substring(1).toIntOrNull() ?: 0)
                line.uppercase().startsWith("NAV") -> 300 + (line.substring(3).toIntOrNull() ?: 0)
                else -> 9999
            }
        })
    }
    
    /**
     * Parses the desserte field and extracts all lines (including bus lines)
     * 
     * Desserte field format:
     * - "A:A" = Metro A outbound direction (:A is the direction, not the line!)
     * - "5:A,86:A" = Buses 5 and 86 outbound direction
     * - "A:A,D:A" = Metros A and D
     * - "F1:A,F2:A" = Funiculars F1 and F2
     * - "T1:A,T2:A" = Trams T1 and T2
     * 
     * IMPORTANT: Don't confuse ":A" (outbound direction) with metro line A
     * 
     * @param desserte The stop's desserte field
     * @return List of all lines present in the desserte
     */
    fun parseAllConnections(desserte: String): List<Connection> {
        val connections = mutableListOf<Connection>()
        if (desserte.isBlank()) return connections

        val lines = desserte.split(",")
        for (line in lines) {
            val parts = line.trim().split(":")
            if (parts.size == 2) {
                val lineName = parts[0]
                val direction = parts[1]
                val transportType = getTransportType(lineName)
                connections.add(Connection(lineName, transportType))
            }
        }
        return connections.distinctBy { it.lineName }
    }

    /**
     * Finds metro and funicular transfers for a given stop
     * by searching in the list of all stops
     * 
     * @param stopName Name of the stop to search for
     * @param currentLine Current line (to exclude from transfers)
     * @param allStops List of all available stops
     * @return List of metro and funicular lines in transfer
     */
    fun findConnectionsForStop(
        stopName: String,
        currentLine: String,
        allStops: List<StopFeature>
    ): List<String> {
        // Search for stops matching the name
        val matchingStops = allStops.filter { stop ->
            stationNamesMatch(stop.properties.nom, stopName)
        }
        
        // Extract all connections from dessertes
        val allConnections = mutableSetOf<String>()
        for (stop in matchingStops) {
            val connections = parseMetroFunicularAndTramConnections(stop.properties.desserte)
            allConnections.addAll(connections)
        }
        
        // Remove the current line from the transfers list
        allConnections.remove(currentLine)
        
        // Sort for consistent order
        return allConnections.sortedWith(compareBy { line ->
            when (line) {
                "A" -> 1
                "B" -> 2
                "C" -> 3
                "D" -> 4
                "F1" -> 5
                "F2" -> 6
                else -> 99
            }
        })
    }
}
