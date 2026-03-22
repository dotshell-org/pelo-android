package com.pelotcl.app.specific

import com.pelotcl.app.generic.data.network.TransportConfig

/**
 * Configuration spécifique de transport
 * Implémente TransportConfig pour fournir les paramètres
 */
object TransportConfigImpl : TransportConfig {
    
    override val baseUrl: String = "https://data.grandlyon.com/"
    
    override val networkName: String = "Réseau de transport"
    
    override val region: String = "Région métropolitaine"
    
    override val organizingAuthority: String = "Autorité organisatrice"
    
    override val dataSource: String = "Source de données"
    
    override val dataSourceUrl: String = "https://data.grandlyon.com"
    
    override val dataLicense: String = "Licence de données"
    
    // Bounding box de la région
    override val regionBounds: DoubleArray = doubleArrayOf(
        45.55, 4.65,  // Southwest
        45.95, 5.10   // Northeast
    )
    
    override val offlineMapZoomRange: IntRange = 8..16
    
    override val schoolHolidaysFile: String = "holidays.json"
    
    override val primaryColor: String = "#E60000" // Couleur primaire
    
    override val secondaryColor: String = "#000000" // Couleur secondaire
}