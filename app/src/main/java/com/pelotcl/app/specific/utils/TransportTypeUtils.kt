package com.pelotcl.app.specific.utils

/**
 * Utility functions for determining transport types based on line names.
 */
object TransportTypeUtils {

    /**
     * Gets the transport type category for a given line name.
     * 
     * @param lineName The line name (e.g., "A", "F1", "C1", "T1", "6")
     * @return The transport type category (e.g., "Métro", "Funiculaire", "Chrono", "Bus")
     */
    fun getTransportType(lineName: String): String {
        val upperLine = lineName.uppercase()
        
        return when {
            // Metro lines A, B, C, D
            upperLine in setOf("A", "B", "C", "D") -> "Métro"
            
            // Funiculaire F1, F2
            upperLine == "F1" || upperLine == "F2" -> "Funiculaire"
            
            // Tram lines (T1, T2, etc. but not TB or other tram variants)
            upperLine.startsWith("T") && upperLine.length == 2 -> "Tramway"
            upperLine.startsWith("TB") -> "Trambus"
            upperLine == "RX" -> ""
            
            // Chrono lines (C1, C2, etc.)
            upperLine.startsWith("C") && upperLine.length >= 2 -> "Chrono"
            
            // Junior Direct lines
            upperLine.startsWith("JD") -> "Bus Scolaire"
            
            // Navigone lines
            upperLine.startsWith("NAVI") -> "Navigone"
            
            // Navette lines
            upperLine.startsWith("N") -> "Navette"
            
            // Cars du Rhône (numeric lines with 3+ digits, except 128)
            upperLine.length >= 3 && upperLine != "128" && upperLine.all { it.isDigit() } -> "Car"
            
            // Default to Bus for everything else
            else -> "Bus"
        }
    }
}