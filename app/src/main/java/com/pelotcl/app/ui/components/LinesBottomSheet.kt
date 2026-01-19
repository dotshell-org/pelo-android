package com.pelotcl.app.ui.components

import android.os.Build
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
    favoriteLines: Set<String> = emptySet()
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }

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
                val filtered = lines.filter { it.contains(searchQuery, ignoreCase = true) }
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
                            LineChip(
                                lineName = line,
                                onClick = { onLineClick(line) },
                                modifier = Modifier.weight(1f)
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
 * Section pour une catégorie de lignes
 */
// CategorySection removed in favor of a fully lazy layout within LazyColumn to avoid initial lag

/**
 * Chip pour afficher une ligne avec son icône TCL officielle
 */
@Suppress("DiscouragedApi") // Dynamic resource loading for transport line icons
@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun LineChip(
    lineName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
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
            .height(36.dp) // Reduced from 48dp to 36dp
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(2.dp), // Reduced from 4dp to 2dp
        contentAlignment = Alignment.Center
    ) {
        if (drawableId != 0) {
            // Use official TCL icon
            Icon(
                painter = painterResource(id = drawableId),
                contentDescription = "Ligne $lineName",
                modifier = Modifier.fillMaxSize(),
                tint = Color.Unspecified // Garde les couleurs d'origine du SVG
            )
        } else {
            // Fallback if icon doesn't exist
            val backgroundColor = Color(LineColorHelper.getColorForLineString(lineName))
            val textColor = if (lineName.uppercase() == "T3") Color.Black else Color.White
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
                    .background(backgroundColor)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
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
}

/**
 * Vérifie si une ligne a une icône SVG disponible
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
 * Organise les lignes par catégorie et filtre celles qui n'ont pas d'icône
 */
private fun categorizeLines(lines: List<String>, context: android.content.Context): Map<String, List<String>> {
    // First filter lines that don't have icons
    val linesWithIcon = lines.filter { hasLineIcon(it, context) }
    
    val metros = mutableListOf<String>()
    val trams = mutableListOf<String>()
    val funiculaires = mutableListOf<String>()
    val chrono = mutableListOf<String>() // Lignes C
    val pleineLune = mutableListOf<String>() // Lignes PL
    val jd = mutableListOf<String>() // Lignes JD
    val navigone = mutableListOf<String>() // Ligne NAVI1
    val bus = mutableListOf<String>()
    
    linesWithIcon.forEach { line ->
        val upperLine = line.uppercase()
        when {
            upperLine in setOf("A", "B", "C", "D") -> metros.add(line)
            upperLine.startsWith("F") && (upperLine == "F1" || upperLine == "F2") -> funiculaires.add(line)
            // Trams: T + 1 digit (T1-T9), Tb (trambus), and RX (Rhonexpress)
            upperLine.startsWith("TB") || upperLine == "RX" || upperLine.contains("RHON") -> trams.add(line)
            upperLine.startsWith("T") && upperLine.length == 2 -> trams.add(line)
            upperLine.startsWith("C") && upperLine.length >= 2 -> chrono.add(line) // C21, C22, etc.
            upperLine.startsWith("PL") -> pleineLune.add(line) // PL1, PL2, etc.
            upperLine.startsWith("JD") -> jd.add(line) // Lignes JD
            upperLine.startsWith("NAV") -> navigone.add(line) // NAV1
            else -> bus.add(line) // Bus normaux
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
    if (bus.isNotEmpty()) result["Bus"] = naturalSort(bus)
    if (jd.isNotEmpty()) result["Junior Direct"] = naturalSort(jd)
    
    return result
}
