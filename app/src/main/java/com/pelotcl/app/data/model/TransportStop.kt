package com.pelotcl.app.data.model

import androidx.compose.runtime.Immutable
import com.google.gson.annotations.SerializedName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Représente une collection GeoJSON d'arrêts de transport
 */
@Immutable
@Serializable
data class StopCollection(
    val type: String,
    val features: List<StopFeature>,
    val totalFeatures: Int? = null,
    val numberMatched: Int? = null,
    val numberReturned: Int? = null,
    val timeStamp: String? = null,
    val crs: CRS? = null,
    val bbox: List<Double>? = null
)

/**
 * Représente un arrêt de transport (Feature GeoJSON)
 */
@Immutable
@Serializable
data class StopFeature(
    val type: String,
    val id: String,
    val geometry: StopGeometry,
    @SerializedName("geometry_name")
    @SerialName("geometry_name")
    val geometryName: String? = null,
    val properties: StopProperties,
    val bbox: List<Double>? = null
)

/**
 * Géométrie d'un arrêt (Point)
 */
@Immutable
@Serializable
data class StopGeometry(
    val type: String, // "Point"
    val coordinates: List<Double> // [longitude, latitude]
)

/**
 * Propriétés d'un arrêt TCL
 */
@Immutable
@Serializable
data class StopProperties(
    val id: Int,
    val nom: String,
    val desserte: String, // Lines serving this stop (e.g. "C:A", "M:A:B")
    val pmr: Boolean, // Accessible for people with reduced mobility
    val ascenseur: Boolean,
    val escalator: Boolean,
    val gid: Int,
    @SerializedName("last_update")
    @SerialName("last_update")
    val lastUpdate: String?,
    @SerializedName("last_update_fme")
    @SerialName("last_update_fme")
    val lastUpdateFme: String?,
    val adresse: String?,
    @SerializedName("localise_face_a_adresse")
    @SerialName("localise_face_a_adresse")
    val localiseFaceAAdresse: Boolean,
    val commune: String?,
    val insee: String?,
    val zone: String?
)