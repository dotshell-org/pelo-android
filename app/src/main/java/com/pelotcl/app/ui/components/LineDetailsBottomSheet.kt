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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
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
import com.pelotcl.app.utils.Connection
import com.pelotcl.app.utils.LineColorHelper
import com.pelotcl.app.utils.ListItemRecompositionCounter
import com.pelotcl.app.utils.TransportType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

data class LineInfo(
    val lineName: String,
    val currentStationName: String,
    val showFavoriteIcon: Boolean = true
)

private fun getLineColor(lineName: String): Color {
    // Utilise le helper centralisé pour garantir la cohérence (TB → #eab308, etc.)
    return Color(LineColorHelper.getColorForLineString(lineName))
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

        val diffMinutes = ChronoUnit.MINUTES.between(now, schedule)

        if (diffMinutes < 0) {
            return Green500
        }

        return when {
            diffMinutes in 0..<2 -> Red500
            diffMinutes in 2..<15 -> Orange500
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
    selectedDirection: Int,
    onDirectionChange: (Int) -> Unit,
    onDismiss: () -> Unit,
    onBackToStation: () -> Unit,
    onLineClick: (String) -> Unit = {},
    onStopClick: (String) -> Unit = {},
    onShowAllSchedules: (lineName: String, directionName: String, schedules: List<String>) -> Unit,
    onItineraryClick: (stopName: String) -> Unit = {}
) {
    val context = LocalContext.current

    // Key states on lineInfo to reset when switching lines - prevents stale data accumulation
    val lineKey = lineInfo?.lineName to lineInfo?.currentStationName
    var lineStops by remember(lineKey) { mutableStateOf<List<LineStopInfo>>(emptyList()) }
    var isLoading by remember(lineKey) { mutableStateOf(true) }
    var lineAlerts by remember(lineKey) { mutableStateOf<List<com.pelotcl.app.data.model.TrafficAlert>>(emptyList()) }

    // Cleanup when lineInfo changes or component leaves composition
    DisposableEffect(lineKey) {
        onDispose {
            // Cancel any pending coroutines and clear stale states when switching lines
            viewModel.resetLineDetailState()
        }
    }

    val connections = remember(lineInfo?.currentStationName, lineInfo?.lineName) {
        if (lineInfo != null) {
            viewModel.getConnectionsForStop(lineInfo.currentStationName, lineInfo.lineName)
        } else {
            emptyList()
        }
    }

    // Load alerts for the line using the new state-based approach
    LaunchedEffect(lineInfo?.lineName) {
        if (lineInfo != null && lineInfo.lineName.isNotBlank()) {
            try {
                lineAlerts = viewModel.getAlertsForLine(lineInfo.lineName)
            } catch (e: Exception) {
                Log.e("LineDetailsBottomSheet", "Error loading alerts for line ${lineInfo.lineName}", e)
            }
        }
    }


    val linesState by viewModel.uiState.collectAsState()

    val loadedLineNames = remember(linesState) {
        when (linesState) {
            is TransportLinesUiState.Success -> (linesState as TransportLinesUiState.Success).lines
                .map { it.properties.ligne.uppercase() }
                .toSet()
            is TransportLinesUiState.PartialSuccess -> (linesState as TransportLinesUiState.PartialSuccess).lines
                .map { it.properties.ligne.uppercase() }
                .toSet()
            else -> emptySet()
        }
    }

    // Load stops when lineInfo, loadedLineNames, or selectedDirection changes
    LaunchedEffect(lineInfo?.lineName, lineInfo?.currentStationName, loadedLineNames, selectedDirection) {
        if (lineInfo != null) {
            isLoading = true
            // Toujours tenter de charger les arrêts, même si la ligne n'est pas encore
            // présente dans uiState (cas des lignes bus/Chrono/JD ajoutées à la volée).
            // getStopsForLine utilise maintenant la table stop_sequences GTFS pour l'ordre.
            try {
                withContext(Dispatchers.IO) {
                    val stops = viewModel.getStopsForLine(
                        lineName = lineInfo.lineName,
                        currentStopName = lineInfo.currentStationName.takeIf { it.isNotBlank() },
                        directionId = selectedDirection
                    )
                    if (stops.isEmpty()) {
                        // Recharge du cache si besoin puis nouvelle tentative rapide
                        viewModel.reloadStopsCache()
                        delay(500)
                        lineStops = viewModel.getStopsForLine(
                            lineName = lineInfo.lineName,
                            currentStopName = lineInfo.currentStationName.takeIf { it.isNotBlank() },
                            directionId = selectedDirection
                        )
                    } else {
                        lineStops = stops
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Coroutine was cancelled (e.g., user switched lines) - don't log as error
                throw e
            } catch (e: Exception) {
                Log.e("LineDetailsBottomSheet", "Error loading stops: ${e.message}", e)
            } finally {
                isLoading = false
            }
        }
    }

    // Utilise directement lineStops car l'ordre est déjà correct depuis getStopsForLine avec directionId
    val displayedStops = lineStops

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
                    // Favorite button
                    if (lineInfo.currentStationName == "") {
                        val favorites by viewModel.favoriteLines.collectAsState()
                        val isFav = favorites.contains(lineInfo.lineName.uppercase())
                        IconButton(onClick = { viewModel.toggleFavorite(lineInfo.lineName) }) {
                            val starIcon = if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder
                            Icon(imageVector = starIcon, contentDescription = if (isFav) "Unfavorite" else "Favorite", tint = if (isFav) Red500 else Color.Gray)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(36.dp))

                // Scrollable Content (Schedules + Stops)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (lineAlerts.isNotEmpty()) {
                        TrafficAlertsSection(
                            alerts = lineAlerts,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                    }

                    // Part 1: Next Schedules (contains Itinerary button)
                    if (lineInfo.currentStationName.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        NextSchedulesSection(
                            viewModel = viewModel,
                            lineInfo = lineInfo,
                            selectedDirection = selectedDirection,
                            onDirectionChange = onDirectionChange,
                            onShowAllSchedules = onShowAllSchedules,
                            onItineraryClick = { onItineraryClick(lineInfo.currentStationName) }
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                    }

                    // Part 2: Connections
                    if (connections.isNotEmpty()) {
                        ConnectionsSection(
                            connections = connections,
                            onLineClick = onLineClick
                        )
                    }

                    // Part 3: Stops or Loader
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
                                    key(stop.stopId) {
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

/**
 * Composable pour afficher les alertes de trafic pour une ligne
 */
@Composable
private fun TrafficAlertsSection(
    alerts: List<com.pelotcl.app.data.model.TrafficAlert>,
    modifier: Modifier = Modifier
) {
    if (alerts.isEmpty()) {
        return
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFFF5F5F5))
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        alerts.forEachIndexed { index, alert ->
            var isExpanded by remember { mutableStateOf(false) }
            val severity = com.pelotcl.app.data.model.AlertSeverity.fromSeverityType(alert.severityType, alert.severityLevel)
            val severityColor = Color(severity.color)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(severityColor)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = alert.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        modifier = Modifier.weight(1f)
                    )

                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Réduire" else "Développer",
                        tint = Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (isExpanded) {
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = alert.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.DarkGray
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    fun formatDate(input: String): String {
                        val inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                        val outputFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
                        val date = LocalDateTime.parse(input, inputFormatter)
                        return date.format(outputFormatter)
                    }
                    Text(
                        text = "Du ${formatDate(alert.startDate)} au ${formatDate(alert.endDate)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                        fontStyle = FontStyle.Italic
                    )
                }
            }

            if (index < alerts.size - 1) {
                androidx.compose.material3.HorizontalDivider(
                    color = Color.LightGray,
                    thickness = 1.dp,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp)
                )
            }
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
    onShowAllSchedules: (lineName: String, directionName: String, schedules: List<String>) -> Unit,
    onItineraryClick: () -> Unit = {}
) {
    val headsigns by viewModel.headsigns.collectAsState()
    val allSchedules by viewModel.allSchedules.collectAsState()
    val nextSchedules by viewModel.nextSchedules.collectAsState()
    val availableDirections by viewModel.availableDirections.collectAsState()

    // Key for tracking line changes - used to trigger cleanup
    val lineKey = lineInfo.lineName to lineInfo.currentStationName

    // Load headsigns when line changes, and compute directions when stop changes
    LaunchedEffect(lineKey) {
        // Load headsigns if not already loaded for this line
        if (headsigns.isEmpty()) {
            viewModel.loadHeadsign(lineInfo.lineName)
        }
        // Always compute available directions when stop changes
        if (lineInfo.currentStationName.isNotBlank()) {
            // Wait briefly for headsigns if they were just requested
            if (headsigns.isEmpty()) {
                kotlinx.coroutines.delay(100)
            }
            viewModel.computeAvailableDirections(lineInfo.lineName, lineInfo.currentStationName)
        }
    }

    // Recompute directions when headsigns become available (for initial load)
    LaunchedEffect(headsigns) {
        if (lineInfo.currentStationName.isNotBlank() && headsigns.isNotEmpty()) {
            viewModel.computeAvailableDirections(lineInfo.lineName, lineInfo.currentStationName)
        }
    }

    // Si une seule direction possède des horaires, auto‑sélectionner celle‑ci
    LaunchedEffect(availableDirections) {
        if (availableDirections.size == 1) {
            val onlyDir = availableDirections.first()
            if (selectedDirection != onlyDir) {
                onDirectionChange(onlyDir)
            }
        } else if (availableDirections.isNotEmpty()) {
            // Si la direction sélectionnée actuelle n'est pas disponible, basculer sur la première dispo
            if (!availableDirections.contains(selectedDirection)) {
                onDirectionChange(availableDirections.first())
            }
        }
    }

    // Load schedules when direction changes
    LaunchedEffect(lineKey, selectedDirection) {
        if (lineInfo.currentStationName.isNotBlank()) {
            viewModel.loadSchedulesForDirection(
                lineName = lineInfo.lineName,
                stopName = lineInfo.currentStationName,
                directionId = selectedDirection
            )
        }
    }

    val lineColor = getLineColor(lineInfo.lineName)

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Itinéraire button
        Button(
            onClick = onItineraryClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Black,
                contentColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Directions,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Itinéraire",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        if (availableDirections.isNotEmpty()) {
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
                availableDirections.forEach { directionId ->
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
        } else {
            Text(
                text = "Aucun horaire disponible à cet arrêt",
                textAlign = TextAlign.Left,
                color = Color.DarkGray,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, bottom = 16.dp)
            )
        }

        if (availableDirections.isNotEmpty()) {
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
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConnectionsSection(
    connections: List<Connection>,
    onLineClick: (String) -> Unit
) {
    if (connections.isEmpty()) return

    val strongLines = connections.filter {
        val isStrongType = it.transportType in listOf(
            TransportType.METRO,
            TransportType.TRAM,
            TransportType.FUNICULAR,
            TransportType.NAVIGONE,
        )

        isStrongType || it.lineName == "RX"
    }
    val weakLines = connections.filter { it !in strongLines }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 16.dp)
    ) {
        Text(
            text = "Correspondances",
            textAlign = TextAlign.Left,
            fontSize = 22.sp,
            color = Color.Black,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 30.dp, bottom = 12.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (strongLines.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(horizontal = 12.dp)
            ) {
                strongLines.forEach { connection ->
                    ConnectionBadge(
                        lineName = connection.lineName,
                        size = 48.dp,
                        onClick = { onLineClick(connection.lineName) }
                    )
                }
            }
        }

        if (weakLines.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy((-20).dp),
                modifier = Modifier.padding(horizontal = 12.dp)
            ) {
                weakLines.forEach { connection ->
                    ConnectionBadge(
                        lineName = connection.lineName,
                        size = 48.dp,
                        onClick = { onLineClick(connection.lineName) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StopItemWithLine(stop: LineStopInfo, lineColor: Color, isFirst: Boolean, isLast: Boolean, onStopClick: () -> Unit = {}) {
    // Debug: measure the recompositions of this item
    ListItemRecompositionCounter("LineStops", stop.stopId)

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).height(IntrinsicSize.Min).clickable { onStopClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.width(40.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
            if (!isFirst) {
                Box(modifier = Modifier.width(4.dp).height(35.dp).offset(y = (-16).dp).background(lineColor).align(Alignment.Center))
            }
            if (!isLast) {
                Box(modifier = Modifier.width(4.dp).height(35.dp).offset(y = (16).dp).background(lineColor).align(Alignment.Center))
            }
            Box(
                modifier = Modifier.size(16.dp).clip(CircleShape)
                    .background(if (stop.isCurrentStop) lineColor else Color.White)
                    .border(width = if (stop.isCurrentStop) 0.dp else 3.dp, color = lineColor, shape = CircleShape)
            )
        }

        val filteredConnections = stop.connections.filter { connection ->
            val upperCaseConnection = connection.uppercase()

            upperCaseConnection in listOf("A", "B", "C", "D") || // Metro
                    (upperCaseConnection.startsWith("T") && !upperCaseConnection.endsWith("36")) || // Tram & Trambus
                    upperCaseConnection in listOf("F1", "F2") || // Funicular
                    upperCaseConnection.startsWith("NAV") || // Navigone
                    upperCaseConnection == "RX" // Rhone Express
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
                fontWeight = if (stop.isCurrentStop) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.weight(1f, fill = false)
            )

            if (filteredConnections.isNotEmpty()) {
                Spacer(modifier = Modifier.width(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    maxLines = 2,
                    maxItemsInEachRow = 4
                ) {
                    filteredConnections.forEach { connectionLine ->
                        ConnectionBadge(lineName = connectionLine, size = 32.dp)
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
private fun ConnectionBadge(
    lineName: String,
    size: androidx.compose.ui.unit.Dp = 32.dp,
    onClick: (() -> Unit)? = null
) {
    val context = LocalContext.current

    // Convert line name to drawable name using BusIconHelper for consistency
    val drawableName = BusIconHelper.getDrawableNameForLineName(lineName)
    val resourceId = context.resources.getIdentifier(drawableName, "drawable", context.packageName)

    val modifier = if (onClick != null) {
        Modifier.size(size).clickable { onClick() }
    } else {
        Modifier.size(size)
    }

    if (resourceId != 0) {
        // Display TCL image
        Image(
            painter = painterResource(id = resourceId),
            contentDescription = "Ligne $lineName",
            modifier = modifier
        )
    }
}