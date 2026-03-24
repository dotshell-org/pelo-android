package com.pelotcl.app.generic.data.network

import com.google.gson.JsonObject
import com.pelotcl.app.generic.data.model.FeatureCollection
import com.pelotcl.app.generic.data.model.StopCollection
import com.pelotcl.app.generic.data.model.TrafficAlertsResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Abstract interface for urban transport APIs
 * Must be implemented for each city/network
 */
interface TransportApi {

    /**
     * Fetches metro/funicular lines
     */
    @GET("geoserver/sytral/ows")
    suspend fun getMetroLines(
        @Query("SERVICE") service: String = "WFS",
        @Query("VERSION") version: String = "2.0.0",
        @Query("request") request: String = "GetFeature",
        @Query("typename") typename: String = "sytral:tcl_sytral.tcllignemf_2_0_0",
        @Query("outputFormat") outputFormat: String = "application/json",
        @Query("SRSNAME") srsName: String = "EPSG:4171",
        @Query("startIndex") startIndex: Int = 0,
        @Query("sortby") sortBy: String = "gid",
        @Query("count") count: Int = 1000
    ): FeatureCollection

    /**
     * Fetches tram lines
     */
    @GET("geoserver/sytral/ows")
    suspend fun getTramLines(
        @Query("SERVICE") service: String = "WFS",
        @Query("VERSION") version: String = "2.0.0",
        @Query("request") request: String = "GetFeature",
        @Query("typename") typename: String = "sytral:tcl_sytral.tcllignetram_2_0_0",
        @Query("outputFormat") outputFormat: String = "application/json",
        @Query("SRSNAME") srsName: String = "EPSG:4171",
        @Query("startIndex") startIndex: Int = 0,
        @Query("sortby") sortBy: String = "gid",
        @Query("count") count: Int = 1000
    ): FeatureCollection

    /**
     * Fetches bus lines
     */
    @GET("geoserver/sytral/ows")
    suspend fun getBusLines(
        @Query("SERVICE") service: String = "WFS",
        @Query("VERSION") version: String = "2.0.0",
        @Query("request") request: String = "GetFeature",
        @Query("typename") typename: String = "sytral:tcl_sytral.tcllignebus_2_0_0",
        @Query("outputFormat") outputFormat: String = "application/json",
        @Query("SRSNAME") srsName: String = "EPSG:4171",
        @Query("startIndex") startIndex: Int = 0,
        @Query("sortby") sortBy: String = "gid",
        @Query("count") count: Int = 10000,
        @Query("cql_filter") cqlFilter: String? = null
    ): FeatureCollection

    /**
     * Fetches a bus line by name
     */
    @GET("geoserver/sytral/ows")
    suspend fun getBusLineByName(
        @Query("SERVICE") service: String = "WFS",
        @Query("VERSION") version: String = "2.0.0",
        @Query("request") request: String = "GetFeature",
        @Query("typename") typename: String,
        @Query("outputFormat") outputFormat: String = "application/json",
        @Query("SRSNAME") srsName: String = "EPSG:4171",
        @Query("sortby") sortBy: String = "gid",
        @Query("count") count: Int = 10,
        @Query("cql_filter") cqlFilter: String
    ): FeatureCollection

    /**
     * Fetches Navigone (river) lines
     */
    @GET("geoserver/sytral/ows")
    suspend fun getNavigoneLines(
        @Query("SERVICE") service: String = "WFS",
        @Query("VERSION") version: String = "2.0.0",
        @Query("request") request: String = "GetFeature",
        @Query("typename") typename: String = "sytral:tcl_sytral.tcllignefluv",
        @Query("outputFormat") outputFormat: String = "application/json",
        @Query("SRSNAME") srsName: String = "EPSG:4171",
        @Query("startIndex") startIndex: Int = 0,
        @Query("sortby") sortBy: String = "gid",
        @Query("count") count: Int = 1000
    ): FeatureCollection

    /**
     * Fetches Trambus (TB) lines
     */
    @GET("geoserver/sytral/ows")
    suspend fun getTrambusLines(
        @Query("SERVICE") service: String = "WFS",
        @Query("VERSION") version: String = "2.0.0",
        @Query("request") request: String = "GetFeature",
        @Query("typename") typename: String = "sytral:tcl_sytral.tcllignebus_2_0_0",
        @Query("outputFormat") outputFormat: String = "application/json",
        @Query("SRSNAME") srsName: String = "EPSG:4171",
        @Query("startIndex") startIndex: Int = 0,
        @Query("sortby") sortBy: String = "gid",
        @Query("count") count: Int = 1000,
        @Query("cql_filter") cqlFilter: String = "ligne LIKE 'TB%'"
    ): FeatureCollection

    /**
     * Fetches transport stops
     */
    @GET("geoserver/sytral/ows")
    suspend fun getTransportStops(
        @Query("SERVICE") service: String = "WFS",
        @Query("VERSION") version: String = "2.0.0",
        @Query("request") request: String = "GetFeature",
        @Query("typename") typename: String = "sytral:tcl_sytral.tclarret",
        @Query("outputFormat") outputFormat: String = "application/json",
        @Query("SRSNAME") srsName: String = "EPSG:4171",
        @Query("startIndex") startIndex: Int = 0,
        @Query("sortby") sortBy: String = "gid",
        @Query("count") count: Int = 10000
    ): StopCollection

    /**
     * Fetches traffic alerts
     */
    @GET("https://api.dotshell.eu/pelo/v1/traffic/alerts")
    suspend fun getTrafficAlerts(): TrafficAlertsResponse

    /**
     * Fetches raw geometry for special lines
     */
    @GET("geoserver/sytral/ows")
    suspend fun getSpecialLineRaw(
        @Query("SERVICE") service: String = "WFS",
        @Query("VERSION") version: String = "2.0.0",
        @Query("request") request: String = "GetFeature",
        @Query("typename") typename: String,
        @Query("outputFormat") outputFormat: String = "application/json",
        @Query("SRSNAME") srsName: String = "EPSG:4326",
        @Query("startIndex") startIndex: Int = 0,
        @Query("sortby") sortBy: String = "gid",
        @Query("count") count: Int = 1000
    ): JsonObject
}
