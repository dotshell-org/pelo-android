package com.pelotcl.app.generic.data.model

import androidx.compose.runtime.Immutable
import com.google.gson.annotations.SerializedName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a GeoJSON collection of transport stops
 */
@Immutable
@Serializable
data class StopCollection(
    val type: String = "FeatureCollection",
    val features: List<StopFeature> = emptyList(),
    val totalFeatures: Int? = null,
    val numberMatched: Int? = null,
    val numberReturned: Int? = null,
    val timeStamp: String? = null,
    val crs: CRS? = null,
    val bbox: List<Double>? = null
)

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

/**
 * Geometry of a stop (Point)
 */
@Immutable
@Serializable
data class StopGeometry(
    val type: String = "Point",
    val coordinates: List<Double> = emptyList()
)

/**
 * Properties of a TCL stop
 */
@Immutable
@Serializable
data class StopProperties(
    val id: Int = 0,
    val nom: String = "",
    val desserte: String = "", // Lines serving this stop (e.g. "C:A", "M:A:B")
    val ascenseur: Boolean = false,
    val escalator: Boolean = false,
    val gid: Int = 0,
    @SerializedName("last_update")
    @SerialName("last_update")
    val lastUpdate: String? = null,
    @SerializedName("last_update_fme")
    @SerialName("last_update_fme")
    val lastUpdateFme: String? = null,
    val adresse: String? = null,
    @SerializedName("localise_face_a_adresse")
    @SerialName("localise_face_a_adresse")
    val localiseFaceAAdresse: Boolean = false,
    val commune: String? = null,
    val insee: String? = null,
    val zone: String? = null
)
