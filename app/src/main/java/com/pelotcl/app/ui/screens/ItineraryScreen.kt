package com.pelotcl.app.ui.screens

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pelotcl.app.data.repository.JourneyLeg
import com.pelotcl.app.data.repository.JourneyResult
import com.pelotcl.app.data.repository.RaptorRepository
import com.pelotcl.app.data.repository.RaptorStop
import com.pelotcl.app.ui.components.StationSearchResult
import com.pelotcl.app.ui.theme.Gray200
import com.pelotcl.app.ui.theme.Gray700
import com.pelotcl.app.ui.theme.Red500
import com.pelotcl.app.ui.viewmodel.TransportViewModel
import com.pelotcl.app.utils.BusIconHelper
import com.pelotcl.app.utils.LineColorHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.geometry.LatLng
import android.util.Log

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
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    
    // Change status bar to light content (white icons) for dark background
    SideEffect {
        (view.context as? ComponentActivity)?.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.dark(
                android.graphics.Color.TRANSPARENT
            )
        )
    }
    
    val raptorRepository = remember { RaptorRepository(context) }
    
    // Stop selection states
    var departureStop by remember { mutableStateOf<SelectedStop?>(null) }
    var arrivalStop by remember { mutableStateOf<SelectedStop?>(null) }
    
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
    
    // Initialize raptor and set default stops
    LaunchedEffect(Unit) {
        Log.d("ItineraryScreen", "Initializing Raptor repository...")
        raptorRepository.initialize()
        Log.d("ItineraryScreen", "Raptor initialized. Destination: '$destinationStopName', UserLocation: $userLocation")

        // Set arrival stop from the destination
        if (destinationStopName.isNotBlank()) {
            val arrivalResults = raptorRepository.searchStopsByName(destinationStopName)
            Log.d("ItineraryScreen", "Arrival search for '$destinationStopName': found ${arrivalResults.size} stops - ${arrivalResults.map { "${it.name}(${it.id})" }}")
            if (arrivalResults.isNotEmpty()) {
                arrivalStop = SelectedStop(
                    name = destinationStopName,
                    stopIds = arrivalResults.map { it.id }
                )
            }
        }
        
        // Find closest stop for departure based on GPS
        if (userLocation != null) {
            val closestStop = raptorRepository.findClosestStop(
                latitude = userLocation.latitude,
                longitude = userLocation.longitude
            )
            Log.d("ItineraryScreen", "Closest stop to $userLocation: ${closestStop?.name} (id=${closestStop?.id})")
            if (closestStop != null) {
                // Get all stop IDs with the same name
                val allStopsWithName = raptorRepository.searchStopsByName(closestStop.name)
                Log.d("ItineraryScreen", "Departure search for '${closestStop.name}': found ${allStopsWithName.size} stops - ${allStopsWithName.map { "${it.name}(${it.id})" }}")
                departureStop = SelectedStop(
                    name = closestStop.name,
                    stopIds = allStopsWithName.map { it.id }
                )
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
    
    // Calculate journey when both stops are selected
    LaunchedEffect(departureStop, arrivalStop) {
        if (departureStop != null && arrivalStop != null) {
            isLoading = true
            errorMessage = null
            journeys = emptyList()
            
            Log.d("ItineraryScreen", "Calculating journey from '${departureStop!!.name}' (ids=${departureStop!!.stopIds}) to '${arrivalStop!!.name}' (ids=${arrivalStop!!.stopIds})")

            scope.launch {
                try {
                    // Try with current time first
                    var results = raptorRepository.getOptimizedPaths(
                        originStopIds = departureStop!!.stopIds,
                        destinationStopIds = arrivalStop!!.stopIds
                    )

                    // If no results with current time, try with 9:00 AM as fallback test
                    if (results.isEmpty()) {
                        Log.d("ItineraryScreen", "No results with current time, trying with 9:00 AM...")
                        results = raptorRepository.getOptimizedPaths(
                            originStopIds = departureStop!!.stopIds,
                            destinationStopIds = arrivalStop!!.stopIds,
                            departureTimeSeconds = 9 * 3600 // 9:00 AM
                        )
                    }

                    Log.d("ItineraryScreen", "Journey results: ${results.size} journeys found")
                    journeys = results
                    if (results.isEmpty()) {
                        errorMessage = "Aucun itinéraire trouvé"
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Itinéraire", fontWeight = FontWeight.Normal, color = Color.White) },
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
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
        ) {
            // Stop selection fields directly on black background
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
                            }
                        },
                        onClear = {
                            departureStop = null
                            departureQuery = ""
                            isSearchingDeparture = false
                        },
                        icon = Icons.Default.MyLocation
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
                        icon = Icons.Default.Search
                    )
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            // Results section
            when {
                isLoading -> {
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
                errorMessage != null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = errorMessage!!,
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                journeys.isNotEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        journeys.forEachIndexed { index, journey ->
                            JourneyCard(journey = journey)
                            if (index < journeys.size - 1) {
                                HorizontalDivider(
                                    color = Color.White.copy(alpha = 0.2f),
                                    modifier = Modifier.padding(vertical = 32.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
                departureStop != null && arrivalStop != null -> {
                    // Both stops selected but no results yet
                }
                else -> {
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

            Spacer(modifier = Modifier.height(80.dp))
        }
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
    icon: androidx.compose.ui.graphics.vector.ImageVector
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
                if (selectedStop != null || query.isNotEmpty()) {
                    IconButton(onClick = onClear) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Effacer",
                            tint = Gray700
                        )
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
                            modifier = Modifier.clickable { onStopSelected(result) },
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
private fun JourneyCard(journey: JourneyResult) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
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
                    text = if (journey.durationMinutes < 60) "${journey.durationMinutes} min" else "${journey.durationMinutes / 60}h${(journey.durationMinutes % 60).toString().padStart(2, '0')}min",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
            
        Spacer(modifier = Modifier.height(16.dp))
        
        // Journey legs
        journey.legs.forEachIndexed { index, leg ->
            JourneyLegItem(
                leg = leg,
                isFirst = index == 0,
                isLast = index == journey.legs.size - 1
            )
        }
    }
}

@Composable
private fun JourneyLegItem(
    leg: JourneyLeg,
    isFirst: Boolean,
    isLast: Boolean
) {
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
                        imageVector = Icons.Default.DirectionsWalk,
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
