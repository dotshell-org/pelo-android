package com.pelotcl.app.specific.utils.orphans

fun canonicalLineName(lineName: String): String {
    return when (val upperName = lineName.trim().uppercase()) {
        "NAVI1" -> "NAV1"
        else -> upperName
    }
}
