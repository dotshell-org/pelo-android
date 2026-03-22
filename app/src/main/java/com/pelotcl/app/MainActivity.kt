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
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.location.LocationServices
import com.pelotcl.app.core.ui.components.favorites.AddFavoriteDialog
import com.pelotcl.app.core.ui.components.favorites.FavoritesBar
import com.pelotcl.app.core.ui.components.TransportSearchBar
import com.pelotcl.app.core.ui.components.TransportSearchContent
import com.pelotcl.app.core.ui.components.StationSearchResult
import com.pelotcl.app.core.data.repository.offline.SearchHistoryItem
import com.pelotcl.app.core.data.repository.offline.SearchType
import com.pelotcl.app.core.data.repository.offline.MapStyle
import com.pelotcl.app.core.ui.screens.settings.about.AboutScreen
import com.pelotcl.app.core.ui.screens.settings.about.ContactScreen
import com.pelotcl.app.core.ui.screens.settings.about.CreditsScreen
import com.pelotcl.app.core.ui.screens.settings.about.LegalScreen
import com.pelotcl.app.core.ui.screens.plan.PlanScreen
import com.pelotcl.app.core.ui.screens.settings.ItinerarySettingsScreen
import com.pelotcl.app.core.ui.screens.settings.OfflineSettingsScreen
import com.pelotcl.app.core.ui.screens.settings.SettingsScreen
import com.pelotcl.app.core.ui.theme.PeloTheme
import com.pelotcl.app.core.ui.theme.Red500
import com.pelotcl.app.core.ui.viewmodel.TransportViewModel
import com.pelotcl.app.core.ui.viewmodel.TransportStopsUiState
import com.pelotcl.app.core.data.network.RetrofitInstance
import com.pelotcl.app.core.data.cache.TransportCache
import com.pelotcl.app.core.data.repository.offline.SchedulesRepository
import com.pelotcl.app.utils.BusIconHelper
import com.pelotcl.app.utils.LocationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import org.maplibre.android.geometry.LatLng

class MainActivity : ComponentActivity() {

