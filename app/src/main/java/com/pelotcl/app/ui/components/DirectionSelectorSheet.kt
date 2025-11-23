package com.pelotcl.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pelotcl.app.ui.theme.Gray700
import com.pelotcl.app.utils.BusIconHelper
import com.pelotcl.app.utils.LineColorHelper

/**
 * Représente une direction disponible pour un segment
 */
data class AvailableDirection(
    val lineName: String,
    val directionName: String,
    val directionId: Int
)

/**
 * Composant pour sélectionner une direction avec icônes de lignes et chevrons
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectionSelectorSheet(
    availableDirections: List<AvailableDirection>,
    onDirectionSelected: (AvailableDirection) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Sélectionner la direction",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth()
            ) {
                availableDirections.forEach { direction ->
                    DirectionOption(
                        direction = direction,
                        onClick = {
                            onDirectionSelected(direction)
                            onDismiss()
                        },
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DirectionOption(
    direction: AvailableDirection,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lineColor = Color(LineColorHelper.getColorForLineString(direction.lineName))
    val drawableName = BusIconHelper.getDrawableNameForLineName(direction.lineName)
    val resourceId = context.resources.getIdentifier(drawableName, "drawable", context.packageName)
    
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = lineColor.copy(alpha = 0.1f),
        border = androidx.compose.foundation.BorderStroke(1.dp, lineColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Icône de la ligne
                if (resourceId != 0) {
                    Image(
                        painter = painterResource(id = resourceId),
                        contentDescription = "Ligne ${direction.lineName}",
                        modifier = Modifier.size(40.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(lineColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = direction.lineName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
                
                // Chevron
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = lineColor,
                    modifier = Modifier.size(24.dp)
                )
                
                // Nom de la direction
                Text(
                    text = direction.directionName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black
                )
            }
        }
    }
}

