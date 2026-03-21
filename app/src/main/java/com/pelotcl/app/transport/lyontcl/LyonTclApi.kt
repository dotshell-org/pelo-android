package com.pelotcl.app.transport.lyontcl

import com.google.gson.JsonObject
import com.pelotcl.app.core.data.model.FeatureCollection
import com.pelotcl.app.core.data.model.StopCollection
import com.pelotcl.app.core.data.model.TrafficAlertsResponse
import com.pelotcl.app.core.data.network.TransportApi
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Implémentation concrète de TransportApi pour Lyon TCL
 * Utilise l'API Grand Lyon / SYTRAL
 */
interface LyonTclApi : TransportApi {

    /**
     * Récupère les lignes de métro/funiculaire TCL
     */
    @GET("geoserver/sytral/ows")
    suspend fun getTclMetroLines(
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
     * Récupère les lignes de tramway TCL
     */
    @GET("geoserver/sytral/ows")
    suspend fun getTclTramLines(
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
     * Récupère les lignes de bus TCL
     */
    @GET("geoserver/sytral/ows")
    suspend fun getTclBusLines(
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
     * Récupère les lignes de métro/funiculaire
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
     * Récupère les lignes de tramway
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
     * Récupère les lignes de bus
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
     * Récupère une ligne de bus par nom
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
     * Récupère les lignes de Navigone (fluviales)
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
     * Récupère les lignes de Trambus (lignes TB)
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
     * Récupère les arrêts de transport
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
     * Récupère les alertes trafic TCL
     */
    @GET("https://api.dotshell.eu/pelo/v1/traffic/alerts")
    override suspend fun getTrafficAlerts(): TrafficAlertsResponse

    /**
     * Récupère la géométrie Rhônexpress
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
    ): JsonObject {
        return getSpecialLineRaw(
            service = service,
            version = version,
            request = request,
            typename = "sytral:rx_rhonexpress.rxligne_2_0_0",
            outputFormat = outputFormat,
            srsName = "EPSG:4326",
            startIndex = startIndex,
            sortBy = sortBy,
            count = count
        )
    }
}