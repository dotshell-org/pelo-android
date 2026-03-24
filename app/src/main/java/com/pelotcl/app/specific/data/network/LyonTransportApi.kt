package com.pelotcl.app.specific.data.network

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pelotcl.app.generic.data.model.TrafficAlertsResponse
import com.pelotcl.app.generic.data.network.TransportApi
import com.pelotcl.app.specific.data.mapper.TrafficAlertMapper
import com.pelotcl.app.specific.data.model.LyonTrafficAlertsResponse
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET

/**
 * Lyon-specific implementation of TransportApi
 * Handles field name mapping between Lyon API and generic models
 */
class LyonTransportApi(private val baseUrl: String) : TransportApi {

    // Lyon-specific API interface that matches the actual Lyon API response
    interface LyonApi {
        @GET("pelo/v1/traffic/alerts")
        suspend fun getLyonTrafficAlerts(): LyonTrafficAlertsResponse
    }

    private val retrofit: Retrofit
    private val lyonApi: LyonApi

    init {
        retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        lyonApi = retrofit.create(LyonApi::class.java)
    }

    // Implement all TransportApi methods, handling Lyon-specific mapping where needed
    // For traffic alerts, we need to map from Lyon-specific to generic format
    override suspend fun getTrafficAlerts(): TrafficAlertsResponse {
        val lyonResponse = lyonApi.getLyonTrafficAlerts()
        return TrafficAlertMapper.mapResponseToGeneric(lyonResponse)
    }

    // TODO: Implement other TransportApi methods or delegate to another implementation
    // For now, we'll throw UnsupportedOperationException for methods not needed by TrafficAlerts
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
    ): com.pelotcl.app.generic.data.model.FeatureCollection {
        throw UnsupportedOperationException("Use TransportLineService for line data")
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
    ): com.pelotcl.app.generic.data.model.FeatureCollection {
        throw UnsupportedOperationException("Use TransportLineService for line data")
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
    ): com.pelotcl.app.generic.data.model.FeatureCollection {
        throw UnsupportedOperationException("Use TransportLineService for line data")
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
    ): com.pelotcl.app.generic.data.model.FeatureCollection {
        throw UnsupportedOperationException("Use TransportLineService for line data")
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
    ): com.pelotcl.app.generic.data.model.FeatureCollection {
        throw UnsupportedOperationException("Use TransportLineService for line data")
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
    ): com.pelotcl.app.generic.data.model.FeatureCollection {
        throw UnsupportedOperationException("Use TransportLineService for line data")
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
    ): com.pelotcl.app.generic.data.model.StopCollection {
        throw UnsupportedOperationException("Use TransportLineService for stop data")
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
    ): com.google.gson.JsonObject {
        throw UnsupportedOperationException("Use TransportLineService for special line data")
    }
}