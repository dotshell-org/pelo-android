package com.pelotcl.app.ui.components

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.pelotcl.app.utils.SearchUtils
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.text.isDigitsOnly
import com.pelotcl.app.data.model.AlertSeverity
import com.pelotcl.app.data.model.AlertSeverity as TrafficAlertSeverity
import com.pelotcl.app.ui.viewmodel.TransportViewModel
import com.pelotcl.app.utils.LineColorHelper

/**
 * Bottom Sheet qui affiche toutes les lignes organisées par catégories
 */
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun LinesBottomSheet(
    allLines: List<String>,
    onLineClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    favoriteLines: Set<String> = emptySet(),
    viewModel: TransportViewModel? = null
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    
    // Observe traffic alerts from ViewModel
    val trafficAlerts by viewModel?.trafficAlerts?.collectAsState() ?: remember { mutableStateOf(emptyMap()) }
    
    // Compute a map of alerts for all lines to be used by Chips
    val lineAlerts = remember(trafficAlerts, allLines) {
        Log.d("AlertCheck", "Recomputing lineAlerts map. trafficAlerts size: ${trafficAlerts.size}, allLines size: ${allLines.size}")
        if (viewModel != null && allLines.isNotEmpty()) {
            val alertsMap = mutableMapOf<String, TrafficAlertSeverity>()
            allLines.forEach { lineName ->
                // Log each line check to see why it might be missing
                try {
                    val severity = viewModel.getAlertSeverityForLine(lineName)
                    if (severity != null) {
                        Log.d("AlertCheck", "Line $lineName has alert severity: ${severity.name}")
                        alertsMap[lineName.uppercase()] = severity
                    }
                } catch (e: Exception) {
                    Log.e("LinesBottomSheet", "Error loading alerts for line $lineName", e)
                }
            }
            Log.d("AlertCheck", "Computed alertsMap size: ${alertsMap.size}")
            alertsMap
        } else {
            Log.d("AlertCheck", "viewModel is null or allLines is empty")
            emptyMap()
        }
    }

    // Organize lines by category
    val categorizedLines = remember(allLines, favoriteLines) {
        // Map<String, List<String>> but we will iterate deterministically by turning to list
        val base = categorizeLines(allLines, context).toMutableMap()

        // Insert favorites first if present
        val favoritesInAll = allLines
            .map { it.uppercase() }
            .distinct()
            .filter { favoriteLines.contains(it) }
            .sortedWith(java.util.Comparator { a, b -> naturalComparatorString(a, b) })

        if (favoritesInAll.isNotEmpty()) {
            // We want to preserve the original tile formats (case) - pick the case from `allLines`
            val favoritesWithCase = allLines.filter { favoritesInAll.contains(it.uppercase()) }
            val orderedFavorites = favoritesWithCase.sortedWith(java.util.Comparator { a, b -> naturalComparatorString(a, b) })
            // Prepend as the 'Favoris' category
            val result = linkedMapOf<String, List<String>>()
            result["Favoris"] = orderedFavorites
            base.forEach { (k, v) -> result[k] = v }
            result.toList()
        } else {
            base.toList()
        }
    }
    
    // Filtrer les lignes selon la recherche
    val filteredCategories = remember(categorizedLines, searchQuery) {
        if (searchQuery.isEmpty()) {
            categorizedLines
        } else {
            categorizedLines.mapNotNull { (category, lines) ->
                val filtered = lines.filter { SearchUtils.fuzzyContains(it, searchQuery) }
                if (filtered.isNotEmpty()) category to filtered else null
            }
        }
    }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.9f)
            .background(Color.White, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(16.dp)
    ) {
        // List of lines by category
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // We lazily compose rows per category to avoid inflating hundreds of chips at once
            filteredCategories.forEach { (category, lines) ->
                val chunks = lines.chunked(4)

                // Category header
                item(key = "header_$" + category) {
                    Text(
                        text = category,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 8.dp)
                    )
                }

                // One lazy item per row
                items(count = chunks.size, key = { rowIndex -> "${category}_row_$rowIndex" }) { rowIndex ->
                    val rowLines = chunks[rowIndex]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Up to 4 chips per row
                        rowLines.forEach { line ->
                            // Each chip now also accepts an optional favorite status & toggle
                            val alertSeverity = lineAlerts[line.uppercase()]
                            LineChip(
                                lineName = line,
                                onClick = { onLineClick(line) },
                                modifier = Modifier.weight(1f),
                                alertSeverity = alertSeverity
                            )
                        }
                        // Fill remaining columns for alignment consistency
                        repeat(4 - rowLines.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            
            // Message if no results
            if (filteredCategories.isEmpty() && searchQuery.isNotEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Aucune ligne trouvée",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    }
}

private fun naturalComparatorString(a: String, b: String): Int {
    val partsA = a.split(Regex("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)"))
    val partsB = b.split(Regex("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)"))
    val maxParts = maxOf(partsA.size, partsB.size)

    for (i in 0 until maxParts) {
        val partA = partsA.getOrNull(i)
        val partB = partsB.getOrNull(i)

        if (partA == null) return -1
        if (partB == null) return 1

        val numA = partA.toIntOrNull()
        val numB = partB.toIntOrNull()

        if (numA != null && numB != null) {
            val numCompare = numA.compareTo(numB)
            if (numCompare != 0) return numCompare
        } else {
            val strCompare = partA.compareTo(partB)
            if (strCompare != 0) return strCompare
        }
    }
    return 0
}

/**
 * Chip to show a line with the official TCL icon
 */
@Suppress("DiscouragedApi") // Dynamic resource loading for transport line icons
@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun LineChip(
    lineName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    alertSeverity: TrafficAlertSeverity? = null
) {
    val context = LocalContext.current
    
    // Get icon resource ID
    val drawableId = remember(lineName) {
        val resourceName = lineName.lowercase()
            .replace("é", "e")
            .replace("è", "e")
            .replace("ê", "e")
            .replace("-", "")
            .replace(" ", "")
            .let { if (it.first().isDigit()) "_$it" else it } // Prefix _ if starts with digit
        
        context.resources.getIdentifier(resourceName, "drawable", context.packageName)
    }
    
    Box(
        modifier = modifier
            .height(48.dp)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        // Content Box with clipping and click
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (drawableId != 0) {
                // Use official TCL icon
                Icon(
                    painter = painterResource(id = drawableId),
                    contentDescription = "Ligne $lineName",
                    modifier = Modifier.size(80.dp),
                    tint = Color.Unspecified
                )
            } else {
                // Fallback if icon doesn't exist
                val backgroundColor = Color(LineColorHelper.getColorForLineString(lineName))
                val textColor = if (lineName.uppercase() == "T3") Color.Black else Color.White
                
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(backgroundColor)
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = lineName,
                        color = textColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // Alert badge (bottom-right corner) - Placed outside the clipped box
        if (alertSeverity != null) {
            AlertBadge(
                severity = alertSeverity,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = (5).dp, y = (2).dp)
            )
        }
    }
}

/**
 * Composable for displaying an alert pastilla (color circle)
 */
@Composable
private fun AlertBadge(
    severity: TrafficAlertSeverity,
    modifier: Modifier = Modifier
) {
    val badgeColor = Color(severity.color)
    val badgeSize = 16.dp

    Box(
        modifier = modifier
            .size(badgeSize)
            .clip(CircleShape)
            .background(badgeColor),
        contentAlignment = Alignment.Center
    ) {
        if (severity == AlertSeverity.INFORMATION || severity == AlertSeverity.OTHER_EFFECT) {
            // Use a text-based "i" to avoid the double circle from Icons.Default.Info
            Text(
                text = "i",
                color = Color.White,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Serif
                ),
                modifier = Modifier.padding(bottom = 1.dp)
            )
        } else {
            // PriorityHigh is a plain "!" without a surrounding circle
            Icon(
                imageVector = Icons.Default.PriorityHigh,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

/**
 * Checks if a line has an available SVG icon
 */
@Suppress("DiscouragedApi") // Dynamic resource loading for transport line icons
private fun hasLineIcon(lineName: String, context: android.content.Context): Boolean {
    val resourceName = lineName.lowercase()
        .replace("é", "e")
        .replace("è", "e")
        .replace("ê", "e")
        .replace("-", "")
        .replace(" ", "")
        .let { if (it.firstOrNull()?.isDigit() == true) "_$it" else it }
    
    val drawableId = context.resources.getIdentifier(resourceName, "drawable", context.packageName)
    return drawableId != 0
}

/**
 * Organises lines by category and filters those which haven't icon.
 */
private fun categorizeLines(lines: List<String>, context: android.content.Context): Map<String, List<String>> {
    // First filter lines that don't have icons
    val linesWithIcon = lines.filter { hasLineIcon(it, context) }
    
    val metros = mutableListOf<String>()
    val trams = mutableListOf<String>()
    val funiculaires = mutableListOf<String>()
    val chrono = mutableListOf<String>()
    val pleineLune = mutableListOf<String>()
    val jd = mutableListOf<String>()
    val navigone = mutableListOf<String>()
    val gareExpress = mutableListOf<String>()
    val soyeuses = mutableListOf<String>()
    val navettes = mutableListOf<String>()
    val zi = mutableListOf<String>()
    val carsDuRhone = mutableListOf<String>()
    val bus = mutableListOf<String>()
    
    linesWithIcon.forEach { line ->
        val upperLine = line.uppercase()
        when {
            upperLine in setOf("A", "B", "C", "D") -> metros.add(line)
            upperLine.startsWith("F") && (upperLine == "F1" || upperLine == "F2") -> funiculaires.add(line)
            upperLine.startsWith("TB") || upperLine == "RX" || upperLine.contains("RHON") -> trams.add(line)
            upperLine.startsWith("T") && upperLine.length == 2 -> trams.add(line)
            upperLine.startsWith("C") && upperLine.length >= 2 -> chrono.add(line)
            upperLine.startsWith("PL") -> pleineLune.add(line)
            upperLine.startsWith("JD") -> jd.add(line)
            upperLine.startsWith("NAV") -> navigone.add(line)
            upperLine.startsWith("GE") -> gareExpress.add(line)
            upperLine.startsWith("S") -> soyeuses.add(line)
            upperLine.startsWith("ZI") -> zi.add(line)
            upperLine.startsWith("N") -> navettes.add(line)
            upperLine.length >= 3 && upperLine != "128" && upperLine.isDigitsOnly() -> carsDuRhone.add(line)
            else -> bus.add(line)
        }
    }
    
    // Natural sort that correctly handles numbers in strings
    val naturalComparator = Comparator<String> { a, b ->
        val partsA = a.split(Regex("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)"))
        val partsB = b.split(Regex("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)"))
        val maxParts = maxOf(partsA.size, partsB.size)

        for (i in 0 until maxParts) {
            val partA = partsA.getOrNull(i)
            val partB = partsB.getOrNull(i)

            if (partA == null) return@Comparator -1 // a est plus court
            if (partB == null) return@Comparator 1  // b est plus court

            val numA = partA.toIntOrNull()
            val numB = partB.toIntOrNull()

            if (numA != null && numB != null) {
                val numCompare = numA.compareTo(numB)
                if (numCompare != 0) return@Comparator numCompare
            } else {
                val strCompare = partA.compareTo(partB)
                if (strCompare != 0) return@Comparator strCompare
            }
        }
        return@Comparator 0
    }

    fun naturalSort(lines: List<String>): List<String> {
        return lines.sortedWith(naturalComparator)
    }
    
    val result = mutableMapOf<String, List<String>>()
    
    if (metros.isNotEmpty()) result["Métro"] = naturalSort(metros)
    if (funiculaires.isNotEmpty()) result["Funiculaire"] = naturalSort(funiculaires)
    if (trams.isNotEmpty()) result["Tramway"] = naturalSort(trams)
    if (navigone.isNotEmpty()) result["Navigône"] = naturalSort(navigone)
    if (chrono.isNotEmpty()) result["Chrono"] = naturalSort(chrono)
    if (pleineLune.isNotEmpty()) result["Pleine Lune"] = naturalSort(pleineLune)
    if (gareExpress.isNotEmpty()) result["Gare Express"] = naturalSort(gareExpress)
    if (navettes.isNotEmpty()) result["Navette"] = naturalSort(navettes)
    if (soyeuses.isNotEmpty()) result["Soyeuse"] = naturalSort(soyeuses)
    if (zi.isNotEmpty()) result["Zone Industrielle"] = naturalSort(zi)
    if (bus.isNotEmpty()) result["Bus"] = naturalSort(bus)
    if (carsDuRhone.isNotEmpty()) result["Cars du Rhône TCL unifié"] = naturalSort(carsDuRhone)
    if (jd.isNotEmpty()) result["Junior Direct"] = naturalSort(jd)
    
    return result
}
