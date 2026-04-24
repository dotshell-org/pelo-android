package com.pelotcl.app.specific.utils.orphans

fun isStrongLine(line: String): Boolean {
    val upperLine = line.uppercase()
    return when {
        upperLine in setOf("A", "B", "C", "D") -> true
        upperLine in setOf("F1", "F2") -> true
        upperLine.startsWith("NAVI") -> true
        upperLine.startsWith("T") -> true
        upperLine == "RX" -> true
        else -> false
    }
}