    // Application-level coroutine scope for early background work
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Preload disk cache and binary schedule data in parallel BEFORE UI (critical for first render)
        // Retrofit initialization is deferred to after setContent
        appScope.launch {
            try {
                // Parallel cache and schedule-data warmup - these are needed for initial UI
                val cacheJob = launch {
                    val cache = TransportCache(applicationContext)
                    cache.preloadFromDisk()
                }
                val schedulesJob = launch {
                    val schedulesRepo =
                        SchedulesRepository.getInstance(applicationContext)
                    schedulesRepo.warmupDatabase()
                }
                // Wait for cache and schedule data warmup (critical for UI)
                cacheJob.join()
                schedulesJob.join()
            } catch (e: Exception) {
                android.util.Log.w("MainActivity", "Startup preload failed: ${e.message}")
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

        // Deferred initialization - run AFTER setContent to not block first frame
        // These are not needed for initial UI display
        appScope.launch {
            // Pre-populate all drawable resource IDs in one reflection pass
            // Avoids ~960 individual getIdentifier() calls during first map render
            BusIconHelper.preloadResourceIds(applicationContext)

            // Initialize transport services with Lyon TCL configuration
            com.pelotcl.app.core.service.TransportServiceProvider.initialize(applicationContext)
            
            // Initialize HTTP cache for network requests (not needed for cached data display)
            RetrofitInstance.initialize(applicationContext, com.pelotcl.app.core.service.TransportServiceProvider.getTransportConfig())

            // Preload Raptor library in background (only needed for itinerary calculations)
            // yield() gives the UI thread priority without an arbitrary delay
            kotlinx.coroutines.yield()
            try {
                val raptorRepo =
                    com.pelotcl.app.core.data.repository.itinerary.RaptorRepository.getInstance(
                        applicationContext
                    )
                raptorRepo.initialize()
                raptorRepo.preloadJourneyCache()
            } catch (e: Exception) {
                android.util.Log.w("MainActivity", "Raptor preload failed: ${e.message}")
            }

            // Refresh home screen widgets with fresh schedule data
            delay(3000)
            try {
                val widget = com.pelotcl.app.core.widget.PeloWidget()
                val manager = androidx.glance.appwidget.GlanceAppWidgetManager(applicationContext)
                val glanceIds = manager.getGlanceIds(widget.javaClass)
                for (glanceId in glanceIds) {
                    widget.update(applicationContext, glanceId)
                }
            } catch (e: Exception) {
                android.util.Log.w("MainActivity", "Widget refresh failed: ${e.message}")
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
        const val ABOUT = "about"
        const val LEGAL = "legal"
        const val CREDITS = "credits"
        const val CONTACT = "contact"
        const val ITINERARY_SETTINGS = "itinerary_settings"
        const val OFFLINE_SETTINGS = "offline_settings"
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavBar(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val startDestination = Destination.PLAN
    var selectedDestination by rememberSaveable { mutableIntStateOf(startDestination.ordinal) }
    var isBottomSheetOpen by remember { mutableStateOf(false) }
    var showLinesSheet by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val application = remember(context) { context.applicationContext as android.app.Application }

    // Memoize the factory to avoid recreation on recomposition
    val viewModelFactory = remember(application) {
        object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return TransportViewModel(application) as T
            }
        }
    }
    val viewModel: TransportViewModel = viewModel(factory = viewModelFactory)

    var currentMapStyle by remember { mutableStateOf(MapStyle.POSITRON) }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var stopOptionsSelectedStop by remember { mutableStateOf<StationSearchResult?>(null) }
    val favoriteStops by viewModel.favoriteStops.collectAsState(initial = emptySet())
    var favoriteStopItems by remember { mutableStateOf<List<SearchHistoryItem>>(emptyList()) }
    val stopsUiState by viewModel.stopsUiState.collectAsState(initial = TransportStopsUiState.Loading)
    val userFavorites by viewModel.userFavorites.collectAsState(initial = emptyList())
    var showAddFavoriteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(favoriteStops, stopsUiState) {
        val stops = (stopsUiState as? TransportStopsUiState.Success)?.stops
        favoriteStopItems = favoriteStops.map { stopName ->
            val stop = stops?.find { (it as com.pelotcl.app.core.data.model.StopFeature).properties.nom.equals(stopName, ignoreCase = true) }
            val lines = stop?.let { BusIconHelper.getAllLinesForStop(it) } ?: emptyList()
            SearchHistoryItem(
                query = stopName,
                type = SearchType.STOP,
                lines = lines
            )
        }
    }

    // User location for itinerary (continuously updated)
    var userLocation by remember { mutableStateOf<LatLng?>(null) }

    // Fused location client for continuous updates
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    // Itinerary destination stop
    var itineraryDestinationStop by remember { mutableStateOf<String?>(null) }
    var isItineraryModeActive by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.any { it.value }
        if (granted) {
            // Get last known location immediately for instant map centering
            scope.launch {
                val lastKnown = LocationHelper.getLastKnownLocation(fusedLocationClient)
                if (lastKnown != null) {
                    userLocation = lastKnown
                }
            }
            // Start continuous location updates when permission granted
            LocationHelper.startLocationUpdates(fusedLocationClient) { location ->
                userLocation = location
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
            // PRIORITY: Get last known location immediately for instant map centering
            // This is cached by the system and returns almost instantly
            val lastKnown = LocationHelper.getLastKnownLocation(fusedLocationClient)
            if (lastKnown != null) {
                userLocation = lastKnown
            }
            // Then start continuous location updates for real-time tracking
            LocationHelper.startLocationUpdates(fusedLocationClient) { location ->
                userLocation = location
            }
        }
    }

    // Stop location updates when composable is disposed
    DisposableEffect(Unit) {
        onDispose {
            LocationHelper.stopLocationUpdates(fusedLocationClient)
        }
    }

    // Observer la route courante pour gérer la barre de statut
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Gérer la barre de statut selon l'écran actif
    // Les écrans Settings et ses sous-écrans (About, Legal, Credits, Contact) ont un fond noir
    DisposableEffect(currentRoute) {
        val activity = context as? ComponentActivity
        val darkBackgroundRoutes = listOf(
            Destination.PARAMETRES.route,
            Destination.ABOUT,
            Destination.LEGAL,
            Destination.CREDITS,
            Destination.CONTACT,
            Destination.ITINERARY_SETTINGS,
            Destination.OFFLINE_SETTINGS
        )

        if (currentRoute in darkBackgroundRoutes) {
            // Barre de statut avec icônes blanches pour fond noir (fond transparent)
            activity?.enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.dark(
                    android.graphics.Color.TRANSPARENT
                ),
                navigationBarStyle = SystemBarStyle.dark(
                    android.graphics.Color.TRANSPARENT
                )
            )
        } else {
            // Barre de statut normale pour les autres écrans (fond clair)
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
                                if (destination == Destination.LIGNES) {
                                    // Close itinerary when going to Lines
                                    itineraryDestinationStop = null
                                    // Si on n'est pas sur Plan, naviguer vers Plan d'abord
                                    if (selectedDestination != Destination.PLAN.ordinal) {
                                        selectedDestination = Destination.PLAN.ordinal
                                    }
                                    showLinesSheet = true
                                } else if (destination == Destination.PARAMETRES) {
                                    // Don't close itinerary when going to Settings (preserve state)
                                    // If already on Settings tab, check if we're in a sub-page
                                    val settingsSubRoutes = listOf(
                                        Destination.ABOUT,
                                        Destination.LEGAL,
                                        Destination.CREDITS,
                                        Destination.CONTACT,
                                        Destination.ITINERARY_SETTINGS,
                                        Destination.OFFLINE_SETTINGS
                                    )
                                    if (currentRoute in settingsSubRoutes) {
                                        // Pop back to Settings root
                                        navController.popBackStack(
                                            Destination.PARAMETRES.route,
                                            false
                                        )
                                    } else if (selectedDestination != index) {
                                        selectedDestination = index
                                        showLinesSheet = false
                                    }
                                } else if (selectedDestination != index) {
                                    // PLAN destination - just go back, don't close itinerary (preserve state)
                                    selectedDestination = index
                                    showLinesSheet = false
                                } else if (destination == Destination.PLAN) {
                                    // Already on Plan tab - close inline itinerary entry point if set
                                    if (itineraryDestinationStop != null) {
                                        itineraryDestinationStop = null
                                    }
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
            // Keep PlanScreen always mounted to preserve map state
            // Settings and other screens are displayed on top when active
            Box(modifier = Modifier.fillMaxSize()) {

                // This preserves the map state when navigating to settings and back
                PlanScreen(
                    contentPadding = contentPadding,
                    onSheetStateChanged = { isOpen -> isBottomSheetOpen = isOpen },
                    showLinesSheet = showLinesSheet,
                    onLinesSheetDismiss = {
                        showLinesSheet = false
                    },
                    itinerarySelectedStopName = itineraryDestinationStop,
                    onItinerarySelectionHandled = { itineraryDestinationStop = null },
                    optionsSelectedStop = stopOptionsSelectedStop,
                    onOptionsSelectionHandled = { stopOptionsSelectedStop = null },
                    viewModel = viewModel,
                    initialUserLocation = userLocation,
                    isVisible = selectedDestination == Destination.PLAN.ordinal,
                    onMapStyleChanged = { style ->
                        currentMapStyle = style
                    },
                    isSearchExpanded = isSearchExpanded,
                    onItineraryModeChanged = { active ->
                        isItineraryModeActive = active
                    }
                )

                // Settings screens - displayed on top when on settings tab
                if (selectedDestination == Destination.PARAMETRES.ordinal) {
                    AppNavHost(
                        navController = navController,
                        modifier = Modifier,
                        viewModel = viewModel,
                        onNavigateToPlan = {
                            selectedDestination = Destination.PLAN.ordinal
                        }
                    )
                }
            }

        }

        // UI Overlays - Search Bar at top, favorites row (with create button) below it
        // LIVE button remains in PlanScreen for proper integration with map controls

        // Calculate UI element positions - declared outside if blocks for shared access
        val searchBarHeight = 56.dp // Standard Material 3 search bar height
        val addFavoriteButtonHeight = 40.dp // Favorites row height approximation
        val spacingBetweenElements = 4.dp // Spacing between UI elements

        // Position calculations:
        // Favorites row sits below search bar and contains the create button + favorites chips
        val favoritesBarTopPosition = searchBarHeight + 38.dp

        // Search Bar - keep visible on Plan, including when station/line detail sheets are open.
        if (selectedDestination == Destination.PLAN.ordinal && !showLinesSheet && itineraryDestinationStop == null && !isItineraryModeActive) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
            ) {
                TransportSearchBar(
                    viewModel = viewModel,
                    currentMapStyle = currentMapStyle,
                    content = TransportSearchContent.STOPS_AND_LINES,
                    showHistory = true,
                    onExpandedChange = { expanded -> isSearchExpanded = expanded },
                    onStopPrimary = { stop ->
                        itineraryDestinationStop = stop.stopName
                    },
                    onStopSecondary = { stopResult ->
                        stopOptionsSelectedStop = stopResult
                    },
                    onLineSelected = { line ->
                        viewModel.selectLine(line.lineName)
                    }
                )
            }
        }

        // Favorites row - hidden while a sheet is open to free space for map controls.
        if (selectedDestination == Destination.PLAN.ordinal && !isBottomSheetOpen && !showLinesSheet && itineraryDestinationStop == null && !isSearchExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = favoritesBarTopPosition)
            ) {
                FavoritesBar(
                    favorites = userFavorites,
                    onAddFavoriteClick = { showAddFavoriteDialog = true },
                    onFavoriteClick = { favorite ->
                        // Navigate to the favorite's stop
                        val stopResult = StationSearchResult(
                            stopName = favorite.stopName,
                            lines = emptyList() // Will be loaded from stops data
                        )
                        stopOptionsSelectedStop = stopResult
                    },
                    onRemoveFavoriteClick = { favorite ->
                        viewModel.removeUserFavorite(favorite.id)
                    }
                )
            }
        }

