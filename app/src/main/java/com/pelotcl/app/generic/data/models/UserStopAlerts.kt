package com.pelotcl.app.generic.data.models

import androidx.compose.runtime.Immutable
import com.google.gson.annotations.SerializedName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a single user stop alert with karma status
 * Used for identifying problematic stops that should be avoided in route planning
 */
@Immutable
@Serializable
data class UserStopAlert(
    @SerializedName("id")
    @SerialName("id")
    val id: String,

    @SerializedName("stopId")
    @SerialName("stopId")
    val stopId: String,

    @SerializedName("type")
    @SerialName("type")
    val type: String, // e.g., "crowding", "incident", etc.

    @SerializedName("karma")
    @SerialName("karma")
    val karma: Int,

    @SerializedName("createdAt")
    @SerialName("createdAt")
    val createdAt: String
)
