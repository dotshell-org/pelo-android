package com.pelotcl.app.generic.ui.screens.plan

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pelotcl.app.R
import com.pelotcl.app.generic.data.network.mapstyle.MapStyleData
import com.pelotcl.app.generic.utils.BitmapUtils.rememberPreviewImage

@Composable
fun MapStylePreviewTile(
    style: MapStyleData,
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    val imageRes = when (style.key) {
        "positron" -> R.drawable.visu_positron
        "dark_matter" -> R.drawable.visu_dark_matter
        "bright" -> R.drawable.visu_osm_bright
        "liberty" -> R.drawable.visu_liberty
        "satellite" -> R.drawable.visu_satellite
        else -> R.drawable.visu_positron
    }
    val previewBitmap = rememberPreviewImage(imageRes)
    val alpha = if (isEnabled) 1f else 0.4f
    Box(
        modifier = Modifier
            .size(60.dp)
            .border(
                width = 0.5.dp,
                color = Color.Gray,
                shape = RoundedCornerShape(12.dp)
            )
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = isEnabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (previewBitmap != null) {
            Image(
                bitmap = previewBitmap,
                contentDescription = stringResource(R.string.map_style_preview),
                modifier = Modifier.fillMaxSize(),
                alpha = alpha
            )
        } else {
            // Safety fallback: avoid blank tile if bitmap decode ever fails.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFE5E7EB).copy(alpha = alpha))
            )
        }
    }
}
