package com.pelotcl.app.ui.components

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pelotcl.app.data.repository.JourneyResult
import com.pelotcl.app.ui.screens.CompactJourneyCard
import com.pelotcl.app.ui.screens.DatePickerDialog
import com.pelotcl.app.ui.screens.JourneyDetailsSheetContent
import com.pelotcl.app.ui.screens.SelectedStop
import com.pelotcl.app.ui.screens.TimeMode
import com.pelotcl.app.ui.screens.TimePickerDialog
import com.pelotcl.app.ui.screens.TimeSelectionRow
import com.pelotcl.app.ui.viewmodel.TransportViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.Calendar

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun InlineItinerarySheetContent(
    viewModel: TransportViewModel,
    departureStop: SelectedStop?,
    arrivalStop: SelectedStop?,
    maxHeight: Dp,
    nearbyDepartureStops: List<String> = emptyList(),
    onDepartureFallbackSelected: (SelectedStop) -> Unit = {},
    onJourneysChanged: (List<JourneyResult>) -> Unit = {},
    onSelectedJourneyChanged: (JourneyResult?) -> Unit = {},
    onClose: () -> Unit
) {
    val raptorRepository = viewModel.raptorRepository

    var timeMode by remember { mutableStateOf(TimeMode.DEPARTURE) }
    var selectedTimeSeconds by remember { mutableStateOf<Int?>(null) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    var journeys by remember { mutableStateOf<List<JourneyResult>>(emptyList()) }
    var selectedJourney by remember { mutableStateOf<JourneyResult?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    suspend fun recalc() {
        val departureStopIds = departureStop?.stopIds ?: emptyList()
        val arrivalStopIds = arrivalStop?.stopIds ?: emptyList()
        if (departureStopIds.isEmpty() || arrivalStopIds.isEmpty()) {
            journeys = emptyList()
            selectedJourney = null
            errorText = null
            return
        }
        isLoading = true
        errorText = null
        selectedJourney = null
        try {
            val date = selectedDate ?: LocalDate.now()
            journeys = withContext(Dispatchers.IO) {
                if (timeMode == TimeMode.ARRIVAL) {
                    raptorRepository.getOptimizedPathsArriveBy(
                        originStopIds = departureStopIds,
                        destinationStopIds = arrivalStopIds,
                        arrivalTimeSeconds = selectedTimeSeconds ?: defaultArrivalSeconds(),
                        searchWindowMinutes = 120,
                        date = date,
                        blockedRouteNames = emptySet()
                    )
                } else {
                    raptorRepository.getOptimizedPaths(
                        originStopIds = departureStopIds,
                        destinationStopIds = arrivalStopIds,
                        departureTimeSeconds = selectedTimeSeconds,
                        date = date,
                        blockedRouteNames = emptySet()
                    )
                }
            }
            if (journeys.isEmpty() && nearbyDepartureStops.isNotEmpty() && timeMode == TimeMode.DEPARTURE) {
                for (fallbackName in nearbyDepartureStops) {
                    if (fallbackName.equals(departureStop?.name, ignoreCase = true)) continue

                    val fallbackIds = raptorRepository.searchStopsByName(fallbackName).map { it.id }
                    if (fallbackIds.isEmpty()) continue

                    val fallbackJourneys = withContext(Dispatchers.IO) {
                        raptorRepository.getOptimizedPaths(
                            originStopIds = fallbackIds,
                            destinationStopIds = arrivalStopIds,
                            departureTimeSeconds = selectedTimeSeconds,
                            date = date,
                            blockedRouteNames = emptySet()
                        )
                    }

                    if (fallbackJourneys.isNotEmpty()) {
                        journeys = fallbackJourneys
                        onDepartureFallbackSelected(
                            SelectedStop(
                                name = fallbackName,
                                stopIds = fallbackIds
                            )
                        )
                        break
                    }
                }
            }

            if (journeys.isEmpty()) {
                errorText = "Aucun itineraire trouve"
            }
        } catch (_: Exception) {
            errorText = "Erreur lors du calcul d'itineraire"
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(departureStop, arrivalStop, timeMode, selectedTimeSeconds, selectedDate) {
        recalc()
    }

    LaunchedEffect(journeys) {
        onJourneysChanged(journeys)
    }

    LaunchedEffect(selectedJourney) {
        onSelectedJourneyChanged(selectedJourney)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selectedJourney != null) {
                    IconButton(onClick = { selectedJourney = null }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour",
                            tint = Color.Black
                        )
                    }
                }
                Text(
                    "Itineraire",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Fermer", tint = Color.Black)
            }
        }

        if (selectedJourney == null) {
            TimeSelectionRow(
                timeMode = timeMode,
                selectedTimeSeconds = selectedTimeSeconds,
                selectedDate = selectedDate,
                onTimeModeChange = { timeMode = it },
                onTimeClick = { showTimePicker = true },
                onDateClick = { showDatePicker = true },
                onClearDateTime = {
                    selectedTimeSeconds = null
                    selectedDate = null
                },
                useLightColors = true
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (isLoading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(color = Color.Black)
                }
            } else if (errorText != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = errorText!!, color = Color.Black)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
                ) {
                    items(journeys) { journey ->
                        CompactJourneyCard(
                            journey = journey,
                            onClick = { selectedJourney = journey },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            useLightColors = true
                        )
                    }
                }
            }
        } else {
            JourneyDetailsSheetContent(
                journey = selectedJourney!!,
                isExpanded = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                useLightColors = true
            )
        }

        if (showTimePicker) {
            TimePickerDialog(
                initialTimeSeconds = selectedTimeSeconds ?: defaultArrivalSeconds(),
                onTimeSelected = { seconds ->
                    selectedTimeSeconds = seconds
                },
                onDismiss = { showTimePicker = false }
            )
        }

        if (showDatePicker) {
            DatePickerDialog(
                initialDate = selectedDate ?: LocalDate.now(),
                onDateSelected = { date ->
                    selectedDate = date
                },
                onDismiss = { showDatePicker = false }
            )
        }
    }
}

private fun defaultArrivalSeconds(): Int {
    val cal = Calendar.getInstance()
    return (cal.get(Calendar.HOUR_OF_DAY) + 1) * 3600 + cal.get(Calendar.MINUTE) * 60
}
