package com.pelotcl.app.utils.transport

import com.pelotcl.app.generic.data.model.Feature
import androidx.core.graphics.toColorInt

/**
 * Utilitary to determine the color of a transport line based on its type
 */
object LineColorHelper {

    // Cache for toColorInt() results — ~15 unique colors, near-100% hit rate
    private val colorIntCache = HashMap<String, Int>(20)

    // Defined colors
    private const val METRO_A_COLOR = "#EC4899"
    private const val METRO_B_COLOR = "#3B82F6"
    private const val METRO_C_COLOR = "#F59E0B"
    private const val METRO_D_COLOR = "#22C55E"
    private const val TRAM_COLOR = "#A855F7"
    private const val FUNICULAR_COLOR = "#84CC16"
    private const val NAVIGONE_COLOR = "#14b8a6"
    private const val BUS_COLOR = "#EF4444"
    private const val TRAMBUS_COLOR = "#eab308"
    private const val TRAMBUS_TB12_COLOR = "#92400e"

    /**
     * Returns the hex color appropriated for a transport line
     *
     * @param feature The feature containing line information
     * @return The color in hexadecimal format (#RRGGBB)
     */
    fun getColorForLine(feature: Feature): String {
        val line = feature.properties.lineName

        return getColorForLineStringAux(line)
    }

    fun getColorForLineStringAux(lineName: String): String {
        val name = lineName.uppercase()
        val hexColor = when {
            name == "RX" -> "#E30613"
            name in (1..7).map { "T$it" } -> TRAM_COLOR
            name.equals("TB12", ignoreCase = true) -> TRAMBUS_TB12_COLOR
            name.startsWith("TB") -> TRAMBUS_COLOR

            name == "A" -> METRO_A_COLOR
            name == "B" -> METRO_B_COLOR
            name == "C" -> METRO_C_COLOR
            name == "D" -> METRO_D_COLOR

            name in (1..2).map { "F$it" } -> FUNICULAR_COLOR

            name == "NAV1" -> NAVIGONE_COLOR

            else -> BUS_COLOR
        }

        return hexColor
    }

    /**
     * Returns the appropriate color for a line based on its name
     *
     * @param lineName The name of the line (e.g., "A", "B", "T1", "C3", etc.)
     * @return The color in hexadecimal format (#RRGGBB)
     */
    fun getColorForLineString(lineName: String): Int {
        val key = lineName.uppercase()
        colorIntCache[key]?.let { return it }
        val color = getColorForLineStringAux(lineName).toColorInt()
        colorIntCache[key] = color
        return color
    }

}
