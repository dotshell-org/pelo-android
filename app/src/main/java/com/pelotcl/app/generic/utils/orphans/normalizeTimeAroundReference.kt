package com.pelotcl.app.generic.utils.orphans

fun normalizeTimeAroundReference(timeSeconds: Int, referenceSeconds: Int): Int {
    val day = 24 * 3600
    var normalized = timeSeconds
    while (normalized < referenceSeconds - day / 2) normalized += day
    while (normalized > referenceSeconds + day / 2) normalized -= day
    return normalized
}
