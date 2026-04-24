package com.pelotcl.app.specific.utils.orphans

/**
 * Returns the mode icon name for a bus line.
 * - Chrono lines (C1, C2, etc.) -> mode_chrono
 * - JD lines (JD...) -> mode_jd
 * - Regular bus -> mode_bus
 * Returns null for lignes fortes (metro, tram, funicular)
 */
fun getModeIconForLine(lineName: String): String? {
    val upperName = lineName.uppercase()
    return when {
        isMetroTramOrFunicular(lineName) -> null // No mode icon for lignes fortes
        upperName.startsWith("C") && upperName.substring(1).toIntOrNull() != null -> "mode_chrono"
        upperName.startsWith("JD") -> "mode_jd"
        else -> "mode_bus"
    }
}