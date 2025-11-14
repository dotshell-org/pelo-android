package com.pelotcl.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
 * Retourne la couleur d'une ligne selon son nom et type
 */
private fun getLineColor(lineName: String): Color {
    return when (lineName.uppercase()) {
        // Métros
        "A" -> Color(0xFFEC4899) // Rose
        "B" -> Color(0xFF3B82F6) // Bleu
        "C" -> Color(0xFFF59E0B) // Orange
        "D" -> Color(0xFF22C55E) // Vert
        // Funiculaires
        "F1", "F2" -> Color(0xFF84CC16) // Vert lime
        // Trams (commence par T)
        else -> when {
            lineName.uppercase().startsWith("T") -> Color(0xFFA855F7) // Violet
            else -> Color(0xFFEF4444) // Rouge (Bus)
        }
    }
}

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
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        if (lineStops.isEmpty()) {
                            Text(
                                text = "Aucun arrêt trouvé",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Gray700,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        } else {
                            // Obtenir la couleur de la ligne
                            val lineColor = getLineColor(lineInfo.lineName)
                            
                            // Afficher tous les arrêts avec ligne verticale
                            lineStops.forEachIndexed { index, stop ->
                                StopItemWithLine(
                                    stop = stop,
                                    lineColor = lineColor,
                                    isFirst = index == 0,
                                    isLast = index == lineStops.size - 1
                                )
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
 * Item affichant un arrêt de la ligne avec une ligne verticale et un cercle
 */
@Composable
private fun StopItemWithLine(
    stop: LineStopInfo,
    lineColor: Color,
    isFirst: Boolean,
    isLast: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.Top
    ) {
        // Colonne avec la ligne verticale et le cercle
        Box(
            modifier = Modifier
                .width(40.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.TopCenter
        ) {
            // Ligne verticale (ne s'affiche pas pour le premier arrêt en haut)
            if (!isFirst) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .offset(y = (-24).dp)
                        .background(lineColor.copy(alpha = 0.5f))
                        .align(Alignment.TopCenter)
                )
            }
            
            // Ligne verticale (ne s'affiche pas pour le dernier arrêt en bas)
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .offset(y = 24.dp)
                        .background(lineColor.copy(alpha = 0.5f))
                        .align(Alignment.TopCenter)
                )
            }
            
            // Cercle pour l'arrêt - aligné avec le texte
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(if (stop.isCurrentStop) lineColor else Color.White)
                    .border(
                        width = 3.dp,
                        color = lineColor,
                        shape = CircleShape
                    )
                    .align(Alignment.TopCenter)
                    .offset(y = 0.dp)
            )
        }
        
        // Nom de l'arrêt
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, bottom = 22.dp)
        ) {
            Text(
                text = stop.stopName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (stop.isCurrentStop) FontWeight.Bold else FontWeight.Normal,
                color = if (stop.isCurrentStop) lineColor else Color.Black
            )
        }
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
