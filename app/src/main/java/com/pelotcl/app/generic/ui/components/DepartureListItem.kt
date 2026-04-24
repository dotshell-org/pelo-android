package com.pelotcl.app.generic.ui.components

import android.annotation.SuppressLint
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pelotcl.app.generic.ui.theme.Gray700
import com.pelotcl.app.generic.ui.theme.PrimaryColor
import com.pelotcl.app.generic.utils.BusIconHelper
import com.pelotcl.app.generic.utils.DepartureManager

/**
 * List item for a line departure in all-lines station mode.
 */
@SuppressLint("ComposeBackingChainViolation")
@Suppress("DiscouragedApi", "ComposeLocalCurrentInLambda")
@Composable
fun DepartureListItem(
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
                    color = DepartureManager().getDepartureColor(departureTime)
                )
                DepartureManager().formatRelativeDeparture(departureTime)?.let { relativeText ->
                    Text(
                        text = relativeText,
                        style = MaterialTheme.typography.bodySmall,
                        color = DepartureManager().getDepartureColor(departureTime)
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
