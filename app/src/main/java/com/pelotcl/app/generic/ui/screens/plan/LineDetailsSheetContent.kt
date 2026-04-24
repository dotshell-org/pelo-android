package com.pelotcl.app.generic.ui.screens.plan

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import com.pelotcl.app.generic.ui.viewmodel.TransportViewModel

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LineDetailsSheetContent(
    lineInfo: LineInfo,
    viewModel: TransportViewModel,
    selectedDirection: Int,
    onDirectionChange: (Int) -> Unit,
    onBackToStation: () -> Unit,
    onLineClick: (String) -> Unit = {},
    onStopClick: (String) -> Unit = {},
    onShowAllSchedules: (lineName: String, directionName: String, schedules: List<String>) -> Unit,
    onItineraryClick: (stopName: String) -> Unit = {},
    onHeaderClick: () -> Unit = {},
    favoriteStops: Set<String> = emptySet(),
    onToggleFavoriteStop: (String) -> Unit = {},
    onHeaderLineCountChanged: (Int) -> Unit = {}
) {
    LineDetailsBottomSheet(
        viewModel = viewModel,
        lineInfo = lineInfo,
        sheetState = null,
        selectedDirection = selectedDirection,
        onDirectionChange = onDirectionChange,
        onDismiss = {},
        onBackToStation = onBackToStation,
        onLineClick = onLineClick,
        onStopClick = onStopClick,
        onShowAllSchedules = onShowAllSchedules,
        onItineraryClick = onItineraryClick,
        onHeaderClick = onHeaderClick,
        favoriteStops = favoriteStops,
        onToggleFavoriteStop = onToggleFavoriteStop,
        onHeaderLineCountChanged = onHeaderLineCountChanged
    )
}