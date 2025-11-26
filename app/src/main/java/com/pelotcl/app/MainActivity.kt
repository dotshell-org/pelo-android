package com.pelotcl.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pelotcl.app.ui.components.SimpleSearchBar
import com.pelotcl.app.ui.components.StationSearchResult
import com.pelotcl.app.ui.screens.PlanScreen
import com.pelotcl.app.ui.theme.PeloTheme
import com.pelotcl.app.ui.theme.Red500
import com.pelotcl.app.ui.viewmodel.TransportViewModel
import kotlinx.coroutines.Dispatchers
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
        contentDescription = "Plan Tab"
    ),
    LIGNES(
        route = "lines",
        label = "Lines",
        icon = Icons.Filled.Route,
        contentDescription = "Lines Tab"
    ),
    PARAMETRES(
        route = "settings",
        label = "Settings",
        icon = Icons.Filled.Settings,
        contentDescription = "Settings Tab"
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

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

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
        }
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                val results = viewModel.searchStops(searchQuery)
                stationSearchResults = results
            }
        } else {
            stationSearchResults = emptyList()
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
                                if (destination == Destination.LIGNES) {
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
                isBottomSheetOpen = isBottomSheetOpen,
                showLinesSheet = showLinesSheet,
                onBottomSheetStateChanged = { isOpen -> isBottomSheetOpen = isOpen },
                onLinesSheetDismiss = {
                    showLinesSheet = false
                },
                searchSelectedStop = selectedStationFromSearch,
                onSearchSelectionHandled = { selectedStationFromSearch = null },
                modifier = if (shouldApplyPadding) Modifier.padding(contentPadding) else Modifier,
                viewModel = viewModel
            )
        }

        if (selectedDestination == Destination.PLAN.ordinal && !isBottomSheetOpen && !showLinesSheet) {
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
    isBottomSheetOpen: Boolean,
    showLinesSheet: Boolean,
    onBottomSheetStateChanged: (Boolean) -> Unit,
    onLinesSheetDismiss: () -> Unit,
    searchSelectedStop: StationSearchResult?,
    onSearchSelectionHandled: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TransportViewModel
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
                onLinesSheetDismiss = onLinesSheetDismiss,
                searchSelectedStop = searchSelectedStop,
                onSearchSelectionHandled = onSearchSelectionHandled,
                viewModel = viewModel
            )
        }
        composable(Destination.LIGNES.route) {
            SimpleScreen(title = "Lines")
        }
        composable(Destination.PARAMETRES.route) {
            SimpleScreen(title = "Settings")
        }
    }
}

@Composable
private fun SimpleScreen(title: String) {
    Text(text = title)
}