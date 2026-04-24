package com.pelotcl.app.generic.utils.orphans

import androidx.compose.runtime.Composable
import com.pelotcl.app.generic.data.network.mapstyle.MapStyleData

@Composable
fun mapStyleLabel(style: MapStyleData): String {
    return when (style.key) {
        "positron" -> "Clair"
        "dark_matter" -> "Sombre"
        "bright" -> "OSM"
        "liberty" -> "3D"
        "satellite" -> "Satellite"
        else -> style.displayName
    }
}