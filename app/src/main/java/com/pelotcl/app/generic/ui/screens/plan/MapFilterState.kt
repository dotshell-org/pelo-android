package com.pelotcl.app.generic.ui.screens.plan

import com.pelotcl.app.generic.ui.viewmodel.TransportLinesUiState
import com.pelotcl.app.generic.ui.viewmodel.TransportStopsUiState

/**
 * Data class to hold map filter state for snapshotFlow.
 * Used to batch state changes and avoid excessive recompositions.
 */
data class MapFilterState(
    val sheetContentState: SheetContentState?,
    val selectedLine: LineInfo?,
    val uiState: TransportLinesUiState,
    val stopsUiState: TransportStopsUiState
)