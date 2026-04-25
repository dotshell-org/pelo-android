package com.pelotcl.app.generic.data.repository

import android.content.Context
import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.pelotcl.app.generic.data.network.transport.TransportApi
import com.pelotcl.app.generic.service.TransportServiceProvider
import com.pelotcl.app.generic.data.models.geojson.Feature
import com.pelotcl.app.generic.data.models.geojson.FeatureCollection
import com.pelotcl.app.generic.data.models.lines.MultiLineStringGeometry
import com.pelotcl.app.generic.data.models.lines.TransportLineProperties
import com.pelotcl.app.generic.data.network.transport.TransportLinesQuery
import com.pelotcl.app.generic.data.offline.OfflineRepository
import com.pelotcl.app.generic.utils.network.withRetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for managing transport line data
 * Uses TransportServiceProvider for dependency access
 */
class TransportRepository(context: Context? = null) {

    private val transportApi: TransportApi = TransportServiceProvider.getTransportApi()
    private val cache = context?.let { com.pelotcl.app.specific.data.cache.TransportCacheImpl(it) }
    private val offlineRepo = context?.let { OfflineRepository(it) }

    /**
     * Fetches all default (non-bus-by-pagination) strong transport line geometries.
     */
    suspend fun getAllLines(): Result<FeatureCollection> {
        return withContext(Dispatchers.IO) {
            runCatching {
                withRetry(maxRetries = 2, initialDelayMs = 1000) {
                    transportApi.getLines(TransportLinesQuery.StrongLines)
                }
            }.fold(
                onSuccess = { Result.success(it) },
                onFailure = { e ->
                    // Try offline repository first
                    val offlineLines = offlineRepo?.loadAllLines().orEmpty()
                    if (offlineLines.isNotEmpty()) {
                        val uniqueLines = offlineLines
                            .groupBy { it.properties.traceCode }
                            .map { (_, features) -> features.first() }
                        Result.success(
                            FeatureCollection(
                                type = "FeatureCollection",
                                features = uniqueLines
                            )
                        )
                    } else {
                        // Try cache as last resort
                        val cachedMetroLines = cache?.getMetroLines().orEmpty()
                        val cachedTramLines = cache?.getTramLines().orEmpty()
                        val allCachedLines = cachedMetroLines + cachedTramLines
                        
                        if (allCachedLines.isNotEmpty()) {
                            Result.success(
                                FeatureCollection(
                                    type = "FeatureCollection",
                                    features = allCachedLines
                                )
                            )
                        } else {
                            Result.failure(e)
                        }
                    }
                }
            )
        }
    }

    /**
     * Loads a single line geometry by line name, including non-strong lines.
     * This is used to add weak lines on demand without loading the whole bus dataset.
     */
    suspend fun getLineByName(lineName: String): Result<List<Feature>> {
        return withContext(Dispatchers.IO) {
            runCatching {
                transportApi
                    .getLines(
                        TransportLinesQuery.LineByName(lineName)
                    )
                    .features
            }
        }
    }

