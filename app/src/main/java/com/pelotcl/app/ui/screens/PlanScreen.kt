package com.pelotcl.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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

private val ALWAYS_VISIBLE_LINES = setOf("F1", "F2", "A", "B", "C", "D", "NAV1")
private const val PRIORITY_STOPS_MIN_ZOOM = 12.5f
private const val SECONDARY_STOPS_MIN_ZOOM = 15f
private const val SELECTED_STOP_MIN_ZOOM = 9.0f // Zoom minimum when a specific stop is selected (much more permissive)

/**
 * Détermine si une ligne est un métro, tram, funiculaire ou navigone (pas un bus)
 */
private fun isMetroTramOrFunicular(lineName: String): Boolean {
    val upperName = lineName.uppercase()
    return when {
        // Metros
        upperName in setOf("A", "B", "C", "D") -> true
        // Funiculaires
        upperName in setOf("F1", "F2") -> true
        // Navigone
        upperName.startsWith("NAV") -> true
        // Trams (commence par T)
        upperName.startsWith("T") -> true
        // Sinon c'est un bus
        else -> false
    }
}

/**
 * Vérifie si une ligne est un bus qui doit être déchargé quand on quitte la vue des détails.
 * Tous les bus (y compris C, PL, JD) doivent être déchargés car ils ne sont pas
 * chargés par défaut au démarrage de l'application.
 * Les lignes fortes (métro, tram, funiculaire, navigone) sont toujours chargées.
 */
