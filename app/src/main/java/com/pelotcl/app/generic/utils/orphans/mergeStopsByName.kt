package com.pelotcl.app.generic.utils.orphans

import com.pelotcl.app.generic.data.models.geojson.StopFeature
import com.pelotcl.app.generic.data.models.stops.StopGeometry
import com.pelotcl.app.generic.data.models.stops.StopProperties
import com.pelotcl.app.generic.utils.BusIconHelper
import com.pelotcl.app.specific.utils.orphans.isMetroTramOrFunicular
import kotlin.collections.forEach

fun mergeStopsByName(stops: List<StopFeature>): List<StopFeature> {
    fun normalizeStopName(name: String): String {
        return name.filter { it.isLetter() }.lowercase()
    }

    val strongLineStops = mutableListOf<StopFeature>()
    val weakLineStops = mutableListOf<StopFeature>()

    stops.forEach { stop ->
        val allLines = BusIconHelper.getAllLinesForStop(stop)
        val strongLines = allLines.filter { isMetroTramOrFunicular(it) }
        val weakLines = allLines.filter { !isMetroTramOrFunicular(it) }

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

            // Calculate average position (centroid) for all stops with same name
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
