package com.pelotcl.app.ui.screens

import JourneyMapView
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pelotcl.app.data.repository.JourneyLeg
import com.pelotcl.app.data.repository.JourneyResult
import com.pelotcl.app.data.repository.RaptorStop
import com.pelotcl.app.ui.components.StationSearchResult
import com.pelotcl.app.ui.theme.Gray200
import com.pelotcl.app.ui.theme.Gray700
import com.pelotcl.app.ui.theme.Red500
import com.pelotcl.app.ui.viewmodel.TransportViewModel
import com.pelotcl.app.utils.BusIconHelper
import com.pelotcl.app.utils.LineColorHelper
import com.pelotcl.app.utils.ListItemRecompositionCounter
import com.pelotcl.app.utils.LocationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.geometry.LatLng

/**
 * Represents a selected stop for the itinerary
 */
data class SelectedStop(
    val name: String,
    val stopIds: List<Int>
)

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItineraryScreen(
    destinationStopName: String,
    userLocation: LatLng?,
    viewModel: TransportViewModel,
    onBack: () -> Unit
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    // Track selected journey for map view
    var selectedJourney by remember { mutableStateOf<JourneyResult?>(null) }

    // Bottom sheet state for swipable journey details
    val journeySheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.PartiallyExpanded,
        skipHiddenState = true
    )
    val journeyScaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = journeySheetState
    )

    val sheetPeekHeight = 254.dp

    // Change status bar based on whether map is shown (light background) or list view (dark background)
    LaunchedEffect(selectedJourney) {
        (view.context as? ComponentActivity)?.enableEdgeToEdge(
            statusBarStyle = if (selectedJourney != null) {
                // Map view: light background, need dark icons (black)
                SystemBarStyle.light(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT
                )
            } else {
                // List view: dark background, need light icons (white)
                SystemBarStyle.dark(
                    android.graphics.Color.TRANSPARENT
                )
            },
            navigationBarStyle = SystemBarStyle.dark(
                android.graphics.Color.TRANSPARENT
            )
        )
    }

    // Use shared RaptorRepository from ViewModel (kept alive during app lifetime)
    val raptorRepository = viewModel.raptorRepository

    // Track if Raptor is ready
    var isRaptorReady by remember { mutableStateOf(raptorRepository.isReady()) }

    // Stop selection states - initialize with names immediately
    var departureStop by remember { mutableStateOf<SelectedStop?>(null) }
    var arrivalStop by remember {
        mutableStateOf(
            if (destinationStopName.isNotBlank()) {
                SelectedStop(name = destinationStopName, stopIds = emptyList())
            } else null
        )
    }

    // Search states
    var departureQuery by remember { mutableStateOf("") }
    var arrivalQuery by remember { mutableStateOf("") }
    var departureSearchResults by remember { mutableStateOf<List<StationSearchResult>>(emptyList()) }
    var arrivalSearchResults by remember { mutableStateOf<List<StationSearchResult>>(emptyList()) }
    var isSearchingDeparture by remember { mutableStateOf(false) }
    var isSearchingArrival by remember { mutableStateOf(false) }

    // Journey results
    var journeys by remember { mutableStateOf<List<JourneyResult>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var fallbackInfoMessage by remember { mutableStateOf<String?>(null) }

    // Track if user has manually selected a departure stop (to avoid auto-override)
    var hasManuallySelectedDeparture by remember { mutableStateOf(false) }

    // Track last location used for departure stop calculation
    var lastLocationUsedForDeparture by remember { mutableStateOf<LatLng?>(null) }

    // Store nearby stops for fallback when no itinerary is found
    var nearbyDepartureStops by remember { mutableStateOf<List<RaptorStop>>(emptyList()) }

    // Track if this is the initial screen opening (to allow fallback only on first load)
    var isInitialLoad by remember { mutableStateOf(true) }

    // Initialize raptor (only if not already initialized) and resolve arrival stop IDs
    LaunchedEffect(Unit) {
        // Initialize Raptor if not ready
        if (!raptorRepository.isReady()) {
            raptorRepository.initialize()
        }

        isRaptorReady = true
        // Resolve arrival stop IDs from the destination name
        if (destinationStopName.isNotBlank()) {
            val arrivalResults = raptorRepository.searchStopsByName(destinationStopName)
            if (arrivalResults.isNotEmpty()) {
                arrivalStop = SelectedStop(
                    name = destinationStopName,
                    stopIds = arrivalResults.map { it.id }
                )
            }
        }
    }

    // Recalculate nearest departure stop when location changes significantly
    LaunchedEffect(userLocation, isRaptorReady) {
        if (!isRaptorReady || userLocation == null) return@LaunchedEffect

        // Only auto-update if user hasn't manually selected a departure
        if (hasManuallySelectedDeparture) return@LaunchedEffect

        // Check if location has changed significantly (more than 100 meters)
        val shouldUpdate = lastLocationUsedForDeparture == null ||
                LocationHelper.isSignificantlyDifferent(lastLocationUsedForDeparture, userLocation, 100.0)

        if (shouldUpdate) {
            // Get multiple nearby stops for fallback capability
            val nearestStops = raptorRepository.findNearestStops(
                latitude = userLocation.latitude,
                longitude = userLocation.longitude,
                limit = 5
            )
            nearbyDepartureStops = nearestStops

            if (nearestStops.isNotEmpty()) {
                val closestStop = nearestStops.first()
                val allStopsWithName = raptorRepository.searchStopsByName(closestStop.name)
                departureStop = SelectedStop(
                    name = closestStop.name,
                    stopIds = allStopsWithName.map { it.id }
                )
                lastLocationUsedForDeparture = userLocation
            }
        }
    }

    // Search for departure stops when query changes
    LaunchedEffect(departureQuery) {
        if (departureQuery.length >= 2) {
            delay(300) // Debounce
            withContext(Dispatchers.IO) {
                val results = viewModel.searchStops(departureQuery)
                departureSearchResults = results
            }
        } else {
            departureSearchResults = emptyList()
        }
    }

    // Search for arrival stops when query changes
    LaunchedEffect(arrivalQuery) {
        if (arrivalQuery.length >= 2) {
            delay(300) // Debounce
            withContext(Dispatchers.IO) {
                val results = viewModel.searchStops(arrivalQuery)
                arrivalSearchResults = results
            }
        } else {
            arrivalSearchResults = emptyList()
        }
    }

    // Calculate journey when both stops are selected and have IDs resolved
    LaunchedEffect(departureStop, arrivalStop, isRaptorReady) {
        if (departureStop != null && arrivalStop != null &&
            departureStop!!.stopIds.isNotEmpty() && arrivalStop!!.stopIds.isNotEmpty()) {
            isLoading = true
            errorMessage = null
            fallbackInfoMessage = null
            journeys = emptyList()

            scope.launch {
                try {
                    // Try with current time first
                    var results = raptorRepository.getOptimizedPaths(
                        originStopIds = departureStop!!.stopIds,
                        destinationStopIds = arrivalStop!!.stopIds
                    )

                    // If no results with current time, try with 9:00 AM as fallback test
                    if (results.isEmpty()) {
                        results = raptorRepository.getOptimizedPaths(
                            originStopIds = departureStop!!.stopIds,
                            destinationStopIds = arrivalStop!!.stopIds,
                            departureTimeSeconds = 9 * 3600 // 9:00 AM
                        )
                    }

                    // If still no results and we have nearby stops for fallback, try them
                    // But only during initial load, not when user makes changes
                    if (results.isEmpty() && nearbyDepartureStops.isNotEmpty() && !hasManuallySelectedDeparture && isInitialLoad) {
                        // Skip the first stop (already tried) and try the others
                        val fallbackStops = nearbyDepartureStops.drop(1)

                        for (fallbackStop in fallbackStops) {
                            val fallbackStopIds = raptorRepository.searchStopsByName(fallbackStop.name)
                                .map { it.id }

                            if (fallbackStopIds.isEmpty()) continue

                            // Try current time
                            results = raptorRepository.getOptimizedPaths(
                                originStopIds = fallbackStopIds,
                                destinationStopIds = arrivalStop!!.stopIds
                            )

                            // If no results, try 9:00 AM
                            if (results.isEmpty()) {
                                results = raptorRepository.getOptimizedPaths(
                                    originStopIds = fallbackStopIds,
                                    destinationStopIds = arrivalStop!!.stopIds,
                                    departureTimeSeconds = 9 * 3600
                                )
                            }

                            if (results.isNotEmpty()) {
                                // Found a route from a fallback stop - update departure
                                departureStop = SelectedStop(
                                    name = fallbackStop.name,
                                    stopIds = fallbackStopIds
                                )
                                fallbackInfoMessage = "Itinéraire depuis ${ fallbackStop.name } (arrêt proche avec service)"
                                break
                            }
                        }
                    }

                    // Mark that initial load is complete
                    isInitialLoad = false

                    journeys = results
                    if (results.isEmpty()) {
                        errorMessage = "Aucun itinéraire trouvé depuis les arrêts proches"
                    }
                } catch (e: Exception) {
                    Log.e("ItineraryScreen", "Error calculating journey", e)
                    errorMessage = "Erreur lors du calcul de l'itinéraire"
                } finally {
                    isLoading = false
                }
            }
        }
    }

    // Handle back button press
    BackHandler {
        when {
            // If sheet is expanded, collapse it first
            selectedJourney != null &&
                    journeyScaffoldState.bottomSheetState.currentValue == SheetValue.Expanded -> {
                scope.launch { journeyScaffoldState.bottomSheetState.partialExpand() }
            }
            // If viewing journey map (sheet collapsed), go back to journey list
            selectedJourney != null -> {
                selectedJourney = null
            }
            // Otherwise, exit the itinerary screen
            else -> onBack()
        }
    }

    Scaffold(
        topBar = {
            // Only show TopAppBar when not viewing the map (map has its own back button)
            if (selectedJourney == null) {
                TopAppBar(
                    title = {
                        Text(
                            text = "Itinéraire",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Retour",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black
                    )
                )
            }
        }
    ) { contentPadding ->
        // Show map view when a journey is selected
        if (selectedJourney != null) {
            // Calculate map padding based on sheet state
            val mapBottomPadding by remember {
                derivedStateOf {
                    when (journeyScaffoldState.bottomSheetState.currentValue) {
                        SheetValue.Expanded -> 450
                        else -> 220
                    }
                }
            }

            BottomSheetScaffold(
                scaffoldState = journeyScaffoldState,
                sheetPeekHeight = sheetPeekHeight,
                sheetContainerColor = Color.Black,
                sheetShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                sheetDragHandle = {
                    // Drag handle bar
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .width(32.dp)
                                .height(4.dp)
                                .background(
                                    Color.White.copy(alpha = 0.4f),
                                    RoundedCornerShape(2.dp)
                                )
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                },
                sheetContent = {
                    JourneyDetailsSheetContent(
                        journey = selectedJourney!!,
                        isExpanded = journeyScaffoldState.bottomSheetState.currentValue == SheetValue.Expanded,
                        modifier = Modifier
                            .navigationBarsPadding()
                            .padding(bottom = 20.dp)
                    )
                }
            ) { _ ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    JourneyMapView(
                        journey = selectedJourney!!,
                        onBack = { selectedJourney = null },
                        bottomPadding = mapBottomPadding,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        } else {
            // Normal journey list view - using LazyColumn for performance
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .padding(contentPadding)
            ) {
                // Stop selection fields (sticky header-like behavior)
                item(key = "stop_selection") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        // Departure stop field
                        StopSelectionField(
                            selectedStop = departureStop,
                            query = departureQuery,
                            onQueryChange = {
                                departureQuery = it
                                isSearchingDeparture = it.isNotEmpty()
                            },
                            isSearching = isSearchingDeparture,
                            searchResults = departureSearchResults,
                            onStopSelected = { result ->
                                scope.launch {
                                    val raptorStops = raptorRepository.searchStopsByName(result.stopName)
                                    departureStop = SelectedStop(
                                        name = result.stopName,
                                        stopIds = raptorStops.map { it.id }
                                    )
                                    departureQuery = ""
                                    isSearchingDeparture = false
                                    departureSearchResults = emptyList()
                                    // Mark as manually selected to prevent auto-override
                                    hasManuallySelectedDeparture = true
                                }
                            },
                            onClear = {
                                departureStop = null
                                departureQuery = ""
                                isSearchingDeparture = false
                                // Don't reset manual selection flag - user manually cleared
                                // This prevents automatic fallback when field is empty
                                hasManuallySelectedDeparture = true
                                lastLocationUsedForDeparture = null
                            },
                            icon = Icons.Default.MyLocation,
                            onRefresh = if (userLocation != null) {
                                {
                                    scope.launch {
                                        // Force recalculation from current GPS location
                                        hasManuallySelectedDeparture = false
                                        fallbackInfoMessage = null
                                        isInitialLoad = true // Allow fallback on refresh

                                        // Get multiple nearby stops for fallback capability
                                        val nearestStops = raptorRepository.findNearestStops(
                                            latitude = userLocation.latitude,
                                            longitude = userLocation.longitude,
                                            limit = 5
                                        )
                                        nearbyDepartureStops = nearestStops

                                        if (nearestStops.isNotEmpty()) {
                                            val closestStop = nearestStops.first()
                                            val allStopsWithName = raptorRepository.searchStopsByName(closestStop.name)
                                            departureStop = SelectedStop(
                                                name = closestStop.name,
                                                stopIds = allStopsWithName.map { it.id }
                                            )
                                            lastLocationUsedForDeparture = userLocation
                                        }
                                    }
                                }
                            } else null,
                            keyboardController = keyboardController
                        )

                        // Swap button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            IconButton(
                                onClick = {
                                    val temp = departureStop
                                    departureStop = arrivalStop
                                    arrivalStop = temp
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SwapVert,
                                    contentDescription = "Inverser",
                                    tint = Color.White
                                )
                            }
                        }

                        // Arrival stop field
                        StopSelectionField(
                            selectedStop = arrivalStop,
                            query = arrivalQuery,
                            onQueryChange = {
                                arrivalQuery = it
                                isSearchingArrival = it.isNotEmpty()
                            },
                            isSearching = isSearchingArrival,
                            searchResults = arrivalSearchResults,
                            onStopSelected = { result ->
                                scope.launch {
                                    val raptorStops = raptorRepository.searchStopsByName(result.stopName)
                                    arrivalStop = SelectedStop(
                                        name = result.stopName,
                                        stopIds = raptorStops.map { it.id }
                                    )
                                    arrivalQuery = ""
                                    isSearchingArrival = false
                                    arrivalSearchResults = emptyList()
                                }
                            },
                            onClear = {
                                arrivalStop = null
                                arrivalQuery = ""
                                isSearchingArrival = false
                            },
                            icon = Icons.Default.Search,
                            keyboardController = keyboardController
                        )
                    }
                }

                item(key = "spacer_top") {
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Results section
                when {
                    isLoading -> {
                        item(key = "loading") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = Color.White)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Calcul de l'itinéraire...", color = Color.White.copy(alpha = 0.7f))
                                }
                            }
                        }
                    }
                    errorMessage != null -> {
                        item(key = "error") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = errorMessage!!,
                                    color = Color.White.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.bodyLarge,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .padding(horizontal = 10.dp)
                                )
                            }
                        }
                    }
                    journeys.isNotEmpty() -> {
                        // Show fallback info message if an alternative stop was used
                        if (fallbackInfoMessage != null) {
                            item(key = "fallback_message") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                        .padding(bottom = 16.dp)
                                        .background(
                                            color = Color(0xFF2A5298).copy(alpha = 0.6f),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .padding(12.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = fallbackInfoMessage!!,
                                            color = Color.White,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }

                        // Journey cards with stable keys for efficient recomposition
                        itemsIndexed(
                            items = journeys,
                            key = { _, journey -> "${journey.departureTime}_${journey.arrivalTime}" },
                            contentType = { _, _ -> "journey_card" }
                        ) { index, journey ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                            ) {
                                JourneyCard(
                                    journey = journey,
                                    onClick = {
                                        selectedJourney = journey
                                    }
                                )
                                if (index < journeys.size - 1) {
                                    HorizontalDivider(
                                        color = Color.White.copy(alpha = 0.2f),
                                        modifier = Modifier.padding(vertical = 32.dp)
                                    )
                                }
                            }
                        }

                        item(key = "spacer_bottom") {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                    departureStop != null && arrivalStop != null -> {
                        // Both stops selected but no results yet
                    }
                    else -> {
                        item(key = "empty_state") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Sélectionnez un arrêt de départ et d'arrivée",
                                    color = Color.White.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }

                item(key = "navigation_spacer") {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        } // End of else block for selectedJourney == null
    }
}

@Composable
private fun StopSelectionField(
    selectedStop: SelectedStop?,
    query: String,
    onQueryChange: (String) -> Unit,
    isSearching: Boolean,
    searchResults: List<StationSearchResult>,
    onStopSelected: (StationSearchResult) -> Unit,
    onClear: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onRefresh: (() -> Unit)? = null,
    keyboardController: SoftwareKeyboardController? = null
) {
    Column {
        OutlinedTextField(
            value = if (selectedStop != null && !isSearching) selectedStop.name else query,
            onValueChange = onQueryChange,
            leadingIcon = {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Red500
                )
            },
            trailingIcon = {
                Row {
                    // Refresh button (recalculate from GPS)
                    if (onRefresh != null && selectedStop != null && !isSearching) {
                        IconButton(onClick = onRefresh) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Recalculer depuis ma position",
                                tint = Gray700
                            )
                        }
                    }
                    // Clear button
                    if (selectedStop != null || query.isNotEmpty()) {
                        IconButton(onClick = onClear) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Effacer",
                                tint = Gray700
                            )
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.Black,
                unfocusedTextColor = Color.Black,
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                focusedLabelColor = Color.Black,
                unfocusedLabelColor = Color.Gray,
                cursorColor = Color.Black
            ),
            shape = RoundedCornerShape(12.dp)
        )

        // Search results dropdown
        if (isSearching && searchResults.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    searchResults.take(5).forEach { result ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = result.stopName,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = Color.Black
                                )
                            },
                            supportingContent = {
                                if (result.lines.isNotEmpty()) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.padding(top = 4.dp)
                                    ) {
                                        result.lines.take(5).forEach { lineName ->
                                            SmallLineBadge(lineName = lineName)
                                        }
                                        if (result.lines.size > 5) {
                                            Text(
                                                text = "+${result.lines.size - 5}",
                                                fontSize = 10.sp,
                                                color = Gray700
                                            )
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.clickable {
                                keyboardController?.hide()
                                onStopSelected(result)
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.White)
                        )
                        if (result != searchResults.last())
                        {
                            HorizontalDivider(color = Gray200)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallLineBadge(lineName: String) {
    val context = LocalContext.current
    val drawableName = BusIconHelper.getDrawableNameForLineName(lineName)
    val resourceId = context.resources.getIdentifier(drawableName, "drawable", context.packageName)

    if (resourceId != 0) {
        Image(
            painter = painterResource(id = resourceId),
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
    } else {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(Color(LineColorHelper.getColorForLineString(lineName))),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = lineName,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun JourneyCard(
    journey: JourneyResult,
    onClick: () -> Unit = {}
) {
    // Debug: measure the recompositions of this card
    ListItemRecompositionCounter("JourneyList", journey.departureTime)

    // Memoize formatted duration to avoid recalculation on recomposition
    val formattedDuration by remember(journey.durationMinutes) {
        derivedStateOf {
            if (journey.durationMinutes < 60) {
                "${journey.durationMinutes} min"
            } else {
                "${journey.durationMinutes / 60}h${(journey.durationMinutes % 60).toString().padStart(2, '0')}min"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp)
    ) {
        // Header with times and duration
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = journey.formatDepartureTime(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = " → ",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Text(
                    text = journey.formatArrivalTime(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Box(
                modifier = Modifier
                    .background(
                        color = Color.White.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    )
            ) {
                Text(
                    text = formattedDuration,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Journey legs
        journey.legs.forEachIndexed { index, leg ->
            key("${leg.fromStopId}_${leg.departureTime}") {
                JourneyLegItem(
                    leg = leg,
                    isFirst = index == 0,
                    isLast = index == journey.legs.size - 1
                )
            }
        }
    }
}

@Composable
private fun JourneyLegItem(
    leg: JourneyLeg,
    isFirst: Boolean,
    isLast: Boolean
) {
    // Debug: measure the recompositions of this leg
    ListItemRecompositionCounter("JourneyLegs", "${leg.fromStopId}_${leg.departureTime}")

    val context = LocalContext.current
    val lineColor = if (leg.isWalking) Gray700 else Color(LineColorHelper.getColorForLineString(leg.routeName ?: ""))

    // State for expanding intermediate stops
    var isExpanded by remember { mutableStateOf(false) }
    val hasIntermediateStops = !leg.isWalking && leg.intermediateStops.isNotEmpty()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .width(40.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxHeight()
            ) {
                if (!isFirst) {
                    Box(modifier = Modifier.width(3.dp).height(8.dp).background(lineColor))
                }

                if (leg.isWalking) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                        contentDescription = null,
                        tint = Gray700,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    val drawableName = BusIconHelper.getDrawableNameForLineName(leg.routeName ?: "")
                    val resourceId = context.resources.getIdentifier(drawableName, "drawable", context.packageName)

                    if (resourceId != 0) {
                        Image(
                            painter = painterResource(id = resourceId),
                            contentDescription = null,
                            modifier = Modifier.size(30.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier.size(24.dp).clip(CircleShape).background(lineColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = leg.routeName ?: "?", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .weight(1f)
                        .background(lineColor)
                )

                if (isLast) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .border(3.dp, lineColor, CircleShape)
                            .background(Color.Black)
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp)
        ) {
            Text(text = leg.fromStopName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = Color.White)
            Text(text = leg.formatDepartureTime(), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (leg.isWalking) "Marche ${leg.durationMinutes} min" else "Direction ${leg.direction ?: leg.toStopName}",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall
            )

            // Expandable intermediate stops section
            if (hasIntermediateStops) {
                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier
                        .clickable { isExpanded = !isExpanded }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = if (isExpanded) "Réduire" else "Développer",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "${leg.intermediateStops.size} arrêt${if (leg.intermediateStops.size > 1) "s" else ""}",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }

                // Expanded intermediate stops list
                if (isExpanded) {
                    Column(
                        modifier = Modifier.padding(start = 20.dp, top = 4.dp)
                    ) {
                        leg.intermediateStops.forEach { stop ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stop.stopName,
                                    color = Color.White.copy(alpha = 0.6f),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = stop.formatArrivalTime(),
                                    color = Color.White.copy(alpha = 0.5f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }

            if (isLast) {
                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = leg.toStopName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                Text(text = leg.formatArrivalTime(), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
            } else {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/**
 * Sheet content for journey details shown in BottomSheetScaffold
 * Shows a compact horizontal view of the journey with line icons
 * Expands to show full itinerary details when sheet is expanded
 */
@Composable
private fun JourneyDetailsSheetContent(
    journey: JourneyResult,
    isExpanded: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Memoize formatted duration to avoid recalculation on recomposition
    val formattedDuration by remember(journey.durationMinutes) {
        derivedStateOf {
            if (journey.durationMinutes < 60) {
                "${journey.durationMinutes} min"
            } else {
                "${journey.durationMinutes / 60}h${(journey.durationMinutes % 60).toString().padStart(2, '0')}min"
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(1f)
            .padding(horizontal = 16.dp)
    ) {
        // --- Header Section (Always Visible) ---
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header with times
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = journey.formatDepartureTime(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = " → ",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    Text(
                        text = journey.formatArrivalTime(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Box(
                    modifier = Modifier
                        .background(
                            color = Color.White.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp)
                        )
                ) {
                    Text(
                        text = formattedDuration,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Horizontal journey summary with line icons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                journey.legs.forEachIndexed { index, leg ->
                    if (leg.isWalking) {
                        // Walking icon
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                            contentDescription = null,
                            tint = Gray700,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        // Transport line icon
                        val drawableName =
                            BusIconHelper.getDrawableNameForLineName(leg.routeName ?: "")
                        val resourceId = context.resources.getIdentifier(
                            drawableName,
                            "drawable",
                            context.packageName
                        )

                        if (resourceId != 0) {
                            Image(
                                painter = painterResource(id = resourceId),
                                contentDescription = null,
                                modifier = Modifier.size(32.dp)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(LineColorHelper.getColorForLineString(leg.routeName ?: ""))),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = leg.routeName ?: "?",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    // Arrow between legs
                    if (index < journey.legs.size - 1) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            HorizontalDivider(
                color = Color.White.copy(alpha = 0.2f),
                thickness = 1.dp
            )
            Spacer(modifier = Modifier.height(32.dp))
        }

        // --- Expanded Details Section ---
        // This content is always part of the layout but pushed down.
        // The weight(1f) ensures it fills the rest of the available height.
        // Scroll is only enabled when the sheet is expanded.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(
                    state = rememberScrollState(),
                    enabled = isExpanded // Scroll only works when expanded
                )
        ) {
            journey.legs.forEachIndexed { index, leg ->
                key("${leg.fromStopId}_${leg.departureTime}") {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        JourneyLegItem(
                            leg = leg,
                            isFirst = index == 0,
                            isLast = index == journey.legs.size - 1
                        )
                    }
                }
            }
            // Bottom spacer to prevent content from being cut off by navigation bar
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}