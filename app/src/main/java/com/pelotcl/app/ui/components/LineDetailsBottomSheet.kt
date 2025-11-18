package com.pelotcl.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pelotcl.app.data.gtfs.LineStopInfo
import com.pelotcl.app.ui.theme.Gray700
import com.pelotcl.app.ui.theme.Green500
import com.pelotcl.app.ui.theme.Orange500
import com.pelotcl.app.ui.theme.Red500
import com.pelotcl.app.ui.viewmodel.TransportViewModel
import com.pelotcl.app.ui.viewmodel.TransportLinesUiState
import com.pelotcl.app.utils.BusIconHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LineInfo(
    val lineName: String,
    val currentStationName: String
)

private fun getLineColor(lineName: String): Color {
    return when (lineName.uppercase()) {
        "A" -> Color(0xFFEC4899)
        "B" -> Color(0xFF3B82F6)
        "C" -> Color(0xFFF59E0B)
        "D" -> Color(0xFF22C55E)
        "F1", "F2" -> Color(0xFF84CC16)
        "NAV1" -> Color(0xFF14b8a6)
        else -> when {
            lineName.uppercase().startsWith("NAV") -> Color(0xFF14b8a6)
            lineName.uppercase().startsWith("T") -> Color(0xFFA855F7)
            else -> Color(0xFFEF4444)
        }
    }
}

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

    val linesState by viewModel.uiState.collectAsState()

    val loadedLineNames = remember(linesState) {
        when (linesState) {
            is TransportLinesUiState.Success -> (linesState as TransportLinesUiState.Success).lines.map { it.properties.ligne.uppercase() }.toSet()
            else -> emptySet()
        }
    }

    LaunchedEffect(lineInfo?.lineName, lineInfo?.currentStationName, loadedLineNames) {
        if (lineInfo != null) {
            isLoading = true
            val lineLoaded = lineInfo.lineName.uppercase() in loadedLineNames
            if (lineLoaded) {
                withContext(Dispatchers.IO) {
                    try {
                        val stops = viewModel.getStopsForLine(
                            lineName = lineInfo.lineName,
                            currentStopName = lineInfo.currentStationName.takeIf { it.isNotBlank() }
                        )
                        if (stops.isEmpty()) {
                            viewModel.reloadStopsCache()
                            kotlinx.coroutines.delay(500)
                            lineStops = viewModel.getStopsForLine(
                                lineName = lineInfo.lineName,
                                currentStopName = lineInfo.currentStationName.takeIf { it.isNotBlank() }
                            )
                        } else {
                            lineStops = stops
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("LineDetailsBottomSheet", "Error loading stops: ${e.message}", e)
                    }
                }
            }
            isLoading = false
        }
    }

    if (lineInfo != null) {
        val content = @Composable {
            Column(modifier = Modifier.fillMaxSize()) {
                // Fixed Header
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBackToStation) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to station", tint = Gray700)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    val drawableName = BusIconHelper.getDrawableNameForLineName(lineInfo.lineName)
                    val resourceId = context.resources.getIdentifier(drawableName, "drawable", context.packageName)
                    if (resourceId != 0) {
                        Image(painter = painterResource(id = resourceId), contentDescription = "Line ${lineInfo.lineName}", modifier = Modifier.size(50.dp))
                    } else {
                        Box(
                            modifier = Modifier.size(50.dp).clip(CircleShape).background(getLineColor(lineInfo.lineName)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = lineInfo.lineName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = lineInfo.currentStationName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(36.dp))

                // Scrollable Content (Schedules + Stops)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {

                    // Part 1: Next Schedules (Now inside the scrollable column)
                    if (lineInfo.currentStationName.isNotBlank()) {
                        NextSchedulesSection(viewModel = viewModel, lineInfo = lineInfo)
                        Spacer(modifier = Modifier.height(20.dp))
                    }

                    // Part 2: Stops or Loader
                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp), // Give it some height to look good
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                                .padding(top = 10.dp, bottom = 40.dp)
                        ) {
                            if (lineStops.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp), // Give it some height to look good
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            } else {
                                val lineColor = getLineColor(lineInfo.lineName)
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
        }

        if (sheetState != null) {
            ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Color.White) {
                content()
            }
        } else {
            content()
        }
    }
}

