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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.pelotcl.app.ui.components.LinesBottomSheet
import com.pelotcl.app.ui.components.SimpleSearchBar
import com.pelotcl.app.ui.components.StationSearchResult
import com.pelotcl.app.ui.screens.AboutScreen
import com.pelotcl.app.ui.screens.ContactScreen
import com.pelotcl.app.ui.screens.CreditsScreen
import com.pelotcl.app.ui.screens.ItineraryScreen
import com.pelotcl.app.ui.screens.LegalScreen
import com.pelotcl.app.ui.screens.PlanScreen
import com.pelotcl.app.ui.screens.SettingsScreen
import com.pelotcl.app.ui.theme.PeloTheme
import com.pelotcl.app.ui.theme.Red500
import com.pelotcl.app.ui.viewmodel.TransportViewModel
import com.pelotcl.app.data.api.RetrofitInstance
import com.pelotcl.app.data.cache.TransportCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import org.maplibre.android.geometry.LatLng

class MainActivity : ComponentActivity() {

    // Application-level coroutine scope for early background work
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize HTTP cache early for network optimization
        RetrofitInstance.initialize(applicationContext)

        // Preload disk cache in background for faster data access
        // This runs before UI is shown, so data is ready when needed
        appScope.launch {
            try {
                val cache = TransportCache.getInstance(applicationContext)
                cache.preloadFromDisk()
            } catch (e: Exception) {
                android.util.Log.w("MainActivity", "Cache preload failed: ${e.message}")
            }
        }

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
        contentDescription = "Plan Tab"
    ),
    LIGNES(
        route = "lines",
        label = "Lignes",
        icon = Icons.Filled.Route,
        contentDescription = "Lines Tab"
    ),
    PARAMETRES(
        route = "settings",
        label = "Paramètres",
        icon = Icons.Filled.Settings,
        contentDescription = "Settings Tab"
    );

    companion object {
        val entries: List<Destination> = values().toList()
        const val ABOUT = "about"
        const val LEGAL = "legal"
        const val CREDITS = "credits"
        const val CONTACT = "contact"
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

    val context = LocalContext.current
    val application = context.applicationContext as android.app.Application

    val viewModel: TransportViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return TransportViewModel(application) as T
            }
        }
    )

    var searchQuery by remember { mutableStateOf("") }
    var stationSearchResults by remember { mutableStateOf<List<StationSearchResult>>(emptyList()) }
    var selectedStationFromSearch by remember { mutableStateOf<StationSearchResult?>(null) }
    
    // User location for itinerary
    var userLocation by remember { mutableStateOf<LatLng?>(null) }
    
    // Itinerary destination stop
    var itineraryDestinationStop by remember { mutableStateOf<String?>(null) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.any { it.value }
        if (granted) {
            // Get location when permission granted
            try {
                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    CancellationTokenSource().token
                ).addOnSuccessListener { location ->
                    if (location != null) {
                        userLocation = LatLng(location.latitude, location.longitude)
                    }
                }
            } catch (e: SecurityException) {
                // Handle exception
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

        if (!hasPermission) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            // Get location if permission already granted
            try {
                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    CancellationTokenSource().token
                ).addOnSuccessListener { location ->
                    if (location != null) {
                        userLocation = LatLng(location.latitude, location.longitude)
                    }
                }
            } catch (e: SecurityException) {
                // Handle exception
            }
        }
    }

    LaunchedEffect(searchQuery) {
        val current = searchQuery.trim()
        if (current.isNotEmpty()) {
            // Debounce to avoid querying on every keystroke
            delay(300)
            if (current == searchQuery.trim()) {
                val results = viewModel.searchStops(current)
                stationSearchResults = results
            }
        } else {
            stationSearchResults = emptyList()
        }
    }

    // Gérer la barre de statut selon l'écran actif
    DisposableEffect(selectedDestination) {
        val activity = context as? ComponentActivity
        if (selectedDestination == Destination.PARAMETRES.ordinal) {
            // Barre de statut avec icônes blanches pour fond noir
            activity?.enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.dark(
                    android.graphics.Color.TRANSPARENT
                ),
                navigationBarStyle = SystemBarStyle.dark(
                    android.graphics.Color.TRANSPARENT
                )
            )
        } else {
            // Barre de statut normale pour les autres écrans
            activity?.enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.light(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT
                ),
                navigationBarStyle = SystemBarStyle.dark(
                    android.graphics.Color.TRANSPARENT
                )
            )
        }
        onDispose { }
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
                                // Close itinerary overlay when navigating
                                itineraryDestinationStop = null
                                
                                if (destination == Destination.LIGNES) {
                                    // Si on n'est pas sur Plan, naviguer vers Plan d'abord
                                    if (selectedDestination != Destination.PLAN.ordinal) {
                                        navController.navigate(Destination.PLAN.route) {
                                            launchSingleTop = true
                                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                                            restoreState = true
                                        }
                                        selectedDestination = Destination.PLAN.ordinal
                                    }
                                    showLinesSheet = true
                                } else if (selectedDestination != index) {
                                    navController.navigate(destination.route) {
                                        launchSingleTop = true
                                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                                        restoreState = true
                                    }
                                    selectedDestination = index
                                    showLinesSheet = false
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
            val shouldApplyPadding = selectedDestination != Destination.PLAN.ordinal
            AppNavHost(
                navController = navController,
                startDestination = startDestination,
                contentPadding = contentPadding,
                showLinesSheet = showLinesSheet,
                onBottomSheetStateChanged = { isOpen -> isBottomSheetOpen = isOpen },
                onLinesSheetDismiss = {
                    showLinesSheet = false
                },
                searchSelectedStop = selectedStationFromSearch,
                onSearchSelectionHandled = { selectedStationFromSearch = null },
                modifier = if (shouldApplyPadding) Modifier.padding(contentPadding) else Modifier,
                viewModel = viewModel,
                userLocation = userLocation,
                onItineraryClick = { stopName ->
                    itineraryDestinationStop = stopName
                }
            )
            
            // Itinerary overlay - displayed on top of everything
            AnimatedVisibility(
                visible = itineraryDestinationStop != null,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                ItineraryScreen(
                    destinationStopName = itineraryDestinationStop ?: "",
                    userLocation = userLocation,
                    viewModel = viewModel,
                    onBack = {
                        itineraryDestinationStop = null
                    }
                )
            }
        }

        if (selectedDestination == Destination.PLAN.ordinal && !isBottomSheetOpen && !showLinesSheet && itineraryDestinationStop == null) {
            SimpleSearchBar(
                searchResults = stationSearchResults,
                onQueryChange = { query ->
                    searchQuery = query
                },
                onSearch = { result ->
                    selectedStationFromSearch = result
                    searchQuery = ""
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
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
    showLinesSheet: Boolean,
    onBottomSheetStateChanged: (Boolean) -> Unit,
    onLinesSheetDismiss: () -> Unit,
    searchSelectedStop: StationSearchResult?,
    onSearchSelectionHandled: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TransportViewModel,
    userLocation: LatLng?,
    onItineraryClick: (String) -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = startDestination.route,
        modifier = modifier,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None }
    ) {
        composable(Destination.PLAN.route) {
            PlanScreen(
                contentPadding = contentPadding,
                onSheetStateChanged = onBottomSheetStateChanged,
                showLinesSheet = showLinesSheet,
                onLinesSheetDismiss = onLinesSheetDismiss,
                searchSelectedStop = searchSelectedStop,
                onSearchSelectionHandled = onSearchSelectionHandled,
                viewModel = viewModel,
                onItineraryClick = onItineraryClick,
                initialUserLocation = userLocation
            )
        }
        composable(Destination.LIGNES.route) {
            val favoriteLines by viewModel.favoriteLines.collectAsState()
            LinesBottomSheet(
                allLines = viewModel.getAllAvailableLines(),
                onDismiss = { /* Nothing to dismiss - full-screen */ },
                onLineClick = { lineName ->
                    // Select line and navigate back to Plan screen where it will open the details
                    viewModel.selectLine(lineName)
                    navController.navigate(Destination.PLAN.route) {
                        launchSingleTop = true
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        restoreState = true
                    }
                },
                favoriteLines = favoriteLines,
                onToggleFavorite = { viewModel.toggleFavorite(it) }
            )
        }
        composable(Destination.PARAMETRES.route) {
            SettingsScreen(
                onAboutClick = {
                    navController.navigate(Destination.ABOUT)
                }
            )
        }
        composable(Destination.ABOUT) {
            AboutScreen(
                onBackClick = {
                    navController.popBackStack()
                },
                onLegalClick = {
                    navController.navigate(Destination.LEGAL)
                },
                onCreditsClick = {
                    navController.navigate(Destination.CREDITS)
                },
                onContactClick = {
                    navController.navigate(Destination.CONTACT)
                }
            )
        }
        composable(Destination.LEGAL) {
            LegalScreen(
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
        composable(Destination.CREDITS) {
            CreditsScreen(
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
        composable(Destination.CONTACT) {
            ContactScreen(
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
    }
}