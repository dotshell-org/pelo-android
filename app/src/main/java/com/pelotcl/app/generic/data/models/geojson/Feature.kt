package com.pelotcl.app.generic.data.models.geojson

import androidx.compose.runtime.Immutable
import com.google.gson.annotations.SerializedName
import com.pelotcl.app.generic.data.models.lines.MultiLineStringGeometry
import com.pelotcl.app.generic.data.models.lines.TransportLineProperties
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a GeoJSON Feature with geometry and properties
 */
@Immutable
@Serializable
data class Feature(
    val type: String = "Feature",
    val id: String = "",
    val multiLineStringGeometry: MultiLineStringGeometry = MultiLineStringGeometry(),
    @SerializedName("geometry_name")
    @SerialName("geometry_name")
    val geometryName: String? = null,
    val properties: TransportLineProperties = TransportLineProperties(),
    val bbox: List<Double>? = null
) {
    val geometry: MultiLineStringGeometry
        get() = multiLineStringGeometry
}
