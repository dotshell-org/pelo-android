package com.pelotcl.app.core.extensions

/**
 * Normalizes a stop name by keeping only letters and converting to lowercase.
 * Used for comparing stop names regardless of accents, spaces, or case.
 *
 * @return Normalized string containing only lowercase letters
 */
fun String.normalizeStopName(): String {
    return this.filter { it.isLetter() }.lowercase()
}

/**
 * Normalizes a line name for comparison.
 * Handles special cases like NAVI1 → NAV1.
 *
 * @return Normalized line name in uppercase
 */
fun String.normalizeLineName(): String {
    return when (this.uppercase()) {
        "NAVI1" -> "NAV1"
        else -> this.uppercase()
    }
}

/**
 * Checks if a line is a "ligne forte" (metro, tram, funicular, or navigone).
 *
 * @return true if the line is a strong line, false otherwise
 */
fun String.isMetroTramOrFunicular(): Boolean {
    val upperName = this.uppercase()
    return when {
        upperName in setOf("A", "B", "C", "D") -> true
        upperName in setOf("F1", "F2") -> true
        upperName.startsWith("NAV") -> true
        upperName.startsWith("T") -> true
        upperName == "RX" -> true
        else -> false
    }
}

/**
 * Checks if a line is temporarily a bus (not a strong line).
 *
 * @return true if the line is a temporary bus, false otherwise
 */
fun String.isTemporaryBus(): Boolean {
    return !this.isMetroTramOrFunicular()
}

/**
 * Checks if a line can be tracked live.
 * Excludes metro, funicular, and navigone lines.
 *
 * @return true if the line can be tracked live, false otherwise
 */
fun String.isLiveTrackableLine(): Boolean {
    val upperName = this.uppercase()
    return when {
        upperName in setOf("A", "B", "C", "D") -> false // metro
        upperName in setOf("F1", "F2") -> false // funicular
        upperName.startsWith("NAV") -> false // Navigone
        upperName == "RX" -> false
        else -> true // bus + tram + trambus
    }
}