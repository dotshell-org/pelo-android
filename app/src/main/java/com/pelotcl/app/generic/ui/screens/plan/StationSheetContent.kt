package com.pelotcl.app.generic.ui.screens.plan

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import com.pelotcl.app.generic.data.models.stops.StationInfo
import com.pelotcl.app.generic.ui.components.StationBottomSheet
import com.pelotcl.app.generic.ui.viewmodel.TransportViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationSheetContent(
    stationInfo: StationInfo,
    viewModel: TransportViewModel,
    onDismiss: () -> Unit,
    onDepartureClick: (lineName: String, directionId: Int, departureTime: String) -> Unit,
    isFavoriteStop: Boolean = false,
    onToggleFavoriteStop: () -> Unit = {},
    onAddFavoriteClick: (String) -> Unit = {},
    onItineraryClick: (String) -> Unit = {},
    onReportAlertClick: (String, List<String>) -> Unit = { _, _ -> }
) {
    StationBottomSheet(
        stationInfo = stationInfo,
        sheetState = null,
        onDismiss = onDismiss,
        viewModel = viewModel,
        onDepartureClick = onDepartureClick,
        isFavoriteStop = isFavoriteStop,
        onToggleFavoriteStop = onToggleFavoriteStop,
        onAddFavoriteClick = onAddFavoriteClick,
        onItineraryClick = { onItineraryClick(stationInfo.nom) },
        onReportAlertClick = onReportAlertClick
    )
}