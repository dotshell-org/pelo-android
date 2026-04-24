package com.pelotcl.app.generic.utils.orphans

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes

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
