package com.pelotcl.app.core.data.network

import com.google.gson.JsonObject
import com.pelotcl.app.core.data.model.FeatureCollection
import com.pelotcl.app.core.data.model.StopCollection
import com.pelotcl.app.core.data.model.TrafficAlertsResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Interface abstraite pour les APIs de transport urbain
 * Doit être implémentée pour chaque ville/réseau
 */
interface TransportApi {

    @GET("geoserver/sytral/ows")
    suspend fun getTransportLines(
        @Query("SERVICE") service: String = "WFS",
        @Query("VERSION") version: String = "2.0.0",
        @Query("request") request: String = "GetFeature",
        @Query("typename") typename: String,
        @Query("outputFormat") outputFormat: String = "application/json",
        @Query("SRSNAME") srsName: String = "EPSG:4171",
        @Query("startIndex") startIndex: Int = 0,
        @Query("sortby") sortBy: String = "gid",
        @Query("count") count: Int = 1000
    ): FeatureCollection

    /**
     * Récupère les lignes de métro/funiculaire
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
     * Récupère les lignes de tramway
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
     * Récupère les lignes de bus
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
     * Récupère une ligne de bus par nom
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
     * Récupère les lignes de Navigone (fluviales)
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
     * Récupère les lignes de Trambus (lignes TB)
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
     * Récupère les arrêts de transport
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
     * Récupère les alertes trafic
     */
    @GET("https://api.dotshell.eu/pelo/v1/traffic/alerts")
    suspend fun getTrafficAlerts(): TrafficAlertsResponse

    /**
     * Récupère la géométrie brute pour les lignes spéciales
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