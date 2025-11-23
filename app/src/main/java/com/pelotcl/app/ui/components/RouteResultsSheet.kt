package com.pelotcl.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pelotcl.app.data.graph.RoutePath
import com.pelotcl.app.data.graph.RouteSegment
import com.pelotcl.app.ui.theme.Gray700
import com.pelotcl.app.ui.theme.Red500
import com.pelotcl.app.utils.BusIconHelper
import com.pelotcl.app.utils.LineColorHelper
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.pelotcl.app.ui.components.AvailableDirection

/**
 * Composant pour afficher le résultat de recherche d'itinéraire
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteResultsSheet(
    route: RoutePath,
    fromStopName: String,
    toStopName: String,
    onDismiss: () -> Unit,
    onLineClick: (String, String) -> Unit = { _, _ -> }, // lineName, fromStop
    getAvailableDirections: (String, String) -> List<AvailableDirection> = { _, _ -> emptyList() },
    modifier: Modifier = Modifier
) {
    var showDirectionSelector by remember { mutableStateOf<Pair<String, String>?>(null) } // lineName, fromStop
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // En-tête
            Text(
                text = "Itinéraires",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Text(
                text = "$fromStopName → $toStopName",
                fontSize = 16.sp,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth()
            ) {
                RouteCard(
                    route = route,
                    onLineClick = { lineName, fromStop ->
                        val directions = getAvailableDirections(lineName, fromStop)
                        if (directions.isNotEmpty()) {
                            showDirectionSelector = Pair(lineName, fromStop)
                        } else {
                            onLineClick(lineName, fromStop)
                        }
                    },
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
        
        // Direction selector sheet
        showDirectionSelector?.let { (lineName, fromStop) ->
            val directions = getAvailableDirections(lineName, fromStop)
            if (directions.isNotEmpty()) {
                DirectionSelectorSheet(
                    availableDirections = directions,
                    onDirectionSelected = { direction ->
                        // Navigate to line details with selected direction
                        onLineClick(direction.lineName, fromStop)
                    },
                    onDismiss = {
                        showDirectionSelector = null
                    }
                )
            }
        }
    }
}

@Composable
private fun RouteCard(
    route: RoutePath,
    onLineClick: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expandedSegments by remember { mutableStateOf<Set<Int>>(emptySet()) }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // En-tête de la carte
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Itinéraire",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                
                Text(
                    text = formatDuration(route.totalDuration),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Red500
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Segments de l'itinéraire
            route.segments.forEachIndexed { segmentIndex, segment ->
                RouteSegmentItem(
                    segment = segment,
                    segmentIndex = segmentIndex,
                    isLast = segmentIndex == route.segments.size - 1,
                    isExpanded = expandedSegments.contains(segmentIndex),
                    onToggleExpand = {
                        expandedSegments = if (expandedSegments.contains(segmentIndex)) {
                            expandedSegments - segmentIndex
                        } else {
                            expandedSegments + segmentIndex
                        }
                    },
                    onLineClick = { lineName ->
                        onLineClick(lineName, segment.fromStop)
                    },
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun RouteSegmentItem(
    segment: RouteSegment,
    segmentIndex: Int,
    isLast: Boolean,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onLineClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lineName = segment.lineName ?: "?"
    val lineColor = Color(LineColorHelper.getColorForLineString(lineName))
    
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Ligne verticale avec cercle
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                if (!isLast) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(if (isExpanded) 60.dp else 35.dp)
                            .offset(y = (-16).dp)
                            .background(lineColor)
                            .align(Alignment.Center)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(lineColor)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Contenu du segment
            Column(modifier = Modifier.weight(1f)) {
                // Arrêt de départ
                Text(
                    text = segment.fromStop,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Ligne avec direction (si disponible)
                if (segment.lineName != null) {
                    DirectionSelector(
                        lineName = segment.lineName,
                        direction = segment.direction,
                        lineColor = lineColor,
                        onClick = { onLineClick(segment.lineName!!) }
                    )
                } else {
                    // Afficher un placeholder pour la sélection de direction
                    DirectionSelectorPlaceholder(
                        onClick = { onLineClick("?") }
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Arrêt d'arrivée ou bouton pour voir les arrêts intermédiaires
                if (segment.intermediateStops.isNotEmpty()) {
                    Row(
                        modifier = Modifier.clickable { onToggleExpand() },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${segment.intermediateStops.size} arrêts",
                            fontSize = 12.sp,
                            color = Gray700,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "Réduire" else "Développer",
                            tint = Gray700,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    // Arrêts intermédiaires (dépliables)
                    if (isExpanded) {
                        Column(
                            modifier = Modifier.padding(start = 12.dp, top = 8.dp)
                        ) {
                            segment.intermediateStops.forEach { stopName ->
                                Text(
                                    text = stopName,
                                    fontSize = 12.sp,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
                
                if (!isLast) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
        
        // Arrêt d'arrivée (si c'est le dernier segment)
        if (isLast) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(lineColor)
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Text(
                    text = segment.toStop,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Composant pour sélectionner la direction avec icônes de lignes et chevrons
 */
@Composable
private fun DirectionSelector(
    lineName: String,
    direction: String?,
    lineColor: Color,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val drawableName = BusIconHelper.getDrawableNameForLineName(lineName)
    val resourceId = context.resources.getIdentifier(drawableName, "drawable", context.packageName)
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        color = lineColor.copy(alpha = 0.1f),
        border = androidx.compose.foundation.BorderStroke(1.dp, lineColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (resourceId != 0) {
                    Image(
                        painter = painterResource(id = resourceId),
                        contentDescription = "Ligne $lineName",
                        modifier = Modifier.size(32.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(lineColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = lineName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
                
                if (direction != null) {
                    Text(
                        text = "→ $direction",
                        fontSize = 12.sp,
                        color = Color.Black
                    )
                }
            }
            
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Voir les détails",
                tint = Gray700,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Placeholder pour la sélection de direction quand la ligne n'est pas encore déterminée
 */
@Composable
private fun DirectionSelectorPlaceholder(
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        color = Color.LightGray.copy(alpha = 0.3f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Sélectionner la direction",
                fontSize = 12.sp,
                color = Color.Gray
            )
            
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Sélectionner",
                tint = Gray700,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return if (minutes > 0) {
        if (remainingSeconds > 0) {
            "${minutes}min ${remainingSeconds}s"
        } else {
            "${minutes}min"
        }
    } else {
        "${remainingSeconds}s"
    }
}
