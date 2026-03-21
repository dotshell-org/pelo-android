package com.pelotcl.app.ui.screens.plan

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pelotcl.app.data.repository.itinerary.JourneyLeg
import com.pelotcl.app.data.repository.itinerary.JourneyResult
import com.pelotcl.app.ui.theme.Gray700
import com.pelotcl.app.ui.theme.Red500
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.window.Dialog
import com.pelotcl.app.utils.BusIconHelper
import com.pelotcl.app.utils.LineColorHelper
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Enum for time selection mode: departure or arrival
 */
enum class TimeMode {
    DEPARTURE,  // Search by departure time (default)
    ARRIVAL     // Search by arrival time ("I need to be there by...")
}

/**
 * Represents a selected stop for the itinerary
 */
@Immutable
data class SelectedStop(
    val name: String,
    val stopIds: List<Int>
)

/**
 * Compact journey card showing key information in a condensed format
 * Similar to the bottom sheet header in map view
 */
@Composable
fun CompactJourneyCard(
    journey: JourneyResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    useLightColors: Boolean = false
) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val primaryTextColor = if (useLightColors) Color.Black else Color.White
    val secondaryTextColor =
        if (useLightColors) Color(0xFF4B5563) else Color.White.copy(alpha = 0.7f)
    val chipBackgroundColor =
        if (useLightColors) Color(0xFFF3F4F6) else Color.White.copy(alpha = 0.15f)
    val baseBackgroundColor =
        if (useLightColors) Color(0xFFF9FAFB) else Color.White.copy(alpha = 0.1f)
    val backgroundColor by animateColorAsState(
        targetValue = if (isPressed) {
            if (useLightColors) Color(0xFFF3F4F6) else Color.White.copy(alpha = 0.16f)
        } else {
            baseBackgroundColor
        },
        label = "compact_journey_press"
    )

    val formattedDuration = remember(journey.durationMinutes) {
        if (journey.durationMinutes < 60) {
            "${journey.durationMinutes} min"
        } else {
            "${journey.durationMinutes / 60}h${
                (journey.durationMinutes % 60).toString().padStart(2, '0')
            }"
        }
    }

    Card(
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = journey.formatDepartureTime(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = primaryTextColor
                    )
                    Text(
                        text = " -> ",
                        style = MaterialTheme.typography.titleMedium,
                        color = secondaryTextColor
                    )
                    Text(
                        text = journey.formatArrivalTime(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = primaryTextColor
                    )
                }

                Box(
                    modifier = Modifier
                        .background(
                            color = chipBackgroundColor,
                            shape = RoundedCornerShape(8.dp)
                        )
                ) {
                    Text(
                        text = formattedDuration,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = primaryTextColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val nonWalkingLegs = journey.legs.filterNot { it.isWalking }

                nonWalkingLegs.forEachIndexed { index, leg ->
                    val resourceId =
                        BusIconHelper.getResourceIdForLine(context, leg.routeName ?: "")

                    if (resourceId != 0) {
                        Image(
                            painter = painterResource(id = resourceId),
                            contentDescription = null,
                            modifier = Modifier.size(28.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
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
                                text = (leg.routeName ?: "?").take(3),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    if (index < nonWalkingLegs.size - 1) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = if (useLightColors) Color(0xFF6B7280) else Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun JourneyLegItem(
    leg: JourneyLeg,
    isFirst: Boolean,
    isLast: Boolean,
    useLightColors: Boolean
) {

    val context = LocalContext.current
    val lineColor = if (leg.isWalking) Gray700 else Color(
        LineColorHelper.getColorForLineString(
            leg.routeName ?: ""
        )
    )
    val primaryTextColor = if (useLightColors) Color.Black else Color.White
    val secondaryTextColor =
        if (useLightColors) Color(0xFF4B5563) else Color.White.copy(alpha = 0.7f)
    val tertiaryTextColor =
        if (useLightColors) Color(0xFF6B7280) else Color.White.copy(alpha = 0.6f)

    // State for expanding intermediate stops
    var isExpanded by remember { mutableStateOf(false) }
    val hasIntermediateStops = !leg.isWalking && leg.intermediateStops.isNotEmpty()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .width(40.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxHeight()
            ) {
                if (!isFirst) {
                    Box(modifier = Modifier
                        .width(3.dp)
                        .height(8.dp)
                        .background(lineColor))
                }

                if (leg.isWalking) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                        contentDescription = null,
                        tint = Gray700,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    val resourceId =
                        BusIconHelper.getResourceIdForLine(context, leg.routeName ?: "")

                    if (resourceId != 0) {
                        Image(
                            painter = painterResource(id = resourceId),
                            contentDescription = null,
                            modifier = Modifier.size(30.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(lineColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = leg.routeName ?: "?",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .weight(1f)
                        .background(lineColor)
                )

                if (isLast) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .border(3.dp, lineColor, CircleShape)
                            .background(if (useLightColors) Color.White else Color.Black)
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp)
        ) {
            Text(
                text = leg.fromStopName,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium,
                color = primaryTextColor
            )
            Text(
                text = leg.formatDepartureTime(),
                color = secondaryTextColor,
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (leg.isWalking) "Marche ${leg.durationMinutes} min" else "Direction ${leg.direction ?: leg.toStopName}",
                color = secondaryTextColor,
                style = MaterialTheme.typography.bodySmall
            )

            // Expandable intermediate stops section
            if (hasIntermediateStops) {
                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier
                        .clickable { isExpanded = !isExpanded }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = if (isExpanded) "Réduire" else "Développer",
                        tint = secondaryTextColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "${leg.intermediateStops.size} arrêt${if (leg.intermediateStops.size > 1) "s" else ""}",
                        color = secondaryTextColor,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }

                // Expanded intermediate stops list
                if (isExpanded) {
                    Column(
                        modifier = Modifier.padding(start = 20.dp, top = 4.dp)
                    ) {
                        leg.intermediateStops.forEach { stop ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stop.stopName,
                                    color = tertiaryTextColor,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = stop.formatArrivalTime(),
                                    color = if (useLightColors) Color(0xFF9CA3AF) else Color.White.copy(
                                        alpha = 0.5f
                                    ),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }

            if (isLast) {
                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = leg.toStopName,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                    color = primaryTextColor
                )
                Text(
                    text = leg.formatArrivalTime(),
                    color = secondaryTextColor,
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/**
 * Sheet content for journey details shown in BottomSheetScaffold
 * Shows a compact horizontal view of the journey with line icons
 * Expands to show full itinerary details when sheet is expanded
 */
@Composable
fun JourneyDetailsSheetContent(
    journey: JourneyResult,
    isExpanded: Boolean,
    modifier: Modifier = Modifier,
    useLightColors: Boolean = false,
    scrollAllContent: Boolean = false
) {
    val context = LocalContext.current
    val primaryTextColor = if (useLightColors) Color.Black else Color.White
    val secondaryTextColor =
        if (useLightColors) Color(0xFF4B5563) else Color.White.copy(alpha = 0.7f)
    val chipBackgroundColor =
        if (useLightColors) Color(0xFFF3F4F6) else Color.White.copy(alpha = 0.15f)

    // Memoize formatted duration to avoid recalculation on recomposition
    val formattedDuration by remember(journey.durationMinutes) {
        derivedStateOf {
            if (journey.durationMinutes < 60) {
                "${journey.durationMinutes} min"
            } else {
                "${journey.durationMinutes / 60}h${
                    (journey.durationMinutes % 60).toString().padStart(2, '0')
                }min"
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(1f)
            .padding(horizontal = 16.dp)
    ) {
        val headerAndLegsModifier = if (scrollAllContent) {
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(
                    state = rememberScrollState(),
                    enabled = isExpanded
                )
        } else {
            Modifier.fillMaxWidth()
        }

        Column(modifier = headerAndLegsModifier) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = journey.formatDepartureTime(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = primaryTextColor
                    )
                    Text(
                        text = " -> ",
                        style = MaterialTheme.typography.titleMedium,
                        color = secondaryTextColor
                    )
                    Text(
                        text = journey.formatArrivalTime(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = primaryTextColor
                    )
                }

                Box(
                    modifier = Modifier
                        .background(
                            color = chipBackgroundColor,
                            shape = RoundedCornerShape(8.dp)
                        )
                ) {
                    Text(
                        text = formattedDuration,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = primaryTextColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clipToBounds()
            ) {
                Row(
                    modifier = Modifier.wrapContentWidth(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val nonWalkingLegs = journey.legs.filterNot { it.isWalking }

                    nonWalkingLegs.forEachIndexed { index, leg ->
                        val resourceId =
                            BusIconHelper.getResourceIdForLine(context, leg.routeName ?: "")

                        if (resourceId != 0) {
                            Image(
                                painter = painterResource(id = resourceId),
                                contentDescription = null,
                                modifier = Modifier.size(32.dp)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
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
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        if (index < nonWalkingLegs.size - 1) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = if (useLightColors) Color(0xFF6B7280) else Color.White.copy(
                                    alpha = 0.5f
                                ),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            HorizontalDivider(
                color = if (useLightColors) Color(0xFFE5E7EB) else Color.White.copy(alpha = 0.2f),
                thickness = 1.dp
            )
            Spacer(modifier = Modifier.height(32.dp))

            if (scrollAllContent) {
                journey.legs.forEachIndexed { index, leg ->
                    key("${leg.fromStopId}_${leg.departureTime}") {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            JourneyLegItem(
                                leg = leg,
                                isFirst = index == 0,
                                isLast = index == journey.legs.size - 1,
                                useLightColors = useLightColors
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(80.dp))
            }
        }

        if (!scrollAllContent) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(
                        state = rememberScrollState(),
                        enabled = isExpanded
                    )
            ) {
                journey.legs.forEachIndexed { index, leg ->
                    key("${leg.fromStopId}_${leg.departureTime}") {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            JourneyLegItem(
                                leg = leg,
                                isFirst = index == 0,
                                isLast = index == journey.legs.size - 1,
                                useLightColors = useLightColors
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

/**
 * Row for selecting departure/arrival time mode and picking a date/time
 */
@Composable
fun TimeSelectionRow(
    timeMode: TimeMode,
    selectedTimeSeconds: Int?,
    selectedDate: LocalDate?,
    onTimeModeChange: (TimeMode) -> Unit,
    onTimeClick: () -> Unit,
    onDateClick: () -> Unit,
    onClearDateTime: () -> Unit,
    useLightColors: Boolean = false
) {
    val containerColor = if (useLightColors) Color(0xFFF9FAFB) else Color.White.copy(alpha = 0.1f)
    val selectedModeBackground =
        if (useLightColors) Color(0xFFE5E7EB) else Color.White.copy(alpha = 0.2f)
    val pickerBackground =
        if (useLightColors) Color(0xFFF3F4F6) else Color.White.copy(alpha = 0.15f)
    val primaryTextColor = if (useLightColors) Color.Black else Color.White
    val secondaryTextColor =
        if (useLightColors) Color(0xFF4B5563) else Color.White.copy(alpha = 0.6f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = containerColor,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(12.dp)
    ) {
        // First row: Mode toggle and clear button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Mode toggle buttons
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Departure mode button
                Box(
                    modifier = Modifier
                        .background(
                            color = if (timeMode == TimeMode.DEPARTURE) selectedModeBackground else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { onTimeModeChange(TimeMode.DEPARTURE) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Départ",
                        color = if (timeMode == TimeMode.DEPARTURE) primaryTextColor else secondaryTextColor,
                        fontWeight = if (timeMode == TimeMode.DEPARTURE) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Arrival mode button
                Box(
                    modifier = Modifier
                        .background(
                            color = if (timeMode == TimeMode.ARRIVAL) selectedModeBackground else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { onTimeModeChange(TimeMode.ARRIVAL) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Arrivée",
                        color = if (timeMode == TimeMode.ARRIVAL) primaryTextColor else secondaryTextColor,
                        fontWeight = if (timeMode == TimeMode.ARRIVAL) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 14.sp
                    )
                }
            }

            // Clear button (only show if date or time is set)
            if (selectedTimeSeconds != null || selectedDate != null) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Réinitialiser",
                    tint = secondaryTextColor,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { onClearDateTime() }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Second row: Date and time pickers
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Date picker
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .background(
                        color = pickerBackground,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { onDateClick() }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarToday,
                    contentDescription = null,
                    tint = primaryTextColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatDateDisplay(selectedDate),
                    color = primaryTextColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Time picker
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .background(
                        color = pickerBackground,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { onTimeClick() }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = primaryTextColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (selectedTimeSeconds != null) {
                        formatTimeSeconds(selectedTimeSeconds)
                    } else {
                        "Maintenant"
                    },
                    color = primaryTextColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Simple time picker dialog using wheel-style pickers
 * Minutes are rounded to 5-minute intervals
 */
@Composable
fun TimePickerDialog(
    initialTimeSeconds: Int,
    onTimeSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val initialHour = (initialTimeSeconds / 3600) % 24
    // Round initial minute to nearest 5 minutes
    val initialMinute = ((initialTimeSeconds % 3600) / 60 / 5) * 5

    var selectedHour by remember { mutableIntStateOf(initialHour) }
    var selectedMinute by remember { mutableIntStateOf(initialMinute) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Time display
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    // Hour picker
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = { selectedHour = (selectedHour + 1) % 24 }) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = "Augmenter l'heure",
                                tint = Color.White
                            )
                        }
                        Text(
                            text = String.format(Locale.ROOT, "%02d", selectedHour),
                            color = Color.White,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = {
                            selectedHour = if (selectedHour == 0) 23 else selectedHour - 1
                        }) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Diminuer l'heure",
                                tint = Color.White
                            )
                        }
                    }

                    Text(
                        text = ":",
                        color = Color.White,
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    // Minute picker
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = { selectedMinute = (selectedMinute + 5) % 60 }) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = "Augmenter les minutes",
                                tint = Color.White
                            )
                        }
                        Text(
                            text = String.format(Locale.ROOT, "%02d", selectedMinute),
                            color = Color.White,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = {
                            selectedMinute = if (selectedMinute < 5) 55 else selectedMinute - 5
                        }) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Diminuer les minutes",
                                tint = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = "Annuler",
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier
                            .clickable { onDismiss() }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(
                                color = Red500,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                onTimeSelected(selectedHour * 3600 + selectedMinute * 60)
                                onDismiss()
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Confirmer",
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

/**
 * Format time in seconds to HH:mm string
 */
private fun formatTimeSeconds(seconds: Int): String {
    val hours = (seconds / 3600) % 24
    val minutes = (seconds % 3600) / 60
    return String.format(Locale.ROOT, "%02d:%02d", hours, minutes)
}

/**
 * Format date for display
 * Shows "Aujourd'hui" for today, "Demain" for tomorrow, or the date
 */
private fun formatDateDisplay(date: LocalDate?): String {
    if (date == null) return "Aujourd'hui"

    val today = LocalDate.now()
    val tomorrow = today.plusDays(1)

    return when (date) {
        today -> "Aujourd'hui"
        tomorrow -> "Demain"
        else -> {
            val formatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.FRENCH)
            date.format(formatter).replaceFirstChar { it.uppercase() }
        }
    }
}

/**
 * Date picker dialog for selecting a journey date
 * Allows selecting dates with month navigation
 */
@Composable
fun DatePickerDialog(
    initialDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedDate by remember { mutableStateOf(initialDate) }
    val today = LocalDate.now()

    // Current displayed month
    var displayedMonth by remember {
        mutableStateOf(initialDate.withDayOfMonth(1))
    }

    val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRENCH)
    DateTimeFormatter.ofPattern("E", Locale.FRENCH)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Month navigation
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Previous month button
                    IconButton(
                        onClick = {
                            val prevMonth = displayedMonth.minusMonths(1)
                            if (!prevMonth.plusMonths(1).isBefore(today.withDayOfMonth(1))) {
                                displayedMonth = prevMonth
                            }
                        },
                        enabled = !displayedMonth.isBefore(today.withDayOfMonth(1)) &&
                                !displayedMonth.isEqual(today.withDayOfMonth(1))
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "Mois précédent",
                            tint = if (!displayedMonth.isBefore(today.withDayOfMonth(1)) &&
                                !displayedMonth.isEqual(today.withDayOfMonth(1))
                            )
                                Color.White else Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.rotate(-90f)
                        )
                    }

                    Text(
                        text = displayedMonth.format(monthFormatter)
                            .replaceFirstChar { it.uppercase() },
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )

                    // Next month button
                    IconButton(onClick = { displayedMonth = displayedMonth.plusMonths(1) }) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "Mois suivant",
                            tint = Color.White,
                            modifier = Modifier.rotate(90f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Days of week header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf("L", "M", "M", "J", "V", "S", "D").forEach { day ->
                        Text(
                            text = day,
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.width(36.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Calendar grid
                val firstDayOfMonth = displayedMonth
                val lastDayOfMonth = displayedMonth.plusMonths(1).minusDays(1)
                // Monday = 1, Sunday = 7, we want Monday as first column (index 0)
                val firstDayOfWeek = (firstDayOfMonth.dayOfWeek.value - 1) // 0 = Monday
                val daysInMonth = lastDayOfMonth.dayOfMonth

                // Generate calendar rows (max 6 weeks)
                val calendarDays = mutableListOf<LocalDate?>()
                // Add empty cells for days before the first of month
                repeat(firstDayOfWeek) { calendarDays.add(null) }
                // Add all days of the month
                for (day in 1..daysInMonth) {
                    calendarDays.add(displayedMonth.withDayOfMonth(day))
                }
                // Fill remaining cells to complete the last row
                while (calendarDays.size % 7 != 0) {
                    calendarDays.add(null)
                }

                calendarDays.chunked(7).forEach { week ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        week.forEach { date ->
                            if (date != null) {
                                val isSelected = date == selectedDate
                                val isToday = date == today
                                val isSelectable = !date.isBefore(today)

                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(
                                            color = when {
                                                isSelected -> Red500
                                                isToday -> Color.White.copy(alpha = 0.15f)
                                                else -> Color.Transparent
                                            },
                                            shape = CircleShape
                                        )
                                        .then(
                                            if (isSelectable) Modifier.clickable {
                                                selectedDate = date
                                            }
                                            else Modifier
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = date.dayOfMonth.toString(),
                                        color = when {
                                            !isSelectable -> Color.White.copy(alpha = 0.3f)
                                            isSelected -> Color.White
                                            else -> Color.White.copy(alpha = 0.9f)
                                        },
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            } else {
                                // Empty cell
                                Box(modifier = Modifier.size(36.dp))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = "Annuler",
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier
                            .clickable { onDismiss() }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(
                                color = Red500,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                onDateSelected(selectedDate)
                                onDismiss()
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Confirmer",
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
