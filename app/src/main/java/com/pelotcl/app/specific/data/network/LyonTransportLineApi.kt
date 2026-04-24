package com.pelotcl.app.specific.data.network

import android.util.Log
import com.google.gson.JsonObject
import com.pelotcl.app.generic.data.models.FeatureCollection
import com.pelotcl.app.generic.data.models.StopCollection
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

    /**
     * Raw GeoJSON feature response (e.g. Rhônexpress) without Lyon model mapping.
     */
    @GET("geoserver/sytral/ows")
    suspend fun getSpecialLineRaw(
        @Query("SERVICE") service: String,
        @Query("VERSION") version: String,
        @Query("request") request: String,
        @Query("typename") typename: String,
        @Query("outputFormat") outputFormat: String,
        @Query("SRSNAME") srsName: String,
        @Query("startIndex") startIndex: Int,
        @Query("sortby") sortBy: String,
        @Query("count") count: Int
    ): JsonObject
}
