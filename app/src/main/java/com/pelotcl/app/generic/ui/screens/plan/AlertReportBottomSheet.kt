package com.pelotcl.app.generic.ui.screens.plan

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Elevator
import androidx.compose.material.icons.filled.EmojiPeople
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Traffic
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pelotcl.app.generic.ui.components.search.LineSearchResult
import com.pelotcl.app.generic.ui.components.search.StationSearchResult
import com.pelotcl.app.generic.ui.components.search.TransportSearchBar
import com.pelotcl.app.generic.ui.components.search.TransportSearchContent
import com.pelotcl.app.generic.ui.theme.Amber500
import com.pelotcl.app.generic.ui.theme.Fuchsia500
import com.pelotcl.app.generic.ui.theme.Indigo700
import com.pelotcl.app.generic.ui.theme.Lime500
import com.pelotcl.app.generic.ui.theme.Red500
import com.pelotcl.app.generic.ui.theme.Rose500
import com.pelotcl.app.generic.ui.theme.SecondaryColor
import com.pelotcl.app.generic.ui.theme.Violet500
import com.pelotcl.app.generic.ui.theme.Yellow500
import com.pelotcl.app.generic.ui.viewmodel.TransportViewModel
import com.pelotcl.app.utils.transport.BusIconHelper
import com.pelotcl.app.utils.transport.LineColorHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

enum class AlertType(val id: String, val label: String, val icon: ImageVector, val color: Color, val isStop: Boolean, val isLine: Boolean) {
    // STOP_ALERT_TYPES=closure,delay,elevator,crowding,works,strike,fire
    // LINE_ALERT_TYPES=interruption,congestion,works,strike
    
    CLOSURE("closure", "Arrêt Fermé", Icons.Default.Block, Red500, isStop = true, isLine = false),
    DELAY("delay", "Retard", Icons.Default.Schedule, Indigo700, isStop = true, isLine = false),
    ELEVATOR("elevator", "Ascenseur HS", Icons.Default.Elevator, Amber500, isStop = true, isLine = false),
    CROWDING("crowding", "Forte Foule", Icons.Default.Groups, Fuchsia500, isStop = true, isLine = false),
    WORKS("works", "Travaux", Icons.Default.Engineering, Yellow500, isStop = true, isLine = true),
    STRIKE("strike", "Grève", Icons.Default.EmojiPeople, Lime500, isStop = true, isLine = true),
    FIRE("fire", "Incendie", Icons.Default.Whatshot, Rose500, isStop = true, isLine = false),
    INTERRUPTION("interruption", "Interruption", Icons.Default.Pause, Rose500, isStop = false, isLine = true),
    CONGESTION("congestion", "Traffic Elevé", Icons.AutoMirrored.Filled.TrendingUp,Violet500, isStop = false, isLine = true)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertReportBottomSheet(
    viewModel: TransportViewModel,
    onDismiss: () -> Unit,
    initialStop: StationSearchResult? = null
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var selectedStop by remember { mutableStateOf<StationSearchResult?>(initialStop) }
    var selectedLine by remember { mutableStateOf<LineSearchResult?>(null) }
    var showSearchFullscreen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    
    val allAlertTypes = AlertType.entries
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            if (selectedStop == null && selectedLine == null) {
                // PAGE 1: Initial view
                Text(
                    text = "Signaler une alerte",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.Black,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.padding(bottom = 16.dp, start = 8.dp)
                )

                Text(
                    text = "Commencez par rechercher un arrêt ou une ligne.",
                    color = Color.Black,
                    modifier = Modifier.padding(bottom = 16.dp, start = 8.dp)
                )

                // Search entry point
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CircleShape)
                        .background(Color.Black)
                        .clickable {
                            android.util.Log.d("AlertReportBS", "Opening search. Query reset.")
                            searchQuery = "" // Reset query when opening
                            showSearchFullscreen = true
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = Color.Red,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Rechercher",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                }
            } else {
                // PAGE 2: Selection view with icons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Retour",
                        tint = Color.Black,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable {
                                selectedStop = null
                                selectedLine = null
                            }
                    )
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    if (selectedLine != null) {
                        val lineName = selectedLine!!.lineName
                        val iconRes = BusIconHelper.getResourceIdForLine(context, lineName)
                        val fallbackColor = Color(LineColorHelper.getColorForLineString(lineName))

                        if (iconRes != 0) {
                            Image(
                                painter = painterResource(id = iconRes),
                                contentDescription = "Ligne $lineName",
                                modifier = Modifier.size(44.dp)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(fallbackColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = lineName.ifBlank { "?" }.take(3),
                                    color = SecondaryColor,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else if (selectedStop != null) {
                        Text(
                            text = selectedStop!!.stopName,
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                val filteredAlertTypes = allAlertTypes.filter { alertType ->
                    (selectedStop != null && alertType.isStop) || (selectedLine != null && alertType.isLine)
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(filteredAlertTypes) { alertType ->
                        AlertButton(
                            alertType = alertType,
                            enabled = true,
                            onClick = {
                                android.util.Log.d("AlertReportBS", "Alert clicked: ${alertType.id}")
                                scope.launch {
                                    sendAlert(
                                        alertType = alertType,
                                        stopId = selectedStop?.stopId,
                                        stopName = selectedStop?.stopName,
                                        lineId = selectedLine?.lineName,
                                        onSuccess = { onDismiss() }
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showSearchFullscreen) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { 
                android.util.Log.d("AlertReportBS", "Search dialog dismissed via onDismissRequest")
                showSearchFullscreen = false 
            },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary)
            ) {
                TransportSearchBar(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize(),
                    content = TransportSearchContent.STOPS_AND_LINES,
                    showHistory = false,
                    startExpanded = true,
                    showDarkOutline = false,
                    searchPlaceholder = "Ligne ou arrêt concerné",
                    query = searchQuery,
                    onQueryChange = { q ->
                        searchQuery = q
                    },
                    onExpandedChange = { expanded ->
                        android.util.Log.d("AlertReportBS", "Search expanded change: $expanded")
                        if (!expanded) showSearchFullscreen = false
                    },
                    onStopPrimary = { result ->
                        android.util.Log.d("AlertReportBS", "Stop selected: ${result.stopName}, id=${result.stopId}")
                        selectedStop = result
                        selectedLine = null
                        showSearchFullscreen = false
                    },
                    onLineSelected = { result ->
                        android.util.Log.d("AlertReportBS", "Line selected: ${result.lineName}")
                        selectedLine = result
                        selectedStop = null
                        showSearchFullscreen = false
                    },
                    showDirections = false
                )
            }
        }
    }
}

@Composable
fun AlertButton(
    alertType: AlertType,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(alertType.color),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = alertType.icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = Color.White
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = alertType.label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            softWrap = false,
            overflow = TextOverflow.Visible
        )
    }
}

private suspend fun sendAlert(
    alertType: AlertType,
    stopId: Int?,
    stopName: String?,
    lineId: String?,
    onSuccess: () -> Unit
) {
    withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient()
            val url = "https://api.dotshell.eu/pelo/v1/app/users-alerts"
            
            val json = JSONObject().apply {
                put("type", alertType.id)
                if (stopId != null) put("stopId", stopId)
                if (stopName != null) put("stopName", stopName)
                if (lineId != null) put("lineId", lineId)
            }
            
            val body = json.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()
                
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    withContext(Dispatchers.Main) {
                        onSuccess()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
