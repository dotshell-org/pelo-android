package com.pelotcl.app.specific.data.model

import androidx.compose.runtime.Immutable
import com.google.gson.annotations.SerializedName
import kotlinx.serialization.Serializable

/**
 * Lyon-specific stop feature
 */
@Immutable
@Serializable
data class LyonStopFeature(
    val type: String = "Feature",
    val id: String = "",
    val geometry: LyonStopGeometry = LyonStopGeometry(),
    @SerializedName("geometry_name")
    val geometryName: String? = null,
    val properties: LyonStopProperties = LyonStopProperties(),
    val bbox: List<Double>? = null
)