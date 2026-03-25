package com.pelotcl.app.generic.service

import android.content.Context
import com.pelotcl.app.generic.data.network.MapStyleConfig
import com.pelotcl.app.generic.data.network.TransportApi
import com.pelotcl.app.generic.data.network.TransportConfig
import com.pelotcl.app.generic.data.network.TransportLineRules
import com.pelotcl.app.generic.data.network.RetrofitInstance
import com.pelotcl.app.generic.data.network.TransportLineService
import com.pelotcl.app.generic.data.network.TrafficAlertsService
import com.pelotcl.app.generic.data.network.VehiclePositionsService
import com.pelotcl.app.specific.data.rules.LyonTransportLineRules
import com.pelotcl.app.specific.data.network.LyonTransportApi
import com.pelotcl.app.generic.ui.theme.TransportTheme
import com.pelotcl.app.generic.ui.screens.about.AboutScreenContract
import com.pelotcl.app.specific.MapStyleConfigImpl
import com.pelotcl.app.specific.TransportConfigImpl
import com.pelotcl.app.specific.TransportLineServiceImpl
import com.pelotcl.app.specific.TrafficAlertsServiceImpl
import com.pelotcl.app.specific.VehiclePositionsServiceImpl
import com.pelotcl.app.specific.ui.screens.settings.AboutScreenImpl
import com.pelotcl.app.specific.ui.theme.TransportThemeImpl

/**
 * Service provider for the application
 * Manages initialization and provides concrete implementations
 * Replaces dependency injection for a simpler approach
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
    private lateinit var transportLineRules: TransportLineRules

    /**
     * Initializes the provider with Lyon TCL configuration
     */
    fun initialize(context: Context) {
        // Lyon TCL configuration
        transportConfig = TransportConfigImpl

        // Lyon TCL map style configuration
        mapStyleConfig = MapStyleConfigImpl()

        // Lyon TCL vehicle positions service
        vehiclePositionsService = VehiclePositionsServiceImpl()

        // Lyon TCL transport line service
        transportLineService = TransportLineServiceImpl()

        // Lyon-specific rules for matching/normalizing line names
        transportLineRules = LyonTransportLineRules()

        // Lyon TCL traffic alerts service
        trafficAlertsService = TrafficAlertsServiceImpl()

        // Initialize Retrofit with the configuration
        RetrofitInstance.initialize(context, transportConfig)

        // Create the API - use LyonTransportApi for Lyon-specific field mapping
        transportApi = LyonTransportApi(transportConfig.baseUrl)

        // Lyon TCL theme
        transportTheme = TransportThemeImpl()

        // Lyon TCL "About" screens
        aboutScreen = AboutScreenImpl()

        // Apply the default theme
        com.pelotcl.app.generic.ui.theme.TransportThemeProvider.setTheme(transportTheme)
    }

    /**
     * Gets the transport configuration
     */
    fun getTransportConfig(): TransportConfig {
        if (!::transportConfig.isInitialized) {
            error("TransportServiceProvider not initialized. Call initialize() first.")
        }
        return transportConfig
    }

    /**
     * Gets the transport API
     */
    fun getTransportApi(): TransportApi {
        if (!::transportApi.isInitialized) {
            error("TransportServiceProvider not initialized. Call initialize() first.")
        }
        return transportApi
    }

    /**
     * Gets the map style configuration
     */
    fun getMapStyleConfig(): MapStyleConfig {
        if (!::mapStyleConfig.isInitialized) {
            error("TransportServiceProvider not initialized. Call initialize() first.")
        }
        return mapStyleConfig
    }

    fun getTransportLineRules(): TransportLineRules {
        if (!::transportLineRules.isInitialized) {
            error("TransportServiceProvider not initialized. Call initialize() first.")
        }
        return transportLineRules
    }

    /**
     * Gets the vehicle positions service
     */
    fun getVehiclePositionsService(): VehiclePositionsService {
        if (!::vehiclePositionsService.isInitialized) {
            error("TransportServiceProvider not initialized. Call initialize() first.")
        }
        return vehiclePositionsService
    }
}
