package com.pelotcl.app.data.api

import com.pelotcl.app.data.model.FeatureCollection
import com.pelotcl.app.data.model.StopCollection
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit interface for Grand Lyon's WFS API
 */
interface GrandLyonApi {
    
    /**
     * Retrieves TCL metro/funicular lines from Grand Lyon's WFS API
     * 
     * @param service Service type (WFS)
     * @param version WFS protocol version (2.0.0)
     * @param request Request type (GetFeature)
     * @param typename Data layer name
     * @param outputFormat Output format (application/json)
     * @param srsName Spatial reference system (EPSG:4171)
     * @param startIndex Start index for pagination
     * @param sortBy Sort field
     * @param count Number of results to return
     */
    @GET("geoserver/sytral/ows")
    suspend fun getTransportLines(
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
     * Retrieves TCL tram lines from Grand Lyon's WFS API
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
     * Retrieves TCL bus lines from Grand Lyon's WFS API
     * Lines are sorted by gid to guarantee correct order
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
        @Query("count") count: Int = 10000
    ): FeatureCollection

    /**
     * Retrieves TCL navigone (river shuttle) lines from Grand Lyon's WFS API
     * This includes the NAVI1 line (Confluence-Île Barbe)
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
     * Retrieves TCL navigone (river shuttle) stops from Grand Lyon's WFS API
     * This includes stops for the NAV1 line
     */
    @GET("geoserver/sytral/ows")
    suspend fun getNavigoneStops(
        @Query("SERVICE") service: String = "WFS",
        @Query("VERSION") version: String = "2.0.0",
        @Query("request") request: String = "GetFeature",
        @Query("typename") typename: String = "sytral:tcl_sytral.tclarretfluv",
        @Query("outputFormat") outputFormat: String = "application/json",
        @Query("SRSNAME") srsName: String = "EPSG:4171",
        @Query("startIndex") startIndex: Int = 0,
        @Query("sortby") sortBy: String = "gid",
        @Query("count") count: Int = 1000
    ): com.pelotcl.app.data.model.StopCollection

    /**
     * Retrieves TCL transport stops from Grand Lyon's WFS API
     * 
     * @param service Service type (WFS)
     * @param version WFS protocol version (2.0.0)
     * @param request Request type (GetFeature)
     * @param typename Stops data layer name
     * @param outputFormat Output format (application/json)
     * @param srsName Spatial reference system (EPSG:4171)
     * @param startIndex Start index for pagination
     * @param sortBy Sort field
     * @param count Number of results to return
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
    ): com.pelotcl.app.data.model.StopCollection
}