private fun isTemporaryBus(lineName: String): Boolean {
    // Metros, trams, funiculars and navigone are always loaded, donc pas temporaires
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
    onLinesSheetDismiss: () -> Unit = {}
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
    // Enable Hidden state so we can call hide() safely without crashing
    val bottomSheetState = rememberStandardBottomSheetState(
        // Start hidden; we control expansion programmatically
        initialValue = androidx.compose.material3.SheetValue.Hidden,
        skipHiddenState = false
    )
    val scaffoldSheetState = rememberBottomSheetScaffoldState(
        bottomSheetState = bottomSheetState
    )
    var selectedStation by remember { mutableStateOf<StationInfo?>(null) }
    var selectedLine by remember { mutableStateOf<LineInfo?>(null) }
    
    // State for the new AllSchedulesBottomSheet
    var allSchedulesInfo by remember { mutableStateOf<AllSchedulesInfo?>(null) }
    
    // Track temporarily loaded bus lines (to unload them when exiting line details)
    var temporaryLoadedBusLines by remember { mutableStateOf<Set<String>>(emptySet()) }
    
    // Determine which content to show: station info or line details
    var sheetContentState by remember { mutableStateOf<SheetContentState?>(null) }
    


    
    // Notify parent when sheet state changes
    LaunchedEffect(sheetContentState) {
        onSheetStateChanged(sheetContentState != null)
    }


    
    // Monitor bottom sheet state changes to detect when user swipes down to dismiss
    // Fix: don't treat the initial Hidden state as a dismissal. Only react when transitioning
    // from a visible state (Expanded/PartiallyExpanded) to Hidden.
    var previousSheetValue by remember { mutableStateOf<androidx.compose.material3.SheetValue?>(null) }
    LaunchedEffect(scaffoldSheetState.bottomSheetState.currentValue) {
        val current = scaffoldSheetState.bottomSheetState.currentValue
        val prev = previousSheetValue
        val transitionedToHidden = (prev != null && prev != androidx.compose.material3.SheetValue.Hidden && current == androidx.compose.material3.SheetValue.Hidden)
        if (transitionedToHidden) {
            sheetContentState = null
        }
        previousSheetValue = current
    }
    
    // Permission launcher - must be registered before STARTED lifecycle state
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.any { it.value }
        if (granted) {
            // Get location once permission is granted
            getLocation(context) { location ->
                userLocation = location
                shouldCenterOnUser = true
            }
        }
    }
    
    // Check and request location permission
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
            // Already have permission, get location
            getLocation(context) { location ->
                userLocation = location
                shouldCenterOnUser = true
            }
        } else {
            // Request permission
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }
    
    // Load all lines and stops at startup
    LaunchedEffect(Unit) {
        viewModel.loadAllLines()
        viewModel.preloadStops() // Preload stops in background pour le cache
    }
    
    // Track which lines are currently displayed on the map
    var displayedLines by remember { mutableStateOf<Set<String>>(emptySet()) }
    
    // When data is loaded and map is ready, update lines
    LaunchedEffect(uiState, mapInstance) {
        val map = mapInstance ?: return@LaunchedEffect
        
        when (val state = uiState) {
            is TransportLinesUiState.Success -> {
                // Get current line names from state
                val currentLineNames = state.lines.map { 
                    "${it.properties.ligne}-${it.properties.codeTrace}" 
                }.toSet()
                
                // Remove lines that are no longer in the state
                val linesToRemove = displayedLines - currentLineNames
                linesToRemove.forEach { lineKey ->
                    removeLineFromMap(map, lineKey)
                    android.util.Log.d("PlanScreen", "Removed line from map: $lineKey")
                }
                
                // Add new lines that aren't yet displayed
                state.lines.forEach { feature ->
                    val lineKey = "${feature.properties.ligne}-${feature.properties.codeTrace}"
                    if (lineKey !in displayedLines) {
                        addLineToMap(map, feature)
                        android.util.Log.d("PlanScreen", "Added line to map: $lineKey")
                    }
                }
                
                // Update displayed lines to match current state
                displayedLines = currentLineNames
            }
            else -> {}
        }
    }
    
    // When stops are loaded and map is ready, display stops
    LaunchedEffect(stopsUiState, mapInstance) {
        val map = mapInstance ?: return@LaunchedEffect
        
        when (val state = stopsUiState) {
            is TransportStopsUiState.Success -> {
                addStopsToMap(map, state.stops, context) { clickedStationInfo ->
                    // Callback when a station is clicked
                    scope.launch {
                        // If line details are open, simulate clicking back arrow first
                        if (sheetContentState == SheetContentState.LINE_DETAILS || sheetContentState == SheetContentState.ALL_SCHEDULES) {
                            // Step 1: Remove the line from loaded lines if it's a bus
                            selectedLine?.let { lineInfo ->
                                val lineName = lineInfo.lineName
                                if (!isMetroTramOrFunicular(lineName)) {
                                    android.util.Log.d("PlanScreen", "Removing bus line when clicking another stop: $lineName")
                                    viewModel.removeLineFromLoaded(lineName)
                                }
                            }
                            
                            // Step 2: Close line details (back to station view)
                            selectedLine = null
                            sheetContentState = null
                            
                            // Step 3: Close the sheet completely
                            scaffoldSheetState.bottomSheetState.partialExpand()
                            
                            // Step 4: Wait a bit for the animation
                            kotlinx.coroutines.delay(300)
                        }
                        
                        // Check if the station has only one line
                        if (clickedStationInfo.lignes.size == 1) {
                            // Directly open line details for the single line
                            selectedStation = clickedStationInfo
                            val lineName = clickedStationInfo.lignes[0]
                            selectedLine = LineInfo(
                                lineName = lineName,
                                currentStationName = clickedStationInfo.nom
                            )
                            
                            // Si c'est un bus ou T1, charger la ligne avant d'ouvrir le bottom sheet
                            if (!isMetroTramOrFunicular(lineName)) {
                                android.util.Log.d("PlanScreen", "Pre-loading line: $lineName")
                                viewModel.addLineToLoaded(lineName)
                                // Add to temporary lines (all buses must be temporary)
                                if (isTemporaryBus(lineName)) {
                                    temporaryLoadedBusLines = temporaryLoadedBusLines + lineName
                                }
                                // Wait a bit for line to be added au state
                                kotlinx.coroutines.delay(100)
                            }
                            
                            sheetContentState = SheetContentState.LINE_DETAILS
                            scaffoldSheetState.bottomSheetState.expand()
                        } else {
                            // Open the station sheet showing all lines
                            selectedStation = clickedStationInfo
                            sheetContentState = SheetContentState.STATION
                            scaffoldSheetState.bottomSheetState.expand()
                        }
                    }
                }
            }
            else -> {}
        }
    }
    
    // Load a bus line on demand when selected
    LaunchedEffect(sheetContentState, selectedLine) {
        if (sheetContentState == SheetContentState.LINE_DETAILS && selectedLine != null) {
            val lineName = selectedLine!!.lineName
            android.util.Log.d("PlanScreen", "sheetContentState=LINE_DETAILS, lineName=$lineName")

            // If it's a bus.*load line on demand
            if (!isMetroTramOrFunicular(lineName)) {
                android.util.Log.d("PlanScreen", "Loading bus line: $lineName")
                // Add bus line to already loaded lines.*without replacing)
                viewModel.addLineToLoaded(lineName)
                // Add to temporary lines (all buses must be temporary)
                if (isTemporaryBus(lineName)) {
                    temporaryLoadedBusLines = temporaryLoadedBusLines + lineName
                    android.util.Log.d("PlanScreen", "Added to temporary bus lines: $lineName, total: ${temporaryLoadedBusLines.size}")
                }
            }
        }
    }
    
    // Unload ALL temporary lines when closing details
    LaunchedEffect(sheetContentState) {
        if (sheetContentState != SheetContentState.LINE_DETAILS && sheetContentState != SheetContentState.ALL_SCHEDULES && temporaryLoadedBusLines.isNotEmpty()) {
            // When closing details, remove ALL temporary bus lines
            android.util.Log.d("PlanScreen", "sheetContentState changed, clearing ${temporaryLoadedBusLines.size} temporary bus lines")
            temporaryLoadedBusLines.forEach { busLine ->
                android.util.Log.d("PlanScreen", "Removing temporary bus line: $busLine")
                viewModel.removeLineFromLoaded(busLine)
            }
            temporaryLoadedBusLines = emptySet()
        }
    }
    
    // Unload temporary lines when closing lines sheet AND no detail is open
    LaunchedEffect(showLinesSheet, sheetContentState) {
        // When lines sheet closes and we're not viewing line details,
        // c'est qu'on a fait "retour" sans ouvrir de ligne : nettoyer toutes les lignes temporaires
        if (!showLinesSheet && sheetContentState != SheetContentState.LINE_DETAILS && sheetContentState != SheetContentState.ALL_SCHEDULES && temporaryLoadedBusLines.isNotEmpty()) {
            android.util.Log.d("PlanScreen", "LinesSheet closed without line details open, clearing ${temporaryLoadedBusLines.size} temporary bus lines")
            temporaryLoadedBusLines.forEach { busLine ->
                android.util.Log.d("PlanScreen", "Removing temporary bus line from lines sheet: $busLine")
                viewModel.removeLineFromLoaded(busLine)
            }
            temporaryLoadedBusLines = emptySet()
        }
    }
    
    // Filter visible lines based on selected line
    LaunchedEffect(sheetContentState, selectedLine, uiState, mapInstance, stopsUiState) {
        val map = mapInstance ?: return@LaunchedEffect
        
        when (val state = uiState) {
            is TransportLinesUiState.Success -> {
                if ((sheetContentState == SheetContentState.LINE_DETAILS || sheetContentState == SheetContentState.ALL_SCHEDULES) && selectedLine != null) {
                    android.util.Log.d("PlanScreen", "LaunchedEffect: sheetContentState is LINE_DETAILS or ALL_SCHEDULES, selectedLine=${selectedLine!!.lineName}")
                    android.util.Log.d("PlanScreen", "LaunchedEffect: state.lines.size=${state.lines.size}")
                    state.lines.filter { it.properties.ligne.equals(selectedLine!!.lineName, ignoreCase = true) }.forEach {
                        android.util.Log.d("PlanScreen", "LaunchedEffect: Found matching feature: ligne=${it.properties.ligne}, codeTrace=${it.properties.codeTrace}")
                    }
                    
                    // Display only selected line
                    filterMapLines(map, state.lines, selectedLine!!.lineName)
                    
                    // Filter stops: display selected stop with icons, others as circles
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
                            
                            // Zoom: if a specific stop is selected, zoom to stop; otherwise zoom to line
                            if (selectedStopName != null) {
                                zoomToStop(map, selectedStopName, stopsState.stops)
                            } else {
                                zoomToLine(map, state.lines, selectedLine!!.lineName)
                            }
                        }
                        else -> {}
                    }
                } else {
                    // Afficher toutes les lignes
                    showAllMapLines(map, state.lines)
                }
            }
            else -> {}
        }
    }
    
    // Calculate bottom padding (height of navbar + drag handle)
    val bottomPadding = contentPadding.calculateBottomPadding()
    
    // Determine peek height:
    // - 0 when closed (station sheet not expanded)
    // - Compact header height for line details (drag handle + header + padding)
    val peekHeight = when(sheetContentState) {
        SheetContentState.LINE_DETAILS, SheetContentState.ALL_SCHEDULES -> bottomPadding + 150.dp
        SheetContentState.STATION -> 0.dp // Make it fully disappear when in STATION state
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
                            // Display line details
                            LineDetailsSheetContent(
                                lineInfo = selectedLine!!,
                                viewModel = viewModel,
                                onBackToStation = {
                                    // Retour à l'état de départ: fermer la sheet et réafficher la barre de recherche
                                    // Déclenche aussi le déchargement des lignes temporaires via LaunchedEffect
                                    scope.launch {
                                        // Ferme complètement la BottomSheet
                                        scaffoldSheetState.bottomSheetState.hide()
                                    }
                                    // Réinitialise l'état des sélections
                                    selectedLine = null
                                    selectedStation = null
                                    // Mettre l'état à null pour signaler au parent de réafficher la barre de recherche
                                    sheetContentState = null
                                },
                                onStopClick = { stopName ->
                                    // Update the selected line with the new stop
                                    selectedLine = LineInfo(
                                        lineName = selectedLine!!.lineName,
                                        currentStationName = stopName
                                    )
                                    // Collapse the sheet to show the map with the selected stop
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
                            // Afficher les informations de la station
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
                                    
                                    // Si c'est un bus ou T1, charger la ligne avant d'ouvrir le bottom sheet
                                    if (!isMetroTramOrFunicular(lineName)) {
                                        android.util.Log.d("PlanScreen", "Pre-loading line from station sheet: $lineName")
                                        scope.launch {
                                            viewModel.addLineToLoaded(lineName)
                                            // Add to temporary lines (all buses must be temporary)
                                            if (isTemporaryBus(lineName)) {
                                                temporaryLoadedBusLines = temporaryLoadedBusLines + lineName
                                            }
                                            // Wait a bit for line to be added au state
                                            kotlinx.coroutines.delay(100)
                                            sheetContentState = SheetContentState.LINE_DETAILS
                                            scaffoldSheetState.bottomSheetState.expand()
                                        }
                                    } else {
                                        sheetContentState = SheetContentState.LINE_DETAILS
                                        // Expand the sheet when showing line details
                                        scope.launch {
                                            scaffoldSheetState.bottomSheetState.expand()
                                        }
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
                    null -> {
                        // Empty content when sheet is hidden
                    }
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
            
            // Afficher un indicateur de chargement
            if (uiState is TransportLinesUiState.Loading || stopsUiState is TransportStopsUiState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
    
    // Reset center flag after first centering
    LaunchedEffect(shouldCenterOnUser) {
        if (shouldCenterOnUser) {
            shouldCenterOnUser = false
        }
    }

    // Display LinesBottomSheet on top of everything when requested
    if (showLinesSheet) {
        val modalBottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        
        // LaunchedEffect to hide the sheet when showLinesSheet becomes false
        LaunchedEffect(showLinesSheet) {
            if (!showLinesSheet) {
                modalBottomSheetState.hide()
            }
        }

        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = onLinesSheetDismiss,
            containerColor = Color.White,
            sheetState = modalBottomSheetState // Use the local state
        ) {
            LinesBottomSheet(
                allLines = viewModel.getAllAvailableLines(),
                onDismiss = onLinesSheetDismiss,
                onLineClick = { lineName ->
                    // Fermer la sheet des lignes
                    onLinesSheetDismiss()
                    
                    // If line isn't metro/tram/funicular/navigone, load it
                    if (!isMetroTramOrFunicular(lineName)) {
                        scope.launch {
                            android.util.Log.d("PlanScreen", "Loading bus line from lines sheet: $lineName")
                            viewModel.addLineToLoaded(lineName)
                            // Add to temporary lines (all buses must be temporary)
                            if (isTemporaryBus(lineName)) {
                                temporaryLoadedBusLines = temporaryLoadedBusLines + lineName
                            }
                            // Wait a bit for line to be added au state
                            kotlinx.coroutines.delay(100)
                            
                            // Open line details
                            selectedLine = LineInfo(
                                lineName = lineName,
                                currentStationName = ""
                            )
                            sheetContentState = SheetContentState.LINE_DETAILS
                            scaffoldSheetState.bottomSheetState.partialExpand() // Open in collapsed mode
                        }
                    } else {
                        // For metros/trams/funiculars/navigone, they should already be loaded
                        // But let's verify and add if missing (safety check for navigone)
                        scope.launch {
                            // Check if line is already loaded
                            val currentState = uiState
                            val isLoaded = if (currentState is TransportLinesUiState.Success) {
                                currentState.lines.any { it.properties.ligne.equals(lineName, ignoreCase = true) }
                            } else {
                                false
                            }
                            
                            if (!isLoaded) {
                                android.util.Log.w("PlanScreen", "Line $lineName should be loaded but isn't! Loading now...")
                                viewModel.addLineToLoaded(lineName)
                                kotlinx.coroutines.delay(100)
                            }
                            
                            selectedLine = LineInfo(
                                lineName = lineName,
                                currentStationName = ""
                            )
                            sheetContentState = SheetContentState.LINE_DETAILS
                            scaffoldSheetState.bottomSheetState.partialExpand() // Open in collapsed mode
                        }
                    }
                }
            )
        }
    }
}

/**
 * Content for station info sheet (without ModalBottomSheet wrapper)
 */
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

/**
 * Content for line details sheet (without ModalBottomSheet wrapper)
 */
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

/**
 * Filtre les lignes de la carte pour n'afficher que celle spécifiée
 */
private fun filterMapLines(
    map: MapLibreMap,
    allLines: List<com.pelotcl.app.data.model.Feature>,
    selectedLineName: String
) {
    map.getStyle { style ->
        android.util.Log.d("PlanScreen", "filterMapLines: selectedLineName=$selectedLineName, allLines.size=${allLines.size}")
        
        var foundCount = 0
        var notFoundCount = 0
        var madeVisibleCount = 0
        
        allLines.forEach { feature ->
            val layerId = "layer-${feature.properties.ligne}-${feature.properties.codeTrace}"
            val isSelectedLine = feature.properties.ligne.equals(selectedLineName, ignoreCase = true)
            
            // Try to get existing layer
            val existingLayer = style.getLayer(layerId)
            
            if (existingLayer != null) {
                // Layer exists, just change visibility
                foundCount++
                if (isSelectedLine) {
                    existingLayer.setProperties(PropertyFactory.visibility("visible"))
                    madeVisibleCount++
                    android.util.Log.d("PlanScreen", "filterMapLines: Made visible: $layerId")
                } else {
                    existingLayer.setProperties(PropertyFactory.visibility("none"))
                }
            } else {
                // Layer doesn't exist
                notFoundCount++
                if (isSelectedLine) {
                    android.util.Log.w("PlanScreen", "filterMapLines: Selected line layer NOT found, recreating: $layerId (ligne=${feature.properties.ligne})")
                    // Recreate the layer for the selected line
                    addLineToMap(map, feature)
                    madeVisibleCount++
                }
            }
        }
        
        android.util.Log.d("PlanScreen", "filterMapLines: Found $foundCount layers, $notFoundCount not found, made $madeVisibleCount visible")
    }
}

/**
 * Zoome la caméra pour afficher toute la ligne sélectionnée
 */
private fun zoomToLine(
    map: MapLibreMap,
    allLines: List<com.pelotcl.app.data.model.Feature>,
    selectedLineName: String
) {
    // Find all features of selected line
    val lineFeatures = allLines.filter { 
        it.properties.ligne.equals(selectedLineName, ignoreCase = true) 
    }
    
    if (lineFeatures.isEmpty()) return
    
    // Calculate bounds of all line coordinates
    val boundsBuilder = LatLngBounds.Builder()
    var hasCoordinates = false
    
    lineFeatures.forEach { feature ->
        // Chaque feature a un MultiLineString avec plusieurs segments
        feature.geometry.coordinates.forEach { lineString ->
            lineString.forEach { coord ->
                // coord[1] = latitude, coord[0] = longitude
                boundsBuilder.include(LatLng(coord[1], coord[0]))
                hasCoordinates = true
            }
        }
    }
    
    if (!hasCoordinates) return
    
    val bounds = boundsBuilder.build()
    
    // Add asymmetric padding to compensate for bottom sheet collapsed at bottom
    // More padding at bottom (where sheet is located), less at top
    val paddingLeft = 200
    val paddingTop = 100
    val paddingRight = 200
    val paddingBottom = 600 // Increased to raise camera even more
    
    // Animate camera to these bounds with asymmetric padding
    map.animateCamera(
        CameraUpdateFactory.newLatLngBounds(bounds, paddingLeft, paddingTop, paddingRight, paddingBottom),
        1000 // duration in ms
    )
}

/**
 * Zoome sur un arrêt spécifique
 */
private fun zoomToStop(
    map: MapLibreMap,
    stopName: String,
    allStops: List<com.pelotcl.app.data.model.StopFeature>
) {
    // Normalize stop name for comparison
    fun normalizeStopName(name: String): String {
        return name.filter { it.isLetter() }.lowercase()
    }
    
    val normalizedStopName = normalizeStopName(stopName)
    
    android.util.Log.d("PlanScreen", "zoomToStop - Looking for stop: '$stopName', normalized: '$normalizedStopName'")
    
    // Find the stop - try exact match first, then normalized
    var stop = allStops.find { 
        it.properties.nom.equals(stopName, ignoreCase = true)
    }
    
    // If not found, try normalized comparison
    if (stop == null) {
        stop = allStops.find { 
            normalizeStopName(it.properties.nom) == normalizedStopName 
        }
    }
    
    if (stop == null) {
        android.util.Log.w("PlanScreen", "zoomToStop - Stop not found: '$stopName'. Available stops (first 10): ${allStops.take(10).map { it.properties.nom }}")
        return
    }
    
    android.util.Log.d("PlanScreen", "zoomToStop - Found stop: '${stop.properties.nom}' at [${stop.geometry.coordinates[1]}, ${stop.geometry.coordinates[0]}]")
    
    // Get stop coordinates
    val lat = stop.geometry.coordinates[1]
    val lon = stop.geometry.coordinates[0]
    val stopLocation = LatLng(lat, lon)
    
    // Zoom to stop with appropriate zoom level
    map.animateCamera(
        CameraUpdateFactory.newLatLngZoom(stopLocation, 15.0),
        1000 // duration in ms
    )
}

/**
 * Filtre les arrêts pour n'afficher que ceux de la ligne sélectionnée
 * Sur ces arrêts, affiche toutes les icônes de correspondances métro/tram/funiculaire empilées
 * En mode filtré, les trams sont au même niveau que métro/funiculaire (priority stops)
 */
private fun filterMapStops(
    style: org.maplibre.android.maps.Style,
    selectedLineName: String
) {
    val priorityLayerPrefix = "transport-stops-layer-priority"
    val secondaryLayerPrefix = "transport-stops-layer-secondary"
    
    // Create property name for this line
    val linePropertyName = "has_line_${selectedLineName.uppercase()}"
    
    android.util.Log.d("PlanScreen", "filterMapStops: filtering for line $selectedLineName, property: $linePropertyName")
    
    (-25..25).forEach { idx ->
        val priorityLayerId = "$priorityLayerPrefix-$idx"
        val secondaryLayerId = "$secondaryLayerPrefix-$idx"
        
        (style.getLayer(priorityLayerId) as? SymbolLayer)?.let { layer ->
            // Filter to display only stops that have property has_line_X = true
            layer.setFilter(
                Expression.all(
                    Expression.eq(Expression.get("priority_stop"), true),
                    Expression.eq(Expression.get("slot"), idx),
                    Expression.eq(Expression.get(linePropertyName), true)
                )
            )
            // Keep same minZoom as usual (already at PRIORITY_STOPS_MIN_ZOOM)
        }
        
        (style.getLayer(secondaryLayerId) as? SymbolLayer)?.let { layer ->
            // In filtered mode, display all line stops (including trams and metro transfers)
            // AND promote their zoom to priority level for better visibility
            layer.setFilter(
                Expression.all(
                    Expression.eq(Expression.get("priority_stop"), false),
                    Expression.eq(Expression.get("slot"), idx),
                    Expression.eq(Expression.get(linePropertyName), true)
                )
            )
            // Reduce minZoom for ALL line stops in filtered mode
            // (not only trams, to also see metro transfers)
            layer.setMinZoom(PRIORITY_STOPS_MIN_ZOOM)
        }
    }
}

/**
 * Filtre les arrêts avec support pour un arrêt sélectionné spécifique
 * - L'arrêt sélectionné est affiché avec ses icônes normales
 * - Les autres arrêts de la ligne sont affichés comme des cercles de couleur
 */
private fun filterMapStopsWithSelectedStop(
    map: MapLibreMap,
    selectedLineName: String,
    selectedStopName: String?,
    allStops: List<com.pelotcl.app.data.model.StopFeature>,
    allLines: List<com.pelotcl.app.data.model.Feature>
) {
    map.getStyle { style ->
        // If no specific stop is selected, use default filtering (show all icons)
        if (selectedStopName.isNullOrBlank()) {
            filterMapStops(style, selectedLineName)
            // Remove circle layers if they exist
            style.getLayer("line-stops-circles")?.let { style.removeLayer(it) }
            style.getSource("line-stops-circles-source")?.let { style.removeSource(it) }
            return@getStyle
        }
        
        // Normalize stop name for comparison
        fun normalizeStopName(name: String): String {
            return name.filter { it.isLetter() }.lowercase()
        }
        
        val normalizedSelectedStop = normalizeStopName(selectedStopName)
        val priorityLayerPrefix = "transport-stops-layer-priority"
        val secondaryLayerPrefix = "transport-stops-layer-secondary"
        val linePropertyName = "has_line_${selectedLineName.uppercase()}"
        
        // Filter icon layers to show ONLY the selected stop
        (-25..25).forEach { idx ->
            val priorityLayerId = "$priorityLayerPrefix-$idx"
            val secondaryLayerId = "$secondaryLayerPrefix-$idx"
            
            (style.getLayer(priorityLayerId) as? SymbolLayer)?.let { layer ->
                layer.setFilter(
                    Expression.all(
                        Expression.eq(Expression.get("priority_stop"), true),
                        Expression.eq(Expression.get("slot"), idx),
                        Expression.eq(Expression.get(linePropertyName), true),
                        Expression.eq(Expression.get("normalized_nom"), normalizedSelectedStop)
                    )
                )
                // More permissive minZoom when a stop is selected
                layer.setMinZoom(SELECTED_STOP_MIN_ZOOM)
            }
            
            (style.getLayer(secondaryLayerId) as? SymbolLayer)?.let { layer ->
                layer.setFilter(
                    Expression.all(
                        Expression.eq(Expression.get("priority_stop"), false),
                        Expression.eq(Expression.get("slot"), idx),
                        Expression.eq(Expression.get(linePropertyName), true),
                        Expression.eq(Expression.get("normalized_nom"), normalizedSelectedStop)
                    )
                )
                // More permissive minZoom when a stop is selected
                layer.setMinZoom(SELECTED_STOP_MIN_ZOOM)
            }
        }
        
        // Create circle layer for non-selected stops
        addCircleLayerForLineStops(
            style, 
            selectedLineName, 
            selectedStopName,
            allStops,
            allLines
        )
    }
}

/**
 * Ajoute une couche de cercles pour représenter les arrêts non-sélectionnés d'une ligne
 */
private fun addCircleLayerForLineStops(
    style: org.maplibre.android.maps.Style,
    selectedLineName: String,
    selectedStopName: String,
    allStops: List<com.pelotcl.app.data.model.StopFeature>,
    allLines: List<com.pelotcl.app.data.model.Feature>
) {
    // Remove existing circle layers
    style.getLayer("line-stops-circles")?.let { style.removeLayer(it) }
    style.getSource("line-stops-circles-source")?.let { style.removeSource(it) }
    
    // Normalize stop name for comparison
    fun normalizeStopName(name: String): String {
        return name.filter { it.isLetter() }.lowercase()
    }
    
    val normalizedSelectedStop = normalizeStopName(selectedStopName)
    
    // Get line color
    val lineColor = allLines
        .find { it.properties.ligne.equals(selectedLineName, ignoreCase = true) }
        ?.let { LineColorHelper.getColorForLine(it) }
        ?: "#EF4444" // Default red
    
    // Filter stops that belong to the selected line but are NOT the selected stop
    val lineStops = allStops.filter { stop ->
        val lines = BusIconHelper.getAllLinesForStop(stop)
        val hasLine = lines.any { it.equals(selectedLineName, ignoreCase = true) }
        val isNotSelected = normalizeStopName(stop.properties.nom) != normalizedSelectedStop
        hasLine && isNotSelected
    }
    
    // Create GeoJSON for circle stops
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
    
    // Add source for circles
    val circlesSource = GeoJsonSource("line-stops-circles-source", circlesGeoJson.toString())
    style.addSource(circlesSource)
    
    // Add circle layer with inverted colors (white fill, colored border)
    // More permissive minZoom when a stop is selected
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

/**
 * Affiche toutes les lignes sur la carte
 */
private fun showAllMapLines(
    map: MapLibreMap,
    allLines: List<com.pelotcl.app.data.model.Feature>
) {
    map.getStyle { style ->
        allLines.forEach { feature ->
            val layerId = "layer-${feature.properties.ligne}-${feature.properties.codeTrace}"
            
            // Rendre toutes les couches visibles
            style.getLayer(layerId)?.let { layer ->
                layer.setProperties(PropertyFactory.visibility("visible"))
            }
        }
        
        // Redisplay all stops
        showAllMapStops(style)
        
        // Remove circle layers if they exist
        style.getLayer("line-stops-circles")?.let { style.removeLayer(it) }
        style.getSource("line-stops-circles-source")?.let { style.removeSource(it) }
    }
}

/**
 * Réaffiche tous les arrêts sur la carte
 */
private fun showAllMapStops(
    style: org.maplibre.android.maps.Style
) {
    val priorityLayerPrefix = "transport-stops-layer-priority"
    val secondaryLayerPrefix = "transport-stops-layer-secondary"
    
    // Restore original filters for all stops
    (-25..25).forEach { idx ->
        (style.getLayer("$priorityLayerPrefix-$idx") as? SymbolLayer)?.let { layer ->
            layer.setFilter(
                Expression.all(
                    Expression.eq(Expression.get("priority_stop"), true),
                    Expression.eq(Expression.get("slot"), idx)
                )
            )
            layer.setMinZoom(PRIORITY_STOPS_MIN_ZOOM)
        }
        
        (style.getLayer("$secondaryLayerPrefix-$idx") as? SymbolLayer)?.let { layer ->
            layer.setFilter(
                Expression.all(
                    Expression.eq(Expression.get("priority_stop"), false),
                    Expression.eq(Expression.get("slot"), idx)
                )
            )
            // Restore original minZoom for secondary stops
            layer.setMinZoom(SECONDARY_STOPS_MIN_ZOOM)
        }
    }
}

/**
 * Ajoute une ligne de transport sur la carte MapLibre avec la couleur appropriée
 */
private fun addLineToMap(
    map: MapLibreMap,
    feature: com.pelotcl.app.data.model.Feature
) {
    map.getStyle { style ->
        val sourceId = "line-${feature.properties.ligne}-${feature.properties.codeTrace}"
        val layerId = "layer-${feature.properties.ligne}-${feature.properties.codeTrace}"
        
        // Supprimer l'ancienne couche et source si elles existent
        style.getLayer(layerId)?.let { style.removeLayer(it) }
        style.getSource(sourceId)?.let { style.removeSource(it) }
        
        // Create GeoJSON for line
        val lineGeoJson = createGeoJsonFromFeature(feature)
        
        // Add data source for the line
        val lineSource = GeoJsonSource(sourceId, lineGeoJson)
        style.addSource(lineSource)
        
        // Get appropriate color for this line
        val lineColor = LineColorHelper.getColorForLine(feature)
        
        // Determine line width based on transport type
        val lineWidth = when {
            // Navigone: very thin line (1f)
            feature.properties.familleTransport == "BAT" || feature.properties.ligne.uppercase().startsWith("NAV") -> 2f
            // Trams: thin lines (2f)
            feature.properties.familleTransport == "TRA" || feature.properties.familleTransport == "TRAM" -> 2f
            // Others (metro, funicular, bus): thicker lines (4f)
            else -> 4f
        }
        
        // Create line layer with appropriate color
        val lineLayer = LineLayer(layerId, sourceId).apply {
            setProperties(
                PropertyFactory.lineColor(lineColor),
                PropertyFactory.lineWidth(lineWidth),
                PropertyFactory.lineOpacity(0.8f),
                PropertyFactory.lineCap("round"),
                PropertyFactory.lineJoin("round")
            )
        }
        
        style.addLayer(lineLayer)
    }
}

/**
 * Retire une ligne de transport de la carte MapLibre
 */
private fun removeLineFromMap(
    map: MapLibreMap,
    lineKey: String  // Format: "ligne-codeTrace" (ex: "86-123456")
) {
    map.getStyle { style ->
        val sourceId = "line-$lineKey"
        val layerId = "layer-$lineKey"
        
        // Supprimer la couche et la source
        style.getLayer(layerId)?.let { 
            style.removeLayer(it)
            android.util.Log.d("PlanScreen", "Removed layer: $layerId")
        }
        style.getSource(sourceId)?.let { 
            style.removeSource(it)
            android.util.Log.d("PlanScreen", "Removed source: $sourceId")
        }
    }
}

/**
 * Ajoute tous les arrêts de transport sur la carte avec les icônes correspondantes
 */
private fun addStopsToMap(
    map: MapLibreMap,
    stops: List<com.pelotcl.app.data.model.StopFeature>,
    context: android.content.Context,
    onStationClick: (StationInfo) -> Unit = {}
) {
    map.getStyle { style ->
        val sourceId = "transport-stops"
        val priorityLayerPrefix = "transport-stops-layer-priority"
        val secondaryLayerPrefix = "transport-stops-layer-secondary"

        // Remove old layers and sources if they exist
        // Remove previous fixed slots (1..3)
        (1..3).forEach { idx ->
            val pId = "$priorityLayerPrefix-$idx"
            val sId = "$secondaryLayerPrefix-$idx"
            style.getLayer(pId)?.let { style.removeLayer(it) }
            style.getLayer(sId)?.let { style.removeLayer(it) }
        }
        // Also remove a reasonable range of dynamic slot layers from previous runs (-25..25)
        (-25..25).forEach { idx ->
            val pId = "$priorityLayerPrefix-$idx"
            val sId = "$secondaryLayerPrefix-$idx"
            style.getLayer(pId)?.let { style.removeLayer(it) }
            style.getLayer(sId)?.let { style.removeLayer(it) }
        }
        style.getSource(sourceId)?.let { style.removeSource(it) }

        // Collect unique icons, valid stops, and used slot indices
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

        // Load all icons into the style
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

        // Create GeoJSON for stops with icon information (unlimited by using per-line features)
        val stopsGeoJson = createStopsGeoJsonFromStops(validStops, context)

        // Add data source for stops
        val stopsSource = GeoJsonSource(sourceId, stopsGeoJson)
        style.addSource(stopsSource)

        // Two data-driven layers across arbitrary number of slots
        val iconSizesPriority = 0.7f
        val iconSizesSecondary = 0.62f

        // Build layers for each used slot index with static offset per layer
        usedSlots.sorted().forEach { slotIndex ->
            val yOffset = slotIndex * 13f // 26px between adjacent icons

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
                        Expression.eq(Expression.get("priority_stop"), true),
                        Expression.eq(Expression.get("slot"), slotIndex)
                    )
                )
                setMinZoom(PRIORITY_STOPS_MIN_ZOOM)
            }
            style.addLayer(priorityLayer)

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
                        Expression.eq(Expression.get("priority_stop"), false),
                        Expression.eq(Expression.get("slot"), slotIndex)
                    )
                )
                setMinZoom(SECONDARY_STOPS_MIN_ZOOM)
            }
            style.addLayer(secondaryLayer)
        }
        
        // Add click listener for all stop layers (including circle layer)
        map.addOnMapClickListener { point ->
            val pixel = map.projection.toScreenLocation(point)
            
            // Check circle layer first
            val circleFeatures = map.queryRenderedFeatures(pixel, "line-stops-circles")
            if (circleFeatures.isNotEmpty()) {
                val feature = circleFeatures.first()
                val stationName = feature.getStringProperty("nom") ?: ""
                val desserte = feature.getStringProperty("desserte") ?: ""
                val isPmr = feature.getBooleanProperty("pmr") ?: false
                
                // Parse all lines from desserte
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
                return@addOnMapClickListener true // Consume the click event
            }
            
            // Check all icon layers for clicked features
            val allLayerIds = usedSlots.flatMap { idx ->
                listOf(
                    "$priorityLayerPrefix-$idx",
                    "$secondaryLayerPrefix-$idx"
                )
            }
            
            val features = map.queryRenderedFeatures(pixel, *allLayerIds.toTypedArray())
            
            if (features.isNotEmpty()) {
                val feature = features.first()
                
                // Extract station info from the feature properties using public getters
                val stationName = feature.getStringProperty("nom") ?: ""
                val desserte = feature.getStringProperty("desserte") ?: ""
                val isPmr = feature.getBooleanProperty("pmr") ?: false
                
                // Parse all lines from desserte
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
                true // Consume the click event
            } else {
                false // Don't consume the click event
            }
        }
    }
}

