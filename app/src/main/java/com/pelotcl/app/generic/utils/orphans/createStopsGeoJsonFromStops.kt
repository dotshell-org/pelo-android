package com.pelotcl.app.generic.utils.orphans

import com.pelotcl.app.generic.data.models.geojson.StopFeature
import com.pelotcl.app.generic.utils.BusIconHelper
import com.pelotcl.app.specific.utils.orphans.getModeIconForLine
import com.pelotcl.app.specific.utils.orphans.isMetroTramOrFunicular

/**
 * Creates a GeoJSON FeatureCollection string from stops using StringBuilder.
 * This avoids creating thousands of JsonObject/JsonArray instances, reducing
 * GC pressure and allocation time by ~60-70% compared to the Gson approach.
 */
fun createStopsGeoJsonFromStops(
    stops: List<StopFeature>,
    validIcons: Set<String>
): String {
    val mergedStops = mergeStopsByName(stops)

    // Pre-size StringBuilder: ~300 bytes per feature, ~2 features per stop on average
    val sb = StringBuilder(mergedStops.size * 600)
    sb.append("{\"type\":\"FeatureCollection\",\"features\":[")

    var firstFeature = true

    for (stop in mergedStops) {
        val lineNamesAll = BusIconHelper.getAllLinesForStop(stop)
        if (lineNamesAll.isEmpty()) continue

        val hasTram = lineNamesAll.any { it.uppercase().startsWith("T") }

        val lignesFortes = lineNamesAll.filter { isMetroTramOrFunicular(it) }
        val busLines = lineNamesAll.filter { !isMetroTramOrFunicular(it) }
        val uniqueModes = busLines.mapNotNull { getModeIconForLine(it) }.distinct()

        // Build the list of icons to display
        val iconsToDisplay = ArrayList<Pair<String, Int>>(lignesFortes.size + uniqueModes.size)

        for (lineName in lignesFortes) {
            val upperName = lineName.uppercase()
            val drawableName = BusIconHelper.getDrawableNameForLineName(lineName)
            if (validIcons.contains(drawableName)) {
                val priority = when {
                    isMetroTramOrFunicular(upperName) && !upperName.startsWith("T") -> 2
                    upperName.startsWith("T") -> 1
                    else -> 0
                }
                iconsToDisplay.add(drawableName to priority)
            }
        }

        for (modeIcon in uniqueModes) {
            if (validIcons.contains(modeIcon)) {
                iconsToDisplay.add(modeIcon to 0)
            }
        }

        if (iconsToDisplay.isEmpty()) continue

        val coordinates = stop.geometry.coordinates
        if (coordinates.size < 2) continue
        val lon = coordinates[0]
        val lat = coordinates[1]
        val nom = escapeJsonString(stop.properties.nom)
        val desserte = escapeJsonString(stop.properties.desserte)
        val normalizedNom = stop.properties.nom.filter { it.isLetter() }.lowercase()

        // Pre-build lignes JSON array string and has_line_ properties
        val lignesJsonSb = StringBuilder()
        lignesJsonSb.append("[")
        lineNamesAll.forEachIndexed { i, l ->
            if (i > 0) lignesJsonSb.append(",")
            lignesJsonSb.append("\"").append(escapeJsonString(l)).append("\"")
        }
        lignesJsonSb.append("]")
        val lignesJson = escapeJsonString(lignesJsonSb.toString())

        val hasLineProps = StringBuilder()
        for (line in lineNamesAll) {
            hasLineProps.append(",\"has_line_${line.uppercase()}\":true")
        }

        val n = iconsToDisplay.size
        var slot = -(n - 1)

        for ((iconName, stopPriority) in iconsToDisplay) {
            if (!firstFeature) sb.append(",")
            firstFeature = false

            sb.append("{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[")
            sb.append(lon).append(",").append(lat)
            sb.append("]},\"properties\":{")
            sb.append("\"nom\":\"").append(nom).append("\",")
            sb.append("\"desserte\":\"").append(desserte).append("\",")
            sb.append("\"stop_id\":").append(stop.properties.id).append(",")
            sb.append("\"type\":\"stop\",")
            sb.append("\"stop_priority\":").append(stopPriority).append(",")
            sb.append("\"has_tram\":").append(hasTram).append(",")
            sb.append("\"icon\":\"").append(iconName).append("\",")
            sb.append("\"slot\":").append(slot).append(",")
            sb.append("\"lignes\":\"").append(lignesJson).append("\",")
            sb.append("\"normalized_nom\":\"").append(normalizedNom).append("\"")
            sb.append(hasLineProps)
            sb.append("}}")

            slot += 2
        }
    }

    sb.append("]}")
    return sb.toString()
}
