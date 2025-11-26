package com.pelotcl.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.pelotcl.app.ui.components.AllSchedulesSheetContent
import com.pelotcl.app.ui.components.LineDetailsBottomSheet
import com.pelotcl.app.ui.components.LineInfo
import com.pelotcl.app.ui.components.LinesBottomSheet
import com.pelotcl.app.ui.components.MapLibreView
import com.pelotcl.app.ui.components.StationBottomSheet
import com.pelotcl.app.ui.components.StationInfo
import com.pelotcl.app.ui.components.StationSearchResult
import com.pelotcl.app.ui.viewmodel.TransportLinesUiState
import com.pelotcl.app.ui.viewmodel.TransportStopsUiState
import com.pelotcl.app.ui.viewmodel.TransportViewModel
import com.pelotcl.app.utils.BusIconHelper
import com.pelotcl.app.utils.LineColorHelper
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource

private val ALWAYS_VISIBLE_LINES = setOf("F1", "F2", "A", "B", "C", "D", "NAV1", "RX")
private const val PRIORITY_STOPS_MIN_ZOOM = 12.5f
private const val TRAM_STOPS_MIN_ZOOM = 14.0f
private const val SECONDARY_STOPS_MIN_ZOOM = 15f
private const val SELECTED_STOP_MIN_ZOOM = 9.0f

private fun isMetroTramOrFunicular(lineName: String): Boolean {
    val upperName = lineName.uppercase()
    return when {
        upperName in setOf("A", "B", "C", "D") -> true
        upperName in setOf("F1", "F2") -> true
        upperName.startsWith("NAV") -> true
        upperName.startsWith("T") -> true
        upperName == "RX" -> true
        else -> false
    }
}

private fun isTemporaryBus(lineName: String): Boolean {
    return !isMetroTramOrFunicular(lineName)
}

data class AllSchedulesInfo(
    val lineName: String,
    val directionName: String,
    val schedules: List<String>
)

