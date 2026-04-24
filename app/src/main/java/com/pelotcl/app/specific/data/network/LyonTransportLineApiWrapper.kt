package com.pelotcl.app.specific.data.network

import android.util.Log
import com.pelotcl.app.generic.data.models.geojson.FeatureCollection
import com.pelotcl.app.generic.data.models.geojson.StopCollection
import com.pelotcl.app.specific.data.mapper.TransportLineMapper

/**
 * Wrapper class that converts Lyon-specific API responses to generic models
 */
class LyonTransportLineApiWrapper(private val lyonApi: LyonTransportLineApi) {

    suspend fun getMetroLines(
        service: String, version: String, request: String, typename: String,
        outputFormat: String, srsName: String, startIndex: Int, sortBy: String, count: Int
    ): FeatureCollection {
        val lyonResponse = lyonApi.getMetroLinesRaw(
            service,
            version,
            request,
            typename,
            outputFormat,
            srsName,
            startIndex,
            sortBy,
            count
        )
        return TransportLineMapper.mapToGeneric(lyonResponse)
    }

    suspend fun getTramLines(
        service: String, version: String, request: String, typename: String,
        outputFormat: String, srsName: String, startIndex: Int, sortBy: String, count: Int
    ): FeatureCollection {
        val lyonResponse = lyonApi.getTramLinesRaw(
            service,
            version,
            request,
            typename,
            outputFormat,
            srsName,
            startIndex,
            sortBy,
            count
        )
        return TransportLineMapper.mapToGeneric(lyonResponse)
    }

    suspend fun getBusLines(
        service: String, version: String, request: String, typename: String,
        outputFormat: String, srsName: String, startIndex: Int, sortBy: String, count: Int, cqlFilter: String?
    ): FeatureCollection {
        val lyonResponse = lyonApi.getBusLinesRaw(
            service,
            version,
            request,
            typename,
            outputFormat,
            srsName,
            startIndex,
            sortBy,
            count,
            cqlFilter
        )
        return TransportLineMapper.mapToGeneric(lyonResponse)
    }

    suspend fun getBusLineByName(
        service: String, version: String, request: String, typename: String,
        outputFormat: String, srsName: String, sortBy: String, count: Int, cqlFilter: String
    ): FeatureCollection {
        val lyonResponse = lyonApi.getBusLineByNameRaw(
            service,
            version,
            request,
            typename,
            outputFormat,
            srsName,
            sortBy,
            count,
            cqlFilter
        )
        return TransportLineMapper.mapToGeneric(lyonResponse)
    }

    suspend fun getNavigoneLines(
        service: String, version: String, request: String, typename: String,
        outputFormat: String, srsName: String, startIndex: Int, sortBy: String, count: Int
    ): FeatureCollection {
        val lyonResponse = lyonApi.getNavigoneLinesRaw(
            service,
            version,
            request,
            typename,
            outputFormat,
            srsName,
            startIndex,
            sortBy,
            count
        )
        return TransportLineMapper.mapToGeneric(lyonResponse)
    }

    suspend fun getTrambusLines(
        service: String, version: String, request: String, typename: String,
        outputFormat: String, srsName: String, startIndex: Int, sortBy: String, count: Int, cqlFilter: String
    ): FeatureCollection {
        val lyonResponse = lyonApi.getTrambusLinesRaw(
            service,
            version,
            request,
            typename,
            outputFormat,
            srsName,
            startIndex,
            sortBy,
            count,
            cqlFilter
        )
        return TransportLineMapper.mapToGeneric(lyonResponse)
    }

    suspend fun getTransportStops(
        service: String, version: String, request: String, typename: String,
        outputFormat: String, srsName: String, startIndex: Int, sortBy: String, count: Int
    ): StopCollection {
        val lyonResponse = lyonApi.getTransportStopsRaw(
            service,
            version,
            request,
            typename,
            outputFormat,
            srsName,
            startIndex,
            sortBy,
            count
        )

        // Debug logging to understand WFS response structure
        Log.i("LyonTransportLineApi", "WFS stops response: ${lyonResponse.features.size} features")
        if (lyonResponse.features.isNotEmpty()) {
            val firstFeature = lyonResponse.features.first()
            Log.i(
                "LyonTransportLineApi",
                "First stop geometry: type=${firstFeature.geometry.type}, coords=${firstFeature.geometry.coordinates}"
            )
            Log.i("LyonTransportLineApi", "First stop properties: ${firstFeature.properties}")
        }

        return com.pelotcl.app.specific.data.mapper.StopMapper.mapToGeneric(lyonResponse)
    }
}