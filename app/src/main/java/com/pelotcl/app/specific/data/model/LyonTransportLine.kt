package com.pelotcl.app.specific.data.model

import androidx.compose.runtime.Immutable
import com.google.gson.annotations.SerializedName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Lyon-specific transport line properties that match the Lyon API response structure
 * Contains Lyon-specific field names and serialized names
 */
@Immutable
@Serializable
data class LyonTransportLineProperties(
    @SerializedName("ligne")
    val ligne: String = "",
    
    @SerializedName("code_trace")
    val codeTrace: String = "",

    @SerializedName("code_ligne")
    val codeLigne: String = "",

    @SerializedName("type_trace")
    val typeTrace: String? = null,

    @SerializedName("nom_trace")
    val nomTrace: String? = null,

    val sens: String? = null,

    val origine: String? = null,

    val destination: String? = null,

    @SerializedName("nom_origine")
    val nomOrigine: String? = null,

    @SerializedName("nom_destination")
    val nomDestination: String? = null,

    @SerializedName("famille_transport")
    val familleTransport: String = "",

    @SerializedName("date_debut")
    val dateDebut: String? = null,

    @SerializedName("date_fin")
    val dateFin: String? = null,

    @SerializedName("code_type_ligne")
    val codeTypeLigne: String? = null,

    @SerializedName("nom_type_ligne")
    val nomTypeLigne: String? = null,

    @SerializedName("code_tri_ligne")
    val codeTriLigne: String? = null,

    @SerializedName("nom_version")
    val nomVersion: String? = null,

    @SerializedName("last_update")
    val lastUpdate: String? = null,

    @SerializedName("last_update_fme")
    val lastUpdateFme: String? = null,

    val gid: Int = 0,

    val couleur: String? = null
)
