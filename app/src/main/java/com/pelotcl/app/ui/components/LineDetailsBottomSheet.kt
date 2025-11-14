package com.pelotcl.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pelotcl.app.data.gtfs.GtfsParser
import com.pelotcl.app.data.gtfs.LineStopInfo
import com.pelotcl.app.data.gtfs.StopDeparture
import com.pelotcl.app.ui.theme.Gray200
import com.pelotcl.app.ui.theme.Gray700
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Informations d'une ligne pour l'affichage dans le bottom sheet
 */
data class LineInfo(
    val lineName: String,
    val currentStationName: String
)

/**
 * Bottom sheet affichant les détails d'une ligne de transport :
 * - Liste des arrêts dans l'ordre
 * - Prochains horaires de passage à l'arrêt actuel
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LineDetailsBottomSheet(
    lineInfo: LineInfo?,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onBackToStation: () -> Unit
) {
    val context = LocalContext.current
    var lineStops by remember { mutableStateOf<List<LineStopInfo>>(emptyList()) }
    var nextDepartures by remember { mutableStateOf<List<StopDeparture>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    
    // Charger les données quand lineInfo change
    LaunchedEffect(lineInfo) {
        if (lineInfo != null) {
            isLoading = true
            
            // Timeout de 5 secondes pour éviter le chargement infini
            val result = withTimeoutOrNull(5000L) {
                withContext(Dispatchers.IO) {
                    try {
                        val parser = GtfsParser(context)
                        
                        // Charger les arrêts de la ligne
                        val stops = parser.getLineStops(
                            lineName = lineInfo.lineName,
                            direction = 0,
                            currentStopName = lineInfo.currentStationName
                        )
                        
                        // Charger les prochains départs
                        val departures = parser.getNextDepartures(
                            stopName = lineInfo.currentStationName,
                            lineName = lineInfo.lineName,
                            maxResults = 5
                        )
                        
                        Pair(stops, departures)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        null
                    }
                }
            }
            
            if (result != null) {
                lineStops = result.first
                nextDepartures = result.second
            }
            
            isLoading = false
        }
    }
    
    if (lineInfo != null) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
            ) {
                // Header avec bouton retour et nom de la ligne
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBackToStation) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour à la station",
                            tint = Gray700
                        )
                    }
                    
                    // Icône de la ligne
                    val drawableName = getDrawableNameForLine(lineInfo.lineName)
                    val resourceId = context.resources.getIdentifier(drawableName, "drawable", context.packageName)
                    
                    if (resourceId != 0) {
                        Image(
                            painter = painterResource(id = resourceId),
                            contentDescription = "Ligne ${lineInfo.lineName}",
                            modifier = Modifier.size(80.dp)
                        )
                    } else {
                        Text(
                            text = "Ligne ${lineInfo.lineName}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(48.dp))
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
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
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Section des prochains départs
                        Text(
                            text = "Prochains départs",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        if (nextDepartures.isEmpty()) {
                            Text(
                                text = "Aucun départ prévu",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Gray700,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        } else {
                            nextDepartures.forEach { departure ->
                                DepartureItem(departure)
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    color = Gray200
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Section des arrêts de la ligne
                        Text(
                            text = "Arrêts de la ligne",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        if (lineStops.isEmpty()) {
                            Text(
                                text = "Aucun arrêt trouvé",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Gray700,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        } else {
                            lineStops.forEach { stop ->
                                StopItem(stop)
                                if (stop != lineStops.last()) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 4.dp),
                                        color = Gray200
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Item affichant un départ
 */
@Composable
private fun DepartureItem(departure: StopDeparture) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = departure.destination,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = Color.Black
            )
        }
        
        Text(
            text = formatTime(departure.departureTime),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2563EB)
        )
    }
}

/**
 * Item affichant un arrêt de la ligne
 */
@Composable
private fun StopItem(stop: LineStopInfo) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (stop.isCurrentStop) Color(0xFFEFF6FF) else Color.Transparent)
            .padding(vertical = 12.dp, horizontal = if (stop.isCurrentStop) 8.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Numéro de séquence
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(
                    color = if (stop.isCurrentStop) Color(0xFF2563EB) else Gray200,
                    shape = MaterialTheme.shapes.small
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stop.stopSequence.toString(),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = if (stop.isCurrentStop) Color.White else Gray700
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Nom de l'arrêt
        Text(
            text = stop.stopName,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (stop.isCurrentStop) FontWeight.Bold else FontWeight.Normal,
            color = if (stop.isCurrentStop) Color(0xFF2563EB) else Color.Black
        )
    }
}

/**
 * Formate un temps HH:MM:SS en HH:MM
 */
private fun formatTime(time: String): String {
    val parts = time.split(":")
    return if (parts.size >= 2) {
        "${parts[0]}:${parts[1]}"
    } else {
        time
    }
}

/**
 * Convertit un nom de ligne en nom de drawable
 */
private fun getDrawableNameForLine(lineName: String): String {
    if (lineName.isBlank()) {
        return ""
    }
    
    val isNumericOnly = lineName.all { it.isDigit() }
    
    return if (isNumericOnly) {
        "_$lineName"
    } else {
        lineName.lowercase()
    }
}
