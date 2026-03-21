package com.pelotcl.app.data.network

import com.google.gson.JsonObject
import com.pelotcl.app.core.data.model.FeatureCollection
import com.pelotcl.app.core.data.model.StopCollection
import com.pelotcl.app.core.data.model.TrafficAlertsResponse
import com.pelotcl.app.transport.lyontcl.LyonTclApi
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Ancienne interface GrandLyonApi - maintenant dépréciée
 * Cette classe est maintenue pour la compatibilité mais utilise l'implémentation moderne
 * @see LyonTclApi pour la nouvelle implémentation
 */
@Deprecated(
    "Cette interface est dépréciée. Utilisez plutôt TransportApi via l'injection de dépendances.",
    ReplaceWith("TransportApi", "com.pelotcl.app.core.data.network.TransportApi")
)
interface GrandLyonApi : LyonTclApi {
    
    // Tous les endpoints sont hérités de LyonTclApi
    // Cette interface existe uniquement pour la rétrocompatibilité
    
    /**
     * @deprecated Utilisez plutôt TransportApi.getMetroLines()
     */
    @Deprecated(
        "Utilisez TransportApi.getMetroLines()",
        level = DeprecationLevel.WARNING
    )
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
     * @deprecated Utilisez plutôt TransportApi.getTramLines()
     */
    @Deprecated(
        "Utilisez TransportApi.getTramLines()",
        level = DeprecationLevel.WARNING
    )
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
     * @deprecated Utilisez plutôt TransportApi.getBusLines()
     */
    @Deprecated(
        "Utilisez TransportApi.getBusLines()",
        level = DeprecationLevel.WARNING
    )
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
     * @deprecated Utilisez plutôt TransportApi.getBusLineByName()
     */
    @Deprecated(
        "Utilisez TransportApi.getBusLineByName()",
        level = DeprecationLevel.WARNING
    )
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
     * @deprecated Utilisez plutôt TransportApi.getNavigoneLines()
     */
    @Deprecated(
        "Utilisez TransportApi.getNavigoneLines()",
        level = DeprecationLevel.WARNING
    )
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
     * @deprecated Utilisez plutôt TransportApi.getTrambusLines()
     */
    @Deprecated(
        "Utilisez TransportApi.getTrambusLines()",
        level = DeprecationLevel.WARNING
    )
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
     * @deprecated Utilisez plutôt TransportApi.getTransportStops()
     */
    @Deprecated(
        "Utilisez TransportApi.getTransportStops()",
        level = DeprecationLevel.WARNING
    )
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
     * @deprecated Utilisez plutôt TransportApi.getTrafficAlerts()
     */
    @Deprecated(
        "Utilisez TransportApi.getTrafficAlerts()",
        level = DeprecationLevel.WARNING
    )
    @GET("https://api.dotshell.eu/pelo/v1/traffic/alerts")
    override suspend fun getTrafficAlerts(): TrafficAlertsResponse

    /**
     * @deprecated Utilisez plutôt TransportApi.getSpecialLineRaw()
     */
    @Deprecated(
        "Utilisez TransportApi.getSpecialLineRaw()",
        level = DeprecationLevel.WARNING
    )
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