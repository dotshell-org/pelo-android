package com.pelotcl.app.generic.data.repository.offline.search

/**
 * Model for a search history item that can be either a stop or a line
 */
data class SearchHistoryItem(
    val query: String,
    val type: SearchType,
    val lines: List<String> = emptyList(), // For stops: the lines serving the stop; For lines: empty
    val timestamp: Long = System.currentTimeMillis()
)