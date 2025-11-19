package com.pelotcl.app.ui.components

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.pelotcl.app.ui.viewmodel.TransportLinesUiState
import com.pelotcl.app.ui.viewmodel.TransportViewModel
import com.pelotcl.app.utils.BusIconHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.time.temporal.ChronoUnit

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

@RequiresApi(Build.VERSION_CODES.O)
private fun getScheduleColorBasedOnTime(scheduleTime: String): Color {
    try {
        val now = LocalTime.now()
        val cleanTime = if (scheduleTime.count { it == ':' } == 2) {
            scheduleTime.substringBeforeLast(":")
        } else {
            scheduleTime
        }

        val parts = cleanTime.split(":")
        if (parts.size < 2) return Green500

        val hour = parts[0].toInt()
        val minute = parts[1].toInt()
        val schedule = LocalTime.of(hour, minute)

        var diffMinutes = ChronoUnit.MINUTES.between(now, schedule)

        if (diffMinutes < -720) {
            diffMinutes += 1440
        } else if (diffMinutes < 0) {
            return Red500
        }

        return when {
            diffMinutes < 2 -> Red500
            diffMinutes < 15 -> Orange500
            else -> Green500
        }
    } catch (_: Exception) {
        return Green500
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LineDetailsBottomSheet(
    viewModel: TransportViewModel,
    lineInfo: LineInfo?,
    sheetState: SheetState?,
    onDismiss: () -> Unit,
    onBackToStation: () -> Unit,
    onStopClick: (String) -> Unit = {},
    onShowAllSchedules: (lineName: String, directionName: String, schedules: List<String>) -> Unit
) {
    val context = LocalContext.current
    var lineStops by remember { mutableStateOf<List<LineStopInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    var selectedDirection by remember { mutableStateOf(0) }

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
                            delay(500)
                            lineStops = viewModel.getStopsForLine(
                                lineName = lineInfo.lineName,
                                currentStopName = lineInfo.currentStationName.takeIf { it.isNotBlank() }
                            )
                        } else {
                            lineStops = stops
                        }
                    } catch (e: Exception) {
                        Log.e("LineDetailsBottomSheet", "Error loading stops: ${e.message}", e)
                    }
                }
            }
            isLoading = false
        }
    }

    val displayedStops = remember(lineStops, selectedDirection) {
        if (selectedDirection == 1) lineStops.reversed() else lineStops
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

                    // Part 1: Next Schedules
                    if (lineInfo.currentStationName.isNotBlank()) {
                        NextSchedulesSection(
                            viewModel = viewModel,
                            lineInfo = lineInfo,
                            selectedDirection = selectedDirection,
                            onDirectionChange = { newDirection -> selectedDirection = newDirection },
                            onShowAllSchedules = onShowAllSchedules
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                    }

                    // Part 2: Stops or Loader
                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
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
                            if (displayedStops.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            } else {
                                val lineColor = getLineColor(lineInfo.lineName)
                                displayedStops.forEachIndexed { index, stop ->
                                    StopItemWithLine(
                                        stop = stop,
                                        lineColor = lineColor,
                                        isFirst = index == 0,
                                        isLast = index == displayedStops.size - 1,
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

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun NextSchedulesSection(
    viewModel: TransportViewModel,
    lineInfo: LineInfo,
    selectedDirection: Int,
    onDirectionChange: (Int) -> Unit,
    onShowAllSchedules: (lineName: String, directionName: String, schedules: List<String>) -> Unit
) {
    val headsigns by viewModel.headsigns.collectAsState()
    val allSchedules by viewModel.allSchedules.collectAsState()
    val nextSchedules by viewModel.nextSchedules.collectAsState()

    LaunchedEffect(lineInfo.lineName) {
        viewModel.loadHeadsigns(lineInfo.lineName)
    }

    LaunchedEffect(selectedDirection, lineInfo.currentStationName) {
        viewModel.loadSchedulesForDirection(
            lineName = lineInfo.lineName,
            stopName = lineInfo.currentStationName,
            directionId = selectedDirection
        )
    }

    val lineColor = getLineColor(lineInfo.lineName)

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (headsigns.isNotEmpty()) {
            Text(
                text = "Direction",
                textAlign = TextAlign.Left,
                fontSize = 22.sp,
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, bottom = 26.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                headsigns.keys.sorted().forEach { directionId ->
                    val headsign = headsigns[directionId] ?: "Direction ${directionId + 1}"

                    Button(
                        onClick = { onDirectionChange(directionId) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedDirection == directionId) lineColor else Color.LightGray,
                            contentColor = if (selectedDirection == directionId) Color.White else Color.DarkGray
                        ),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        Text(
                            text = headsign,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        Text(
            text = "Prochains départs",
            textAlign = TextAlign.Left,
            fontSize = 22.sp,
            color = Color.Black,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 30.dp, bottom = 12.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (allSchedules.isNotEmpty()) {
                        val directionName = headsigns[selectedDirection] ?: ""
                        onShowAllSchedules(lineInfo.lineName, directionName, allSchedules)
                    }
                }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Common style for all schedules
            val timeStyle = MaterialTheme.typography.titleMedium

            // Loop through up to 3 next schedules
            nextSchedules.take(3).forEachIndexed { index, schedule ->
                if (index > 0) {
                    Spacer(modifier = Modifier.width(16.dp))
                }
                Text(
                    text = schedule,
                    style = timeStyle,
                    color = getScheduleColorBasedOnTime(schedule)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Icon(imageVector = Icons.Default.ChevronRight, contentDescription = "See all", tint = Gray700)
        }
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

        val filteredConnections = stop.connections.filter { connection ->
            val upperCaseConnection = connection.uppercase()
            val isTram = upperCaseConnection.startsWith("T") && upperCaseConnection.length == 2 && upperCaseConnection.substring(1).toIntOrNull() != null

            upperCaseConnection in listOf("A", "B", "C", "D") ||
                    isTram ||
                    upperCaseConnection in listOf("F1", "F2")
        }

        if (filteredConnections.isNotEmpty()) {
            Spacer(modifier = Modifier.width(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                filteredConnections.forEach { connectionLine ->
                    ConnectionBadge(lineName = connectionLine)
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