package com.pelotcl.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Accessible
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pelotcl.app.ui.theme.Gray200
import com.pelotcl.app.ui.theme.Gray700

/**
 * Station data for display in the bottom sheet
 */
data class StationInfo(
    val nom: String,
    val lignes: List<String>, // List of line names (ex: ["A", "D", "F1"])
    val isPmr: Boolean = false,
    val desserte: String = "" // Complete service string for reference
)

/**
 * Sorts lines for display in the bottom sheet.
 * Family order:
 *  1) Metro: A, B, C, D (fixed order)
 *  2) Funiculars: F1, F2 (then F3+ if existed) sorted numerically
 *  3) Tram: T1..Tn sorted numerically
 *  4) Buses with letter prefix (ex: C1, JD12, S3, ZI2...) sorted by prefix then number
 *  5) Purely numeric buses (ex: 2, 12, 79) sorted numerically
 *  6) Others/undetermined: case-insensitive lexicographic order
 */
private fun sortLines(lines: List<String>): List<String> {
    data class Key(
        val family: Int,
        val subFamily: String = "",
        val number: Int = Int.MAX_VALUE,
        val raw: String = ""
    )

    fun keyFor(lineRaw: String): Key {
        val line = lineRaw.trim()
        val up = line.uppercase()

        // 1) Metro A-D fixed order
        when (up) {
            "A" -> return Key(1000, number = 0, raw = up)
            "B" -> return Key(1001, number = 0, raw = up)
            "C" -> return Key(1002, number = 0, raw = up)
            "D" -> return Key(1003, number = 0, raw = up)
        }

        // 2) Funiculaire F1..Fn
        if (up.startsWith("F")) {
            val num = up.drop(1).toIntOrNull()
            if (num != null) return Key(2000, number = num, raw = up)
        }

        // 3) Tram T1..Tn
        if (up.startsWith("T")) {
            val num = up.drop(1).toIntOrNull()
            if (num != null) return Key(3000, number = num, raw = up)
        }

        // 4) Buses with letter prefix + number (C1, JD12, S3, etc.)
        // Regex: letters (at least 1) + number (at least 1) + optional letter suffix
        val regex = Regex("^([A-Z]+)(\\d+)([A-Z]*)$")
        val m = regex.matchEntire(up)
        if (m != null) {
            val prefix = m.groupValues[1]
            val num = m.groupValues[2].toIntOrNull() ?: Int.MAX_VALUE
            // Some adjustments to keep consistent order of frequent TCL families
            // Force sub-order for certain prefixes known to avoid, for example, JD passing before C if desired.
            // Here we're satisfied with alphabetical sort of prefix, which gives: C, JD, S, ...
            return Key(4000, subFamily = prefix, number = num, raw = up)
        }

        // 5) Pure numeric
        val pureNum = up.toIntOrNull()
        if (pureNum != null) {
            return Key(5000, number = pureNum, raw = up)
        }

        // 6) Fallback lexicographique
        return Key(9000, subFamily = up, number = Int.MAX_VALUE, raw = up)
    }

    return lines
        .filter { !it.equals("T36", ignoreCase = true) }
        .sortedWith(Comparator { a, b ->
        val ka = keyFor(a)
        val kb = keyFor(b)
        // Compare by family, then prefix/subFamily, then numeric part, finally raw label
        when {
            ka.family != kb.family -> ka.family - kb.family
            ka.subFamily != kb.subFamily -> ka.subFamily.compareTo(kb.subFamily)
            ka.number != kb.number -> ka.number - kb.number
            else -> ka.raw.compareTo(kb.raw)
        }
    })
}

/**
 * Bottom sheet affichant les informations d'une station
 * (nom de la station et toutes les lignes qui la desservent)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationBottomSheet(
    stationInfo: StationInfo?,
    sheetState: SheetState?,
    onDismiss: () -> Unit,
    onLineClick: (String) -> Unit = {}
) {
    val context = LocalContext.current
    
    if (stationInfo != null) {
        val content = @Composable {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
            ) {
                // Nom de la station
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stationInfo.nom,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        modifier = Modifier.weight(1f)
                    )
                    
                    // PMR icon if station is accessible
                    if (stationInfo.isPmr) {
                        Icon(
                            imageVector = Icons.Default.Accessible,
                            contentDescription = "Station accessible PMR",
                            tint = Color(0xFF2563EB),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Scrollable list of lines (sorted)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    val sortedLines = sortLines(stationInfo.lignes)
                    sortedLines.forEachIndexed { index, ligne ->
                        LineListItem(
                            lineName = ligne,
                            onClick = { onLineClick(ligne) }
                        )
                        
                        // Divider between lines, except after last
                        if (index < sortedLines.size - 1) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = Gray200
                            )
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

/**
 * List item for a transport line with next departure times
 */
@Composable
private fun LineListItem(
    lineName: String,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val drawableName = getDrawableNameForLine(lineName)
    val resourceId = context.resources.getIdentifier(drawableName, "drawable", context.packageName)
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Line icon on the left
        if (resourceId != 0) {
            Image(
                painter = painterResource(id = resourceId),
                contentDescription = "Ligne $lineName",
                modifier = Modifier.size(60.dp)
            )
        } else {
            // Fallback if icon doesn't exist
            Text(
                text = lineName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Gray700
            )
        }
        
        // Chevron on the right
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Voir la ligne $lineName",
            tint = Gray700,
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * Convertit un nom de ligne en nom de drawable
 * (même logique que BusIconHelper mais dupliquée ici pour éviter les dépendances circulaires)
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
