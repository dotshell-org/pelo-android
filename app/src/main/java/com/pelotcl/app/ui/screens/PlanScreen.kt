package com.pelotcl.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberBottomSheetScaffoldState
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.pelotcl.app.ui.components.LineDetailsBottomSheet
import com.pelotcl.app.ui.components.LineInfo
import com.pelotcl.app.ui.components.MapLibreView
import com.pelotcl.app.ui.components.StationBottomSheet
import com.pelotcl.app.ui.components.StationInfo
import com.pelotcl.app.ui.viewmodel.TransportLinesUiState
import com.pelotcl.app.ui.viewmodel.TransportStopsUiState
import com.pelotcl.app.ui.viewmodel.TransportViewModel
import com.pelotcl.app.utils.BusIconHelper
import com.pelotcl.app.utils.LineColorHelper
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource

private val ALWAYS_VISIBLE_LINES = setOf("F1", "F2", "A", "B", "C", "D")
private const val PRIORITY_STOPS_MIN_ZOOM = 12.5f
private const val SECONDARY_STOPS_MIN_ZOOM = 15f

/**
 * Détermine si une ligne est un métro, tram ou funiculaire (pas un bus)
 */
private fun isMetroTramOrFunicular(lineName: String): Boolean {
    val upperName = lineName.uppercase()
    return when {
        // Métros
        upperName in setOf("A", "B", "C", "D") -> true
        // Funiculaires
        upperName in setOf("F1", "F2") -> true
        // Trams (commence par T) - temporairement exclu T1 pour le charger à la demande
        upperName.startsWith("T") && upperName != "T1" -> true
        // Sinon c'est un bus
        else -> false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen(
    modifier: Modifier = Modifier,
    contentPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(0.dp),
    viewModel: TransportViewModel = viewModel(),
    onSheetStateChanged: (Boolean) -> Unit = {}
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
    val scaffoldSheetState = rememberBottomSheetScaffoldState()
    var selectedStation by remember { mutableStateOf<StationInfo?>(null) }
    var selectedLine by remember { mutableStateOf<LineInfo?>(null) }
    
    // Determine which content to show: station info or line details
    var showLineDetails by remember { mutableStateOf(false) }
    
    // Track if the sheet is expanded (to hide search bar)
    var isSheetExpanded by remember { mutableStateOf(false) }
    
    // Notify parent when sheet state changes
    LaunchedEffect(isSheetExpanded) {
        onSheetStateChanged(isSheetExpanded)
    }
    
    // Monitor bottom sheet state changes to detect when user swipes down to dismiss
    // BUT only allow dismissal for station sheet, not for line details sheet
    LaunchedEffect(scaffoldSheetState.bottomSheetState.currentValue, showLineDetails) {
        val isPartiallyExpanded = scaffoldSheetState.bottomSheetState.currentValue == 
            androidx.compose.material3.SheetValue.PartiallyExpanded
        val isHidden = scaffoldSheetState.bottomSheetState.currentValue == 
            androidx.compose.material3.SheetValue.Hidden
        
        // Only allow dismissal by swiping for station sheet (not line details)
        if ((isPartiallyExpanded || isHidden) && isSheetExpanded && !showLineDetails) {
            // User has swiped down to dismiss the station sheet
            isSheetExpanded = false
        } else if (isHidden && isSheetExpanded && showLineDetails) {
            // User tried to completely hide line details sheet - prevent by going to partial expand
            scope.launch {
                scaffoldSheetState.bottomSheetState.partialExpand()
            }
        }
        // Note: PartiallyExpanded state is allowed for line details (to show compact header)
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
    
    // Charger toutes les lignes et arrêts au démarrage
    LaunchedEffect(Unit) {
        viewModel.loadAllLines()
        viewModel.preloadStops() // Précharge les arrêts en arrière-plan pour le cache
    }
    
    // Track which lines are currently displayed on the map
    var displayedLines by remember { mutableStateOf<Set<String>>(emptySet()) }
    
    // Quand les données sont chargées et la carte est prête, mettre à jour les lignes
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
                
                // Update displayed lines tracker
                displayedLines = currentLineNames
            }
            else -> {}
        }
    }
    
    // Quand les arrêts sont chargés et la carte est prête, afficher les arrêts
    LaunchedEffect(stopsUiState, mapInstance) {
        val map = mapInstance ?: return@LaunchedEffect
        
        when (val state = stopsUiState) {
            is TransportStopsUiState.Success -> {
                addStopsToMap(map, state.stops, context) { clickedStationInfo ->
                    // Callback when a station is clicked
                    scope.launch {
                        // If line details are open, simulate clicking back arrow first
                        if (showLineDetails) {
                            // Step 1: Close line details (back to station view)
                            selectedLine = null
                            showLineDetails = false
                            
                            // Step 2: Close the sheet completely
                            scaffoldSheetState.bottomSheetState.partialExpand()
                            isSheetExpanded = false
                            
                            // Step 3: Wait a bit for the animation
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
                                // Attendre un peu que la ligne soit ajoutée au state
                                kotlinx.coroutines.delay(100)
                            }
                            
                            showLineDetails = true
                            isSheetExpanded = true
                            scaffoldSheetState.bottomSheetState.expand()
                        } else {
                            // Open the station sheet showing all lines
                            selectedStation = clickedStationInfo
                            isSheetExpanded = true
                            scaffoldSheetState.bottomSheetState.expand()
                        }
                    }
                }
            }
            else -> {}
        }
    }
    
    // Charger une ligne de bus à la demande quand elle est sélectionnée
    // Et la retirer quand on ferme les détails
    LaunchedEffect(showLineDetails, selectedLine) {
        if (showLineDetails && selectedLine != null) {
            val lineName = selectedLine!!.lineName
            android.util.Log.d("PlanScreen", "showLineDetails=true, lineName=$lineName")
            
            // Si c'est un bus (pas métro/tram/funiculaire), charger la ligne à la demande
            if (!isMetroTramOrFunicular(lineName)) {
                android.util.Log.d("PlanScreen", "Loading bus line: $lineName")
                // Ajouter la ligne de bus aux lignes déjà chargées (sans remplacer)
                viewModel.addLineToLoaded(lineName)
            }
        } else if (!showLineDetails && selectedLine != null) {
            // Quand on ferme les détails, retirer la ligne de bus si c'en était une
            val lineName = selectedLine!!.lineName
            android.util.Log.d("PlanScreen", "showLineDetails=false, lineName=$lineName")
            if (!isMetroTramOrFunicular(lineName)) {
                android.util.Log.d("PlanScreen", "Removing bus line: $lineName")
                viewModel.removeLineFromLoaded(lineName)
            }
        }
    }
    
    // Filtrer les lignes visibles en fonction de la ligne sélectionnée
    LaunchedEffect(showLineDetails, selectedLine, uiState, mapInstance) {
        val map = mapInstance ?: return@LaunchedEffect
        
        when (val state = uiState) {
            is TransportLinesUiState.Success -> {
                if (showLineDetails && selectedLine != null) {
                    // Afficher uniquement la ligne sélectionnée
                    filterMapLines(map, state.lines, selectedLine!!.lineName)
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
    val peekHeight = if (isSheetExpanded && showLineDetails) {
        bottomPadding + 150.dp;
    } else {
        0.dp
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
                if (showLineDetails && selectedLine != null) {
                    // Afficher les détails de la ligne
                    LineDetailsSheetContent(
                        lineInfo = selectedLine!!,
                        viewModel = viewModel,
                        onBackToStation = {
                            showLineDetails = false
                        }
                    )
                } else if (selectedStation != null) {
                    // Afficher les informations de la station
                    StationSheetContent(
                        stationInfo = selectedStation!!,
                        onDismiss = {
                            scope.launch { 
                                scaffoldSheetState.bottomSheetState.partialExpand()
                                isSheetExpanded = false
                            }
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
                                    // Attendre un peu que la ligne soit ajoutée au state
                                    kotlinx.coroutines.delay(100)
                                    showLineDetails = true
                                    scaffoldSheetState.bottomSheetState.expand()
                                }
                            } else {
                                showLineDetails = true
                                // Expand the sheet when showing line details
                                scope.launch {
                                    scaffoldSheetState.bottomSheetState.expand()
                                }
                            }
                        }
                    )
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LineDetailsSheetContent(
    lineInfo: LineInfo,
    viewModel: TransportViewModel,
    onBackToStation: () -> Unit
) {
    LineDetailsBottomSheet(
        lineInfo = lineInfo,
        sheetState = null,
        viewModel = viewModel,
        onDismiss = {},
        onBackToStation = onBackToStation
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
        allLines.forEach { feature ->
            val layerId = "layer-${feature.properties.ligne}-${feature.properties.codeTrace}"
            
            // Afficher la couche si c'est la ligne sélectionnée, sinon la cacher
            style.getLayer(layerId)?.let { layer ->
                if (feature.properties.ligne.equals(selectedLineName, ignoreCase = true)) {
                    layer.setProperties(PropertyFactory.visibility("visible"))
                } else {
                    layer.setProperties(PropertyFactory.visibility("none"))
                }
            }
        }
        
        // Filtrer également les arrêts pour n'afficher que ceux de la ligne sélectionnée
        filterMapStops(style, selectedLineName)
    }
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
    
    // Créer le nom de la propriété pour cette ligne
    val linePropertyName = "has_line_${selectedLineName.uppercase()}"
    
    (-25..25).forEach { idx ->
        val priorityLayerId = "$priorityLayerPrefix-$idx"
        val secondaryLayerId = "$secondaryLayerPrefix-$idx"
        
        (style.getLayer(priorityLayerId) as? SymbolLayer)?.let { layer ->
            // Filtrer pour n'afficher que les arrêts qui ont la propriété has_line_X = true
            layer.setFilter(
                Expression.all(
                    Expression.eq(Expression.get("priority_stop"), true),
                    Expression.eq(Expression.get("slot"), idx),
                    Expression.eq(Expression.get(linePropertyName), true)
                )
            )
            // Garder le même minZoom que d'habitude (déjà à PRIORITY_STOPS_MIN_ZOOM)
        }
        
        (style.getLayer(secondaryLayerId) as? SymbolLayer)?.let { layer ->
            // En mode filtré, afficher tous les arrêts de la ligne (y compris trams et correspondances métro)
            // ET promouvoir leur zoom au niveau priority pour une meilleure visibilité
            layer.setFilter(
                Expression.all(
                    Expression.eq(Expression.get("priority_stop"), false),
                    Expression.eq(Expression.get("slot"), idx),
                    Expression.eq(Expression.get(linePropertyName), true)
                )
            )
            // Réduire le minZoom pour TOUS les arrêts de la ligne en mode filtré
            // (pas seulement les trams, pour voir aussi les correspondances métro)
            layer.setMinZoom(PRIORITY_STOPS_MIN_ZOOM)
        }
    }
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
        
        // Réafficher tous les arrêts
        showAllMapStops(style)
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
    
    // Restaurer les filtres originaux pour tous les arrêts
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
            // Restaurer le minZoom original pour les arrêts secondaires
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
        
        // Créer le GeoJSON pour la ligne
        val lineGeoJson = createGeoJsonFromFeature(feature)
        
        // Ajouter la source de données pour la ligne
        val lineSource = GeoJsonSource(sourceId, lineGeoJson)
        style.addSource(lineSource)
        
        // Obtenir la couleur appropriée pour cette ligne
        val lineColor = LineColorHelper.getColorForLine(feature)
        
        // Créer la couche de ligne avec la couleur appropriée
        val lineLayer = LineLayer(layerId, sourceId).apply {
            setProperties(
                PropertyFactory.lineColor(lineColor),
                PropertyFactory.lineWidth(if (feature.properties.familleTransport == "TRA" || feature.properties.familleTransport == "TRAM") 2f else 4f),
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
        
        // Add click listener for all stop layers
        map.addOnMapClickListener { point ->
            val pixel = map.projection.toScreenLocation(point)
            
            // Check all layers for clicked features
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
        
        // Ajouter la géométrie
        val geometryObject = JsonObject().apply {
            addProperty("type", feature.geometry.type)
            
            // Convertir les coordonnées
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
        
        // Ajouter les propriétés
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
 * Fusionne les arrêts qui ont le même nom (correspondances métro/tram/funiculaire)
 * Fusionne UNIQUEMENT les arrêts M/F/T/Tb entre eux, les bus restent séparés
 */
private fun mergeStopsByName(stops: List<com.pelotcl.app.data.model.StopFeature>): List<com.pelotcl.app.data.model.StopFeature> {
    // Fonction pour vérifier si une ligne est M/F/T/Tb
    fun isMetroTramFuni(line: String): Boolean {
        val upperLine = line.uppercase()
        return upperLine in setOf("A", "B", "C", "D") || // Métro A, B, C, D
               upperLine.startsWith("F") || // Funiculaire (F1, F2)
               upperLine.startsWith("T") // Tram (T1-T7) et Trolleybus (Tb)
    }
    
    // Fonction pour normaliser un nom d'arrêt (même logique que dans TransportViewModel)
    fun normalizeStopName(name: String): String {
        return name.filter { it.isLetter() }.lowercase()
    }
    
    // Séparer les arrêts en deux groupes :
    // - Arrêts qui ne contiennent QUE des lignes M/F/T/Tb (pas de bus)
    // - Tous les autres arrêts (bus purs ou mixtes bus+M/F/T/Tb)
    val (pureMetroTramFuni, otherStops) = stops.partition { stop ->
        val allLines = BusIconHelper.getAllLinesForStop(stop)
        val isPure = allLines.isNotEmpty() && allLines.all { isMetroTramFuni(it) }
        
        // Debug log pour certains arrêts
        if (stop.properties.nom.contains("Perrache", ignoreCase = true) || 
            stop.properties.nom.contains("Bellecour", ignoreCase = true)) {
            android.util.Log.d("MergeStops", "Arrêt: ${stop.properties.nom}, Lignes: $allLines, isPure: $isPure")
        }
        
        isPure
    }
    
    // Grouper les arrêts M/F/T/Tb purs par nom NORMALISÉ (sans espaces, ponctuation, etc.)
    val stopsByName = pureMetroTramFuni.groupBy { normalizeStopName(it.properties.nom) }
    
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
            // Un seul arrêt M/F/T/Tb : pas de fusion
            stopsGroup.first()
        } else {
            // Plusieurs arrêts M/F/T/Tb avec le même nom : fusionner
            val mergedDesserte = stopsGroup
                .flatMap { BusIconHelper.getAllLinesForStop(it) }
                .distinct()
                .sorted()
                .joinToString(", ")
            
            val firstStop = stopsGroup.first()
            val isPmr = stopsGroup.any { it.properties.pmr }
            
            // Retourner un seul arrêt fusionné
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
                    lastUpdate = firstStop.properties.lastUpdate.orEmpty(),
                    lastUpdateFme = firstStop.properties.lastUpdateFme.orEmpty(),
                    adresse = firstStop.properties.adresse.orEmpty(),
                    localiseFaceAAdresse = firstStop.properties.localiseFaceAAdresse,
                    commune = firstStop.properties.commune.orEmpty(),
                    insee = firstStop.properties.insee.orEmpty(),
                    zone = firstStop.properties.zone.orEmpty()
                )
            )
        }
    }
    
    // Retourner les arrêts fusionnés (M/F/T/Tb purs) + tous les autres arrêts (bus et mixtes)
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
    
    // Fusionner les arrêts qui ont le même nom (correspondances métro/tram/funiculaire)
    val mergedStops = mergeStopsByName(stops)

    mergedStops.forEach { stop ->
        val lineNamesAll = BusIconHelper.getAllLinesForStop(stop)
        if (lineNamesAll.isEmpty()) return@forEach
        val drawableNamesAll = BusIconHelper.getAllDrawableNamesForStop(stop)
        
        // Filter to only available icons AND keep lineNames synchronized
        val validPairs = lineNamesAll.zip(drawableNamesAll).filter { (_, drawableName) ->
            val id = context.resources.getIdentifier(drawableName, "drawable", context.packageName)
            id != 0
        }
        
        if (validPairs.isEmpty()) return@forEach
        
        val lineNames = validPairs.map { it.first }
        val drawableNames = validPairs.map { it.second }
        
        // Vérifier si l'arrêt dessert au moins un tram
        val hasTram = lineNames.any { it.uppercase().startsWith("T") }

        // Compute centered slot indices for N icons: -(n-1), -(n-3), ..., (n-1)
        val n = drawableNames.size
        var slot = -(n - 1)

        drawableNames.forEachIndexed { index, iconName ->
            // Déterminer la ligne correspondant à cette icône
            val lineName = lineNames[index].uppercase()
            
            // isPriorityStop doit être basé sur l'ICÔNE actuelle, pas sur la ligne principale de l'arrêt
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
                    
                    // Ajouter TOUTES les lignes de l'arrêt comme propriétés booléennes
                    // (pas seulement celles qui ont un drawable)
                    // Cela permet à toutes les features d'un arrêt d'avoir les mêmes propriétés has_line_*
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

