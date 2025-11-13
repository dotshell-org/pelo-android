package com.pelotcl.app.utils

import com.pelotcl.app.data.model.Feature

/**
 * Utilitaire pour déterminer la couleur d'une ligne de transport selon son type
 */
object LineColorHelper {
    
    // Couleurs définies
    private const val METRO_A_COLOR = "#F472B6"  // Rose
    private const val METRO_B_COLOR = "#3B82F6"  // Bleu
    private const val METRO_C_COLOR = "#EAB308"  // Jaune
    private const val METRO_D_COLOR = "#22C55E"  // Vert
    private const val TRAM_COLOR = "#A855F7"     // Violet
    private const val FUNICULAR_COLOR = "#84CC16" // Lime
    private const val BUS_COLOR = "#EF4444"      // Rouge
    
    /**
     * Retourne la couleur hexadécimale appropriée pour une ligne de transport
     * 
     * @param feature La feature contenant les informations de la ligne
     * @return La couleur en format hexadécimal (#RRGGBB)
     */
    fun getColorForLine(feature: Feature): String {
        val ligne = feature.properties.ligne
        val familleTransport = feature.properties.familleTransport
        val nomTypeLigne = feature.properties.nomTypeLigne.lowercase()
        
        return when {
            // Métros
            ligne == "A" && familleTransport == "MET" -> METRO_A_COLOR
            ligne == "B" && familleTransport == "MET" -> METRO_B_COLOR
            ligne == "C" && familleTransport == "MET" -> METRO_C_COLOR
            ligne == "D" && familleTransport == "MET" -> METRO_D_COLOR
            
            // Funiculaire (détection par nom de ligne ou type)
            ligne == "F" || nomTypeLigne.contains("funiculaire") -> FUNICULAR_COLOR
            
            // Trams (famille TRA ou TRAM)
            familleTransport == "TRA" || familleTransport == "TRAM" -> TRAM_COLOR
            
            // Bus (tout le reste, famille BUS)
            else -> BUS_COLOR
        }
    }
    
    /**
     * Retourne une description du type de ligne
     */
    fun getLineTypeDescription(feature: Feature): String {
        val ligne = feature.properties.ligne
        val familleTransport = feature.properties.familleTransport
        val nomTypeLigne = feature.properties.nomTypeLigne.lowercase()
        
        return when {
            familleTransport == "MET" -> "Métro $ligne"
            ligne == "F" || nomTypeLigne.contains("funiculaire") -> "Funiculaire $ligne"
            familleTransport == "TRA" || familleTransport == "TRAM" -> "Tram $ligne"
            else -> "Bus $ligne"
        }
    }
}
