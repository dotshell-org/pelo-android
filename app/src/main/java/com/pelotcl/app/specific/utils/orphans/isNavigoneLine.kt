package com.pelotcl.app.specific.utils.orphans

fun isNavigoneLine(lineName: String): Boolean {
    val upperName = lineName.trim().uppercase()
    return upperName.startsWith("NAVI") || canonicalLineName(upperName) == "NAV1"
}
