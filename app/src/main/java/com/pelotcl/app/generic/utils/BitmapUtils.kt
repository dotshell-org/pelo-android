package com.pelotcl.app.generic.utils

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import com.pelotcl.app.R
import com.pelotcl.app.generic.data.models.ui.VehicleMarkerType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.maplibre.android.maps.Style

object BitmapUtils {

    fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while (halfHeight / inSampleSize >= reqHeight &&
                halfWidth / inSampleSize >= reqWidth
            ) {
                inSampleSize *= 2
            }
        }

        return inSampleSize.coerceAtLeast(1)
    }

    fun decodeSampledBitmapFromResource(
        resources: Resources,
        @DrawableRes resourceId: Int,
        reqWidth: Int,
        reqHeight: Int
    ): Bitmap? {
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeResource(resources, resourceId, bounds)
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds, reqWidth, reqHeight)
            inJustDecodeBounds = false
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeResource(resources, resourceId, decodeOptions)
    }

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
                decodeSampledBitmapFromResource(
                    context.resources,
                    imageRes,
                    targetSizePx,
                    targetSizePx
                )?.asImageBitmap()
            }
        }
        return imageState
    }

    fun ensureVehicleMarkerImage(
        mapStyle: Style,
        context: Context,
        iconName: String,
        color: Int,
        markerType: VehicleMarkerType,
        size: Int
    ) {
        val vehiclePaintCache = HashMap<Int, Paint>(8)

        if (mapStyle.getImage(iconName) != null) return

        val bitmap = createBitmap(size, size)
        val canvas = android.graphics.Canvas(bitmap)

        val circlePaint = vehiclePaintCache.getOrPut(color) {
            Paint().apply {
                this.color = color
                isAntiAlias = true
                style = Paint.Style.FILL
            }
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, circlePaint)

        fun drawCenteredDrawable(drawable: Drawable, maxSize: Int) {
            val intrinsicWidth = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else maxSize
            val intrinsicHeight =
                if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else maxSize
            val scale =
                minOf(maxSize.toFloat() / intrinsicWidth, maxSize.toFloat() / intrinsicHeight)
            val drawWidth = (intrinsicWidth * scale).toInt()
            val drawHeight = (intrinsicHeight * scale).toInt()
            val left = (size - drawWidth) / 2
            val top = (size - drawHeight) / 2
            drawable.setBounds(left, top, left + drawWidth, top + drawHeight)
            drawable.draw(canvas)
        }

        when (markerType) {
            VehicleMarkerType.BUS -> {
                val busDrawable = ContextCompat.getDrawable(context, R.drawable.ic_bus_vehicle)
                busDrawable?.let { drawable ->
                    drawCenteredDrawable(drawable, (size * 0.65f).toInt())
                }
            }

            VehicleMarkerType.TRAM -> {
                val tramDrawable = ContextCompat.getDrawable(context, R.drawable.ic_tramway_vehicle)
                tramDrawable?.let { drawable ->
                    drawCenteredDrawable(drawable, (size * 0.65f).toInt())
                }
            }
        }

        mapStyle.addImage(iconName, bitmap)
    }
}
