package com.pelotcl.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.pelotcl.app.data.graph.RoutePath
import com.pelotcl.app.data.graph.RouteSearchService
import com.pelotcl.app.data.graph.StopSearchResult
import com.pelotcl.app.data.gtfs.SchedulesRepository
import com.pelotcl.app.ui.components.AvailableDirection
import com.pelotcl.app.ui.components.RouteResultsSheet
import com.pelotcl.app.ui.components.SimpleSearchBar
import com.pelotcl.app.ui.screens.PlanScreen
import com.pelotcl.app.ui.theme.PeloTheme
import com.pelotcl.app.ui.theme.Red500
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.dark(
                android.graphics.Color.TRANSPARENT
            )
        )
        setContent {
            PeloTheme {
                NavBar(modifier = Modifier.fillMaxSize())
            }
        }
    }
}

private enum class Destination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val contentDescription: String
) {
    PLAN(
        route = "plan",
        label = "Plan",
        icon = Icons.Filled.Map,
        contentDescription = "Onglet Plan"
    ),
    LIGNES(
        route = "lignes",
        label = "Lignes",
        icon = Icons.Filled.Route,
        contentDescription = "Onglet Lignes"
    ),
    PARAMETRES(
        route = "parametres",
        label = "Paramètres",
        icon = Icons.Filled.Settings,
        contentDescription = "Onglet Paramètres"
    );

    companion object {
        val entries: List<Destination> = values().toList()
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun NavBar(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val startDestination = Destination.PLAN
    var selectedDestination by rememberSaveable { mutableIntStateOf(startDestination.ordinal) }
    var isBottomSheetOpen by remember { mutableStateOf(false) }
    var showLinesSheet by remember { mutableStateOf(false) }
    
    // Route search state
    val context = LocalContext.current
    val routeSearchService = remember { RouteSearchService(context) }
    val schedulesRepository = remember { SchedulesRepository(context) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<StopSearchResult>>(emptyList()) }
    var selectedFromStop by remember { mutableStateOf<StopSearchResult?>(null) }
    var selectedToStop by remember { mutableStateOf<StopSearchResult?>(null) }
    var routeResult by remember { mutableStateOf<RoutePath?>(null) }
    var showRouteResults by remember { mutableStateOf(false) }
    
    // Location permission launcher
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.any { it.value }
        if (granted) {
            // Get location and find nearest stop
            try {
                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                val cancellationTokenSource = CancellationTokenSource()
                
                fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    cancellationTokenSource.token
                ).addOnSuccessListener { location ->
                    if (location != null) {
                        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                            val nearest = routeSearchService.findNearestStop(
                                location.latitude,
                                location.longitude
                            )
                            if (nearest != null && selectedFromStop == null) {
                                selectedFromStop = nearest
                            }
                        }
                    }
                }
            } catch (e: SecurityException) {
                // Permission not granted
            }
        }
    }
    
    // Load graph on startup and find nearest stop
    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            routeSearchService.refreshGraph()
        }
        
        // Check location permission and find nearest stop
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        if (hasPermission) {
            try {
                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                val cancellationTokenSource = CancellationTokenSource()
                
                fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    cancellationTokenSource.token
                ).addOnSuccessListener { location ->
                    if (location != null) {
                        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                            val nearest = routeSearchService.findNearestStop(
                                location.latitude,
                                location.longitude
                            )
                            if (nearest != null && selectedFromStop == null) {
                                selectedFromStop = nearest
                            }
                        }
                    }
                }
            } catch (e: SecurityException) {
                // Request permission
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
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
    
    // Search stops when query changes
    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotEmpty()) {
            kotlinx.coroutines.withContext(Dispatchers.IO) {
                val results = routeSearchService.searchStops(searchQuery)
                searchResults = results
            }
        } else {
            searchResults = emptyList()
        }
    }

    Box(modifier = modifier) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                NavigationBar(
                    windowInsets = NavigationBarDefaults.windowInsets,
                    containerColor = Color.Black
                ) {
                    Destination.entries.forEachIndexed { index, destination ->
                        NavigationBarItem(
                            selected = selectedDestination == index,
                            onClick = {
                                // Special case for "Lines" button - open sheet instead of navigating
                                if (destination == Destination.LIGNES) {
                                    showLinesSheet = true
                                    // Don't change selectedDestination to stay on Plan
                                } else if (selectedDestination != index) {
                                    navController.navigate(destination.route) {
                                        launchSingleTop = true
                                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                                        restoreState = true
                                    }
                                    selectedDestination = index
                                    showLinesSheet = false // Fermer la sheet si ouverte
                                }
                            },
                            icon = {
                                Icon(
                                    destination.icon,
                                    contentDescription = destination.contentDescription
                                )
                            },
                            label = { Text(destination.label) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = Red500,
                                selectedIconColor = Color.White,
                                unselectedIconColor = Color.White,
                                selectedTextColor = Color.White,
                                unselectedTextColor = Color.White
                            )
                        )
                    }
                }
            }
        ) { contentPadding ->
            // Don't apply padding for the Plan screen (full screen)
            val shouldApplyPadding = selectedDestination != Destination.PLAN.ordinal
            AppNavHost(
                navController = navController, 
                startDestination = startDestination,
                contentPadding = contentPadding,
                isBottomSheetOpen = isBottomSheetOpen,
                showLinesSheet = showLinesSheet,
                onBottomSheetStateChanged = { isOpen -> isBottomSheetOpen = isOpen },
                onLinesSheetDismiss = { showLinesSheet = false },
                modifier = if (shouldApplyPadding) Modifier.padding(contentPadding) else Modifier
            )
        }
        
        // Search bar on top of everything only on the Plan screen and when the bottom sheet AND lines sheet are closed
        if (selectedDestination == Destination.PLAN.ordinal && !isBottomSheetOpen && !showLinesSheet && !showRouteResults) {
            SimpleSearchBar(
                searchResults = searchResults,
                onQueryChange = { query ->
                    searchQuery = query
                },
                onSearch = { result ->
                    if (selectedFromStop == null || searchQuery.contains(selectedFromStop!!.stopName, ignoreCase = true)) {
                        // First selection or changing departure stop
                        selectedFromStop = result
                        selectedToStop = null
                        searchQuery = ""
                    } else {
                        // Second selection: destination stop
                        selectedToStop = result
                        searchQuery = ""
                        // Calculate route automatically
                        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                            val route = routeSearchService.findRoute(
                                selectedFromStop!!.nodeIndex,
                                result.nodeIndex
                            )
                            routeResult = route
                            if (route != null) {
                                showRouteResults = true
                            }
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
            )
        }
        
        // Route results sheet
        if (showRouteResults && selectedFromStop != null && selectedToStop != null && routeResult != null) {
            RouteResultsSheet(
                route = routeResult!!,
                fromStopName = selectedFromStop!!.stopName,
                toStopName = selectedToStop!!.stopName,
                onDismiss = {
                    showRouteResults = false
                    // Reset for new search
                    selectedFromStop = null
                    selectedToStop = null
                    routeResult = null
                },
                onLineClick = { lineName, fromStop ->
                    // Navigate to line details
                    // TODO: Implement navigation to line details
                    showRouteResults = false
                },
                getAvailableDirections = { lineName, fromStop ->
                    // Get available directions for this line and stop
                    kotlinx.coroutines.runBlocking {
                        withContext(Dispatchers.IO) {
                            try {
                                val gtfsLineName = if (lineName.equals("NAV1", ignoreCase = true)) "NAVI1" else lineName
                                val headsigns = schedulesRepository.getHeadsigns(gtfsLineName)
                                
                                // For now, return default directions if no headsigns found
                                if (headsigns.isEmpty()) {
                                    listOf(
                                        AvailableDirection(lineName, "Direction 1", 0),
                                        AvailableDirection(lineName, "Direction 2", 1)
                                    )
                                } else {
                                    headsigns.map { (directionId, directionName) ->
                                        AvailableDirection(lineName, directionName, directionId)
                                    }
                                }
                            } catch (e: Exception) {
                                // Fallback to default directions
                                listOf(
                                    AvailableDirection(lineName, "Direction 1", 0),
                                    AvailableDirection(lineName, "Direction 2", 1)
                                )
                            }
                        }
                    }
                }
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun AppNavHost(
    navController: androidx.navigation.NavHostController,
    startDestination: Destination,
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
    isBottomSheetOpen: Boolean,
    showLinesSheet: Boolean,
    onBottomSheetStateChanged: (Boolean) -> Unit,
    onLinesSheetDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = startDestination.route,
        modifier = modifier
    ) {
        composable(Destination.PLAN.route) {
            PlanScreen(
                contentPadding = contentPadding,
                onSheetStateChanged = onBottomSheetStateChanged,
                showLinesSheet = showLinesSheet,
                onLinesSheetDismiss = onLinesSheetDismiss
            )
        }
        composable(Destination.LIGNES.route) {
            SimpleScreen(title = "Lignes")
        }
        composable(Destination.PARAMETRES.route) {
            SimpleScreen(title = "Paramètres")
        }
    }
}

@Composable
private fun SimpleScreen(title: String) {
    // Placeholder content for each tab. Replace with real screens later.
    Text(text = title)
}
