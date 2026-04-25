package com.pelotcl.app.specific.utils

import com.pelotcl.app.generic.data.models.ui.VehicleMarkerType

object LineClassificationUtils {

    fun isNavigoneLine(lineName: String): Boolean {
        val upperName = lineName.trim().uppercase()
        return upperName.startsWith("NAVI") || LineNamingUtils.canonicalLineName(upperName) == "NAV1"
    }

    fun isMetroTramOrFunicular(lineName: String): Boolean {
        val upperName = lineName.uppercase()
        return when {
            upperName in setOf("A", "B", "C", "D") -> true
            upperName in setOf("F1", "F2") -> true
            isNavigoneLine(upperName) -> true
            upperName.startsWith("T") -> true
            upperName == "RX" -> true
            else -> false
        }
    }

    fun isStrongLine(line: String): Boolean {
        val upperLine = line.uppercase()
        return when {
            upperLine in setOf("A", "B", "C", "D") -> true
            upperLine in setOf("F1", "F2") -> true
            upperLine.startsWith("NAVI") -> true
            upperLine.startsWith("T") -> true
            upperLine == "RX" -> true
            else -> false
        }
    }

    fun isTemporaryBus(lineName: String): Boolean {
        return !isMetroTramOrFunicular(lineName)
    }

    fun isLiveTrackableLine(lineName: String): Boolean {
        val upperName = lineName.uppercase()
        return when {
            upperName in setOf("A", "B", "C", "D") -> false
            upperName in setOf("F1", "F2") -> false
            isNavigoneLine(upperName) -> false
            upperName == "RX" -> false
            upperName.startsWith("T") -> true
            else -> true
        }
    }

    fun getModeIconForLine(lineName: String): String? {
        val upperName = lineName.uppercase()
        return when {
            isMetroTramOrFunicular(lineName) -> null
            upperName.startsWith("C") && upperName.substring(1).toIntOrNull() != null -> "mode_chrono"
            upperName.startsWith("JD") -> "mode_jd"
            else -> "mode_bus"
        }
    }

    fun getVehicleMarkerType(lineName: String): VehicleMarkerType {
        val upperName = lineName.uppercase()
        return when {
            upperName.startsWith("TB") -> VehicleMarkerType.BUS
            upperName.startsWith("T") -> VehicleMarkerType.TRAM
            else -> VehicleMarkerType.BUS
        }
    }
}
