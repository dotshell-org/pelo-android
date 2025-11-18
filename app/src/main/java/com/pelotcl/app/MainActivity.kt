package com.pelotcl.app

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pelotcl.app.ui.components.SimpleSearchBar
import com.pelotcl.app.ui.screens.PlanScreen
import com.pelotcl.app.ui.theme.PeloTheme
import com.pelotcl.app.ui.theme.Red500

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
        if (selectedDestination == Destination.PLAN.ordinal && !isBottomSheetOpen && !showLinesSheet) {
            SimpleSearchBar(
                searchResults = emptyList(),
                onSearch = {},
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
