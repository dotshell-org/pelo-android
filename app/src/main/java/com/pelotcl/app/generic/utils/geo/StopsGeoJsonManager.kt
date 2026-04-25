package com.pelotcl.app.generic.utils.geo

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.pelotcl.app.generic.data.models.geojson.Feature
import com.pelotcl.app.generic.data.models.geojson.StopFeature
import com.pelotcl.app.generic.data.models.stops.StopGeometry
import com.pelotcl.app.generic.data.models.stops.StopProperties
import com.pelotcl.app.generic.utils.graphics.BusIconHelper
import com.pelotcl.app.specific.utils.LineClassificationUtils

object StopsGeoJsonManager {

    fun createStopsGeoJsonFromStops(
        stops: List<StopFeature>,
        validIcons: Set<String>
    ): String {
        val mergedStops = mergeStopsByName(stops)

        val sb = StringBuilder(mergedStops.size * 600)
        sb.append("{\"type\":\"FeatureCollection\",\"features\":[")

        var firstFeature = true

        for (stop in mergedStops) {
            val lineNamesAll = BusIconHelper.getAllLinesForStop(stop)
            if (lineNamesAll.isEmpty()) continue

            val hasTram = lineNamesAll.any { it.uppercase().startsWith("T") }

            val lignesFortes = lineNamesAll.filter { LineClassificationUtils.isMetroTramOrFunicular(it) }
            val busLines = lineNamesAll.filter { !LineClassificationUtils.isMetroTramOrFunicular(it) }
            val uniqueModes = busLines.mapNotNull { LineClassificationUtils.getModeIconForLine(it) }.distinct()

            val iconsToDisplay = ArrayList<Pair<String, Int>>(lignesFortes.size + uniqueModes.size)

            for (lineName in lignesFortes) {
                val upperName = lineName.uppercase()
                val drawableName = BusIconHelper.getDrawableNameForLineName(lineName)
                if (validIcons.contains(drawableName)) {
                    val priority = when {
                        LineClassificationUtils.isMetroTramOrFunicular(upperName) &&
                                !upperName.startsWith("T") -> 2
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

    fun mergeStopsByName(stops: List<StopFeature>): List<StopFeature> {
        fun normalizeStopName(name: String): String {
            return name.filter { it.isLetter() }.lowercase()
        }

        val strongLineStops = mutableListOf<StopFeature>()
        val weakLineStops = mutableListOf<StopFeature>()

        stops.forEach { stop ->
            val allLines = BusIconHelper.getAllLinesForStop(stop)
            val strongLines = allLines.filter { LineClassificationUtils.isMetroTramOrFunicular(it) }
            val weakLines = allLines.filter { !LineClassificationUtils.isMetroTramOrFunicular(it) }

            if (strongLines.isNotEmpty()) {
                val strongDesserte = strongLines.joinToString(", ")
                strongLineStops.add(
                    StopFeature(
                        type = stop.type,
                        id = stop.id,
                        geometry = stop.geometry,
                        properties = StopProperties(
                            id = stop.properties.id,
                            nom = stop.properties.nom,
                            desserte = strongDesserte,
                            ascenseur = stop.properties.ascenseur,
                            escalator = stop.properties.escalator,
                            gid = stop.properties.gid,
                            lastUpdate = stop.properties.lastUpdate,
                            lastUpdateFme = stop.properties.lastUpdateFme,
                            adresse = stop.properties.adresse,
                            localiseFaceAAdresse = stop.properties.localiseFaceAAdresse,
                            commune = stop.properties.commune,
                            insee = stop.properties.insee,
                            zone = stop.properties.zone
                        )
                    )
                )
            }

            if (weakLines.isNotEmpty()) {
                val weakDesserte = weakLines.joinToString(", ")
                weakLineStops.add(
                    StopFeature(
                        type = stop.type,
                        id = "${stop.id}-weak",
                        geometry = stop.geometry,
                        properties = StopProperties(
                            id = stop.properties.id,
                            nom = stop.properties.nom,
                            desserte = weakDesserte,
                            ascenseur = stop.properties.ascenseur,
                            escalator = stop.properties.escalator,
                            gid = stop.properties.gid,
                            lastUpdate = stop.properties.lastUpdate,
                            lastUpdateFme = stop.properties.lastUpdateFme,
                            adresse = stop.properties.adresse,
                            localiseFaceAAdresse = stop.properties.localiseFaceAAdresse,
                            commune = stop.properties.commune,
                            insee = stop.properties.insee,
                            zone = stop.properties.zone
                        )
                    )
                )
            }
        }

        val strongStopsByName = strongLineStops.groupBy { normalizeStopName(it.properties.nom) }

        val mergedStrongStops = strongStopsByName.map { (_, stopsGroup) ->
            if (stopsGroup.size == 1) {
                stopsGroup.first()
            } else {
                val mergedDesserte = stopsGroup
                    .flatMap { BusIconHelper.getAllLinesForStop(it) }
                    .distinct()
                    .sorted()
                    .joinToString(", ")

                val firstStop = stopsGroup.first()
                val validCoordinates = stopsGroup
                    .mapNotNull { stop ->
                        val coordinates = stop.geometry.coordinates
                        if (coordinates.size < 2) null else coordinates[0] to coordinates[1]
                    }
                val avgLon = validCoordinates.map { it.first }.average()
                val avgLat = validCoordinates.map { it.second }.average()
                if (avgLon.isNaN() || avgLat.isNaN()) {
                    return@map firstStop
                }
                val mergedGeometry = StopGeometry(
                    type = "Point",
                    coordinates = listOf(avgLon, avgLat)
                )

                StopFeature(
                    type = firstStop.type,
                    id = firstStop.id,
                    geometry = mergedGeometry,
                    properties = StopProperties(
                        id = firstStop.properties.id,
                        nom = firstStop.properties.nom,
                        desserte = mergedDesserte,
                        ascenseur = firstStop.properties.ascenseur,
                        escalator = firstStop.properties.escalator,
                        gid = firstStop.properties.gid,
                        lastUpdate = firstStop.properties.lastUpdate,
                        lastUpdateFme = firstStop.properties.lastUpdateFme,
                        adresse = firstStop.properties.adresse,
                        localiseFaceAAdresse = firstStop.properties.localiseFaceAAdresse,
                        commune = firstStop.properties.commune,
                        insee = firstStop.properties.insee,
                        zone = firstStop.properties.zone
                    )
                )
            }
        }

        return mergedStrongStops + weakLineStops
    }

    fun createGeoJsonFromFeature(feature: Feature): String {
        val geoJsonObject = JsonObject().apply {
            addProperty("type", "Feature")

            val geometryObject = JsonObject().apply {
                addProperty("type", feature.geometry.type)
                val coordinatesArray = JsonArray()
                feature.geometry.coordinates.forEach { lineString ->
                    val lineStringArray = JsonArray()
                    lineString.forEach { point ->
                        val pointArray = JsonArray()
                        point.forEach { coord ->
                            pointArray.add(coord)
                        }
                        lineStringArray.add(pointArray)
                    }
                    coordinatesArray.add(lineStringArray)
                }
                add("coordinates", coordinatesArray)
            }
            add("geometry", geometryObject)

            val propertiesObject = JsonObject().apply {
                addProperty("ligne", feature.properties.lineName)
                addProperty("nom_trace", feature.properties.traceName)
                addProperty("couleur", feature.properties.color ?: "")
            }
            add("properties", propertiesObject)
        }

        return geoJsonObject.toString()
    }

    fun escapeJsonString(s: String): String {
        if (s.isEmpty()) return s
        var needsEscape = false
        for (c in s) {
            if (c == '"' || c == '\\' || c == '\n' || c == '\r' || c == '\t') {
                needsEscape = true
                break
            }
        }
        if (!needsEscape) return s

        val sb = StringBuilder(s.length + 8)
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }
}
