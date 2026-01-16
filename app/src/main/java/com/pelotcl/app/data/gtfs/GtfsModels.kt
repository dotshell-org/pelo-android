package com.pelotcl.app.data.gtfs

/**
 * Represents a stop on a line with its order and information
 */
data class LineStopInfo(
    val stopId: String,
    val stopName: String,
    val stopSequence: Int,
    val isCurrentStop: Boolean = false,
    val connections: List<String> = emptyList() // List of metro/funicular lines in transfer
)