    private suspend fun fetchRhonexpressFromWfs(): List<Feature> {
        fun mapJsonToFeatures(json: JsonObject): List<Feature> {
            // GeoServer can return either GeoJSON FeatureCollection (`features`)
            // or a WFS structure (`featureMember`) depending on configuration.
            val featuresArray: JsonArray = when {
                json.has("features") -> json.getAsJsonArray("features")
                json.has("featureMember") -> json.getAsJsonArray("featureMember")
                json.has("featureMembers") -> json.getAsJsonArray("featureMembers")
                else -> {
                    val keys = json.entrySet().joinToString(", ") { it.key }
                    Log.w("TransportRepository", "RX WFS response has no known features array keys. keys=[$keys]")
                    return emptyList()
                }
            }

            val result = mutableListOf<Feature>()

            var skippedNoGeometry = 0
            var skippedNoCoordinates = 0
            var skippedUnknownCoordinateStructure = 0
            var firstGeometryType: String? = null

            fun parsePosition(pos: JsonElement): List<Double>? {
                if (!pos.isJsonArray) return null
                val arr = pos.asJsonArray
                if (arr.size() < 2) return null
                return listOf(arr[0].asDouble, arr[1].asDouble)
            }

            /**
             * Returns coordinates in the app's expected shape:
             * `MultiLineString` => List<Line> (List<Point>) => Point is [lon, lat]
             */
            fun parseCoordinatesAsMultiLines(coords: JsonElement): List<List<List<Double>>>? {
                if (!coords.isJsonArray) return null
                val outer = coords.asJsonArray
                if (outer.size() == 0) return null

                val first = outer[0]
                if (!first.isJsonArray) return null

                // Distinguish:
                // - LineString: [ [x,y], [x,y], ... ] => first element is a position-array of numbers
                // - MultiLineString: [ [ [x,y], ... ], [ [x,y], ... ] ] => first element is an array-of-positions
                val firstArr = first.asJsonArray
                if (firstArr.size() == 0) return null
                val firstInner = firstArr[0]

                val isMultiLine =
                    firstInner != null && firstInner.isJsonArray // lineArray[0] is position array

                if (isMultiLine) {
                    val lines = outer.mapNotNull { lineEl ->
                        if (!lineEl.isJsonArray) return@mapNotNull null
                        val lineArr = lineEl.asJsonArray
                        val points = lineArr.mapNotNull { parsePosition(it) }
                        if (points.isEmpty()) null else points
                    }
                    return if (lines.isEmpty()) null else lines
                } else {
                    // Treat as LineString
                    val points = outer.mapNotNull { parsePosition(it) }
                    return if (points.isEmpty()) null else listOf(points)
                }
            }

            for (featureElement in featuresArray) {
                val featureObject = featureElement.asJsonObject
                val id = featureObject.get("id")?.asString ?: "rx-${System.nanoTime()}"

                // Some formats put properties directly under the feature member.
                val properties = featureObject.getAsJsonObject("properties") ?: featureObject
                val gid =
                    properties.get("gid")?.asInt
                        ?: properties.get("GID")?.asInt
                        ?: 0

                val geometryObject = featureObject
                    .getAsJsonObject("geometry")
                    ?: featureObject.getAsJsonObject("the_geom")
                    ?: featureObject.getAsJsonObject("geom")
                    ?: run {
                        skippedNoGeometry++
                        continue
                    }

                firstGeometryType = firstGeometryType ?: geometryObject.get("type")?.asString

                val coordinatesElement = geometryObject.get("coordinates") ?: run {
                    skippedNoCoordinates++
                    continue
                }

                val coordinates = parseCoordinatesAsMultiLines(coordinatesElement) ?: run {
                    skippedUnknownCoordinateStructure++
                    continue
                }

                result.add(
                    Feature(
                        type = "Feature",
                        id = "rx_$id",
                        multiLineStringGeometry = MultiLineStringGeometry(
                            type = "MultiLineString",
                            coordinates = coordinates
                        ),
                        geometryName = null,
                        properties = TransportLineProperties(
                            lineName = "RX",
                            traceCode = "RX-$gid",
                            lineId = "RX",
                            traceType = "",
                            traceName = "Rhônexpress",
                            direction = "ALLER",
                            origin = "Gare Part-Dieu Villette",
                            destination = "Aéroport St Exupéry -RX",
                            originName = "Gare Part-Dieu Villette",
                            destinationName = "Aéroport St Exupéry -RX",
                            transportType = "TRAM",
                            startDate = "",
                            endDate = null,
                            lineTypeCode = "TRAM",
                            lineTypeName = "Tramway",
                            lastUpdate = "",
                            lastUpdateFme = "",
                            gid = gid,
                            color = "#E30613"
                        ),
                        bbox = null
                    )
                )
            }

            if (result.isEmpty()) {
                Log.w(
                    "TransportRepository",
                    "RX WFS parsed empty: features=${featuresArray.size()}, skippedNoGeometry=$skippedNoGeometry, skippedNoCoordinates=$skippedNoCoordinates, skippedUnknownCoordinateStructure=$skippedUnknownCoordinateStructure, firstGeometryType=$firstGeometryType"
                )
            }

            return result
        }

        return try {
            transportApi
                .getLines(TransportLinesQuery.LineByName("RX"))
                .features
        } catch (e: Exception) {
            Log.w("TransportRepository", "Failed to fetch Rhônexpress (via TransportApi): ${e.message}")
            emptyList()
        }
    }
}
