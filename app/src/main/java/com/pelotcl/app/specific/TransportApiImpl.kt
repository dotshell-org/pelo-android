package com.pelotcl.app.specific

import com.google.gson.JsonObject
import com.pelotcl.app.generic.data.model.FeatureCollection
import com.pelotcl.app.generic.data.model.StopCollection
import com.pelotcl.app.generic.data.model.TrafficAlertsResponse
import com.pelotcl.app.generic.data.network.TransportApi
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Implémentation concrète de TransportApi
 * Utilise l'API de transport
 */
interface TransportApiImpl : TransportApi {

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
     * Récupère les alertes trafic
     */
    @GET("pelo/v1/traffic/alerts")
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
    ): JsonObject
}
