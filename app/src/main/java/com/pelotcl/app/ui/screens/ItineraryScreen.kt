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
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.ui.draw.rotate
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
import com.pelotcl.app.data.repository.ItineraryPreferencesRepository
import com.pelotcl.app.data.repository.JourneyLeg
import com.pelotcl.app.data.repository.JourneyResult
import com.pelotcl.app.data.repository.RaptorStop
import com.pelotcl.app.ui.components.StationSearchResult
import com.pelotcl.app.ui.theme.Gray200
import com.pelotcl.app.ui.theme.Gray700
import com.pelotcl.app.ui.theme.Red500
import com.pelotcl.app.ui.viewmodel.TransportViewModel
import androidx.compose.runtime.Immutable
import com.pelotcl.app.utils.BusIconHelper
import com.pelotcl.app.utils.LineColorHelper
import com.pelotcl.app.utils.ListItemRecompositionCounter
import com.pelotcl.app.utils.LocationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.geometry.LatLng
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

/**
 * Enum for time selection mode: departure or arrival
 */
enum class TimeMode {
    DEPARTURE,  // Search by departure time (default)
    ARRIVAL     // Search by arrival time ("I need to be there by...")
}

/**
 * Represents a selected stop for the itinerary
 */
@Immutable
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
    onBack: () -> Unit,
    onMapViewChanged: (Boolean) -> Unit = {},
    backFromMapTrigger: Int = 0
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val itineraryPrefsRepo = remember { ItineraryPreferencesRepository(context) }

    // Track selected journey for map view
    var selectedJourney by remember { mutableStateOf<JourneyResult?>(null) }
    
    // Notify parent when map view state changes
    LaunchedEffect(selectedJourney) {
        onMapViewChanged(selectedJourney != null)
    }
    
    // Handle back from map request from parent (e.g., Plan button in navbar)
    LaunchedEffect(backFromMapTrigger) {
        if (backFromMapTrigger > 0 && selectedJourney != null) {
            selectedJourney = null
        }
    }

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

    // Time selection mode: departure (default) or arrival
    var timeMode by remember { mutableStateOf(TimeMode.DEPARTURE) }
    
    // Selected time in seconds from midnight (null = use current time)
    var selectedTimeSeconds by remember { mutableStateOf<Int?>(null) }
    
    // Selected date (null = today)
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    
    // Show time picker dialog
    var showTimePicker by remember { mutableStateOf(false) }
    
    // Show date picker dialog
    var showDatePicker by remember { mutableStateOf(false) }

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
    // Also recalculate when time mode, selected time, or date changes
    LaunchedEffect(departureStop, arrivalStop, isRaptorReady, timeMode, selectedTimeSeconds, selectedDate) {
        if (departureStop != null && arrivalStop != null &&
            departureStop!!.stopIds.isNotEmpty() && arrivalStop!!.stopIds.isNotEmpty()) {
            isLoading = true
            errorMessage = null
            fallbackInfoMessage = null
            journeys = emptyList()

            scope.launch {
                try {
                    var results: List<JourneyResult>
                    val searchDate = selectedDate ?: LocalDate.now()
                    
                    // Get current blocked route patterns from preferences
                    val blockedRouteNames = itineraryPrefsRepo.getBlockedRoutePatterns()
                    
                    when (timeMode) {
                        TimeMode.DEPARTURE -> {
                            // Search by departure time
                            results = raptorRepository.getOptimizedPaths(
                                originStopIds = departureStop!!.stopIds,
                                destinationStopIds = arrivalStop!!.stopIds,
                                departureTimeSeconds = selectedTimeSeconds,
                                date = searchDate,
                                blockedRouteNames = blockedRouteNames
                            )

                            // If no results with selected/current time, try with 9:00 AM as fallback test
                            if (results.isEmpty() && selectedTimeSeconds == null) {
                                results = raptorRepository.getOptimizedPaths(
                                    originStopIds = departureStop!!.stopIds,
                                    destinationStopIds = arrivalStop!!.stopIds,
                                    departureTimeSeconds = 9 * 3600, // 9:00 AM
                                    date = searchDate,
                                    blockedRouteNames = blockedRouteNames
                                )
                            }
                        }
                        TimeMode.ARRIVAL -> {
                            // Search by arrival time (arrive by X)
                            val arrivalTime = selectedTimeSeconds ?: run {
                                // Default to current time + 1 hour if no time selected
                                val cal = Calendar.getInstance()
                                (cal.get(Calendar.HOUR_OF_DAY) + 1) * 3600 + cal.get(Calendar.MINUTE) * 60
                            }
                            results = raptorRepository.getOptimizedPathsArriveBy(
                                originStopIds = departureStop!!.stopIds,
                                destinationStopIds = arrivalStop!!.stopIds,
                                arrivalTimeSeconds = arrivalTime,
                                searchWindowMinutes = 120,
                                date = searchDate,
                                blockedRouteNames = blockedRouteNames
                            )
                        }
                    }

                    // If still no results and we have nearby stops for fallback, try them
                    // But only during initial load, not when user makes changes
                    if (results.isEmpty() && nearbyDepartureStops.isNotEmpty() && !hasManuallySelectedDeparture && isInitialLoad && timeMode == TimeMode.DEPARTURE) {
                        // Skip the first stop (already tried) and try the others
                        val fallbackStops = nearbyDepartureStops.drop(1)

                        for (fallbackStop in fallbackStops) {
                            val fallbackStopIds = raptorRepository.searchStopsByName(fallbackStop.name)
                                .map { it.id }

                            if (fallbackStopIds.isEmpty()) continue

                            // Try current time
                            results = raptorRepository.getOptimizedPaths(
                                originStopIds = fallbackStopIds,
                                destinationStopIds = arrivalStop!!.stopIds,
                                date = searchDate,
                                blockedRouteNames = blockedRouteNames
                            )

                            // If no results, try 9:00 AM
                            if (results.isEmpty()) {
                                results = raptorRepository.getOptimizedPaths(
                                    originStopIds = fallbackStopIds,
                                    destinationStopIds = arrivalStop!!.stopIds,
                                    departureTimeSeconds = 9 * 3600,
                                    date = searchDate,
                                    blockedRouteNames = blockedRouteNames
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
                        userLocation = userLocation,
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
                                if (it.isEmpty()) {
                                    departureStop = null
                                }
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
                                if (it.isEmpty()) {
                                    arrivalStop = null
                                }
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

                        Spacer(modifier = Modifier.height(16.dp))

                        // Time and date selection row
                        TimeSelectionRow(
                            timeMode = timeMode,
                            selectedTimeSeconds = selectedTimeSeconds,
                            selectedDate = selectedDate,
                            onTimeModeChange = { newMode -> 
                                timeMode = newMode
                                // When switching to ARRIVAL mode, set a default time if none selected
                                if (newMode == TimeMode.ARRIVAL && selectedTimeSeconds == null) {
                                    val cal = Calendar.getInstance()
                                    selectedTimeSeconds = (cal.get(Calendar.HOUR_OF_DAY) + 1) * 3600 + cal.get(Calendar.MINUTE) * 60
                                }
                            },
                            onTimeClick = { showTimePicker = true },
                            onDateClick = { showDatePicker = true },
                            onClearDateTime = { 
                                selectedTimeSeconds = null
                                selectedDate = null
                            }
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
            
            // Time picker dialog - must be outside LazyColumn
            if (showTimePicker) {
                TimePickerDialog(
                    initialTimeSeconds = selectedTimeSeconds ?: run {
                        val cal = Calendar.getInstance()
                        cal.get(Calendar.HOUR_OF_DAY) * 3600 + cal.get(Calendar.MINUTE) * 60
                    },
                    onTimeSelected = { timeSeconds ->
                        selectedTimeSeconds = timeSeconds
                        showTimePicker = false
                    },
                    onDismiss = { showTimePicker = false }
                )
            }
            
            // Date picker dialog
            if (showDatePicker) {
                DatePickerDialog(
                    initialDate = selectedDate ?: LocalDate.now(),
                    onDateSelected = { date ->
                        selectedDate = date
                        showDatePicker = false
                    },
                    onDismiss = { showDatePicker = false }
                )
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
    val resourceId = BusIconHelper.getResourceIdForLine(context, lineName)

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
                    val resourceId = BusIconHelper.getResourceIdForLine(context, leg.routeName ?: "")

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
                        val resourceId = BusIconHelper.getResourceIdForLine(context, leg.routeName ?: "")

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

/**
 * Row for selecting departure/arrival time mode and picking a date/time
 */
@Composable
private fun TimeSelectionRow(
    timeMode: TimeMode,
    selectedTimeSeconds: Int?,
    selectedDate: LocalDate?,
    onTimeModeChange: (TimeMode) -> Unit,
    onTimeClick: () -> Unit,
    onDateClick: () -> Unit,
    onClearDateTime: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(12.dp)
    ) {
        // First row: Mode toggle and clear button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Mode toggle buttons
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Departure mode button
                Box(
                    modifier = Modifier
                        .background(
                            color = if (timeMode == TimeMode.DEPARTURE) Color.White.copy(alpha = 0.2f) else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { onTimeModeChange(TimeMode.DEPARTURE) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Départ",
                        color = if (timeMode == TimeMode.DEPARTURE) Color.White else Color.White.copy(alpha = 0.6f),
                        fontWeight = if (timeMode == TimeMode.DEPARTURE) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 14.sp
                    )
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // Arrival mode button
                Box(
                    modifier = Modifier
                        .background(
                            color = if (timeMode == TimeMode.ARRIVAL) Color.White.copy(alpha = 0.2f) else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { onTimeModeChange(TimeMode.ARRIVAL) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Arrivée",
                        color = if (timeMode == TimeMode.ARRIVAL) Color.White else Color.White.copy(alpha = 0.6f),
                        fontWeight = if (timeMode == TimeMode.ARRIVAL) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 14.sp
                    )
                }
            }
            
            // Clear button (only show if date or time is set)
            if (selectedTimeSeconds != null || selectedDate != null) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Réinitialiser",
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { onClearDateTime() }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Second row: Date and time pickers
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Date picker
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .background(
                        color = Color.White.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { onDateClick() }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarToday,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatDateDisplay(selectedDate),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            
            // Time picker
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .background(
                        color = Color.White.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { onTimeClick() }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (selectedTimeSeconds != null) {
                        formatTimeSeconds(selectedTimeSeconds)
                    } else {
                        "Maintenant"
                    },
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Simple time picker dialog using wheel-style pickers
 * Minutes are rounded to 5-minute intervals
 */
@Composable
private fun TimePickerDialog(
    initialTimeSeconds: Int,
    onTimeSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val initialHour = (initialTimeSeconds / 3600) % 24
    // Round initial minute to nearest 5 minutes
    val initialMinute = ((initialTimeSeconds % 3600) / 60 / 5) * 5
    
    var selectedHour by remember { mutableStateOf(initialHour) }
    var selectedMinute by remember { mutableStateOf(initialMinute) }
    
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Time display
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    // Hour picker
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = { selectedHour = (selectedHour + 1) % 24 }) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = "Augmenter l'heure",
                                tint = Color.White
                            )
                        }
                        Text(
                            text = String.format(java.util.Locale.ROOT, "%02d", selectedHour),
                            color = Color.White,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { selectedHour = if (selectedHour == 0) 23 else selectedHour - 1 }) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Diminuer l'heure",
                                tint = Color.White
                            )
                        }
                    }
                    
                    Text(
                        text = ":",
                        color = Color.White,
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    
                    // Minute picker
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = { selectedMinute = (selectedMinute + 5) % 60 }) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = "Augmenter les minutes",
                                tint = Color.White
                            )
                        }
                        Text(
                            text = String.format(java.util.Locale.ROOT, "%02d", selectedMinute),
                            color = Color.White,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { selectedMinute = if (selectedMinute < 5) 55 else selectedMinute - 5 }) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Diminuer les minutes",
                                tint = Color.White
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = "Annuler",
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier
                            .clickable { onDismiss() }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(
                                color = Red500,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { 
                                onTimeSelected(selectedHour * 3600 + selectedMinute * 60) 
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Confirmer",
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

/**
 * Format time in seconds to HH:mm string
 */
private fun formatTimeSeconds(seconds: Int): String {
    val hours = (seconds / 3600) % 24
    val minutes = (seconds % 3600) / 60
    return String.format(java.util.Locale.ROOT, "%02d:%02d", hours, minutes)
}

/**
 * Format date for display
 * Shows "Aujourd'hui" for today, "Demain" for tomorrow, or the date
 */
private fun formatDateDisplay(date: LocalDate?): String {
    if (date == null) return "Aujourd'hui"
    
    val today = LocalDate.now()
    val tomorrow = today.plusDays(1)
    
    return when (date) {
        today -> "Aujourd'hui"
        tomorrow -> "Demain"
        else -> {
            val formatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.FRENCH)
            date.format(formatter).replaceFirstChar { it.uppercase() }
        }
    }
}

/**
 * Date picker dialog for selecting a journey date
 * Allows selecting dates with month navigation
 */
@Composable
private fun DatePickerDialog(
    initialDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedDate by remember { mutableStateOf(initialDate) }
    val today = LocalDate.now()
    
    // Current displayed month
    var displayedMonth by remember { 
        mutableStateOf(initialDate.withDayOfMonth(1)) 
    }
    
    val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRENCH)
    val dayOfWeekFormatter = DateTimeFormatter.ofPattern("E", Locale.FRENCH)
    
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Month navigation
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Previous month button
                    IconButton(
                        onClick = { 
                            val prevMonth = displayedMonth.minusMonths(1)
                            if (!prevMonth.plusMonths(1).isBefore(today.withDayOfMonth(1))) {
                                displayedMonth = prevMonth
                            }
                        },
                        enabled = !displayedMonth.isBefore(today.withDayOfMonth(1)) && 
                                  !displayedMonth.isEqual(today.withDayOfMonth(1))
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "Mois précédent",
                            tint = if (!displayedMonth.isBefore(today.withDayOfMonth(1)) && 
                                       !displayedMonth.isEqual(today.withDayOfMonth(1))) 
                                Color.White else Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.rotate(-90f)
                        )
                    }
                    
                    Text(
                        text = displayedMonth.format(monthFormatter).replaceFirstChar { it.uppercase() },
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    
                    // Next month button
                    IconButton(onClick = { displayedMonth = displayedMonth.plusMonths(1) }) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "Mois suivant",
                            tint = Color.White,
                            modifier = Modifier.rotate(90f)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Days of week header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf("L", "M", "M", "J", "V", "S", "D").forEach { day ->
                        Text(
                            text = day,
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.width(36.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Calendar grid
                val firstDayOfMonth = displayedMonth
                val lastDayOfMonth = displayedMonth.plusMonths(1).minusDays(1)
                // Monday = 1, Sunday = 7, we want Monday as first column (index 0)
                val firstDayOfWeek = (firstDayOfMonth.dayOfWeek.value - 1) // 0 = Monday
                val daysInMonth = lastDayOfMonth.dayOfMonth
                
                // Generate calendar rows (max 6 weeks)
                val calendarDays = mutableListOf<LocalDate?>()
                // Add empty cells for days before the first of month
                repeat(firstDayOfWeek) { calendarDays.add(null) }
                // Add all days of the month
                for (day in 1..daysInMonth) {
                    calendarDays.add(displayedMonth.withDayOfMonth(day))
                }
                // Fill remaining cells to complete the last row
                while (calendarDays.size % 7 != 0) {
                    calendarDays.add(null)
                }
                
                calendarDays.chunked(7).forEach { week ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        week.forEach { date ->
                            if (date != null) {
                                val isSelected = date == selectedDate
                                val isToday = date == today
                                val isSelectable = !date.isBefore(today)
                                
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(
                                            color = when {
                                                isSelected -> Red500
                                                isToday -> Color.White.copy(alpha = 0.15f)
                                                else -> Color.Transparent
                                            },
                                            shape = CircleShape
                                        )
                                        .then(
                                            if (isSelectable) Modifier.clickable { selectedDate = date }
                                            else Modifier
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = date.dayOfMonth.toString(),
                                        color = when {
                                            !isSelectable -> Color.White.copy(alpha = 0.3f)
                                            isSelected -> Color.White
                                            else -> Color.White.copy(alpha = 0.9f)
                                        },
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            } else {
                                // Empty cell
                                Box(modifier = Modifier.size(36.dp))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = "Annuler",
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier
                            .clickable { onDismiss() }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(
                                color = Red500,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { onDateSelected(selectedDate) }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Confirmer",
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}