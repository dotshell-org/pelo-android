package com.pelotcl.app.specific

import com.google.gson.JsonObject
import com.pelotcl.app.generic.data.model.FeatureCollection
import com.pelotcl.app.generic.data.model.StopCollection
import com.pelotcl.app.generic.data.model.TrafficAlertsResponse
import com.pelotcl.app.generic.data.network.TransportApi
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Concrete implementation of TransportApi
 * Uses the transport API
 */
interface TransportApiImpl : TransportApi {

    /**
     * Fetches metro/funicular lines
     */
    @GET("geoserver/sytral/ows")
    override suspend fun getMetroLines(
        @Query("SERVICE") service: String,
        @Query("VERSION") version: String,
        @Query("request") request: String,
        @Query("typename") typename: String,
        @Query("outputFormat") outputFormat: String,
        @Query("SRSNAME") srsName: String,
        @Query("startIndex") startIndex: Int,
        @Query("sortby") sortBy: String,
        @Query("count") count: Int
    ): FeatureCollection

    /**
     * Fetches tram lines
     */
    @GET("geoserver/sytral/ows")
    override suspend fun getTramLines(
        @Query("SERVICE") service: String,
        @Query("VERSION") version: String,
        @Query("request") request: String,
        @Query("typename") typename: String,
        @Query("outputFormat") outputFormat: String,
        @Query("SRSNAME") srsName: String,
        @Query("startIndex") startIndex: Int,
        @Query("sortby") sortBy: String,
        @Query("count") count: Int
    ): FeatureCollection

    /**
     * Fetches bus lines
     */
    @GET("geoserver/sytral/ows")
    override suspend fun getBusLines(
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
    ): FeatureCollection

    /**
     * Fetches a bus line by name
     */
    @GET("geoserver/sytral/ows")
    override suspend fun getBusLineByName(
        @Query("SERVICE") service: String,
        @Query("VERSION") version: String,
        @Query("request") request: String,
        @Query("typename") typename: String,
        @Query("outputFormat") outputFormat: String,
        @Query("SRSNAME") srsName: String,
        @Query("sortby") sortBy: String,
        @Query("count") count: Int,
        @Query("cql_filter") cqlFilter: String
    ): FeatureCollection

    /**
     * Fetches Navigone (river) lines
     */
    @GET("geoserver/sytral/ows")
    override suspend fun getNavigoneLines(
        @Query("SERVICE") service: String,
        @Query("VERSION") version: String,
        @Query("request") request: String,
        @Query("typename") typename: String,
        @Query("outputFormat") outputFormat: String,
        @Query("SRSNAME") srsName: String,
        @Query("startIndex") startIndex: Int,
        @Query("sortby") sortBy: String,
        @Query("count") count: Int
    ): FeatureCollection

    /**
     * Fetches Trambus (TB) lines
     */
    @GET("geoserver/sytral/ows")
    override suspend fun getTrambusLines(
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
    ): FeatureCollection

    /**
     * Fetches transport stops
     */
    @GET("geoserver/sytral/ows")
    override suspend fun getTransportStops(
        @Query("SERVICE") service: String,
        @Query("VERSION") version: String,
        @Query("request") request: String,
        @Query("typename") typename: String,
        @Query("outputFormat") outputFormat: String,
        @Query("SRSNAME") srsName: String,
        @Query("startIndex") startIndex: Int,
        @Query("sortby") sortBy: String,
        @Query("count") count: Int
    ): StopCollection

    /**
     * Fetches traffic alerts
     * Uses custom Gson mapping to handle Lyon-specific field names
     */
    @GET("pelo/v1/traffic/alerts")
    override suspend fun getTrafficAlerts(): TrafficAlertsResponse

    /**
     * Fetches Rhônexpress geometry
     */
    @GET("geoserver/sytral/ows")
    override suspend fun getSpecialLineRaw(
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
