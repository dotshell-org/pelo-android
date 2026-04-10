package com.pelotcl.app.generic.ui.components

import android.annotation.SuppressLint
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import com.pelotcl.app.generic.ui.viewmodel.TransportViewModel.StopDeparturePreview
import kotlinx.coroutines.flow.map
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pelotcl.app.R
import com.pelotcl.app.generic.ui.theme.Gray200
import com.pelotcl.app.generic.ui.theme.Gray700
import com.pelotcl.app.generic.ui.theme.Green500
import com.pelotcl.app.generic.ui.theme.Orange500
import com.pelotcl.app.generic.ui.theme.PrimaryColor
import com.pelotcl.app.generic.ui.theme.AccentColor
import com.pelotcl.app.generic.ui.theme.SecondaryColor
import com.pelotcl.app.generic.ui.viewmodel.TransportViewModel
import com.pelotcl.app.utils.transport.BusIconHelper
import java.util.Calendar

/**
 * Station data for display in the bottom sheet
 */
data class StationInfo(
    val nom: String,
    val lignes: List<String>, // List of line names (ex: ["A", "D", "F1"])
    val desserte: String = "", // Complete service string for reference
    val stopIds: List<Int> = emptyList()
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
private fun parseDepartureToMinutes(rawTime: String): Int? {
    val clean = if (rawTime.count { it == ':' } >= 2) rawTime.substringBeforeLast(":") else rawTime
    val parts = clean.split(":")
    if (parts.size < 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    if (minute !in 0..59) return null
    return (hour * 60) + minute
}

private fun getDepartureColor(departureTime: String): Color {
    val now = Calendar.getInstance()
    val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
    val departureMinutes = parseDepartureToMinutes(departureTime) ?: return Green500
    val diff = departureMinutes - nowMinutes

    if (diff < 0) return Green500

    return when (diff) {
        in 0..1 -> AccentColor
        in 2..14 -> Orange500
        else -> Green500
    }
}

private fun formatRelativeDeparture(departureTime: String): String? {
    val now = Calendar.getInstance()
    val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
    val departureMinutes = parseDepartureToMinutes(departureTime) ?: return null
    val diff = departureMinutes - nowMinutes

    if (diff < 0) return null
    if (diff == 0) return "< 1 min"
    if (diff < 60) return "dans ${diff}min"

    val hours = diff / 60
    val minutes = diff % 60
    return "dans ${hours}h${minutes.toString().padStart(2, '0')}min"
}

private fun minutesUntilDeparture(rawTime: String): Int {
    val now = Calendar.getInstance()
    val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
    val departureMinutes = parseDepartureToMinutes(rawTime) ?: return Int.MAX_VALUE
    return if (departureMinutes >= nowMinutes) {
        departureMinutes - nowMinutes
    } else {
        // Treat past times as next-day departures to keep ordering stable.
        (24 * 60 - nowMinutes) + departureMinutes
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationBottomSheet(
    stationInfo: StationInfo?,
    sheetState: SheetState?,
    onDismiss: () -> Unit,
    viewModel: TransportViewModel? = null,
    onLineClick: (String) -> Unit = {},
    onDepartureClick: (lineName: String, directionId: Int, departureTime: String) -> Unit = { lineName, _, _ ->
        onLineClick(lineName)
    },
    isFavoriteStop: Boolean = false,
    onToggleFavoriteStop: () -> Unit = {},
    onAddFavoriteClick: (String) -> Unit = {},
    onItineraryClick: () -> Unit = {},
    onReportAlertClick: (String, List<String>) -> Unit = { _, _ -> }
) {
    val titleInset = 20.dp
    val departuresInset = 20.dp
    val actionsInset = 8.dp

    if (stationInfo != null) {
        android.util.Log.i("StationBottomSheet", "Rendering bottom sheet for station: ${stationInfo.nom}, lines: ${stationInfo.lignes}")
        val allStopLines by produceState(
            initialValue = stationInfo.lignes,
            key1 = stationInfo.nom,
            key2 = stationInfo.lignes,
            key3 = viewModel
        ) {
            if (viewModel == null) {
                value = stationInfo.lignes
            } else {
                viewModel.getConnectionsForStop(stationInfo.nom, "")
                    .map { connections ->
                        (connections.map { it.lineName } + stationInfo.lignes)
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .distinctBy { it.uppercase() }
                    }.collect { value = it }
            }
        }

        val departuresState = produceState<List<StopDeparturePreview>?>(
            initialValue = null,
            key1 = stationInfo.nom,
            key2 = allStopLines,
            key3 = viewModel
        ) {
            value = viewModel?.getNextDeparturesForStop(
                stopName = stationInfo.nom,
                lines = allStopLines
            )
                ?: emptyList()
        }

        val departures = departuresState.value

        val content = @Composable {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
            ) {
                // Nom de la station
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = titleInset),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stationInfo.nom,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryColor,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Spacer(modifier = Modifier.size(actionsInset))

                    Button(
                        onClick = onItineraryClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryColor,
                            contentColor = SecondaryColor
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Directions,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(text = "Itinéraire", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            onReportAlertClick(stationInfo.nom, stationInfo.lignes)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isFavoriteStop) Color(0xFFD1D5DB) else Color(
                                0xFFE5E7EB
                            ),
                            contentColor = Color(0xFF374151)
                        )
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.add_triangle_24px),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = "Signaler une alerte",
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Button(
                        onClick = { onAddFavoriteClick(stationInfo.nom) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isFavoriteStop) Color(0xFFD1D5DB) else Color(
                                0xFFE5E7EB
                            ),
                            contentColor = Color(0xFF374151)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FavoriteBorder,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = "Ajouter aux favoris",
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.size(actionsInset))
                }

                // Virtualized list of departures (sorted)
                val lineOrder = remember(allStopLines) {
                    sortLines(allStopLines)
                        .mapIndexed { index, line -> line.uppercase() to index }
                        .toMap()
                }

                val sortedDepartures = remember(departures, lineOrder) {
                    departures?.sortedWith(
                        compareBy<StopDeparturePreview> {
                            minutesUntilDeparture(it.nextDeparture)
                        }
                            .thenBy { lineOrder[it.lineName.uppercase()] ?: Int.MAX_VALUE }
                            .thenBy { it.directionId }
                            .thenBy { parseDepartureToMinutes(it.nextDeparture) ?: Int.MAX_VALUE }
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = departuresInset)
                ) {
                    when {
                        departures == null -> {
                            item(key = "loading") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 24.dp),
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    CircularProgressIndicator(color = PrimaryColor)
                                }
                            }
                        }
                        departures.isEmpty() -> {
                            item(key = "empty") {
                                Text(
                                    text = "Aucun horaire disponible pour cet arrêt",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Gray700,
                                    modifier = Modifier.padding(vertical = 12.dp)
                                )
                            }
                        }
                        else -> {
                            itemsIndexed(
                                sortedDepartures!!,
                                key = { _, dep -> "${dep.lineName}-${dep.directionId}-${dep.nextDeparture}" }
                            ) { index, departure ->
                                DepartureListItem(
                                    lineName = departure.lineName,
                                    directionName = departure.directionName,
                                    departureTime = departure.nextDeparture,
                                    onClick = {
                                        onDepartureClick(
                                            departure.lineName,
                                            departure.directionId,
                                            departure.nextDeparture
                                        )
                                    }
                                )

                                if (index < sortedDepartures.size - 1) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 4.dp),
                                        color = Gray200
                                    )
                                }
                            }

                            item(key = "bottom_spacer") {
                                Spacer(modifier = Modifier.height(20.dp))
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
                containerColor = SecondaryColor
            ) {
                content()
            }
        } else {
            content()
        }
    }
}

