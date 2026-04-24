package com.pelotcl.app.specific.utils.orphans

import com.pelotcl.app.generic.data.models.ui.VehicleMarkerType

fun getVehicleMarkerType(lineName: String): VehicleMarkerType {
    val upperName = lineName.uppercase()
    return when {
        upperName.startsWith("TB") -> VehicleMarkerType.BUS
        upperName.startsWith("T") -> VehicleMarkerType.TRAM
        else -> VehicleMarkerType.BUS
    }
}
