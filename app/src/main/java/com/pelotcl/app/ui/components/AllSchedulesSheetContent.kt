package com.pelotcl.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pelotcl.app.ui.screens.AllSchedulesInfo
import com.pelotcl.app.ui.theme.Gray700
import com.pelotcl.app.utils.BusIconHelper

private fun getLineColor(lineName: String): Color {
    return when (lineName.uppercase()) {
        "A" -> Color(0xFFEC4899)
        "B" -> Color(0xFF3B82F6)
        "C" -> Color(0xFFF59E0B)
        "D" -> Color(0xFF22C55E)
        "F1", "F2" -> Color(0xFF84CC16)
        "NAV1" -> Color(0xFF14b8a6)
        else -> when {
            lineName.uppercase().startsWith("NAV") -> Color(0xFF14b8a6)
            lineName.uppercase().startsWith("T") -> Color(0xFFA855F7)
            else -> Color(0xFFEF4444)
        }
    }
}

@Composable
fun AllSchedulesSheetContent(
    allSchedulesInfo: AllSchedulesInfo,
    lineInfo: LineInfo?,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Gray700)
            }
            Spacer(modifier = Modifier.width(8.dp))

            val drawableName = BusIconHelper.getDrawableNameForLineName(allSchedulesInfo.lineName)
            val resourceId = context.resources.getIdentifier(drawableName, "drawable", context.packageName)
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

        Spacer(modifier = Modifier.height(36.dp))

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(allSchedulesInfo.schedules) { schedule ->
                Text(
                    text = schedule,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = Color.Black
                )
                HorizontalDivider()
            }
        }
    }
}
