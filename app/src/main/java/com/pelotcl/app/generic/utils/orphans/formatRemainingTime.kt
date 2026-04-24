package com.pelotcl.app.generic.utils.orphans

fun formatRemainingTime(
    departureTimeSeconds: Int,
    arrivalTimeSeconds: Int,
    nowSeconds: Int
): String {
    val secondsInDay = 24 * 3600
    val fullTripSeconds = if (arrivalTimeSeconds >= departureTimeSeconds) {
        arrivalTimeSeconds - departureTimeSeconds
    } else {
        arrivalTimeSeconds + secondsInDay - departureTimeSeconds
    }

    val elapsedSinceDeparture = if (nowSeconds >= departureTimeSeconds) {
        nowSeconds - departureTimeSeconds
    } else {
        nowSeconds + secondsInDay - departureTimeSeconds
    }

    val remainingSeconds = if (elapsedSinceDeparture in 0..fullTripSeconds) {
        fullTripSeconds - elapsedSinceDeparture
    } else {
        fullTripSeconds
    }

    val remainingMinutes = (remainingSeconds / 60).coerceAtLeast(0)
    return if (remainingMinutes < 60) {
        "$remainingMinutes min"
    } else {
        "${remainingMinutes / 60}h${(remainingMinutes % 60).toString().padStart(2, '0')}"
    }
}
