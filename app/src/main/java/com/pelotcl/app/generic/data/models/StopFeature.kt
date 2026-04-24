package com.pelotcl.app.generic.data.models

import androidx.compose.runtime.Immutable
import com.google.gson.annotations.SerializedName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a transport stop (GeoJSON Feature)
 */
@Immutable
@Serializable
data class StopFeature(
    val type: String = "Feature",
    val id: String = "",
    val geometry: StopGeometry = StopGeometry(),
    @SerializedName("geometry_name")
    @SerialName("geometry_name")
    val geometryName: String? = null,
    val properties: StopProperties = StopProperties(),
    val bbox: List<Double>? = null
)