/**
 * List item for a line departure in all-lines station mode.
 */
@SuppressLint("ComposeBackingChainViolation")
@Suppress("DiscouragedApi", "ComposeLocalCurrentInLambda")
@Composable
private fun DepartureListItem(
    lineName: String,
    directionName: String,
    departureTime: String,
    onClick: () -> Unit
) {
    @Suppress("ComposeLocalContext")
    val context = LocalContext.current
    val resourceId = remember(lineName) {
        BusIconHelper.getResourceIdForLine(context, lineName)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (resourceId != 0) {
                Image(
                    painter = painterResource(id = resourceId),
                    contentDescription = "Ligne $lineName",
                    modifier = Modifier.size(52.dp)
                )
            } else {
                Text(
                    text = lineName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Gray700
                )
            }

            Spacer(modifier = Modifier.size(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = directionName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PrimaryColor,
                    maxLines = 1
                )
                Text(
                    text = departureTime,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = getDepartureColor(departureTime)
                )
                formatRelativeDeparture(departureTime)?.let { relativeText ->
                    Text(
                        text = relativeText,
                        style = MaterialTheme.typography.bodySmall,
                        color = getDepartureColor(departureTime)
                    )
                }
            }
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Voir le détail de la ligne $lineName",
            tint = Gray700,
            modifier = Modifier.size(24.dp)
        )
    }
}
