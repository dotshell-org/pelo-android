package com.pelotcl.app.generic.data.models

import androidx.compose.runtime.Immutable

/**
 * Represents a stop on a line with its order and information
 */
@Immutable
data class LineStopInfo(
    val stopId: String,
    val stopName: String,
    val stopSequence: Int,
    val isCurrentStop: Boolean = false,
    val connections: List<String> = emptyList()
)
