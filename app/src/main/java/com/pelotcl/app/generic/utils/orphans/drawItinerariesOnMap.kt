package com.pelotcl.app.generic.utils.orphans

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.pelotcl.app.generic.data.models.geojson.Feature
import com.pelotcl.app.generic.data.repository.itinerary.itinerary.JourneyResult
import com.pelotcl.app.generic.ui.viewmodel.TransportViewModel
import com.pelotcl.app.specific.utils.LineColorHelper
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import java.util.Locale

fun drawItinerariesOnMap(
    map: MapLibreMap,
    journeys: List<JourneyResult>,
    selectedJourney: JourneyResult?,
    viewModel: TransportViewModel
) {
    map.getStyle { style ->
        clearItineraryLayers(style)
        if (journeys.isEmpty()) return@getStyle

        val journeysToDraw = selectedJourney?.let { listOf(it) } ?: journeys

        journeysToDraw.forEachIndexed { journeyIndex, journey ->
            journey.legs.forEachIndexed { legIndex, leg ->
                val lineColor = if (leg.isWalking) {
                    "#6B7280"
                } else {
                    val colorInt = LineColorHelper.getColorForLineString(leg.routeName ?: "")
                    String.format(Locale.ROOT, "#%06X", 0xFFFFFF and colorInt)
                }

                var drewSection = false

                if (!leg.isWalking) {
                    val lineName = leg.routeName ?: ""
                    val lines = try {
                        kotlinx.coroutines.runBlocking {
                            viewModel.transportRepository.getLineByName(lineName).getOrElse { emptyList() }
                        }
                    } catch (e: Exception) {
                        emptyList<Feature>()
                    }

                    if (lines.isNotEmpty()) {
                        val sectionedLines = viewModel.sectionLinesBetweenStops(
                            lines,
                            leg.fromStopId,
                            leg.toStopId,
                            leg
                        )
                        if (sectionedLines.isNotEmpty()) {
                            val sectionedLine = sectionedLines.first()
                            val firstLine = sectionedLine.multiLineStringGeometry.coordinates.firstOrNull()
                            if (!firstLine.isNullOrEmpty() && firstLine.size > 1) {
                                val coordinatesArray = JsonArray()
                                firstLine.forEach { coord ->
                                    val coordArray = JsonArray()
                                    coordArray.add(coord[0])
                                    coordArray.add(coord[1])
                                    coordinatesArray.add(coordArray)
                                }
                                val lineGeoJson = JsonObject().apply {
                                    addProperty("type", "Feature")
                                    val geometry = JsonObject().apply {
                                        addProperty("type", "LineString")
                                        add("coordinates", coordinatesArray)
                                    }
                                    add("geometry", geometry)
                                }
                                val sourceId = "inline-itinerary-leg-source-$journeyIndex-$legIndex"
                                val layerId = "inline-itinerary-leg-layer-$journeyIndex-$legIndex"
                                style.addSource(GeoJsonSource(sourceId, lineGeoJson.toString()))
                                val lineLayer = LineLayer(layerId, sourceId).apply {
                                    setProperties(
                                        PropertyFactory.lineColor(lineColor),
                                        PropertyFactory.lineWidth(5f),
                                        PropertyFactory.lineOpacity(1.0f),
                                        PropertyFactory.lineCap("round"),
                                        PropertyFactory.lineJoin("round")
                                    )
                                }
                                style.addLayer(lineLayer)
                                drewSection = true
                            }
                        }
                    }
                }

                if (!drewSection) {
                    val coordinatesArray = JsonArray()
                    val fromCoord = JsonArray()
                    fromCoord.add(leg.fromLon)
                    fromCoord.add(leg.fromLat)
                    coordinatesArray.add(fromCoord)

                    leg.intermediateStops.forEach { stop ->
                        val coord = JsonArray()
                        coord.add(stop.lon)
                        coord.add(stop.lat)
                        coordinatesArray.add(coord)
                    }

                    val toCoord = JsonArray()
                    toCoord.add(leg.toLon)
                    toCoord.add(leg.toLat)
                    coordinatesArray.add(toCoord)

                    val lineGeoJson = JsonObject().apply {
                        addProperty("type", "Feature")
                        val geometry = JsonObject().apply {
                            addProperty("type", "LineString")
                            add("coordinates", coordinatesArray)
                        }
                        add("geometry", geometry)
                    }

                    val sourceId = "inline-itinerary-leg-source-$journeyIndex-$legIndex"
                    val layerId = "inline-itinerary-leg-layer-$journeyIndex-$legIndex"

                    style.addSource(GeoJsonSource(sourceId, lineGeoJson.toString()))
                    val lineLayer = LineLayer(layerId, sourceId).apply {
                        setProperties(
                            PropertyFactory.lineColor(lineColor),
                            PropertyFactory.lineWidth(if (leg.isWalking) 3f else 5f),
                            PropertyFactory.lineOpacity(1.0f),
                            PropertyFactory.lineCap("round"),
                            PropertyFactory.lineJoin("round")
                        )
                        if (leg.isWalking) {
                            setProperties(PropertyFactory.lineDasharray(arrayOf(2f, 2f)))
                        }
                    }
                    style.addLayer(lineLayer)
                }
            }
        }
    }
}
