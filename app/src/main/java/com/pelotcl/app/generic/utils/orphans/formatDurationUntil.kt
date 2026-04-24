package com.pelotcl.app.generic.utils.orphans

fun formatDurationUntil(
    nowNormalizedSeconds: Int,
    targetNormalizedSeconds: Int
): String {
    val remainingSeconds = (targetNormalizedSeconds - nowNormalizedSeconds).coerceAtLeast(0)
    if (remainingSeconds < 60) return "moins d'1 min"

    val remainingMinutes = remainingSeconds / 60
    return if (remainingMinutes < 60) {
        "$remainingMinutes min"
    } else {
        "${remainingMinutes / 60}h${(remainingMinutes % 60).toString().padStart(2, '0')}"
    }
}
