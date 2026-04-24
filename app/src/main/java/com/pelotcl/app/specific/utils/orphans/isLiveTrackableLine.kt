package com.pelotcl.app.specific.utils.orphans

fun isLiveTrackableLine(lineName: String): Boolean {
    val upperName = lineName.uppercase()
    return when {
        upperName in setOf("A", "B", "C", "D") -> false // metro
        upperName in setOf("F1", "F2") -> false // funicular
        isNavigoneLine(upperName) -> false // Navigone
        upperName == "RX" -> false
        upperName.startsWith("T") -> true // tram et trambus
        else -> true // bus
    }
}
