package com.pelotcl.app.generic.ui.components.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pelotcl.app.generic.ui.theme.AccentColor
import com.pelotcl.app.generic.ui.theme.PrimaryColor
import com.pelotcl.app.generic.ui.theme.SecondaryColor

/**
 * Icon selection button
 */
@Composable
fun IconSelectionButton(
    iconName: String,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val icon = favoriteIcon(iconName)

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .background(if (isSelected) Color(0x1A000000) else Color.Transparent)
            .border(1.dp, if (isSelected) PrimaryColor else Color.Gray, RoundedCornerShape(24.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(PrimaryColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = iconName,
                    tint = SecondaryColor,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = PrimaryColor,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
            if (isSelected) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(AccentColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = SecondaryColor,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}
