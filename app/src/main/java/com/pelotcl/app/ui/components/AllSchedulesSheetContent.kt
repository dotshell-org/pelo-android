package com.pelotcl.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pelotcl.app.ui.screens.AllSchedulesInfo
import com.pelotcl.app.ui.theme.Orange500
import com.pelotcl.app.ui.theme.Red500
import com.pelotcl.app.ui.theme.Gray700
import com.pelotcl.app.utils.BusIconHelper
import com.pelotcl.app.utils.LineColorHelper
import java.util.Calendar

private fun getLineColor(lineName: String): Color {
    // Harmonise avec LineColorHelper (TB → #eab308, etc.)
    return Color(LineColorHelper.getColorForLineString(lineName))
}

private fun getAllDayScheduleColor(hour: String, minute: String): Color {
    val hourInt = hour.toIntOrNull() ?: return Color.Black
    val minuteInt = minute.toIntOrNull() ?: return Color.Black
    if (hourInt !in 0..23 || minuteInt !in 0..59) return Color.Black

    val now = Calendar.getInstance()
    val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
    val scheduleMinutes = hourInt * 60 + minuteInt
    val diffMinutes = scheduleMinutes - nowMinutes

    return when {
        diffMinutes < 0 -> Color.Gray
        diffMinutes < 2 -> Red500
        diffMinutes < 15 -> Orange500
        else -> Color.Black
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AllSchedulesSheetContent(
    allSchedulesInfo: AllSchedulesInfo,
    lineInfo: LineInfo?,
    selectedDirection: Int,
    availableDirections: List<Int>,
    headsigns: Map<Int, String>,
    onDirectionChange: (Int) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    val groupedSchedules = remember(allSchedulesInfo.schedules) {
        allSchedulesInfo.schedules
            .map { it.split(":") }
            .filter { it.size == 2 }
            .groupBy({ it[0] }, { it[1] })
            .toSortedMap()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 480.dp)
            .padding(horizontal = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Gray700)
            }
            Spacer(modifier = Modifier.width(8.dp))

            val resourceId = BusIconHelper.getResourceIdForLine(context, allSchedulesInfo.lineName)
            if (resourceId != 0) {
                Image(painter = painterResource(id = resourceId), contentDescription = "Line ${allSchedulesInfo.lineName}", modifier = Modifier.size(50.dp))
            } else {
                Box(
                    modifier = Modifier.size(50.dp).clip(CircleShape).background(getLineColor(allSchedulesInfo.lineName)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = allSchedulesInfo.lineName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = lineInfo?.currentStationName ?: "",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (availableDirections.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                availableDirections.forEach { directionId ->
                    val headsign = headsigns[directionId] ?: "Direction ${directionId + 1}"
                    Button(
                        onClick = { onDirectionChange(directionId) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedDirection == directionId) getLineColor(allSchedulesInfo.lineName) else Color.LightGray,
                            contentColor = if (selectedDirection == directionId) Color.White else Color.DarkGray
                        ),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        Text(
                            text = headsign,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        val list = groupedSchedules.entries.toList()

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            itemsIndexed(list, key = { _, (hour, _) -> hour }) { index, (hour, minutesList) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "${hour}h",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = getLineColor(allSchedulesInfo.lineName),
                        modifier = Modifier.width(50.dp)
                    )

                    FlowRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        minutesList.forEach { minute ->
                            val minuteColor = getAllDayScheduleColor(hour, minute)
                            Text(
                                text = minute,
                                style = MaterialTheme.typography.bodyLarge,
                                color = minuteColor,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
                if (index < list.lastIndex) {
                    HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))
                } else {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}
