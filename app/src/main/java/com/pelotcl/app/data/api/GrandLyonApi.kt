package com.pelotcl.app.data.api

import com.pelotcl.app.data.model.FeatureCollection
import com.pelotcl.app.data.model.StopCollection
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Interface Retrofit pour l'API WFS de Grand Lyon
 */
interface GrandLyonApi {
    
    /**
     * Récupère les lignes de métro/funiculaire TCL depuis l'API WFS de Grand Lyon
     * 
     * @param service Type de service (WFS)
     * @param version Version du protocole WFS (2.0.0)
     * @param request Type de requête (GetFeature)
     * @param typename Nom de la couche de données
     * @param outputFormat Format de sortie (application/json)
     * @param srsName Système de référence spatiale (EPSG:4171)
     * @param startIndex Index de départ pour la pagination
     * @param sortBy Champ de tri
     * @param count Nombre de résultats à retourner
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
     * Récupère les lignes de tram TCL depuis l'API WFS de Grand Lyon
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
     * Récupère les lignes de bus TCL depuis l'API WFS de Grand Lyon
     * Les lignes sont triées par gid pour garantir l'ordre correct
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
     * Récupère les arrêts de transport TCL depuis l'API WFS de Grand Lyon
     * 
     * @param service Type de service (WFS)
     * @param version Version du protocole WFS (2.0.0)
     * @param request Type de requête (GetFeature)
     * @param typename Nom de la couche de données des arrêts
     * @param outputFormat Format de sortie (application/json)
     * @param srsName Système de référence spatiale (EPSG:4171)
     * @param startIndex Index de départ pour la pagination
     * @param sortBy Champ de tri
     * @param count Nombre de résultats à retourner
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
