package com.pelotcl.app.generic.ui.screens.plan

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pelotcl.app.generic.ui.theme.SecondaryColor
import com.pelotcl.app.generic.utils.BusIconHelper
import com.pelotcl.app.specific.utils.LineColorHelper

@Composable
fun NavigationLineIcon(
    lineName: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    val context = LocalContext.current
    val iconRes = BusIconHelper.getResourceIdForLine(context, lineName)
    val fallbackColor = Color(LineColorHelper.getColorForLineString(lineName))

    if (iconRes != 0) {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            modifier = modifier.size(size)
        )
    } else {
        Box(
            modifier = modifier
                .size((size - 2.dp).coerceAtLeast(20.dp))
                .clip(CircleShape)
                .background(fallbackColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = lineName.ifBlank { "?" }.take(3),
                color = SecondaryColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
