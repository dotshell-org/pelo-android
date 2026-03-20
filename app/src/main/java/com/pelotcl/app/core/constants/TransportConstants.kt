package com.pelotcl.app.core.constants

/**
 * Constants related to transport lines and types
 */
object TransportConstants {
    // Strong lines (metro, tram, funicular, navigone)
    const val LINE_METRO_A = "A"
    const val LINE_METRO_B = "B"
    const val LINE_METRO_C = "C"
    const val LINE_METRO_D = "D"
    const val LINE_FUNICULAR_F1 = "F1"
    const val LINE_FUNICULAR_F2 = "F2"
    const val LINE_NAVIGONE = "NAV1"
    const val LINE_RX = "RX"

    // Minimum zoom levels for different stop types
    const val PRIORITY_STOPS_MIN_ZOOM = 12.5f
    const val TRAM_STOPS_MIN_ZOOM = 14.0f
    const val SECONDARY_STOPS_MIN_ZOOM = 17.0f
    const val SELECTED_STOP_MIN_ZOOM = 9.0f
    const val LIVE_MODE_ZOOM_LEVEL = 12.0f

    // Vehicle marker sizes
    const val VEHICLE_MARKER_SIZE_LINE = 72
    const val VEHICLE_MARKER_SIZE_GLOBAL = 56

    // Map layer names
    const val ALL_LINES_LAYER = "all-lines-layer"
    const val ALL_LINES_SOURCE = "all-lines-source"
    const val VEHICLE_POSITIONS_LAYER = "vehicle-positions-layer"
    const val VEHICLE_POSITIONS_SOURCE = "vehicle-positions-source"
    const val GLOBAL_VEHICLE_POSITIONS_LAYER = "global-vehicle-positions-layer"
    const val GLOBAL_VEHICLE_POSITIONS_SOURCE = "global-vehicle-positions-source"

    // Stop priority levels
    const val STOP_PRIORITY_METRO = 2
    const val STOP_PRIORITY_TRAM = 1
    const val STOP_PRIORITY_BUS = 0
}