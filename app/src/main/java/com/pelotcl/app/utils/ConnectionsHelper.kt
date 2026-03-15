package com.pelotcl.app.utils

import androidx.compose.runtime.Immutable

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

@Immutable
data class Connection(val lineName: String, val transportType: TransportType)

/**
 * Utility to manage transport transfers
 */
object ConnectionsHelper {

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
                parts[1]
                val transportType = getTransportType(lineName)
                connections.add(Connection(lineName, transportType))
            }
        }
        return connections.distinctBy { it.lineName }
    }

}
