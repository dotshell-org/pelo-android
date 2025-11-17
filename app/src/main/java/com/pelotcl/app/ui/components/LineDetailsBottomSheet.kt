package com.pelotcl.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pelotcl.app.data.gtfs.LineStopInfo
import com.pelotcl.app.ui.theme.Gray700
import com.pelotcl.app.ui.viewmodel.TransportViewModel
import com.pelotcl.app.ui.viewmodel.TransportLinesUiState
import com.pelotcl.app.utils.BusIconHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Line information for display in the bottom sheet
 */
data class LineInfo(
    val lineName: String,
    val currentStationName: String
)

/**
 * Returns a line's color based on its name and type
 */
private fun getLineColor(lineName: String): Color {
    return when (lineName.uppercase()) {
        // Metros
        "A" -> Color(0xFFEC4899) // Pink
        "B" -> Color(0xFF3B82F6) // Blue
        "C" -> Color(0xFFF59E0B) // Orange
        "D" -> Color(0xFF22C55E) // Green
        // Funiculars
        "F1", "F2" -> Color(0xFF84CC16) // Lime green
        // Navigone
        "NAV1" -> Color(0xFF14b8a6) // Teal for water shuttle
        // Trams (starts with T)
        else -> when {
            lineName.uppercase().startsWith("NAV") -> Color(0xFF14b8a6) // Teal for navigone
            lineName.uppercase().startsWith("T") -> Color(0xFFA855F7) // Purple
            else -> Color(0xFFEF4444) // Red (Bus)
        }
    }
}

