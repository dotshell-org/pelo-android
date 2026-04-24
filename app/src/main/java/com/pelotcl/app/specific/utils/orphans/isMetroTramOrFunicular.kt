package com.pelotcl.app.specific.utils.orphans

fun isMetroTramOrFunicular(lineName: String): Boolean {
    val upperName = lineName.uppercase()
    return when {
        upperName in setOf("A", "B", "C", "D") -> true
        upperName in setOf("F1", "F2") -> true
        isNavigoneLine(upperName) -> true
        upperName.startsWith("T") -> true
        upperName == "RX" -> true
        else -> false
    }
}