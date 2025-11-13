package com.pelotcl.app.data.model

import com.google.gson.annotations.SerializedName

/**
 * Représente une collection GeoJSON d'arrêts de transport
 */
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
data class StopFeature(
    val type: String,
    val id: String,
    val geometry: StopGeometry,
    @SerializedName("geometry_name")
    val geometryName: String? = null,
    val properties: StopProperties,
    val bbox: List<Double>? = null
)

/**
 * Géométrie d'un arrêt (Point)
 */
data class StopGeometry(
    val type: String, // "Point"
    val coordinates: List<Double> // [longitude, latitude]
)

/**
 * Propriétés d'un arrêt TCL
 */
data class StopProperties(
    val id: Int,
    val nom: String,
    val desserte: String, // Lignes qui desservent cet arrêt (ex: "C:A", "M:A:B")
    val pmr: Boolean, // Accessible aux personnes à mobilité réduite
    val ascenseur: Boolean,
    val escalator: Boolean,
    val gid: Int,
    @SerializedName("last_update")
    val lastUpdate: String,
    @SerializedName("last_update_fme")
    val lastUpdateFme: String,
    val adresse: String,
    @SerializedName("localise_face_a_adresse")
    val localiseFaceAAdresse: Boolean,
    val commune: String,
    val insee: String,
    val zone: String
)