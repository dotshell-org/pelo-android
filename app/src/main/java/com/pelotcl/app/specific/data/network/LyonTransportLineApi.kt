package com.pelotcl.app.specific.data.network

import com.pelotcl.app.generic.data.model.FeatureCollection
import com.pelotcl.app.generic.data.model.StopCollection
import com.pelotcl.app.specific.data.mapper.TransportLineMapper
import com.pelotcl.app.specific.data.model.LyonFeatureCollection
import com.pelotcl.app.specific.data.model.LyonStopCollection
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Lyon-specific API interface that returns Lyon-specific models
 * These will be converted to generic models using the mapper
 */
interface LyonTransportLineApi {

    /**
     * Fetches metro/funicular lines - returns Lyon-specific model
     */
    @GET("geoserver/sytral/ows")
    suspend fun getMetroLinesRaw(
        @Query("SERVICE") service: String,
        @Query("VERSION") version: String,
        @Query("request") request: String,
        @Query("typename") typename: String,
        @Query("outputFormat") outputFormat: String,
        @Query("SRSNAME") srsName: String,
        @Query("startIndex") startIndex: Int,
        @Query("sortby") sortBy: String,
        @Query("count") count: Int
    ): LyonFeatureCollection

    /**
     * Fetches tram lines - returns Lyon-specific model
     */
    @GET("geoserver/sytral/ows")
    suspend fun getTramLinesRaw(
        @Query("SERVICE") service: String,
        @Query("VERSION") version: String,
        @Query("request") request: String,
        @Query("typename") typename: String,
        @Query("outputFormat") outputFormat: String,
        @Query("SRSNAME") srsName: String,
        @Query("startIndex") startIndex: Int,
        @Query("sortby") sortBy: String,
        @Query("count") count: Int
    ): LyonFeatureCollection

    /**
     * Fetches bus lines - returns Lyon-specific model
     */
    @GET("geoserver/sytral/ows")
    suspend fun getBusLinesRaw(
        @Query("SERVICE") service: String,
        @Query("VERSION") version: String,
        @Query("request") request: String,
        @Query("typename") typename: String,
        @Query("outputFormat") outputFormat: String,
        @Query("SRSNAME") srsName: String,
        @Query("startIndex") startIndex: Int,
        @Query("sortby") sortBy: String,
        @Query("count") count: Int,
        @Query("cql_filter") cqlFilter: String?
    ): LyonFeatureCollection

    /**
     * Fetches a bus line by name - returns Lyon-specific model
     */
    @GET("geoserver/sytral/ows")
    suspend fun getBusLineByNameRaw(
        @Query("SERVICE") service: String,
        @Query("VERSION") version: String,
        @Query("request") request: String,
        @Query("typename") typename: String,
        @Query("outputFormat") outputFormat: String,
        @Query("SRSNAME") srsName: String,
        @Query("sortby") sortBy: String,
        @Query("count") count: Int,
        @Query("cql_filter") cqlFilter: String
    ): LyonFeatureCollection

    /**
     * Fetches Navigone (river) lines - returns Lyon-specific model
     */
    @GET("geoserver/sytral/ows")
    suspend fun getNavigoneLinesRaw(
        @Query("SERVICE") service: String,
        @Query("VERSION") version: String,
        @Query("request") request: String,
        @Query("typename") typename: String,
        @Query("outputFormat") outputFormat: String,
        @Query("SRSNAME") srsName: String,
        @Query("startIndex") startIndex: Int,
        @Query("sortby") sortBy: String,
        @Query("count") count: Int
    ): LyonFeatureCollection

    /**
     * Fetches Trambus (TB) lines - returns Lyon-specific model
     */
    @GET("geoserver/sytral/ows")
    suspend fun getTrambusLinesRaw(
        @Query("SERVICE") service: String,
        @Query("VERSION") version: String,
        @Query("request") request: String,
        @Query("typename") typename: String,
        @Query("outputFormat") outputFormat: String,
        @Query("SRSNAME") srsName: String,
        @Query("startIndex") startIndex: Int,
        @Query("sortby") sortBy: String,
        @Query("count") count: Int,
        @Query("cql_filter") cqlFilter: String
    ): LyonFeatureCollection

