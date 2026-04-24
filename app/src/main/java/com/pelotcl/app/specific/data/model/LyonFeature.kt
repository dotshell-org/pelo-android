package com.pelotcl.app.specific.data.model

import androidx.compose.runtime.Immutable
import com.google.gson.annotations.SerializedName
import kotlinx.serialization.Serializable

/**
 * Lyon-specific Feature model that matches the Lyon API response structure
 */
@Immutable
@Serializable
data class LyonFeature(
    val type: String = "Feature",
    val id: String = "",
    val geometry: LyonGeometry = LyonGeometry(),
    @SerializedName("geometry_name")
    val geometryName: String? = null,
    val properties: LyonTransportLineProperties = LyonTransportLineProperties(),
    val bbox: List<Double>? = null
)