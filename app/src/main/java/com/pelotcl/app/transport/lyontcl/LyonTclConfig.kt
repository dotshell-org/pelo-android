package com.pelotcl.app.transport.lyontcl

import com.pelotcl.app.core.data.network.TransportConfig

/**
 * Configuration spécifique à Lyon TCL
 * Implémente TransportConfig pour fournir les paramètres Lyon
 */
object LyonTclConfig : TransportConfig {
    
    override val baseUrl: String = "https://data.grandlyon.com/"
    
    override val networkName: String = "TCL"
    
    override val region: String = "Lyon et son agglomération"
    
    override val organizingAuthority: String = "SYTRAL"
    
    override val dataSource: String = "Grand Lyon Métropole"
    
    override val dataSourceUrl: String = "https://data.grandlyon.com"
    
    override val dataLicense: String = "Licence Mobilités"
    
    // Bounding box de la métropole de Lyon
    override val regionBounds: DoubleArray = doubleArrayOf(
        45.55, 4.65,  // Southwest
        45.95, 5.10   // Northeast
    )
    
    override val offlineMapZoomRange: IntRange = 8..16
    
    override val schoolHolidaysFile: String = "holidays.json"
    
    override val primaryColor: String = "#E60000" // Rouge TCL
    
    override val secondaryColor: String = "#000000" // Noir
}