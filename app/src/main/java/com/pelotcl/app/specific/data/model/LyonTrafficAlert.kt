package com.pelotcl.app.specific.data.model

import androidx.compose.runtime.Immutable
import com.google.gson.annotations.SerializedName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Lyon-specific traffic alert model that matches the Lyon API response structure
 * Contains Lyon-specific field names like ligne_cli, ligne_com, etc.
 */
@Immutable
@Serializable
data class LyonTrafficAlert(
    @SerializedName("cause")
    @SerialName("cause")
    val cause: String,

    @SerializedName("debut")
    @SerialName("debut")
    val startDate: String,

    @SerializedName("fin")
    @SerialName("fin")
    val endDate: String,

    @SerializedName("last_update_fme")
    @SerialName("last_update_fme")
    val lastUpdate: String,

    @SerializedName("ligne_cli")
    @SerialName("ligne_cli")
    val lineCode: String,

    @SerializedName("ligne_com")
    @SerialName("ligne_com")
    val lineName: String,

    @SerializedName("listeobjet")
    @SerialName("listeobjet")
    val objectList: String,

    @SerializedName("message")
    @SerialName("message")
    val message: String,

    @SerializedName("mode")
    @SerialName("mode")
    val mode: String,

    @SerializedName("n")
    @SerialName("n")
    val alertNumber: Int,

    @SerializedName("niveauseverite")
    @SerialName("niveauseverite")
    val severityLevel: Int,

    @SerializedName("titre")
    @SerialName("titre")
    val title: String,

    @SerializedName("type")
    @SerialName("type")
    val alertType: String,

    @SerializedName("typeobjet")
    @SerialName("typeobjet")
    val objectType: String,

    @SerializedName("typeseverite")
    @SerialName("typeseverite")
    val severityType: String
)