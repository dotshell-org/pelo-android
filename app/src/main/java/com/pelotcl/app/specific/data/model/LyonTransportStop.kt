package com.pelotcl.app.specific.data.model

import androidx.compose.runtime.Immutable
import com.google.gson.annotations.SerializedName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Lyon-specific transport stop properties
 */
@Immutable
@Serializable
data class LyonStopProperties(
    @SerializedName("gid")
    val gid: Int = 0,
    
    @SerializedName("id_arret")
    val stopId: String = "",
    
    @SerializedName("nom_arret")
    val stopName: String = "",
    
    @SerializedName("code_arret")
    val stopCode: String = "",
    
    @SerializedName("type_arret")
    val stopType: String = "",
    
    @SerializedName("x")
    val x: Double = 0.0,
    
    @SerializedName("y")
    val y: Double = 0.0,
    
    @SerializedName("longitude")
    val longitude: Double = 0.0,
    
    @SerializedName("latitude")
    val latitude: Double = 0.0,
    
    @SerializedName("commune")
    val city: String = "",
    
    @SerializedName("code_insee")
    val inseeCode: String = "",

    // Field returned by Lyon's WFS stops layer (used to know which line(s) serve each stop).
    // Format example: "C:A", "M:A:B", etc.
    @SerializedName("desserte")
    val desserte: String? = null,

    // Some WFS layers expose the same data under a slightly different key.
    @SerializedName("desserte_arret")
    val desserteArret: String? = null
)
