package com.pelotcl.app.generic.data.models

import androidx.compose.runtime.Immutable
import com.google.gson.annotations.SerializedName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

/**
 * Represents a traffic alert for a line of transport
 * Generic model that can be used across different transport networks
 */
@Immutable
@Serializable
data class TrafficAlert(
    @SerializedName("cause")
    @SerialName("cause")
    val cause: String,

    @SerializedName("startDate")
    @SerialName("startDate")
    @JsonNames("debut")
    val startDate: String,

    @SerializedName("endDate")
    @SerialName("endDate")
    @JsonNames("fin")
    val endDate: String,

    @SerializedName("lastUpdate")
    @SerialName("lastUpdate")
    @JsonNames("last_update_fme")
    val lastUpdate: String,

    @SerializedName("lineCode")
    @SerialName("lineCode")
    @JsonNames("ligne_cli")
    val lineCode: String,

    @SerializedName("lineName")
    @SerialName("lineName")
    @JsonNames("ligne_com")
    val lineName: String,

    @SerializedName("objectList")
    @SerialName("objectList")
    @JsonNames("listeobjet")
    val objectList: String,

    @SerializedName("message")
    @SerialName("message")
    val message: String,

    @SerializedName("mode")
    @SerialName("mode")
    val mode: String,

    @SerializedName("alertNumber")
    @SerialName("alertNumber")
    @JsonNames("n")
    val alertNumber: Int,

    @SerializedName("severityLevel")
    @SerialName("severityLevel")
    @JsonNames("niveauseverite")
    val severityLevel: Int,

    @SerializedName("title")
    @SerialName("title")
    @JsonNames("titre")
    val title: String,

    @SerializedName("alertType")
    @SerialName("alertType")
    @JsonNames("type")
    val alertType: String,

    @SerializedName("objectType")
    @SerialName("objectType")
    @JsonNames("typeobjet")
    val objectType: String,

    @SerializedName("severityType")
    @SerialName("severityType")
    @JsonNames("typeseverite")
    val severityType: String
)