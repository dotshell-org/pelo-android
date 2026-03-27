@file:Suppress("VariableNeverRead")

package com.pelotcl.app.generic.ui.screens.plan

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pelotcl.app.generic.ui.theme.SecondaryColor
import com.pelotcl.app.utils.transport.BusIconHelper
import com.pelotcl.app.utils.transport.LineColorHelper
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import com.pelotcl.app.generic.data.repository.itinerary.ItineraryPreferencesRepository
import com.pelotcl.app.generic.data.repository.itinerary.JourneyResult
import com.pelotcl.app.generic.ui.theme.PrimaryColor
import com.pelotcl.app.generic.ui.viewmodel.TransportViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.Calendar

private const val MAX_ITINERARY_STOP_IDS_PER_SIDE = 4
private const val MAX_ITINERARY_FALLBACK_STOPS = 2

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
    val context = LocalContext.current
    
    // Load user preferences for route filtering
    val itineraryPrefsRepo = remember { ItineraryPreferencesRepository(context) }
    val jdLinesEnabled = remember { itineraryPrefsRepo.isJdLinesEnabled() }
    val rxLineEnabled = remember { itineraryPrefsRepo.isRxLineEnabled() }
    
    // Build set of blocked route names based on user preferences
    // For JD lines, we need to block all possible JD line numbers (JD1-JD999)
    // since the Raptor library does exact matching, not prefix matching
    val blockedRouteNames = remember(jdLinesEnabled, rxLineEnabled) {
        buildSet {
            if (!jdLinesEnabled) {
                // Add all possible JD line patterns
                for (i in 1..999) {
                    add("JD$i")
                }
            }
            if (!rxLineEnabled) add("RX")
        }
    }

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
        val departureStopIds =
            departureStop?.stopIds?.distinct()?.take(MAX_ITINERARY_STOP_IDS_PER_SIDE) ?: emptyList()
        val arrivalStopIds =
            arrivalStop?.stopIds?.distinct()?.take(MAX_ITINERARY_STOP_IDS_PER_SIDE) ?: emptyList()
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
            val today = LocalDate.now()
            val date = selectedDate ?: today
            journeys = withContext(Dispatchers.IO) {
                if (timeMode == TimeMode.ARRIVAL) {
                    raptorRepository.getOptimizedPathsArriveBy(
                        originStopIds = departureStopIds,
                        destinationStopIds = arrivalStopIds,
                        arrivalTimeSeconds = selectedTimeSeconds ?: defaultArrivalSeconds(),
                        searchWindowMinutes = 120,
                        date = date,
                        blockedRouteNames = blockedRouteNames
                    )
                } else {
                    raptorRepository.getOptimizedPaths(
                        originStopIds = departureStopIds,
                        destinationStopIds = arrivalStopIds,
                        departureTimeSeconds = selectedTimeSeconds,
                        date = date,
                        blockedRouteNames = blockedRouteNames
                    )
                }
            }
            if (journeys.isEmpty() && nearbyDepartureStops.isNotEmpty() && timeMode == TimeMode.DEPARTURE) {
                for (fallbackName in nearbyDepartureStops.take(MAX_ITINERARY_FALLBACK_STOPS)) {
                    if (fallbackName.equals(departureStop?.name, ignoreCase = true)) continue

                    val fallbackIds = raptorRepository.resolveStopIdsByName(
                        fallbackName,
                        maxIds = MAX_ITINERARY_STOP_IDS_PER_SIDE
                    )
                    if (fallbackIds.isEmpty()) continue

                    val fallbackJourneys = withContext(Dispatchers.IO) {
                        raptorRepository.getOptimizedPaths(
                            originStopIds = fallbackIds,
                            destinationStopIds = arrivalStopIds,
                            departureTimeSeconds = selectedTimeSeconds,
                            date = date,
                            blockedRouteNames = blockedRouteNames
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

            if (journeys.isEmpty() && timeMode == TimeMode.DEPARTURE && date == today) {
                val hasServiceEarlierToday = withContext(Dispatchers.IO) {
                    raptorRepository.getOptimizedPaths(
                        originStopIds = departureStopIds,
                        destinationStopIds = arrivalStopIds,
                        departureTimeSeconds = 0,
                        date = today,
                        blockedRouteNames = blockedRouteNames
                    ).isNotEmpty()
                }

                if (hasServiceEarlierToday) {
                    val tomorrow = today.plusDays(1)
                    journeys = withContext(Dispatchers.IO) {
                        raptorRepository.getOptimizedPaths(
                            originStopIds = departureStopIds,
                            destinationStopIds = arrivalStopIds,
                            departureTimeSeconds = 0,
                            date = tomorrow,
                            blockedRouteNames = blockedRouteNames
                        )
                    }
                    selectedDate = tomorrow
                    selectedTimeSeconds = 0
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

    val showSearchBars = selectedJourney == null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .padding(horizontal = 16.dp)
    ) {
        // Show date/time selection only when no journey is selected
        if (showSearchBars) {
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
        }

        // Header row with close button and line icons (when journey selected)
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Show extra large line icons on the left when a journey is selected
            if (selectedJourney != null) {
                val context = LocalContext.current
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    val nonWalkingLegs = selectedJourney!!.legs.filterNot { it.isWalking }
                    
                    nonWalkingLegs.forEachIndexed { index, leg ->
                        val resourceId = BusIconHelper.getResourceIdForLine(context, leg.routeName ?: "")
                        
                        if (resourceId != 0) {
                            Image(
                                painter = painterResource(id = resourceId),
                                contentDescription = null,
                                modifier = Modifier.size(32.dp)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Color(
                                            LineColorHelper.getColorForLineString(
                                                leg.routeName ?: ""
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = leg.routeName ?: "?",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SecondaryColor
                                )
                            }
                        }
                        
                        if (index < nonWalkingLegs.size - 1) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = PrimaryColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            // Add space between line icons and journey details
            Spacer(modifier = Modifier.height(24.dp))

            // Only show close button when a journey is selected (acts as back button)
            if (selectedJourney != null) {
                IconButton(onClick = { selectedJourney = null }) {
                    Icon(Icons.Default.Close, contentDescription = "Retour", tint = PrimaryColor)
                }
            }
        }

        if (selectedJourney == null) {
            if (isLoading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(color = PrimaryColor)
                }
            } else if (errorText != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = errorText!!, color = PrimaryColor)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 24.dp)
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
                useLightColors = true,
                scrollAllContent = true
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
