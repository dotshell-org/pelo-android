package com.pelotcl.app.generic.ui.components.search

import androidx.compose.runtime.Immutable

@Immutable
data class LineSearchResult(
    val lineName: String,
    val category: String = ""
)