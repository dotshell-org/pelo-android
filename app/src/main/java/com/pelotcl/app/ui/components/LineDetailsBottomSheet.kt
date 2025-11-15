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
import androidx.compose.ui.unit.sp
import com.pelotcl.app.data.gtfs.LineStopInfo
import com.pelotcl.app.ui.theme.Gray700
import com.pelotcl.app.ui.viewmodel.TransportViewModel
import com.pelotcl.app.ui.viewmodel.TransportLinesUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    viewModel: TransportViewModel,
    lineInfo: LineInfo?,
    sheetState: SheetState?,
    onDismiss: () -> Unit,
    onBackToStation: () -> Unit
) {
    val context = LocalContext.current
    var lineStops by remember { mutableStateOf<List<LineStopInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    
    // Observer le state des lignes pour attendre que la ligne soit chargée
    val linesState by viewModel.uiState.collectAsState()

    // Charger les données quand lineInfo change ET que la ligne est dans le state
    LaunchedEffect(lineInfo, linesState) {
        if (lineInfo != null) {
            isLoading = true

            // Attendre que la ligne soit chargée dans le state
            val lineLoaded = when (linesState) {
                is TransportLinesUiState.Success -> {
                    (linesState as TransportLinesUiState.Success).lines.any { 
                        it.properties.ligne.equals(lineInfo.lineName, ignoreCase = true)
                    }
                }
                else -> false
            }
            
            android.util.Log.d("LineDetailsBottomSheet", "Line ${lineInfo.lineName} loaded: $lineLoaded")

            if (lineLoaded) {
                // Récupérer les arrêts via l'API (déjà en cache avec correspondances)
                withContext(Dispatchers.IO) {
                    try {
                        // Utiliser la nouvelle méthode du ViewModel qui récupère les arrêts depuis l'API
                        val stops = viewModel.getStopsForLine(
                            lineName = lineInfo.lineName,
                            currentStopName = lineInfo.currentStationName
                        )
                        
                        android.util.Log.d("LineDetailsBottomSheet", "Loaded ${stops.size} stops for line ${lineInfo.lineName} from API")
                        lineStops = stops
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
                // Header compact avec flèche retour, icône de ligne (petite) et nom de la station
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

                    // Icône de la ligne (plus petite)
                    val drawableName = getDrawableNameForLine(lineInfo.lineName)
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
    isLast: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .height(IntrinsicSize.Min),
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
                    .background(
                        if (stop.isCurrentStop) lineColor else Color.White
                    )
                    .border(
                        width = 3.dp,
                        color = lineColor,
                        shape = CircleShape
                    )
            )
        }

        // Partie droite : texte + correspondances
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
 * Badge affichant une ligne de correspondance (métro ou funiculaire)
 * Utilise les images TCL comme sur la carte
 */
@Composable
private fun ConnectionBadge(lineName: String) {
    val context = LocalContext.current

    // Convertir le nom de ligne en nom de drawable (même logique que pour la carte)
    val drawableName = getDrawableNameForLine(lineName)
    val resourceId = context.resources.getIdentifier(drawableName, "drawable", context.packageName)

    if (resourceId != 0) {
        // Afficher l'image TCL
        Image(
            painter = painterResource(id = resourceId),
            contentDescription = "Ligne $lineName",
            modifier = Modifier.size(40.dp)
        )
    } else {
        // Fallback: cercle coloré si l'image n'existe pas
        val backgroundColor = when (lineName) {
            "A" -> Color(0xFFEC4899) // Rose
            "B" -> Color(0xFF3B82F6) // Bleu
            "C" -> Color(0xFFF59E0B) // Orange
            "D" -> Color(0xFF22C55E) // Vert
            "F1", "F2" -> Color(0xFF84CC16) // Vert lime
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