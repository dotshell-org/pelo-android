package com.pelotcl.app.generic.data.model

import androidx.compose.runtime.Immutable
import com.google.gson.annotations.SerializedName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a GeoJSON FeatureCollection
 */
@Immutable
@Serializable
data class FeatureCollection(
    val type: String = "FeatureCollection",
    val features: List<Feature> = emptyList(),
    val totalFeatures: Int? = null,
    val numberMatched: Int? = null,
    val numberReturned: Int? = null,
    val timeStamp: String? = null,
    val crs: CRS? = null,
    val bbox: List<Double>? = null
)

/**
 * Represents a GeoJSON Feature with geometry and properties
 */
@Immutable
@Serializable
data class Feature(
    val type: String = "Feature",
    val id: String = "",
    val geometry: Geometry = Geometry(),
    @SerializedName("geometry_name")
    @SerialName("geometry_name")
    val geometryName: String? = null,
    val properties: TransportLineProperties = TransportLineProperties(),
    val bbox: List<Double>? = null
)

/**
 * Represents a MultiLineString geometry
 */
@Immutable
@Serializable
data class Geometry(
    val type: String = "MultiLineString",
    val coordinates: List<List<List<Double>>> = emptyList()
)

/**
 * Represents transport line properties
 */
@Immutable
@Serializable
data class TransportLineProperties(
    val lineName: String = "",
    @SerializedName("line_code")
    @SerialName("line_code")
    val traceCode: String = "",
    @SerializedName("line_id")
    @SerialName("line_id")
    val lineId: String = "",
    @SerializedName("trace_type")
    @SerialName("trace_type")
    val traceType: String = "",
    @SerializedName("trace_name")
    @SerialName("trace_name")
    val traceName: String = "",
    val direction: String? = null,
    val origin: String = "",
    val destination: String = "",
    @SerializedName("origin_name")
    @SerialName("origin_name")
    val originName: String = "",
    @SerializedName("destination_name")
    @SerialName("destination_name")
    val destinationName: String = "",
    @SerializedName("transport_type")
    @SerialName("transport_type")
    val transportType: String = "",
    @SerializedName("start_date")
    @SerialName("start_date")
    val startDate: String = "",
    @SerializedName("end_date")
    @SerialName("end_date")
    val endDate: String? = null,
    @SerializedName("line_type_code")
    @SerialName("line_type_code")
    val lineTypeCode: String = "",
    @SerializedName("line_type_name")
    @SerialName("line_type_name")
    val lineTypeName: String = "",
    @SerializedName("sort_code")
    @SerialName("sort_code")
    val sortCode: String = "",
    @SerializedName("version_name")
    @SerialName("version_name")
    val versionName: String = "",
    @SerializedName("last_updated")
    @SerialName("last_updated")
    val lastUpdate: String = "",
    @SerializedName("last_updated_fme")
    @SerialName("last_updated_fme")
    val lastUpdateFme: String = "",
    val gid: Int = 0,
    val color: String? = null
)

/**
 * Coordinates System
 */
@Immutable
@Serializable
data class CRS(
    val type: String = "",
    val properties: CRSProperties = CRSProperties()
)

@Immutable
@Serializable
data class CRSProperties(
    val name: String = ""
)