        // Add Favorite Dialog - displayed on top of everything
        if (showAddFavoriteDialog) {
            AddFavoriteDialog(
                onDismiss = { showAddFavoriteDialog = false },
                onFavoriteCreated = { name, iconName, stopName ->
                    viewModel.addUserFavorite(name, iconName, stopName)
                    showAddFavoriteDialog = false
                },
                viewModel = viewModel
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun AppNavHost(
    navController: androidx.navigation.NavHostController,
    modifier: Modifier = Modifier,
    viewModel: TransportViewModel,
    onNavigateToPlan: () -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = Destination.PARAMETRES.route,
        modifier = modifier,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None }
    ) {
        // Settings screens only - PlanScreen is handled outside NavHost
        composable(Destination.PARAMETRES.route) {
            SettingsScreen(
                onBackClick = {
                    // Just switch back to Plan tab - no navigation needed since PlanScreen is always mounted
                    onNavigateToPlan()
                },
                onSystemBack = {
                    onNavigateToPlan()
                },
                onItineraryClick = {
                    navController.navigate(Destination.ITINERARY_SETTINGS)
                },
                onLegalClick = {
                    navController.navigate(Destination.LEGAL)
                },
                onCreditsClick = {
                    navController.navigate(Destination.CREDITS)
                },
                onContactClick = {
                    navController.navigate(Destination.CONTACT)
                },
                onOfflineClick = {
                    navController.navigate(Destination.OFFLINE_SETTINGS)
                }
            )
        }
        composable(Destination.ITINERARY_SETTINGS) {
            ItinerarySettingsScreen(
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
        composable(Destination.OFFLINE_SETTINGS) {
            OfflineSettingsScreen(
                viewModel = viewModel,
                onBackClick = {
                    navController.popBackStack()
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
