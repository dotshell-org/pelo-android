package com.pelotcl.app.generic.utils.orphans

import com.pelotcl.app.generic.data.repository.itinerary.itinerary.JourneyLeg
import com.pelotcl.app.generic.data.repository.itinerary.itinerary.JourneyResult
import com.pelotcl.app.generic.ui.viewmodel.TransportViewModel
import org.maplibre.android.geometry.LatLng

suspend fun buildNavigationPathPoints(
    journey: JourneyResult,
    viewModel: TransportViewModel
): List<LatLng> {
    val points = mutableListOf<LatLng>()

    fun appendPointIfDistinct(point: LatLng) {
        val last = points.lastOrNull()
        if (last == null ||
            last.latitude != point.latitude ||
            last.longitude != point.longitude
        ) {
            points.add(point)
        }
    }

    fun appendFallbackLegPoints(leg: JourneyLeg) {
        if (isValidJourneyCoordinate(leg.fromLat, leg.fromLon)) {
            appendPointIfDistinct(LatLng(leg.fromLat, leg.fromLon))
        }
        leg.intermediateStops.forEach { stop ->
            if (isValidJourneyCoordinate(stop.lat, stop.lon)) {
                appendPointIfDistinct(LatLng(stop.lat, stop.lon))
            }
        }
        if (isValidJourneyCoordinate(leg.toLat, leg.toLon)) {
            appendPointIfDistinct(LatLng(leg.toLat, leg.toLon))
        }
    }

    journey.legs.filterNot { it.isWalking }.forEach { leg ->
        val routeName = leg.routeName?.takeIf { it.isNotBlank() }
        if (routeName == null) {
            appendFallbackLegPoints(leg)
            return@forEach
        }

        val sectionPoints = runCatching {
            val lines = viewModel.transportRepository
                .getLineByName(routeName)
                .getOrElse { emptyList() }
            if (lines.isEmpty()) return@runCatching emptyList<LatLng>()

            val sectionedLines = viewModel.sectionLinesBetweenStops(
                lines = lines,
                startStopId = leg.fromStopId,
                endStopId = leg.toStopId,
                leg = leg
            )

            val coordinates = sectionedLines
                .firstOrNull()
                ?.multiLineStringGeometry
                ?.coordinates
                ?.firstOrNull()
                .orEmpty()

            coordinates.mapNotNull { coord ->
                if (coord.size < 2) return@mapNotNull null
                val lon = coord[0]
                val lat = coord[1]
                if (!isValidJourneyCoordinate(lat, lon)) return@mapNotNull null
                LatLng(lat, lon)
            }
        }.getOrElse { emptyList() }

        if (sectionPoints.size >= 2) {
            sectionPoints.forEach(::appendPointIfDistinct)
        } else {
            appendFallbackLegPoints(leg)
        }
    }

    return points
}
