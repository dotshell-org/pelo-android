package com.pelotcl.app.ui.screens

import android.os.Build
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.pelotcl.app.ui.viewmodel.TransportViewModel
import com.pelotcl.app.utils.BusIconHelper
import com.pelotcl.app.utils.LineColorHelper
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
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
        raptorRepository.initialize()
        
        // Set arrival stop from the destination
        if (destinationStopName.isNotBlank()) {
            val arrivalResults = raptorRepository.searchStopsByName(destinationStopName)
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
            if (closestStop != null) {
                // Get all stop IDs with the same name
                val allStopsWithName = raptorRepository.searchStopsByName(closestStop.name)
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
            
            scope.launch {
                try {
                    val results = raptorRepository.getOptimizedPaths(
                        originStopIds = departureStop!!.stopIds,
                        destinationStopIds = arrivalStop!!.stopIds
                    )
                    journeys = results
                    if (results.isEmpty()) {
                        errorMessage = "Aucun itinéraire trouvé"
                    }
                } catch (e: Exception) {
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
                title = { Text("Itinéraire", color = Color.White) },
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
                .padding(contentPadding)
        ) {
            // Stop selection card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    // Departure stop field
                    StopSelectionField(
                        label = "Départ",
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
                        icon = Icons.Default.MyLocation,
                        iconTint = Color.Black
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
                                tint = Color.Black
                            )
                        }
                    }
                    
                    // Arrival stop field
                    StopSelectionField(
                        label = "Arrivée",
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
                        iconTint = Color.Black
                    )
                }
            }
            
            // Results section
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color.Black)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Calcul de l'itinéraire...", color = Gray700)
                        }
                    }
                }
                errorMessage != null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = errorMessage!!,
                            color = Gray700,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                journeys.isNotEmpty() -> {
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            bottom = 16.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp) // Gère l'espacement automatiquement
                    ) {
                        item {
                            Text(
                                text = "Itinéraires proposés",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }

                        items(journeys.size) { index ->
                            JourneyCard(journey = journeys[index])
                        }
                    }
                }
                departureStop != null && arrivalStop != null -> {
                    // Both stops selected but no results yet
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Sélectionnez un arrêt de départ et d'arrivée",
                            color = Gray700,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StopSelectionField(
    label: String,
    selectedStop: SelectedStop?,
    query: String,
    onQueryChange: (String) -> Unit,
    isSearching: Boolean,
    searchResults: List<StationSearchResult>,
    onStopSelected: (StationSearchResult) -> Unit,
    onClear: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color
) {
    Column {
        OutlinedTextField(
            value = if (selectedStop != null && !isSearching) selectedStop.name else query,
            onValueChange = onQueryChange,
            label = { Text(label) },
            leadingIcon = {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint
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
                focusedBorderColor = Color.Black,
                focusedLabelColor = Color.Black,
                cursorColor = Color.Black
            )
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
                                    overflow = TextOverflow.Ellipsis
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
                        HorizontalDivider(color = Gray200)
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
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
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = " → ",
                        style = MaterialTheme.typography.titleLarge,
                        color = Gray700
                    )
                    Text(
                        text = journey.formatArrivalTime(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.1f))
                ) {
                    Text(
                        text = "${journey.durationMinutes} min",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = Color.Black,
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
}

@Composable
private fun JourneyLegItem(
    leg: JourneyLeg,
    isFirst: Boolean,
    isLast: Boolean
) {
    val context = LocalContext.current
    val lineColor = if (leg.isWalking) {
        Gray700
    } else {
        Color(LineColorHelper.getColorForLineString(leg.routeName ?: ""))
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Timeline
        Box(
            modifier = Modifier
                .width(40.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top connector line
                if (!isFirst) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(8.dp)
                            .background(lineColor)
                    )
                }
                
                // Circle or icon
                if (leg.isWalking) {
                    Icon(
                        imageVector = Icons.Default.DirectionsWalk,
                        contentDescription = "Marche",
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
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(lineColor),
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
                
                // Bottom connector line
                if (!isLast) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .weight(1f)
                            .background(lineColor)
                    )
                }
            }
        }
        
        // Leg details
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp)
        ) {
            Text(
                text = leg.fromStopName,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium
            )
            
            Text(
                text = leg.formatDepartureTime(),
                color = Gray700,
                style = MaterialTheme.typography.bodySmall
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            if (leg.isWalking) {
                Text(
                    text = "Marche ${leg.durationMinutes} min",
                    color = Gray700,
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                val directionText = leg.direction ?: leg.toStopName
                Text(
                    text = "Direction $directionText",
                    color = Gray700,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            if (isLast) {
                Text(
                    text = leg.toStopName,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = leg.formatArrivalTime(),
                    color = Gray700,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
