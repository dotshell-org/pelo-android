package com.pelotcl.app.generic.widget

data class UpcomingDeparture(
    val lineName: String,
    val directionName: String,
    val time: String,
    val minutesUntil: Long
)