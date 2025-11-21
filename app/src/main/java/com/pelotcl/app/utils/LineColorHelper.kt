package com.pelotcl.app.utils

import com.pelotcl.app.data.model.Feature

/**
 * Utilitaire pour déterminer la couleur d'une ligne de transport selon son type
 */
object LineColorHelper {
    
    // Defined colors
    private const val METRO_A_COLOR = "#EC4899"
    private const val METRO_B_COLOR = "#3B82F6"
    private const val METRO_C_COLOR = "#F59E0B"
    private const val METRO_D_COLOR = "#22C55E"
    private const val TRAM_COLOR = "#A855F7"
    private const val FUNICULAR_COLOR = "#84CC16"
    private const val NAVIGONE_COLOR = "#14b8a6" // Teal for water shuttle
    private const val BUS_COLOR = "#EF4444"
    
    /**
     * Retourne la couleur hexadécimale appropriée pour une ligne de transport
     * 
     * @param feature La feature contenant les informations de la ligne
     * @return La couleur en format hexadécimal (#RRGGBB)
     */
    fun getColorForLine(feature: Feature): String {
        val ligne = feature.properties.ligne
        val familleTransport = feature.properties.familleTransport
        val nomTypeLigne = feature.properties.nomTypeLigne?.lowercase() ?: ""
        
        return when {
            // Rhônexpress: affichage explicitement en rouge officiel RX
            ligne.equals("RX", ignoreCase = true) -> "#E30613"
            // Metros
            ligne == "A" && familleTransport == "MET" -> METRO_A_COLOR
            ligne == "B" && familleTransport == "MET" -> METRO_B_COLOR
            ligne == "C" && familleTransport == "MET" -> METRO_C_COLOR
            ligne == "D" && familleTransport == "MET" -> METRO_D_COLOR
            
            // Funicular (detection by line name or type)
            ligne == "F1" || ligne == "F2" || nomTypeLigne.contains("funiculaire") -> FUNICULAR_COLOR
            
            // Navigone (water shuttle) - famille_transport = "BAT" (bateau)
            familleTransport == "BAT" || ligne.uppercase().startsWith("NAV") || nomTypeLigne.contains("fluvial") -> NAVIGONE_COLOR
            
            // Trams (famille TRA ou TRAM)
            familleTransport == "TRA" || familleTransport == "TRAM" -> TRAM_COLOR
            
            // Bus (tout le reste, famille BUS)
            else -> BUS_COLOR
        }
    }
    
    /**
     * Retourne la couleur appropriée pour une ligne à partir de son nom
     * 
     * @param lineName Le nom de la ligne (ex: "A", "B", "T1", "C3", etc.)
     * @return La couleur en format android.graphics.Color
     */
    fun getColorForLineString(lineName: String): Int {
        val upperLine = lineName.uppercase()
        val hexColor = when {
            upperLine == "RX" -> "#E30613"
            upperLine == "A" -> METRO_A_COLOR
            upperLine == "B" -> METRO_B_COLOR
            upperLine == "C" -> METRO_C_COLOR
            upperLine == "D" -> METRO_D_COLOR
            upperLine == "F1" || upperLine == "F2" -> FUNICULAR_COLOR
            upperLine.startsWith("NAV") -> NAVIGONE_COLOR
            (upperLine.startsWith("T") && !upperLine.startsWith("TB")) -> TRAM_COLOR
            else -> BUS_COLOR
        }
        return android.graphics.Color.parseColor(hexColor)
    }
    
    /**
     * Retourne une description du type de ligne
     */
    fun getLineTypeDescription(feature: Feature): String {
        val ligne = feature.properties.ligne
        val familleTransport = feature.properties.familleTransport
        val nomTypeLigne = feature.properties.nomTypeLigne?.lowercase() ?: ""
        
        return when {
            familleTransport == "MET" -> "Métro $ligne"
            ligne == "F1" || ligne == "F2" || nomTypeLigne.contains("funiculaire") -> "Funiculaire $ligne"
            familleTransport == "BAT" || ligne.uppercase().startsWith("NAV") -> "Navigône $ligne"
            familleTransport == "TRA" || familleTransport == "TRAM" -> "Tram $ligne"
            else -> "Bus $ligne"
        }
    }
}
