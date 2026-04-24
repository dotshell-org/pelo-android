package com.pelotcl.app.generic.utils.orphans

import android.content.Context
import android.graphics.Paint
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import com.pelotcl.app.R
import com.pelotcl.app.generic.data.models.ui.VehicleMarkerType
import org.maplibre.android.maps.Style

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
        val scale = minOf(maxSize.toFloat() / intrinsicWidth, maxSize.toFloat() / intrinsicHeight)
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