    /**
     * Fetches transport stops - returns Lyon-specific model
     */
    @GET("geoserver/sytral/ows")
    suspend fun getTransportStopsRaw(
        @Query("SERVICE") service: String,
        @Query("VERSION") version: String,
        @Query("request") request: String,
        @Query("typename") typename: String,
        @Query("outputFormat") outputFormat: String,
        @Query("SRSNAME") srsName: String,
        @Query("startIndex") startIndex: Int,
        @Query("sortby") sortBy: String,
        @Query("count") count: Int
    ): LyonStopCollection
}

/**
 * Wrapper class that converts Lyon-specific API responses to generic models
 */
class LyonTransportLineApiWrapper(private val lyonApi: LyonTransportLineApi) {

    suspend fun getMetroLines(
        service: String, version: String, request: String, typename: String,
        outputFormat: String, srsName: String, startIndex: Int, sortBy: String, count: Int
    ): FeatureCollection {
        val lyonResponse = lyonApi.getMetroLinesRaw(service, version, request, typename, outputFormat, srsName, startIndex, sortBy, count)
        return TransportLineMapper.mapToGeneric(lyonResponse)
    }

    suspend fun getTramLines(
        service: String, version: String, request: String, typename: String,
        outputFormat: String, srsName: String, startIndex: Int, sortBy: String, count: Int
    ): FeatureCollection {
        val lyonResponse = lyonApi.getTramLinesRaw(service, version, request, typename, outputFormat, srsName, startIndex, sortBy, count)
        return TransportLineMapper.mapToGeneric(lyonResponse)
    }

    suspend fun getBusLines(
        service: String, version: String, request: String, typename: String,
        outputFormat: String, srsName: String, startIndex: Int, sortBy: String, count: Int, cqlFilter: String?
    ): FeatureCollection {
        val lyonResponse = lyonApi.getBusLinesRaw(service, version, request, typename, outputFormat, srsName, startIndex, sortBy, count, cqlFilter)
        return TransportLineMapper.mapToGeneric(lyonResponse)
    }

    suspend fun getBusLineByName(
        service: String, version: String, request: String, typename: String,
        outputFormat: String, srsName: String, sortBy: String, count: Int, cqlFilter: String
    ): FeatureCollection {
        val lyonResponse = lyonApi.getBusLineByNameRaw(service, version, request, typename, outputFormat, srsName, sortBy, count, cqlFilter)
        return TransportLineMapper.mapToGeneric(lyonResponse)
    }

    suspend fun getNavigoneLines(
        service: String, version: String, request: String, typename: String,
        outputFormat: String, srsName: String, startIndex: Int, sortBy: String, count: Int
    ): FeatureCollection {
        val lyonResponse = lyonApi.getNavigoneLinesRaw(service, version, request, typename, outputFormat, srsName, startIndex, sortBy, count)
        return TransportLineMapper.mapToGeneric(lyonResponse)
    }

    suspend fun getTrambusLines(
        service: String, version: String, request: String, typename: String,
        outputFormat: String, srsName: String, startIndex: Int, sortBy: String, count: Int, cqlFilter: String
    ): FeatureCollection {
        val lyonResponse = lyonApi.getTrambusLinesRaw(service, version, request, typename, outputFormat, srsName, startIndex, sortBy, count, cqlFilter)
        return TransportLineMapper.mapToGeneric(lyonResponse)
    }

    suspend fun getTransportStops(
        service: String, version: String, request: String, typename: String,
        outputFormat: String, srsName: String, startIndex: Int, sortBy: String, count: Int
    ): StopCollection {
        val lyonResponse = lyonApi.getTransportStopsRaw(service, version, request, typename, outputFormat, srsName, startIndex, sortBy, count)
        return com.pelotcl.app.specific.data.mapper.StopMapper.mapToGeneric(lyonResponse)
    }
}
