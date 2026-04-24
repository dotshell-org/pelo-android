package com.pelotcl.app.generic.ui.screens.plan

import androidx.compose.runtime.Immutable

/**
 * Represents a selected stop for the itinerary
 */
@Immutable
data class SelectedStop(
    val name: String,
    val stopIds: List<Int>
)