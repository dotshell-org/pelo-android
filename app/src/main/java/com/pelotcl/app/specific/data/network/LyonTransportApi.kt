package com.pelotcl.app.specific.data.network

import com.google.gson.JsonObject
import com.pelotcl.app.generic.data.model.FeatureCollection
import com.pelotcl.app.generic.data.model.StopCollection
import com.pelotcl.app.generic.data.model.TrafficAlertsResponse
import com.pelotcl.app.generic.data.network.TransportApi
import com.pelotcl.app.specific.data.mapper.TrafficAlertMapper
import com.pelotcl.app.specific.data.model.LyonTrafficAlertsResponse
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET

/**
 * Lyon-specific implementation of TransportApi
 * Maps traffic alerts from Lyon payloads; WFS line/stop requests use [LyonTransportLineApi]
 * + [LyonTransportLineApiWrapper] so GeoJSON properties match the Lyon field names.
 */
class LyonTransportApi(private val baseUrl: String) : TransportApi {

    interface LyonTrafficAlertsEndpoint {
        @GET("pelo/v1/traffic/alerts")
        suspend fun getLyonTrafficAlerts(): LyonTrafficAlertsResponse
    }

    /**
     * WFS / GeoJSON lines and stops use the city data host (e.g. Grand Lyon).
     * Traffic alerts are served from the Pelo API on api.dotshell.eu only.
     */
    private val linesRetrofit: Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val trafficAlertsRetrofit: Retrofit = Retrofit.Builder()
        .baseUrl(TRAFFIC_ALERTS_BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val lyonTrafficApi: LyonTrafficAlertsEndpoint =
        trafficAlertsRetrofit.create(LyonTrafficAlertsEndpoint::class.java)

    private val lyonLineApi: LyonTransportLineApi =
        linesRetrofit.create(LyonTransportLineApi::class.java)

    private val lineApiWrapper = LyonTransportLineApiWrapper(lyonLineApi)

    override suspend fun getTrafficAlerts(): TrafficAlertsResponse {
        val lyonResponse = lyonTrafficApi.getLyonTrafficAlerts()
        return TrafficAlertMapper.mapResponseToGeneric(lyonResponse)
    }

    override suspend fun getMetroLines(
        SERVICE: String,
        VERSION: String,
        request: String,
        typename: String,
        outputFormat: String,
        SRSNAME: String,
        startIndex: Int,
        sortBy: String,
        count: Int
    ): FeatureCollection {
        return lineApiWrapper.getMetroLines(
            SERVICE, VERSION, request, typename, outputFormat, SRSNAME, startIndex, sortBy, count
        )
    }

    override suspend fun getTramLines(
        SERVICE: String,
        VERSION: String,
        request: String,
        typename: String,
        outputFormat: String,
        SRSNAME: String,
        startIndex: Int,
        sortBy: String,
        count: Int
    ): FeatureCollection {
        return lineApiWrapper.getTramLines(
            SERVICE, VERSION, request, typename, outputFormat, SRSNAME, startIndex, sortBy, count
        )
    }

    override suspend fun getBusLines(
        SERVICE: String,
        VERSION: String,
        request: String,
        typename: String,
        outputFormat: String,
        SRSNAME: String,
        startIndex: Int,
        sortBy: String,
        count: Int,
        cqlFilter: String?
    ): FeatureCollection {
        return lineApiWrapper.getBusLines(
            SERVICE, VERSION, request, typename, outputFormat, SRSNAME, startIndex, sortBy, count,
            cqlFilter
        )
    }

    override suspend fun getBusLineByName(
        SERVICE: String,
        VERSION: String,
        request: String,
        typename: String,
        outputFormat: String,
        SRSNAME: String,
        sortBy: String,
        count: Int,
        cqlFilter: String
    ): FeatureCollection {
        return lineApiWrapper.getBusLineByName(
            SERVICE, VERSION, request, typename, outputFormat, SRSNAME, sortBy, count, cqlFilter
        )
    }

    override suspend fun getNavigoneLines(
        SERVICE: String,
        VERSION: String,
        request: String,
        typename: String,
        outputFormat: String,
        SRSNAME: String,
        startIndex: Int,
        sortBy: String,
        count: Int
    ): FeatureCollection {
        return lineApiWrapper.getNavigoneLines(
            SERVICE, VERSION, request, typename, outputFormat, SRSNAME, startIndex, sortBy, count
        )
    }

    override suspend fun getTrambusLines(
        SERVICE: String,
        VERSION: String,
        request: String,
        typename: String,
        outputFormat: String,
        SRSNAME: String,
        startIndex: Int,
        sortBy: String,
        count: Int,
        cqlFilter: String
    ): FeatureCollection {
        return lineApiWrapper.getTrambusLines(
            SERVICE, VERSION, request, typename, outputFormat, SRSNAME, startIndex, sortBy, count,
            cqlFilter
        )
    }

    override suspend fun getTransportStops(
        SERVICE: String,
        VERSION: String,
        request: String,
        typename: String,
        outputFormat: String,
        SRSNAME: String,
        startIndex: Int,
        sortBy: String,
        count: Int
    ): StopCollection {
        return lineApiWrapper.getTransportStops(
            SERVICE, VERSION, request, typename, outputFormat, SRSNAME, startIndex, sortBy, count
        )
    }

    override suspend fun getSpecialLineRaw(
        SERVICE: String,
        VERSION: String,
        request: String,
        typename: String,
        outputFormat: String,
        SRSNAME: String,
        startIndex: Int,
        sortBy: String,
        count: Int
    ): JsonObject {
        return lyonLineApi.getSpecialLineRaw(
            SERVICE, VERSION, request, typename, outputFormat, SRSNAME, startIndex, sortBy, count
        )
    }

    companion object {
        /** Documented Pelo traffic alerts endpoint host; path is [LyonTrafficAlertsEndpoint]. */
        private const val TRAFFIC_ALERTS_BASE_URL = "https://api.dotshell.eu/"
    }
}
