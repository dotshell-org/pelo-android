package com.pelotcl.app.data.model

import com.google.gson.annotations.SerializedName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Représente une collection GeoJSON de features
 */
@Serializable
data class FeatureCollection(
    val type: String,
    val features: List<Feature>,
    val totalFeatures: Int? = null,
    val numberMatched: Int? = null,
    val numberReturned: Int? = null,
    val timeStamp: String? = null,
    val crs: CRS? = null,
    val bbox: List<Double>? = null
)

/**
 * Représente une feature GeoJSON avec géométrie et propriétés
 */
@Serializable
data class Feature(
    val type: String,
    val id: String,
    val geometry: Geometry,
    @SerializedName("geometry_name")
    @SerialName("geometry_name")
    val geometryName: String? = null,
    val properties: TransportLineProperties,
    val bbox: List<Double>? = null
)

/**
 * Représente une géométrie de type MultiLineString
 */
@Serializable
data class Geometry(
    val type: String,
    val coordinates: List<List<List<Double>>>
)

/**
 * Propriétés d'une ligne de transport TCL
 */
@Serializable
data class TransportLineProperties(
    val ligne: String,
    @SerializedName("code_trace")
    @SerialName("code_trace")
    val codeTrace: String,
    @SerializedName("code_ligne")
    @SerialName("code_ligne")
    val codeLigne: String,
    @SerializedName("type_trace")
    @SerialName("type_trace")
    val typeTrace: String,
    @SerializedName("nom_trace")
    @SerialName("nom_trace")
    val nomTrace: String,
    val sens: String,
    val origine: String,
    val destination: String,
    @SerializedName("nom_origine")
    @SerialName("nom_origine")
    val nomOrigine: String,
    @SerializedName("nom_destination")
    @SerialName("nom_destination")
    val nomDestination: String,
    @SerializedName("famille_transport")
    @SerialName("famille_transport")
    val familleTransport: String,
    @SerializedName("date_debut")
    @SerialName("date_debut")
    val dateDebut: String,
    @SerializedName("date_fin")
    @SerialName("date_fin")
    val dateFin: String? = null,
    @SerializedName("code_type_ligne")
    @SerialName("code_type_ligne")
    val codeTypeLigne: String,
    @SerializedName("nom_type_ligne")
    @SerialName("nom_type_ligne")
    val nomTypeLigne: String,
    val pmr: Boolean,
    @SerializedName("code_tri_ligne")
    @SerialName("code_tri_ligne")
    val codeTriLigne: String,
    @SerializedName("nom_version")
    @SerialName("nom_version")
    val nomVersion: String,
    @SerializedName("last_update")
    @SerialName("last_update")
    val lastUpdate: String,
    @SerializedName("last_update_fme")
    @SerialName("last_update_fme")
    val lastUpdateFme: String,
    val gid: Int,
    val couleur: String
)

/**
 * Système de coordonnées
 */
@Serializable
data class CRS(
    val type: String,
    val properties: CRSProperties
)

@Serializable
data class CRSProperties(
    val name: String
)
