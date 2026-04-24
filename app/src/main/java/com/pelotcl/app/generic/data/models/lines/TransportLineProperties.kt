package com.pelotcl.app.generic.data.models.lines

import androidx.compose.runtime.Immutable
import com.google.gson.annotations.SerializedName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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