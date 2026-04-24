package com.pelotcl.app.generic.ui.components.search

import androidx.compose.runtime.Immutable

@Immutable
data class StationSearchResult(
    val stopName: String,
    val lines: List<String>,
    val stopId: Int? = null
)