@Composable
private fun NextSchedulesSection(viewModel: TransportViewModel, lineInfo: LineInfo) {
    val headsigns by viewModel.headsigns.collectAsState()
    val allSchedules by viewModel.allSchedules.collectAsState()
    val nextSchedules by viewModel.nextSchedules.collectAsState()

    var selectedDirection by remember { mutableStateOf(0) }
    var showAllSchedulesDialog by remember { mutableStateOf(false) }

    LaunchedEffect(lineInfo.lineName) {
        viewModel.loadHeadsigns(lineInfo.lineName)
    }

    LaunchedEffect(selectedDirection, lineInfo.currentStationName) {
        viewModel.loadSchedulesForDirection(
            lineName = lineInfo.lineName,
            stopName = lineInfo.currentStationName,
            directionId = selectedDirection,
            isHoliday = false // Always false for now, as per user request
        )
    }

    val lineColor = getLineColor(lineInfo.lineName)

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (headsigns.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                headsigns.keys.sorted().forEach { directionId ->
                    val headsign = headsigns[directionId] ?: "Direction ${directionId + 1}"
                    Button(
                        onClick = { selectedDirection = directionId },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedDirection == directionId) lineColor else Color.LightGray,
                            contentColor = if (selectedDirection == directionId) Color.White else Color.DarkGray
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(headsign, maxLines = 1)
                    }
                    if (directionId != headsigns.keys.maxOrNull()) {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { if (allSchedules.isNotEmpty()) showAllSchedulesDialog = true }
                .padding(16.dp), // "Big line" thanks to padding
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Common style for all schedules
            val timeStyle = MaterialTheme.typography.titleMedium

            // 1st Schedule (Red)
            if (nextSchedules.isNotEmpty()) {
                Text(text = nextSchedules[0], style = timeStyle, color = Red500)
            }

            // 2nd Schedule (Orange)
            if (nextSchedules.size > 1) {
                Spacer(modifier = Modifier.width(16.dp)) // Space between times
                Text(text = nextSchedules[1], style = timeStyle, color = Orange500)
            }

            // 3rd Schedule (Green)
            if (nextSchedules.size > 2) {
                Spacer(modifier = Modifier.width(16.dp))
                Text(text = nextSchedules[2], style = timeStyle, color = Green500)
            }

            // Push the chevron all the way to the right
            Spacer(modifier = Modifier.weight(1f))

            // Chevron in Gray
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color.Gray
            )
        }
    }

    if (showAllSchedulesDialog) {
        AlertDialog(
            onDismissRequest = { showAllSchedulesDialog = false },
            title = { Text("All Schedules for ${headsigns[selectedDirection] ?: ""}") },
            text = {
                LazyColumn {
                    items(allSchedules) { time ->
                        Text(time, modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 18.sp)
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showAllSchedulesDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun StopItemWithLine(stop: LineStopInfo, lineColor: Color, isFirst: Boolean, isLast: Boolean, onStopClick: () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).height(IntrinsicSize.Min).clickable { onStopClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.width(40.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
            if (!isFirst) {
                Box(modifier = Modifier.width(4.dp).height(30.dp).offset(y = (-16).dp).background(lineColor).align(Alignment.Center))
            }
            if (!isLast) {
                Box(modifier = Modifier.width(4.dp).height(30.dp).offset(y = (16).dp).background(lineColor).align(Alignment.Center))
            }
            Box(
                modifier = Modifier.size(16.dp).clip(CircleShape)
                    .background(if (stop.isCurrentStop) lineColor else Color.White)
                    .border(width = if (stop.isCurrentStop) 0.dp else 3.dp, color = lineColor, shape = CircleShape)
            )
        }
        // Completed the cut-off code here
        Row(
            modifier = Modifier.weight(1f).padding(start = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stop.stopName,
                style = MaterialTheme.typography.bodyLarge,
                color = if (stop.isCurrentStop) lineColor else Color.Black,
                fontWeight = if (stop.isCurrentStop) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}