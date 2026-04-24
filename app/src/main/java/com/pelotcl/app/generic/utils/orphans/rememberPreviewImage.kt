package com.pelotcl.app.generic.utils.orphans

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun rememberPreviewImage(@DrawableRes imageRes: Int): ImageBitmap? {
    val context = LocalContext.current
    val targetSizePx = with(LocalDensity.current) { 60.dp.roundToPx() }
    val imageState by produceState<ImageBitmap?>(
        initialValue = null,
        key1 = context,
        key2 = imageRes,
        key3 = targetSizePx
    ) {
        value = withContext(Dispatchers.IO) {
            decodeSampledBitmapFromResource(context.resources, imageRes, targetSizePx, targetSizePx)
                ?.asImageBitmap()
        }
    }
    return imageState
}