enum class SheetContentState {
    STATION,
    LINE_DETAILS,
    ALL_SCHEDULES
}

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen(
    modifier: Modifier = Modifier,
    contentPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(0.dp),
    viewModel: TransportViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(
                modelClass: Class<T>,
                extras: CreationExtras
            ): T {
                val application = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    ?: throw IllegalStateException("Application not found in CreationExtras")
                @Suppress("UNCHECKED_CAST")
                return TransportViewModel(application) as T
            }
        }
    ),
    onSheetStateChanged: (Boolean) -> Unit = {},
    showLinesSheet: Boolean = false,
    onLinesSheetDismiss: () -> Unit = {},
    searchSelectedStop: StationSearchResult? = null,
    onSearchSelectionHandled: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val stopsUiState by viewModel.stopsUiState.collectAsState()
    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Location state
    var userLocation by remember { mutableStateOf<LatLng?>(null) }
    var shouldCenterOnUser by remember { mutableStateOf(false) }

    // Bottom sheet state for BottomSheetScaffold
    val bottomSheetState = rememberStandardBottomSheetState(
        initialValue = androidx.compose.material3.SheetValue.Hidden,
        skipHiddenState = false
    )
    val scaffoldSheetState = rememberBottomSheetScaffoldState(
        bottomSheetState = bottomSheetState
    )
    var selectedStation by remember { mutableStateOf<StationInfo?>(null) }
    var selectedLine by remember { mutableStateOf<LineInfo?>(null) }

    var allSchedulesInfo by remember { mutableStateOf<AllSchedulesInfo?>(null) }

    var temporaryLoadedBusLines by remember { mutableStateOf<Set<String>>(emptySet()) }

    var sheetContentState by remember { mutableStateOf<SheetContentState?>(null) }

    LaunchedEffect(sheetContentState, selectedStation, selectedLine) {
        onSheetStateChanged(sheetContentState != null)

        if ((sheetContentState == SheetContentState.STATION && selectedStation != null) ||
            (sheetContentState == SheetContentState.LINE_DETAILS && selectedLine != null)) {
            scope.launch {
                scaffoldSheetState.bottomSheetState.expand()
            }
        }
    }

    val latestSheetContentState by rememberUpdatedState(sheetContentState)
    var previousSheetValue by remember { mutableStateOf<androidx.compose.material3.SheetValue?>(null) }
    LaunchedEffect(scaffoldSheetState.bottomSheetState.currentValue) {
        val current = scaffoldSheetState.bottomSheetState.currentValue
        val previous = previousSheetValue

        if (current != previous) {
            val justBecameHidden = current == androidx.compose.material3.SheetValue.Hidden
            val swipedDownToPartial = current == androidx.compose.material3.SheetValue.PartiallyExpanded && previous == androidx.compose.material3.SheetValue.Expanded

            if (justBecameHidden || (swipedDownToPartial && latestSheetContentState == SheetContentState.STATION)) {
                sheetContentState = null
            }
        }

        previousSheetValue = current
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.any { it.value }
        if (granted) {
            getLocation(context) { location ->
                userLocation = location
                shouldCenterOnUser = true
            }
        }
    }

    LaunchedEffect(Unit) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            getLocation(context) { location ->
                userLocation = location
                shouldCenterOnUser = true
            }
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadAllLines()
        viewModel.preloadStops()
    }

    var displayedLines by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(uiState, mapInstance) {
        val map = mapInstance ?: return@LaunchedEffect

        when (val state = uiState) {
            is TransportLinesUiState.Success -> {
                val currentLineNames = state.lines.map {
                    "${it.properties.ligne}-${it.properties.codeTrace}"
                }.toSet()

                val linesToRemove = displayedLines - currentLineNames
                linesToRemove.forEach { lineKey ->
                    removeLineFromMap(map, lineKey)
                }

                state.lines.forEach { feature ->
                    val lineKey = "${feature.properties.ligne}-${feature.properties.codeTrace}"
                    if (lineKey !in displayedLines) {
                        addLineToMap(map, feature)
                    }
                }

                displayedLines = currentLineNames
            }
            else -> {}
        }
    }

    // Handle selection from Search Bar
    LaunchedEffect(searchSelectedStop, stopsUiState, mapInstance) {
        if (searchSelectedStop != null && mapInstance != null && stopsUiState is TransportStopsUiState.Success) {
            val allStops = (stopsUiState as TransportStopsUiState.Success).stops

            val targetStop = allStops.find {
                it.properties.nom.equals(searchSelectedStop.stopName, ignoreCase = true)
            }

            if (targetStop != null) {
                val lines = BusIconHelper.getAllLinesForStop(targetStop)
                val stationInfo = StationInfo(
                    nom = targetStop.properties.nom,
                    lignes = lines,
                    isPmr = targetStop.properties.pmr,
                    desserte = targetStop.properties.desserte
                )

                if (sheetContentState == SheetContentState.LINE_DETAILS || sheetContentState == SheetContentState.ALL_SCHEDULES) {
                    selectedLine?.let { lineInfo ->
                        if (!isMetroTramOrFunicular(lineInfo.lineName)) {
                            viewModel.removeLineFromLoaded(lineInfo.lineName)
                        }
                    }
                    selectedLine = null
                    sheetContentState = null
                    kotlinx.coroutines.delay(100)
                }

                zoomToStop(mapInstance!!, stationInfo.nom, allStops)

                if (stationInfo.lignes.size == 1) {
                    selectedStation = stationInfo
                    val lineName = stationInfo.lignes[0]
                    selectedLine = LineInfo(
                        lineName = lineName,
                        currentStationName = stationInfo.nom
                    )

                    if (!isMetroTramOrFunicular(lineName)) {
                        viewModel.addLineToLoaded(lineName)
                        if (isTemporaryBus(lineName)) {
                            temporaryLoadedBusLines = temporaryLoadedBusLines + lineName
                        }
                        kotlinx.coroutines.delay(100)
                    }

                    sheetContentState = SheetContentState.LINE_DETAILS
                } else {
                    selectedStation = stationInfo
                    sheetContentState = SheetContentState.STATION
                }

                onSearchSelectionHandled()
            }
        }
    }

    LaunchedEffect(stopsUiState, mapInstance) {
        val map = mapInstance ?: return@LaunchedEffect

        when (val state = stopsUiState) {
            is TransportStopsUiState.Success -> {
                addStopsToMap(map, state.stops, context) { clickedStationInfo ->
                    scope.launch {
                        if (sheetContentState == SheetContentState.LINE_DETAILS || sheetContentState == SheetContentState.ALL_SCHEDULES) {
                            selectedLine?.let { lineInfo ->
                                val lineName = lineInfo.lineName
                                if (!isMetroTramOrFunicular(lineName)) {
                                    viewModel.removeLineFromLoaded(lineName)
                                }
                            }

                            selectedLine = null
                            sheetContentState = null

                            scaffoldSheetState.bottomSheetState.partialExpand()

                            kotlinx.coroutines.delay(300)
                        }

                        if (clickedStationInfo.lignes.size == 1) {
                            selectedStation = clickedStationInfo
                            val lineName = clickedStationInfo.lignes[0]
                            selectedLine = LineInfo(
                                lineName = lineName,
                                currentStationName = clickedStationInfo.nom
                            )

                            if (!isMetroTramOrFunicular(lineName)) {
                                viewModel.addLineToLoaded(lineName)
                                if (isTemporaryBus(lineName)) {
                                    temporaryLoadedBusLines = temporaryLoadedBusLines + lineName
                                }
                                kotlinx.coroutines.delay(100)
                            }

                            sheetContentState = SheetContentState.LINE_DETAILS
                        } else {
                            selectedStation = clickedStationInfo
                            sheetContentState = SheetContentState.STATION
                        }
                    }
                }
            }
            else -> {}
        }
    }

    LaunchedEffect(sheetContentState, selectedLine) {
        if (sheetContentState == SheetContentState.LINE_DETAILS && selectedLine != null) {
            val lineName = selectedLine!!.lineName

            if (!isMetroTramOrFunicular(lineName)) {
                viewModel.addLineToLoaded(lineName)
                if (isTemporaryBus(lineName)) {
                    temporaryLoadedBusLines = temporaryLoadedBusLines + lineName
                }
            }
        }
    }

    LaunchedEffect(sheetContentState) {
        if (sheetContentState != SheetContentState.LINE_DETAILS && sheetContentState != SheetContentState.ALL_SCHEDULES && temporaryLoadedBusLines.isNotEmpty()) {
            temporaryLoadedBusLines.forEach { busLine ->
                viewModel.removeLineFromLoaded(busLine)
            }
            temporaryLoadedBusLines = emptySet()
        }
    }

    LaunchedEffect(showLinesSheet, sheetContentState) {
        if (!showLinesSheet && sheetContentState != SheetContentState.LINE_DETAILS && sheetContentState != SheetContentState.ALL_SCHEDULES && temporaryLoadedBusLines.isNotEmpty()) {
            temporaryLoadedBusLines.forEach { busLine ->
                viewModel.removeLineFromLoaded(busLine)
            }
            temporaryLoadedBusLines = emptySet()
        }
    }

    LaunchedEffect(sheetContentState, selectedLine, uiState, mapInstance, stopsUiState) {
        val map = mapInstance ?: return@LaunchedEffect

        when (val state = uiState) {
            is TransportLinesUiState.Success -> {
                if ((sheetContentState == SheetContentState.LINE_DETAILS || sheetContentState == SheetContentState.ALL_SCHEDULES) && selectedLine != null) {
                    val selectedName = selectedLine!!.lineName
                    val hasSelectedInState = state.lines.any { it.properties.ligne.equals(selectedName, ignoreCase = true) }

                    if (!hasSelectedInState && isMetroTramOrFunicular(selectedName)) {
                        viewModel.reloadStrongLines()
                    }

                    filterMapLines(map, state.lines, selectedLine!!.lineName)

                    val selectedStopName = selectedLine!!.currentStationName.takeIf { it.isNotBlank() }
                    when (val stopsState = stopsUiState) {
                        is TransportStopsUiState.Success -> {
                            filterMapStopsWithSelectedStop(
                                map,
                                selectedLine!!.lineName,
                                selectedStopName,
                                stopsState.stops,
                                state.lines
                            )

                            if (selectedStopName != null) {
                                zoomToStop(map, selectedStopName, stopsState.stops)
                            } else {
                                zoomToLine(map, state.lines, selectedLine!!.lineName)
                            }
                        }
                        else -> {}
                    }
                } else {
                    showAllMapLines(map, state.lines)
                }
            }
            else -> {}
        }
    }

    val bottomPadding = contentPadding.calculateBottomPadding()

    val peekHeight = when(sheetContentState) {
        SheetContentState.LINE_DETAILS, SheetContentState.ALL_SCHEDULES -> bottomPadding + 150.dp
        SheetContentState.STATION -> 0.dp
        else -> 0.dp
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldSheetState,
        sheetPeekHeight = peekHeight,
        modifier = modifier,
        sheetContainerColor = Color.White,
        sheetContent = {
            Column(
                modifier = Modifier
                    .padding(bottom = bottomPadding)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when(sheetContentState) {
                    SheetContentState.LINE_DETAILS -> {
                        if (selectedLine != null) {
                            LineDetailsSheetContent(
                                lineInfo = selectedLine!!,
                                viewModel = viewModel,
                                onBackToStation = {
                                    scope.launch {
                                        scaffoldSheetState.bottomSheetState.hide()
                                    }
                                    selectedLine = null
                                    selectedStation = null
                                    sheetContentState = null
                                },
                                onStopClick = { stopName ->
                                    selectedLine = LineInfo(
                                        lineName = selectedLine!!.lineName,
                                        currentStationName = stopName
                                    )
                                    scope.launch {
                                        scaffoldSheetState.bottomSheetState.partialExpand()
                                    }
                                },
                                onShowAllSchedules = { lineName, directionName, schedules ->
                                    allSchedulesInfo = AllSchedulesInfo(lineName, directionName, schedules)
                                    sheetContentState = SheetContentState.ALL_SCHEDULES
                                }
                            )
                        }
                    }
                    SheetContentState.STATION -> {
                        if (selectedStation != null) {
                            StationSheetContent(
                                stationInfo = selectedStation!!,
                                onDismiss = {
                                    scope.launch {
                                        scaffoldSheetState.bottomSheetState.hide()
                                    }
                                    sheetContentState = null
                                },
                                onLineClick = { lineName ->
                                    selectedLine = LineInfo(
                                        lineName = lineName,
                                        currentStationName = selectedStation?.nom ?: ""
                                    )

                                    if (!isMetroTramOrFunicular(lineName)) {
                                        scope.launch {
                                            viewModel.addLineToLoaded(lineName)
                                            if (isTemporaryBus(lineName)) {
                                                temporaryLoadedBusLines = temporaryLoadedBusLines + lineName
                                            }
                                            kotlinx.coroutines.delay(100)
                                            sheetContentState = SheetContentState.LINE_DETAILS
                                        }
                                    } else {
                                        sheetContentState = SheetContentState.LINE_DETAILS
                                    }
                                }
                            )
                        }
                    }
                    SheetContentState.ALL_SCHEDULES -> {
                        if (allSchedulesInfo != null) {
                            AllSchedulesSheetContent(
                                allSchedulesInfo = allSchedulesInfo!!,
                                lineInfo = selectedLine!!,
                                onBack = {
                                    sheetContentState = SheetContentState.LINE_DETAILS
                                }
                            )
                        }
                    }
                    null -> {}
                }
            }
        }
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            MapLibreView(
                modifier = Modifier.fillMaxSize(),
                initialPosition = LatLng(45.75, 4.85),
                initialZoom = 12.0,
                styleUrl = "https://tiles.openfreemap.org/styles/positron",
                onMapReady = { map ->
                    mapInstance = map
                },
                searchResults = emptyList(),
                onSearch = {},
                userLocation = userLocation,
                centerOnUserLocation = shouldCenterOnUser
            )

            if (uiState is TransportLinesUiState.Loading || stopsUiState is TransportStopsUiState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }

    LaunchedEffect(shouldCenterOnUser) {
        if (shouldCenterOnUser) {
            shouldCenterOnUser = false
        }
    }

    if (showLinesSheet) {
        val modalBottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        LaunchedEffect(showLinesSheet) {
            if (!showLinesSheet) {
                modalBottomSheetState.hide()
            }
        }

        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = onLinesSheetDismiss,
            containerColor = Color.White,
            sheetState = modalBottomSheetState
        ) {
            LinesBottomSheet(
                allLines = viewModel.getAllAvailableLines(),
                onDismiss = onLinesSheetDismiss,
                onLineClick = { lineName ->
                    onLinesSheetDismiss()

                    if (!isMetroTramOrFunicular(lineName)) {
                        scope.launch {
                            viewModel.addLineToLoaded(lineName)
                            if (isTemporaryBus(lineName)) {
                                temporaryLoadedBusLines = temporaryLoadedBusLines + lineName
                            }
                            kotlinx.coroutines.delay(100)

                            selectedLine = LineInfo(
                                lineName = lineName,
                                currentStationName = ""
                            )
                            sheetContentState = SheetContentState.LINE_DETAILS
                            scaffoldSheetState.bottomSheetState.partialExpand()
                        }
                    } else {
                        scope.launch {
                            val currentState = uiState
                            val isLoaded = if (currentState is TransportLinesUiState.Success) {
                                currentState.lines.any { it.properties.ligne.equals(lineName, ignoreCase = true) }
                            } else {
                                false
                            }

                            if (!isLoaded) {
                                viewModel.addLineToLoaded(lineName)
                                kotlinx.coroutines.delay(100)
                            }

                            selectedLine = LineInfo(
                                lineName = lineName,
                                currentStationName = ""
                            )
                            sheetContentState = SheetContentState.LINE_DETAILS
                            scaffoldSheetState.bottomSheetState.partialExpand()
                        }
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StationSheetContent(
    stationInfo: StationInfo,
    onDismiss: () -> Unit,
    onLineClick: (String) -> Unit
) {
    StationBottomSheet(
        stationInfo = stationInfo,
        sheetState = null,
        onDismiss = onDismiss,
        onLineClick = onLineClick
    )
}

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LineDetailsSheetContent(
    lineInfo: LineInfo,
    viewModel: TransportViewModel,
    onBackToStation: () -> Unit,
    onStopClick: (String) -> Unit = {},
    onShowAllSchedules: (lineName: String, directionName: String, schedules: List<String>) -> Unit
) {
    LineDetailsBottomSheet(
        lineInfo = lineInfo,
        sheetState = null,
        viewModel = viewModel,
        onDismiss = {},
        onBackToStation = onBackToStation,
        onStopClick = onStopClick,
        onShowAllSchedules = onShowAllSchedules
    )
}

private fun filterMapLines(
    map: MapLibreMap,
    allLines: List<com.pelotcl.app.data.model.Feature>,
    selectedLineName: String
): Int {
    map.getStyle { style ->
        var foundCount = 0
        var notFoundCount = 0
        var madeVisibleCount = 0

        allLines.forEach { feature ->
            val layerId = "layer-${feature.properties.ligne}-${feature.properties.codeTrace}"
            val isSelectedLine = feature.properties.ligne.equals(selectedLineName, ignoreCase = true)

            val existingLayer = style.getLayer(layerId)

            if (existingLayer != null) {
                foundCount++
                if (isSelectedLine) {
                    existingLayer.setProperties(PropertyFactory.visibility("visible"))
                    madeVisibleCount++
                } else {
                    existingLayer.setProperties(PropertyFactory.visibility("none"))
                }
            } else {
                notFoundCount++
                if (isSelectedLine) {
                    addLineToMap(map, feature)
                    madeVisibleCount++
                }
            }
        }
    }
    val visibleCandidates = allLines.count { it.properties.ligne.equals(selectedLineName, ignoreCase = true) }
    return visibleCandidates
}

private fun zoomToLine(
    map: MapLibreMap,
    allLines: List<com.pelotcl.app.data.model.Feature>,
    selectedLineName: String
) {
    val lineFeatures = allLines.filter {
        it.properties.ligne.equals(selectedLineName, ignoreCase = true)
    }

    if (lineFeatures.isEmpty()) return

    val boundsBuilder = LatLngBounds.Builder()
    var hasCoordinates = false

    lineFeatures.forEach { feature ->
        feature.geometry.coordinates.forEach { lineString ->
            lineString.forEach { coord ->
                boundsBuilder.include(LatLng(coord[1], coord[0]))
                hasCoordinates = true
            }
        }
    }

    if (!hasCoordinates) return

    val bounds = boundsBuilder.build()

    val paddingLeft = 200
    val paddingTop = 100
    val paddingRight = 200
    val paddingBottom = 600

    map.animateCamera(
        CameraUpdateFactory.newLatLngBounds(bounds, paddingLeft, paddingTop, paddingRight, paddingBottom),
        1000
    )
}

private fun zoomToStop(
    map: MapLibreMap,
    stopName: String,
    allStops: List<com.pelotcl.app.data.model.StopFeature>
) {
    fun normalizeStopName(name: String): String {
        return name.filter { it.isLetter() }.lowercase()
    }

    val normalizedStopName = normalizeStopName(stopName)

    var stop = allStops.find {
        it.properties.nom.equals(stopName, ignoreCase = true)
    }

    if (stop == null) {
        stop = allStops.find {
            normalizeStopName(it.properties.nom) == normalizedStopName
        }
    }

    if (stop == null) {
        return
    }

    val lat = stop.geometry.coordinates[1]
    val lon = stop.geometry.coordinates[0]
    val stopLocation = LatLng(lat, lon)

    map.animateCamera(
        CameraUpdateFactory.newLatLngZoom(stopLocation, 15.0),
        1000
    )
}

private fun filterMapStops(
    style: org.maplibre.android.maps.Style,
    selectedLineName: String
) {
    val priorityLayerPrefix = "transport-stops-layer-priority"
    val tramLayerPrefix = "transport-stops-layer-tram"
    val secondaryLayerPrefix = "transport-stops-layer-secondary"

    val linePropertyName = "has_line_${selectedLineName.uppercase()}"

    (-25..25).forEach { idx ->
        val priorityLayerId = "$priorityLayerPrefix-$idx"
        val tramLayerId = "$tramLayerPrefix-$idx"
        val secondaryLayerId = "$secondaryLayerPrefix-$idx"

        (style.getLayer(priorityLayerId) as? SymbolLayer)?.let { layer ->
            layer.setFilter(
                Expression.all(
                    Expression.eq(Expression.get("stop_priority"), 2),
                    Expression.eq(Expression.get("slot"), idx),
                    Expression.eq(Expression.get(linePropertyName), true)
                )
            )
        }

        (style.getLayer(tramLayerId) as? SymbolLayer)?.let { layer ->
            layer.setFilter(
                Expression.all(
                    Expression.eq(Expression.get("stop_priority"), 1),
                    Expression.eq(Expression.get("slot"), idx),
                    Expression.eq(Expression.get(linePropertyName), true)
                )
            )
            layer.setMinZoom(PRIORITY_STOPS_MIN_ZOOM)
        }

        (style.getLayer(secondaryLayerId) as? SymbolLayer)?.let { layer ->
            layer.setFilter(
                Expression.all(
                    Expression.eq(Expression.get("stop_priority"), 0),
                    Expression.eq(Expression.get("slot"), idx),
                    Expression.eq(Expression.get(linePropertyName), true)
                )
            )
            layer.setMinZoom(PRIORITY_STOPS_MIN_ZOOM)
        }
    }
}

private fun filterMapStopsWithSelectedStop(
    map: MapLibreMap,
    selectedLineName: String,
    selectedStopName: String?,
    allStops: List<com.pelotcl.app.data.model.StopFeature>,
    allLines: List<com.pelotcl.app.data.model.Feature>
) {
    map.getStyle { style ->
        if (selectedStopName.isNullOrBlank()) {
            filterMapStops(style, selectedLineName)
            style.getLayer("line-stops-circles")?.let { style.removeLayer(it) }
            style.getSource("line-stops-circles-source")?.let { style.removeSource(it) }
            return@getStyle
        }

        fun normalizeStopName(name: String): String {
            return name.filter { it.isLetter() }.lowercase()
        }

        val normalizedSelectedStop = normalizeStopName(selectedStopName)
        val priorityLayerPrefix = "transport-stops-layer-priority"
        val tramLayerPrefix = "transport-stops-layer-tram"
        val secondaryLayerPrefix = "transport-stops-layer-secondary"
        val linePropertyName = "has_line_${selectedLineName.uppercase()}"

        (-25..25).forEach { idx ->
            val priorityLayerId = "$priorityLayerPrefix-$idx"
            val tramLayerId = "$tramLayerPrefix-$idx"
            val secondaryLayerId = "$secondaryLayerPrefix-$idx"

            (style.getLayer(priorityLayerId) as? SymbolLayer)?.let { layer ->
                layer.setFilter(
                    Expression.all(
                        Expression.eq(Expression.get("stop_priority"), 2),
                        Expression.eq(Expression.get("slot"), idx),
                        Expression.eq(Expression.get(linePropertyName), true),
                        Expression.eq(Expression.get("normalized_nom"), normalizedSelectedStop)
                    )
                )
                layer.setMinZoom(SELECTED_STOP_MIN_ZOOM)
            }

            (style.getLayer(tramLayerId) as? SymbolLayer)?.let { layer ->
                layer.setFilter(
                    Expression.all(
                        Expression.eq(Expression.get("stop_priority"), 1),
                        Expression.eq(Expression.get("slot"), idx),
                        Expression.eq(Expression.get(linePropertyName), true),
                        Expression.eq(Expression.get("normalized_nom"), normalizedSelectedStop)
                    )
                )
                layer.setMinZoom(SELECTED_STOP_MIN_ZOOM)
            }

            (style.getLayer(secondaryLayerId) as? SymbolLayer)?.let { layer ->
                layer.setFilter(
                    Expression.all(
                        Expression.eq(Expression.get("stop_priority"), 0),
                        Expression.eq(Expression.get("slot"), idx),
                        Expression.eq(Expression.get(linePropertyName), true),
                        Expression.eq(Expression.get("normalized_nom"), normalizedSelectedStop)
                    )
                )
                layer.setMinZoom(SELECTED_STOP_MIN_ZOOM)
            }
        }

        addCircleLayerForLineStops(
            style,
            selectedLineName,
            selectedStopName,
            allStops,
            allLines
        )
    }
}

private fun addCircleLayerForLineStops(
    style: org.maplibre.android.maps.Style,
    selectedLineName: String,
    selectedStopName: String,
    allStops: List<com.pelotcl.app.data.model.StopFeature>,
    allLines: List<com.pelotcl.app.data.model.Feature>
) {
    style.getLayer("line-stops-circles")?.let { style.removeLayer(it) }
    style.getSource("line-stops-circles-source")?.let { style.removeSource(it) }

    fun normalizeStopName(name: String): String {
        return name.filter { it.isLetter() }.lowercase()
    }

    val normalizedSelectedStop = normalizeStopName(selectedStopName)

    val lineColor = allLines
        .find { it.properties.ligne.equals(selectedLineName, ignoreCase = true) }
        ?.let { LineColorHelper.getColorForLine(it) }
        ?: "#EF4444"

    val lineStops = allStops.filter { stop ->
        val lines = BusIconHelper.getAllLinesForStop(stop)
        val hasLine = lines.any { it.equals(selectedLineName, ignoreCase = true) }
        val isNotSelected = normalizeStopName(stop.properties.nom) != normalizedSelectedStop
        hasLine && isNotSelected
    }

    val circlesGeoJson = JsonObject().apply {
        addProperty("type", "FeatureCollection")
        val features = JsonArray()

        lineStops.forEach { stop ->
            val pointFeature = JsonObject().apply {
                addProperty("type", "Feature")

                val pointGeometry = JsonObject().apply {
                    addProperty("type", "Point")
                    val coordinatesArray = JsonArray()
                    coordinatesArray.add(stop.geometry.coordinates[0])
                    coordinatesArray.add(stop.geometry.coordinates[1])
                    add("coordinates", coordinatesArray)
                }
                add("geometry", pointGeometry)

                val properties = JsonObject().apply {
                    addProperty("nom", stop.properties.nom)
                    addProperty("desserte", stop.properties.desserte)
                    addProperty("pmr", stop.properties.pmr)
                }
                add("properties", properties)
            }
            features.add(pointFeature)
        }

        add("features", features)
    }

    val circlesSource = GeoJsonSource("line-stops-circles-source", circlesGeoJson.toString())
    style.addSource(circlesSource)

    val circlesLayer = org.maplibre.android.style.layers.CircleLayer("line-stops-circles", "line-stops-circles-source").apply {
        setProperties(
            PropertyFactory.circleRadius(6f),
            PropertyFactory.circleColor("#FFFFFF"),
            PropertyFactory.circleStrokeWidth(4.5f),
            PropertyFactory.circleStrokeColor(lineColor),
            PropertyFactory.circleOpacity(1.0f),
            PropertyFactory.circleStrokeOpacity(1.0f)
        )
        setMinZoom(SELECTED_STOP_MIN_ZOOM)
    }
    style.addLayer(circlesLayer)
}

private fun showAllMapLines(
    map: MapLibreMap,
    allLines: List<com.pelotcl.app.data.model.Feature>
) {
    map.getStyle { style ->
        allLines.forEach { feature ->
            val layerId = "layer-${feature.properties.ligne}-${feature.properties.codeTrace}"
            val sourceId = "line-${feature.properties.ligne}-${feature.properties.codeTrace}"

            val existingLayer = style.getLayer(layerId)
            if (existingLayer == null) {
                addLineToMap(map, feature)
            } else {
                existingLayer.setProperties(PropertyFactory.visibility("visible"))
            }

            if (style.getSource(sourceId) == null) {
                addLineToMap(map, feature)
            }
        }

        showAllMapStops(style)

        style.getLayer("line-stops-circles")?.let { style.removeLayer(it) }
        style.getSource("line-stops-circles-source")?.let { style.removeSource(it) }
    }
}

private fun showAllMapStops(
    style: org.maplibre.android.maps.Style
) {
    val priorityLayerPrefix = "transport-stops-layer-priority"
    val tramLayerPrefix = "transport-stops-layer-tram"
    val secondaryLayerPrefix = "transport-stops-layer-secondary"

    (-25..25).forEach { idx ->
        (style.getLayer("$priorityLayerPrefix-$idx") as? SymbolLayer)?.let { layer ->
            layer.setFilter(
                Expression.all(
                    Expression.eq(Expression.get("stop_priority"), 2),
                    Expression.eq(Expression.get("slot"), idx)
                )
            )
            layer.setMinZoom(PRIORITY_STOPS_MIN_ZOOM)
        }

        (style.getLayer("$tramLayerPrefix-$idx") as? SymbolLayer)?.let { layer ->
            layer.setFilter(
                Expression.all(
                    Expression.eq(Expression.get("stop_priority"), 1),
                    Expression.eq(Expression.get("slot"), idx)
                )
            )
            layer.setMinZoom(TRAM_STOPS_MIN_ZOOM)
        }

        (style.getLayer("$secondaryLayerPrefix-$idx") as? SymbolLayer)?.let { layer ->
            layer.setFilter(
                Expression.all(
                    Expression.eq(Expression.get("stop_priority"), 0),
                    Expression.eq(Expression.get("slot"), idx)
                )
            )
            layer.setMinZoom(SECONDARY_STOPS_MIN_ZOOM)
        }
    }
}

private fun addLineToMap(
    map: MapLibreMap,
    feature: com.pelotcl.app.data.model.Feature
) {
    map.getStyle { style ->
        val sourceId = "line-${feature.properties.ligne}-${feature.properties.codeTrace}"
        val layerId = "layer-${feature.properties.ligne}-${feature.properties.codeTrace}"

        style.getLayer(layerId)?.let { style.removeLayer(it) }
        style.getSource(sourceId)?.let { style.removeSource(it) }

        val lineGeoJson = createGeoJsonFromFeature(feature)

        val lineSource = GeoJsonSource(sourceId, lineGeoJson)
        style.addSource(lineSource)

        val lineColor = LineColorHelper.getColorForLine(feature)

        val upperLineName = feature.properties.ligne.uppercase()
        val lineWidth = when {
            feature.properties.familleTransport == "BAT" || upperLineName.startsWith("NAV") -> 2f
            feature.properties.familleTransport == "TRA" || feature.properties.familleTransport == "TRAM" || upperLineName.startsWith("TB") -> 2f
            else -> 4f
        }

        val lineLayer = LineLayer(layerId, sourceId).apply {
            setProperties(
                PropertyFactory.lineColor(lineColor),
                PropertyFactory.lineWidth(lineWidth),
                PropertyFactory.lineOpacity(0.8f),
                PropertyFactory.lineCap("round"),
                PropertyFactory.lineJoin("round")
            )
        }

        val firstStopLayer = style.layers.find { it.id.startsWith("transport-stops-layer") }
        if (firstStopLayer != null) {
            style.addLayerBelow(lineLayer, firstStopLayer.id)
        } else {
            style.addLayer(lineLayer)
        }
    }
}

private fun removeLineFromMap(
    map: MapLibreMap,
    lineKey: String
) {
    map.getStyle { style ->
        val sourceId = "line-$lineKey"
        val layerId = "layer-$lineKey"

        style.getLayer(layerId)?.let {
            style.removeLayer(it)
        }
        style.getSource(sourceId)?.let {
            style.removeSource(it)
        }
    }
}

private fun addStopsToMap(
    map: MapLibreMap,
    stops: List<com.pelotcl.app.data.model.StopFeature>,
    context: android.content.Context,
    onStationClick: (StationInfo) -> Unit = {}
) {
    map.getStyle { style ->
        val sourceId = "transport-stops"
        val priorityLayerPrefix = "transport-stops-layer-priority"
        val tramLayerPrefix = "transport-stops-layer-tram"
        val secondaryLayerPrefix = "transport-stops-layer-secondary"

        (1..3).forEach { idx ->
            val pId = "$priorityLayerPrefix-$idx"
            val tId = "$tramLayerPrefix-$idx"
            val sId = "$secondaryLayerPrefix-$idx"
            style.getLayer(pId)?.let { style.removeLayer(it) }
            style.getLayer(tId)?.let { style.removeLayer(it) }
            style.getLayer(sId)?.let { style.removeLayer(it) }
        }
        (-25..25).forEach { idx ->
            val pId = "$priorityLayerPrefix-$idx"
            val tId = "$tramLayerPrefix-$idx"
            val sId = "$secondaryLayerPrefix-$idx"
            style.getLayer(pId)?.let { style.removeLayer(it) }
            style.getLayer(tId)?.let { style.removeLayer(it) }
            style.getLayer(sId)?.let { style.removeLayer(it) }
        }
        style.getSource(sourceId)?.let { style.removeSource(it) }

        val requiredIcons = mutableSetOf<String>()
        val validStops = mutableListOf<com.pelotcl.app.data.model.StopFeature>()
        val usedSlots = mutableSetOf<Int>()

        stops.forEach { stop ->
            val drawableNames = BusIconHelper.getAllDrawableNamesForStop(stop)
            val availableNames = drawableNames.filter { name ->
                val id = context.resources.getIdentifier(name, "drawable", context.packageName)
                id != 0
            }
            if (availableNames.isNotEmpty()) {
                requiredIcons.addAll(availableNames)
                validStops.add(stop)
                val n = availableNames.size
                val start = -(n - 1)
                var slot = start
                repeat(n) {
                    usedSlots.add(slot)
                    slot += 2
                }
            }
        }

        requiredIcons.forEach { iconName ->
            try {
                val resourceId = context.resources.getIdentifier(iconName, "drawable", context.packageName)
                if (resourceId != 0) {
                    val drawable = ContextCompat.getDrawable(context, resourceId)
                    drawable?.let {
                        style.addImage(iconName, it)
                    }
                }
            } catch (e: Exception) {
                println("Error loading icon $iconName: ${e.message}")
            }
        }

        val stopsGeoJson = createStopsGeoJsonFromStops(validStops, context)

        val stopsSource = GeoJsonSource(sourceId, stopsGeoJson)
        style.addSource(stopsSource)

        val iconSizesPriority = 0.7f
        val iconSizesSecondary = 0.62f

        usedSlots.sorted().forEach { slotIndex ->
            val yOffset = slotIndex * 13f

            val priorityLayerId = "$priorityLayerPrefix-$slotIndex"
            val priorityLayer = SymbolLayer(priorityLayerId, sourceId).apply {
                setProperties(
                    PropertyFactory.iconImage("{icon}"),
                    PropertyFactory.iconSize(iconSizesPriority),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconIgnorePlacement(true),
                    PropertyFactory.iconAnchor("center"),
                    PropertyFactory.iconOffset(arrayOf(0f, yOffset))
                )
                setFilter(
                    Expression.all(
                        Expression.eq(Expression.get("stop_priority"), 2),
                        Expression.eq(Expression.get("slot"), slotIndex)
                    )
                )
                setMinZoom(PRIORITY_STOPS_MIN_ZOOM)
            }
            style.addLayer(priorityLayer)

            val tramLayerId = "$tramLayerPrefix-$slotIndex"
            val tramLayer = SymbolLayer(tramLayerId, sourceId).apply {
                setProperties(
                    PropertyFactory.iconImage("{icon}"),
                    PropertyFactory.iconSize(iconSizesPriority),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconIgnorePlacement(true),
                    PropertyFactory.iconAnchor("center"),
                    PropertyFactory.iconOffset(arrayOf(0f, yOffset))
                )
                setFilter(
                    Expression.all(
                        Expression.eq(Expression.get("stop_priority"), 1),
                        Expression.eq(Expression.get("slot"), slotIndex)
                    )
                )
                setMinZoom(TRAM_STOPS_MIN_ZOOM)
            }
            style.addLayer(tramLayer)

            val secondaryLayerId = "$secondaryLayerPrefix-$slotIndex"
            val secondaryLayer = SymbolLayer(secondaryLayerId, sourceId).apply {
                setProperties(
                    PropertyFactory.iconImage("{icon}"),
                    PropertyFactory.iconSize(iconSizesSecondary),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconIgnorePlacement(true),
                    PropertyFactory.iconAnchor("center"),
                    PropertyFactory.iconOffset(arrayOf(0f, yOffset))
                )
                setFilter(
                    Expression.all(
                        Expression.eq(Expression.get("stop_priority"), 0),
                        Expression.eq(Expression.get("slot"), slotIndex)
                    )
                )
                setMinZoom(SECONDARY_STOPS_MIN_ZOOM)
            }
            style.addLayer(secondaryLayer)
        }

        map.addOnMapClickListener { point ->
            val pixel = map.projection.toScreenLocation(point)

            val circleFeatures = map.queryRenderedFeatures(pixel, "line-stops-circles")
            if (circleFeatures.isNotEmpty()) {
                val feature = circleFeatures.first()
                val stationName = feature.getStringProperty("nom") ?: ""
                val desserte = feature.getStringProperty("desserte") ?: ""
                val isPmr = feature.getBooleanProperty("pmr") ?: false

                val lines = BusIconHelper.getAllLinesForStop(
                    com.pelotcl.app.data.model.StopFeature(
                        type = "Feature",
                        id = "",
                        geometry = com.pelotcl.app.data.model.StopGeometry("Point", listOf(0.0, 0.0)),
                        properties = com.pelotcl.app.data.model.StopProperties(
                            id = 0,
                            nom = stationName,
                            desserte = desserte,
                            pmr = isPmr,
                            ascenseur = false,
                            escalator = false,
                            gid = 0,
                            lastUpdate = "",
                            lastUpdateFme = "",
                            adresse = "",
                            localiseFaceAAdresse = false,
                            commune = "",
                            insee = "",
                            zone = ""
                        )
                    )
                )

                val stationInfo = StationInfo(
                    nom = stationName,
                    lignes = lines,
                    isPmr = isPmr,
                    desserte = desserte
                )

                onStationClick(stationInfo)
                return@addOnMapClickListener true
            }

            val allLayerIds = usedSlots.flatMap { idx ->
                listOf(
                    "$priorityLayerPrefix-$idx",
                    "$tramLayerPrefix-$idx",
                    "$secondaryLayerPrefix-$idx"
                )
            }

            val features = map.queryRenderedFeatures(pixel, *allLayerIds.toTypedArray())

            if (features.isNotEmpty()) {
                val feature = features.first()

                val stationName = feature.getStringProperty("nom") ?: ""
                val desserte = feature.getStringProperty("desserte") ?: ""
                val isPmr = feature.getBooleanProperty("pmr") ?: false

                val lines = BusIconHelper.getAllLinesForStop(
                    com.pelotcl.app.data.model.StopFeature(
                        type = "Feature",
                        id = "",
                        geometry = com.pelotcl.app.data.model.StopGeometry("Point", listOf(0.0, 0.0)),
                        properties = com.pelotcl.app.data.model.StopProperties(
                            id = 0,
                            nom = stationName,
                            desserte = desserte,
                            pmr = isPmr,
                            ascenseur = false,
                            escalator = false,
                            gid = 0,
                            lastUpdate = "",
                            lastUpdateFme = "",
                            adresse = "",
                            localiseFaceAAdresse = false,
                            commune = "",
                            insee = "",
                            zone = ""
                        )
                    )
                )

                val stationInfo = StationInfo(
                    nom = stationName,
                    lignes = lines,
                    isPmr = isPmr,
                    desserte = desserte
                )

                onStationClick(stationInfo)
                true
            } else {
                false
            }
        }
    }
}

private fun createGeoJsonFromFeature(feature: com.pelotcl.app.data.model.Feature): String {
    val geoJsonObject = JsonObject().apply {
        addProperty("type", "Feature")

        val geometryObject = JsonObject().apply {
            addProperty("type", feature.geometry.type)

            val coordinatesArray = JsonArray()
            feature.geometry.coordinates.forEach { lineString ->
                val lineStringArray = JsonArray()
                lineString.forEach { point ->
                    val pointArray = JsonArray()
                    point.forEach { coord ->
                        pointArray.add(coord)
                    }
                    lineStringArray.add(pointArray)
                }
                coordinatesArray.add(lineStringArray)
            }
            add("coordinates", coordinatesArray)
        }
        add("geometry", geometryObject)

        val propertiesObject = JsonObject().apply {
            addProperty("ligne", feature.properties.ligne)
            addProperty("nom_trace", feature.properties.nomTrace)
            addProperty("couleur", feature.properties.couleur)
        }
        add("properties", propertiesObject)
    }

    return geoJsonObject.toString()
}

private fun mergeStopsByName(stops: List<com.pelotcl.app.data.model.StopFeature>): List<com.pelotcl.app.data.model.StopFeature> {
    fun isStrongLine(line: String): Boolean {
        val upperLine = line.uppercase()
        return when {
            upperLine in setOf("A", "B", "C", "D") -> true
            upperLine in setOf("F1", "F2") -> true
            upperLine.startsWith("NAV") -> true
            upperLine.startsWith("T") -> true
            upperLine == "RX" -> true
            else -> false
        }
    }

    fun normalizeStopName(name: String): String {
        return name.filter { it.isLetter() }.lowercase()
    }

    val strongLineStops = mutableListOf<com.pelotcl.app.data.model.StopFeature>()
    val weakLineStops = mutableListOf<com.pelotcl.app.data.model.StopFeature>()

    stops.forEach { stop ->
        val allLines = BusIconHelper.getAllLinesForStop(stop)
        val strongLines = allLines.filter { isStrongLine(it) }
        val weakLines = allLines.filter { !isStrongLine(it) }

        if (strongLines.isNotEmpty()) {
            val strongDesserte = strongLines.joinToString(", ")
            strongLineStops.add(
                com.pelotcl.app.data.model.StopFeature(
                    type = stop.type,
                    id = stop.id,
                    geometry = stop.geometry,
                    properties = com.pelotcl.app.data.model.StopProperties(
                        id = stop.properties.id,
                        nom = stop.properties.nom,
                        desserte = strongDesserte,
                        pmr = stop.properties.pmr,
                        ascenseur = stop.properties.ascenseur,
                        escalator = stop.properties.escalator,
                        gid = stop.properties.gid,
                        lastUpdate = stop.properties.lastUpdate ?: "",
                        lastUpdateFme = stop.properties.lastUpdateFme ?: "",
                        adresse = stop.properties.adresse ?: "",
                        localiseFaceAAdresse = stop.properties.localiseFaceAAdresse,
                        commune = stop.properties.commune ?: "",
                        insee = stop.properties.insee ?: "",
                        zone = stop.properties.zone ?: ""
                    )
                )
            )
        }

        if (weakLines.isNotEmpty()) {
            val weakDesserte = weakLines.joinToString(", ")
            weakLineStops.add(
                com.pelotcl.app.data.model.StopFeature(
                    type = stop.type,
                    id = "${stop.id}-weak",
                    geometry = stop.geometry,
                    properties = com.pelotcl.app.data.model.StopProperties(
                        id = stop.properties.id,
                        nom = stop.properties.nom,
                        desserte = weakDesserte,
                        pmr = stop.properties.pmr,
                        ascenseur = stop.properties.ascenseur,
                        escalator = stop.properties.escalator,
                        gid = stop.properties.gid,
                        lastUpdate = stop.properties.lastUpdate ?: "",
                        lastUpdateFme = stop.properties.lastUpdateFme ?: "",
                        adresse = stop.properties.adresse ?: "",
                        localiseFaceAAdresse = stop.properties.localiseFaceAAdresse,
                        commune = stop.properties.commune ?: "",
                        insee = stop.properties.insee ?: "",
                        zone = stop.properties.zone ?: ""
                    )
                )
            )
        }
    }

    val strongStopsByName = strongLineStops.groupBy { normalizeStopName(it.properties.nom) }

    val mergedStrongStops = strongStopsByName.map { (normalizedName, stopsGroup) ->
        if (stopsGroup.size == 1) {
            stopsGroup.first()
        } else {
            val mergedDesserte = stopsGroup
                .flatMap { BusIconHelper.getAllLinesForStop(it) }
                .distinct()
                .sorted()
                .joinToString(", ")

            val firstStop = stopsGroup.first()
            val isPmr = stopsGroup.any { it.properties.pmr }

            com.pelotcl.app.data.model.StopFeature(
                type = firstStop.type,
                id = firstStop.id,
                geometry = firstStop.geometry,
                properties = com.pelotcl.app.data.model.StopProperties(
                    id = firstStop.properties.id,
                    nom = firstStop.properties.nom,
                    desserte = mergedDesserte,
                    pmr = isPmr,
                    ascenseur = firstStop.properties.ascenseur,
                    escalator = firstStop.properties.escalator,
                    gid = firstStop.properties.gid,
                    lastUpdate = firstStop.properties.lastUpdate ?: "",
                    lastUpdateFme = firstStop.properties.lastUpdateFme ?: "",
                    adresse = firstStop.properties.adresse ?: "",
                    localiseFaceAAdresse = firstStop.properties.localiseFaceAAdresse,
                    commune = firstStop.properties.commune ?: "",
                    insee = firstStop.properties.insee ?: "",
                    zone = firstStop.properties.zone ?: ""
                )
            )
        }
    }

    return mergedStrongStops + weakLineStops
}

private fun createStopsGeoJsonFromStops(
    stops: List<com.pelotcl.app.data.model.StopFeature>,
    context: android.content.Context
): String {
    val features = JsonArray()

    val mergedStops = mergeStopsByName(stops)

    mergedStops.forEach { stop ->
        val lineNamesAll = BusIconHelper.getAllLinesForStop(stop)
        if (lineNamesAll.isEmpty()) return@forEach

        val drawableNamesAll = BusIconHelper.getAllDrawableNamesForStop(stop)

        val validPairs = lineNamesAll.zip(drawableNamesAll).filter { (_, drawableName) ->
            val id = context.resources.getIdentifier(drawableName, "drawable", context.packageName)
            id != 0
        }

        if (validPairs.isEmpty()) return@forEach

        val lineNames = validPairs.map { it.first }
        val drawableNames = validPairs.map { it.second }

        val hasTram = lineNames.any { it.uppercase().startsWith("T") }

        val n = drawableNames.size
        var slot = -(n - 1)

        drawableNames.forEachIndexed { index, iconName ->
            val lineName = lineNames[index].uppercase()

            val stopPriority = when {
                isMetroTramOrFunicular(lineName) && !lineName.startsWith("T") -> 2
                lineName.startsWith("T") -> 1
                else -> 0
            }

            val pointFeature = JsonObject().apply {
                addProperty("type", "Feature")

                val pointGeometry = JsonObject().apply {
                    addProperty("type", "Point")
                    val coordinatesArray = JsonArray()
                    coordinatesArray.add(stop.geometry.coordinates[0])
                    coordinatesArray.add(stop.geometry.coordinates[1])
                    add("coordinates", coordinatesArray)
                }
                add("geometry", pointGeometry)

                val properties = JsonObject().apply {
                    addProperty("nom", stop.properties.nom)
                    addProperty("desserte", stop.properties.desserte)
                    addProperty("pmr", stop.properties.pmr)
                    addProperty("type", "stop")
                    addProperty("stop_priority", stopPriority)
                    addProperty("has_tram", hasTram)
                    addProperty("icon", iconName)
                    addProperty("slot", slot)

                    val normalizedNom = stop.properties.nom.filter { it.isLetter() }.lowercase()
                    addProperty("normalized_nom", normalizedNom)

                    lineNamesAll.forEach { line ->
                        addProperty("has_line_${line.uppercase()}", true)
                    }
                }
                add("properties", properties)
            }
            features.add(pointFeature)
            slot += 2
        }
    }

    val geoJsonCollection = JsonObject().apply {
        addProperty("type", "FeatureCollection")
        add("features", features)
    }

    return geoJsonCollection.toString()
}

private fun getLocation(
    context: android.content.Context,
    onSuccess: (LatLng) -> Unit
) {
    try {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        val cancellationTokenSource = CancellationTokenSource()

        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            cancellationTokenSource.token
        ).addOnSuccessListener { location ->
            if (location != null) {
                onSuccess(LatLng(location.latitude, location.longitude))
            } else {
                fusedLocationClient.lastLocation.addOnSuccessListener { lastLocation ->
                    if (lastLocation != null) {
                        onSuccess(LatLng(lastLocation.latitude, lastLocation.longitude))
                    }
                }
            }
        }
    } catch (e: SecurityException) {
        // Permission denied
    }
}