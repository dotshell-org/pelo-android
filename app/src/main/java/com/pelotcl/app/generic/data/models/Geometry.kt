package com.pelotcl.app.generic.data.models

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * Represents a MultiLineString geometry
 */
@Immutable
@Serializable
data class Geometry(
    val type: String = "MultiLineString",
    val coordinates: List<List<List<Double>>> = emptyList()
)