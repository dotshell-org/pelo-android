package com.pelotcl.app.specific.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class LyonCRSProperties(
    val name: String = ""
)