/**
 * Convertit une Feature en GeoJSON String pour MapLibre
 */
private fun createGeoJsonFromFeature(feature: com.pelotcl.app.data.model.Feature): String {
    val geoJsonObject = JsonObject().apply {
        addProperty("type", "Feature")
        
        // Add geometry
        val geometryObject = JsonObject().apply {
            addProperty("type", feature.geometry.type)
            
            // Convert coordinates
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
        
        // Add properties
        val propertiesObject = JsonObject().apply {
            addProperty("ligne", feature.properties.ligne)
            addProperty("nom_trace", feature.properties.nomTrace)
            addProperty("couleur", feature.properties.couleur)
        }
        add("properties", propertiesObject)
    }
    
    return geoJsonObject.toString()
}

/**
 * Fusionne les arrêts qui ont le même nom (correspondances métro/tram/funicular/navigone)
 * Fusionne UNIQUEMENT les arrêts M/F/T/Tb/NAVI entre eux, les bus restent séparés
 */
private fun mergeStopsByName(stops: List<com.pelotcl.app.data.model.StopFeature>): List<com.pelotcl.app.data.model.StopFeature> {
    // Function to check if a line is M/F/T/Tb/NAVI
    fun isMetroTramFuniNavi(line: String): Boolean {
        val upperLine = line.uppercase()
        return upperLine in setOf("A", "B", "C", "D") || // Metro A, B, C, D
               upperLine.startsWith("F") || // Funiculaire (F1, F2)
               upperLine.startsWith("NAV") || // Navigone (NAV1, etc.)
               upperLine.startsWith("T") // Tram (T1-T7) et Trolleybus (Tb)
    }
    
    // Function to normalize stop name (same logic as in TransportViewModel)
    fun normalizeStopName(name: String): String {
        return name.filter { it.isLetter() }.lowercase()
    }
    
    // Separate stops into two groups:
    // - Stops that contain ONLY M/F/T/Tb/NAVI lines (no buses)
    // - All other stops (pure buses or mixed bus+M/F/T/Tb/NAVI)
    val (pureMetroTramFuniNavi, otherStops) = stops.partition { stop ->
        val allLines = BusIconHelper.getAllLinesForStop(stop)
        val isPure = allLines.isNotEmpty() && allLines.all { isMetroTramFuniNavi(it) }
        
        // Debug log for certain stops
        if (stop.properties.nom.contains("Perrache", ignoreCase = true) || 
            stop.properties.nom.contains("Bellecour", ignoreCase = true)) {
            android.util.Log.d("MergeStops", "Arrêt: ${stop.properties.nom}, Lignes: $allLines, isPure: $isPure")
        }
        
        isPure
    }
    
    // Group pure M/F/T/Tb/NAVI stops by NORMALIZED name (without spaces, punctuation, etc.)
    val stopsByName = pureMetroTramFuniNavi.groupBy { normalizeStopName(it.properties.nom) }
    
    // Debug log pour les groupes
    stopsByName.forEach { (normalizedName, group) ->
        if (group.size > 1) {
            val names = group.map { it.properties.nom }
            val lines = group.flatMap { BusIconHelper.getAllLinesForStop(it) }
            android.util.Log.d("MergeStops", "Fusion: $normalizedName -> $names avec lignes: $lines")
        }
    }
    
    val merged = stopsByName.map { (name, stopsGroup) ->
        if (stopsGroup.size == 1) {
            // Single M/F/T/Tb/NAVI stop: no merging
            stopsGroup.first()
        } else {
            // Multiple M/F/T/Tb/NAVI stops with same name: merge
            val mergedDesserte = stopsGroup
                .flatMap { BusIconHelper.getAllLinesForStop(it) }
                .distinct()
                .sorted()
                .joinToString(", ")
            
            val firstStop = stopsGroup.first()
            val isPmr = stopsGroup.any { it.properties.pmr }
            
            // Return single merged stop
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
                    lastUpdate = firstStop.properties.lastUpdate,
                    lastUpdateFme = firstStop.properties.lastUpdateFme,
                    adresse = firstStop.properties.adresse,
                    localiseFaceAAdresse = firstStop.properties.localiseFaceAAdresse,
                    commune = firstStop.properties.commune,
                    insee = firstStop.properties.insee,
                    zone = firstStop.properties.zone
                )
            )
        }
    }
    
    // Return merged stops (pure M/F/T/Tb/NAVI) + all other stops (buses and mixed)
    return merged + otherStops
}

