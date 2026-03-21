package com.pelotcl.app.core.data.network

/**
 * Configuration de base pour une implémentation de transport
 * Chaque ville doit fournir sa propre implémentation
 */
interface TransportConfig {
    
    /**
     * URL de base de l'API
     */
    val baseUrl: String
    
    /**
     * Nom de la ville/réseau
     */
    val networkName: String
    
    /**
     * Région/zone couverte
     */
    val region: String
    
    /**
     * Autorité organisatrice (ex: SYTRAL pour Lyon)
     */
    val organizingAuthority: String
    
    /**
     * Source des données
     */
    val dataSource: String
    
    /**
     * URL de la source des données
     */
    val dataSourceUrl: String
    
    /**
     * Licence des données
     */
    val dataLicense: String
    
    /**
     * Bounding box de la région (minLat, minLon, maxLat, maxLon)
     * Pour la carte hors ligne
     */
    val regionBounds: DoubleArray
    
    /**
     * Niveaux de zoom pour la carte hors ligne
     */
    val offlineMapZoomRange: IntRange
    
    /**
     * Fichier de vacances scolaires
     */
    val schoolHolidaysFile: String
    
    /**
     * Couleur principale du réseau (pour le thème)
     */
    val primaryColor: String
    
    /**
     * Couleur secondaire du réseau
     */
    val secondaryColor: String
}