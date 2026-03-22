package com.pelotcl.app.generic.service

import android.content.Context
import com.pelotcl.app.generic.data.network.MapStyleConfig
import com.pelotcl.app.generic.data.network.TransportApi
import com.pelotcl.app.generic.data.network.TransportConfig
import com.pelotcl.app.generic.data.network.RetrofitInstance
import com.pelotcl.app.generic.data.network.TransportLineService
import com.pelotcl.app.generic.data.network.TrafficAlertsService
import com.pelotcl.app.generic.data.network.VehiclePositionsService
import com.pelotcl.app.generic.ui.theme.TransportTheme
import com.pelotcl.app.generic.ui.screens.about.AboutScreenContract
import com.pelotcl.app.specific.MapStyleConfigImpl
import com.pelotcl.app.specific.TransportConfigImpl
import com.pelotcl.app.specific.TransportLineServiceImpl
import com.pelotcl.app.specific.TrafficAlertsServiceImpl
import com.pelotcl.app.specific.VehiclePositionsServiceImpl
import com.pelotcl.app.specific.ui.screens.about.AboutScreenImpl
import com.pelotcl.app.specific.ui.theme.TransportThemeImpl

/**
 * Fournisseur de services pour l'application
 * Gère l'initialisation et fournit les implémentations concrètes
 * Remplace l'injection de dépendances pour une approche plus simple
 */
object TransportServiceProvider {
    
    private lateinit var transportConfig: TransportConfig
    private lateinit var transportApi: TransportApi
    private lateinit var transportTheme: TransportTheme
    private lateinit var aboutScreen: AboutScreenContract
    private lateinit var mapStyleConfig: MapStyleConfig
    private lateinit var vehiclePositionsService: VehiclePositionsService
    private lateinit var transportLineService: TransportLineService
    private lateinit var trafficAlertsService: TrafficAlertsService
    
    /**
     * Initialise le fournisseur avec la configuration Lyon TCL
     */
    fun initialize(context: Context) {
        // Configuration Lyon TCL
        transportConfig = TransportConfigImpl
        
        // Configuration des styles de carte Lyon TCL
        mapStyleConfig = MapStyleConfigImpl()
        
        // Service de positions des véhicules Lyon TCL
        vehiclePositionsService = VehiclePositionsServiceImpl()
        
        // Service des lignes de transport Lyon TCL
        transportLineService = TransportLineServiceImpl()
        
        // Service des alertes trafic Lyon TCL
        trafficAlertsService = TrafficAlertsServiceImpl()
        
        // Initialiser Retrofit avec la configuration
        RetrofitInstance.initialize(context, transportConfig)
        
        // Créer l'API
        transportApi = RetrofitInstance.getRetrofit(transportConfig)
            .create(TransportApi::class.java)
        
        // Thème Lyon TCL
        transportTheme = TransportThemeImpl()
        
        // Écrans "À propos" Lyon TCL
        aboutScreen = AboutScreenImpl()
        
        // Appliquer le thème par défaut
        com.pelotcl.app.generic.ui.theme.TransportThemeProvider.setTheme(transportTheme)
    }
    
    /**
     * Obtient la configuration de transport
     */
    fun getTransportConfig(): TransportConfig {
        if (!::transportConfig.isInitialized) {
            error("TransportServiceProvider not initialized. Call initialize() first.")
        }
        return transportConfig
    }
    
    /**
     * Obtient l'API de transport
     */
    fun getTransportApi(): TransportApi {
        if (!::transportApi.isInitialized) {
            error("TransportServiceProvider not initialized. Call initialize() first.")
        }
        return transportApi
    }
    
    /**
     * Obtient le thème de transport
     */
    fun getTransportTheme(): TransportTheme {
        if (!::transportTheme.isInitialized) {
            error("TransportServiceProvider not initialized. Call initialize() first.")
        }
        return transportTheme
    }
    
    /**
     * Obtient les écrans "À propos"
     */
    fun getAboutScreen(): AboutScreenContract {
        if (!::aboutScreen.isInitialized) {
            error("TransportServiceProvider not initialized. Call initialize() first.")
        }
        return aboutScreen
    }
    
    /**
     * Obtient la configuration des styles de carte
     */
    fun getMapStyleConfig(): MapStyleConfig {
        if (!::mapStyleConfig.isInitialized) {
            error("TransportServiceProvider not initialized. Call initialize() first.")
        }
        return mapStyleConfig
    }
    
    /**
     * Obtient le service de positions des véhicules
     */
    fun getVehiclePositionsService(): VehiclePositionsService {
        if (!::vehiclePositionsService.isInitialized) {
            error("TransportServiceProvider not initialized. Call initialize() first.")
        }
        return vehiclePositionsService
    }
    
    /**
     * Obtient le service des lignes de transport
     */
    fun getTransportLineService(): TransportLineService {
        if (!::transportLineService.isInitialized) {
            error("TransportServiceProvider not initialized. Call initialize() first.")
        }
        return transportLineService
    }
    
    /**
     * Obtient le service des alertes trafic
     */
    fun getTrafficAlertsService(): TrafficAlertsService {
        if (!::trafficAlertsService.isInitialized) {
            error("TransportServiceProvider not initialized. Call initialize() first.")
        }
        return trafficAlertsService
    }
    
    /**
     * Méthode utilitaire pour créer un repository avec l'API injectée
     */
    inline fun <reified T> createRepository(
        context: Context? = null,
        noinline creator: (TransportApi, Context?) -> T
    ): T {
        return creator(getTransportApi(), context)
    }
    
    /**
     * Méthode utilitaire pour créer un repository avec les services injectés
     */
    inline fun <reified T> createRepositoryWithServices(
        context: Context? = null,
        noinline creator: (TransportApi, MapStyleConfig, VehiclePositionsService, TransportLineService, TrafficAlertsService, Context?) -> T
    ): T {
        return creator(
            getTransportApi(),
            getMapStyleConfig(),
            getVehiclePositionsService(),
            getTransportLineService(),
            getTrafficAlertsService(),
            context
        )
    }
}