/**
 * Crée un GeoJSON contenant des Points pour tous les arrêts de transport avec informations d'icônes
 */
private fun createStopsGeoJsonFromStops(
    stops: List<com.pelotcl.app.data.model.StopFeature>,
    context: android.content.Context
): String {
    val features = JsonArray()
    
    // Merge stops with same name (metro/tram/funicular transfers)
    val mergedStops = mergeStopsByName(stops)

    mergedStops.forEach { stop ->
        val lineNamesAll = BusIconHelper.getAllLinesForStop(stop)
        if (lineNamesAll.isEmpty()) return@forEach
        
        // Debug: log stops that serve NAV1
        if (lineNamesAll.any { it.equals("NAV1", ignoreCase = true) }) {
            android.util.Log.d("PlanScreen", "createStopsGeoJson: Stop '${stop.properties.nom}' serves NAV1, all lines: $lineNamesAll")
        }
        
        val drawableNamesAll = BusIconHelper.getAllDrawableNamesForStop(stop)
        
        // Filter to only available icons AND keep lineNames synchronized
        val validPairs = lineNamesAll.zip(drawableNamesAll).filter { (_, drawableName) ->
            val id = context.resources.getIdentifier(drawableName, "drawable", context.packageName)
            id != 0
        }
        
        if (validPairs.isEmpty()) return@forEach
        
        val lineNames = validPairs.map { it.first }
        val drawableNames = validPairs.map { it.second }
        
        // Check if stop serves at least one tram
        val hasTram = lineNames.any { it.uppercase().startsWith("T") }

        // Compute centered slot indices for N icons: -(n-1), -(n-3), ..., (n-1)
        val n = drawableNames.size
        var slot = -(n - 1)

        drawableNames.forEachIndexed { index, iconName ->
            // Determine line corresponding to this icon
            val lineName = lineNames[index].uppercase()
            
            // isPriorityStop must be based on CURRENT ICON, not on stop's main line
            val isPriorityStop = ALWAYS_VISIBLE_LINES.contains(lineName)
            
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
                    addProperty("priority_stop", isPriorityStop)
                    addProperty("has_tram", hasTram)
                    addProperty("icon", iconName)
                    addProperty("slot", slot)
                    
                    // Add normalized name for filtering by selected stop
                    val normalizedNom = stop.properties.nom.filter { it.isLetter() }.lowercase()
                    addProperty("normalized_nom", normalizedNom)
                    
                    // Add ALL stop lines as boolean properties
                    // (pas seulement celles qui ont un drawable)
                    // This allows all features of a stop to have the same properties has_line_*
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

/**
 * Helper function to get current location
 */
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
                // Try to get last known location as fallback
                fusedLocationClient.lastLocation.addOnSuccessListener { lastLocation ->
                    if (lastLocation != null) {
                        onSuccess(LatLng(lastLocation.latitude, lastLocation.longitude))
                    }
                }
            }
        }
    } catch (e: SecurityException) {
        // Location permission not granted
    }
}