package com.pelotcl.app.core.service

import android.content.Context
import com.pelotcl.app.core.data.network.TransportApi
import com.pelotcl.app.core.data.network.TransportConfig
import com.pelotcl.app.data.network.RetrofitInstance
import com.pelotcl.app.transport.lyontcl.LyonTclConfig
import com.pelotcl.app.transport.lyontcl.ui.theme.LyonTclTheme
import com.pelotcl.app.core.ui.theme.TransportTheme
import com.pelotcl.app.transport.lyontcl.ui.screens.about.LyonTclAboutScreen
import com.pelotcl.app.core.ui.screens.about.AboutScreenContract

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
    
    /**
     * Initialise le fournisseur avec la configuration Lyon TCL
     */
    fun initialize(context: Context) {
        // Configuration Lyon TCL
        transportConfig = LyonTclConfig
        
        // Initialiser Retrofit avec la configuration
        RetrofitInstance.initialize(context, transportConfig)
        
        // Créer l'API
        transportApi = RetrofitInstance.getRetrofit(transportConfig)
            .create(TransportApi::class.java)
        
        // Thème Lyon TCL
        transportTheme = LyonTclTheme()
        
        // Écrans "À propos" Lyon TCL
        aboutScreen = LyonTclAboutScreen()
        
        // Appliquer le thème par défaut
        com.pelotcl.app.core.ui.theme.TransportThemeProvider.setTheme(transportTheme)
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
     * Méthode utilitaire pour créer un repository avec l'API injectée
     */
    inline fun <reified T> createRepository(
        context: Context? = null,
        noinline creator: (TransportApi, Context?) -> T
    ): T {
        return creator(getTransportApi(), context)
    }
}