package com.pelotcl.app.generic.utils.orphans

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.pelotcl.app.generic.data.models.geojson.Feature

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
