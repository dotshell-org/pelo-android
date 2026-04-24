package com.pelotcl.app.generic.data.models

import androidx.compose.runtime.Immutable
import com.google.gson.annotations.SerializedName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Properties of a stop
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