/**
 * Bottom sheet displaying transport line details:
 * - List of stops in order
 * - Next departure times at current stop
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LineDetailsBottomSheet(
    viewModel: TransportViewModel,
    lineInfo: LineInfo?,
    sheetState: SheetState?,
    onDismiss: () -> Unit,
    onBackToStation: () -> Unit,
    onStopClick: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var lineStops by remember { mutableStateOf<List<LineStopInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    
    // Observe lines state to wait for line to be loaded
    val linesState by viewModel.uiState.collectAsState()
    
    // Extract only the line names to avoid infinite recomposition
    val loadedLineNames = remember(linesState) {
        when (linesState) {
            is TransportLinesUiState.Success -> {
                (linesState as TransportLinesUiState.Success).lines.map { 
                    it.properties.ligne.uppercase() 
                }.toSet()
            }
            else -> emptySet()
        }
    }

    // Load data when lineInfo changes AND line is in state
    LaunchedEffect(lineInfo?.lineName, loadedLineNames) {
        if (lineInfo != null) {
            isLoading = true

            // Wait for line to be loaded in state
            val lineLoaded = lineInfo.lineName.uppercase() in loadedLineNames
            
            android.util.Log.d("LineDetailsBottomSheet", "Line ${lineInfo.lineName} loaded: $lineLoaded")

            if (lineLoaded) {
                // Get stops via API (already in cache with transfers)
                withContext(Dispatchers.IO) {
                    try {
                        // Use new ViewModel method which retrieves stops from API
                        // Convert empty string to null to avoid false positive matches
                        val stops = viewModel.getStopsForLine(
                            lineName = lineInfo.lineName,
                            currentStopName = lineInfo.currentStationName.takeIf { it.isNotBlank() }
                        )
                        
                        android.util.Log.d("LineDetailsBottomSheet", "Loaded ${stops.size} stops for line ${lineInfo.lineName} from API")
                        
                        // If 0 stops found, likely a cache problem
                        // Reload stops cache without affecting map
                        if (stops.isEmpty()) {
                            android.util.Log.w("LineDetailsBottomSheet", "0 stops found for line ${lineInfo.lineName}, forcing cache reload...")
                            viewModel.reloadStopsCache() // Recharger uniquement le cache
                            kotlinx.coroutines.delay(500) // Wait for cache to be updated
                            
                            // Retry
                            val stopsRetry = viewModel.getStopsForLine(
                                lineName = lineInfo.lineName,
                                currentStopName = lineInfo.currentStationName.takeIf { it.isNotBlank() }
                            )
                            android.util.Log.d("LineDetailsBottomSheet", "Retry: Loaded ${stopsRetry.size} stops for line ${lineInfo.lineName}")
                            lineStops = stopsRetry
                        } else {
                            lineStops = stops
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("LineDetailsBottomSheet", "Error loading stops: ${e.message}")
                        e.printStackTrace()
                    }
                }
            }

            isLoading = false
        }
    }

    if (lineInfo != null) {
        val content = @Composable {
            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                // Compact header with back arrow, line icon (small) and station name
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Bouton retour
                    IconButton(onClick = onBackToStation) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour à la station",
                            tint = Gray700
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Line icon (small)
                    val drawableName = BusIconHelper.getDrawableNameForLineName(lineInfo.lineName)
                    val resourceId = context.resources.getIdentifier(drawableName, "drawable", context.packageName)

                    if (resourceId != 0) {
                        Image(
                            painter = painterResource(id = resourceId),
                            contentDescription = "Ligne ${lineInfo.lineName}",
                            modifier = Modifier.size(50.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(getLineColor(lineInfo.lineName)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = lineInfo.lineName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Nom de la station
                    Text(
                        text = lineInfo.currentStationName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp)
                            .padding(top = 10.dp, bottom = 40.dp)
                    ) {
                        Spacer(modifier = Modifier.height(24.dp))

                        if (lineStops.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        } else {
                            // Obtenir la couleur de la ligne
                            val lineColor = getLineColor(lineInfo.lineName)

                            // Display all stops with vertical line
                            lineStops.forEachIndexed { index, stop ->
                                StopItemWithLine(
                                    stop = stop,
                                    lineColor = lineColor,
                                    isFirst = index == 0,
                                    isLast = index == lineStops.size - 1,
                                    onStopClick = { onStopClick(stop.stopName) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // If sheetState is provided, wrap in ModalBottomSheet, otherwise show content directly
        if (sheetState != null) {
            ModalBottomSheet(
                onDismissRequest = onDismiss,
                sheetState = sheetState,
                containerColor = Color.White
            ) {
                content()
            }
        } else {
            content()
        }
    }
}

@Composable
private fun StopItemWithLine(
    stop: LineStopInfo,
    lineColor: Color,
    isFirst: Boolean,
    isLast: Boolean,
    onStopClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .height(IntrinsicSize.Min)
            .clickable { onStopClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(40.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            if (!isFirst) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(30.dp)
                        .offset(y = (-16).dp)
                        .background(lineColor)
                        .align(Alignment.Center)
                )
            }

            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(30.dp)
                        .offset(y = (16).dp)
                        .background(lineColor)
                        .align(Alignment.Center)
                )
            }

            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(if (stop.isCurrentStop) lineColor else Color.White) // Full if current stop, hollow otherwise
                    .border(
                        width = if (stop.isCurrentStop) 0.dp else 3.dp, // No border if full
                        color = lineColor,
                        shape = CircleShape
                    )
            )
        }

        // Right part: text + transfers
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {

            Text(
                text = stop.stopName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (stop.isCurrentStop) FontWeight.Bold else FontWeight.Normal,
                color = if (stop.isCurrentStop) lineColor else Color.Black,
                modifier = Modifier.weight(1f, fill = false)
            )

            if (stop.connections.isNotEmpty()) {
                Spacer(modifier = Modifier.width(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    stop.connections.forEach { connectionLine ->
                        ConnectionBadge(lineName = connectionLine)
                    }
                }
            }
        }
    }
}

/**
 * Badge displaying a transfer line (metro or funicular)
 * Uses TCL images like on the map
 */
@Composable
private fun ConnectionBadge(lineName: String) {
    val context = LocalContext.current

    // Convert line name to drawable name using BusIconHelper for consistency
    val drawableName = BusIconHelper.getDrawableNameForLineName(lineName)
    val resourceId = context.resources.getIdentifier(drawableName, "drawable", context.packageName)

    if (resourceId != 0) {
        // Display TCL image
        Image(
            painter = painterResource(id = resourceId),
            contentDescription = "Ligne $lineName",
            modifier = Modifier.size(40.dp)
        )
    } else {
        // Fallback: colored circle if image doesn't exist
        val backgroundColor = when (lineName) {
            "A" -> Color(0xFFEC4899) // Pink
            "B" -> Color(0xFF3B82F6) // Blue
            "C" -> Color(0xFFF59E0B) // Orange
            "D" -> Color(0xFF22C55E) // Green
            "F1", "F2" -> Color(0xFF84CC16) // Lime green
            else -> Color.Gray
        }

        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(backgroundColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = lineName,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = if (lineName.length > 1) 9.sp else 11.sp
            )
        }
    }
}