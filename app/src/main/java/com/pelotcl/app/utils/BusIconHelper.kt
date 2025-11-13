package com.pelotcl.app.utils

import com.pelotcl.app.data.model.StopFeature

/**
 * Utilitaire pour déterminer les icônes appropriées pour les arrêts de bus
 */
object BusIconHelper {
    
    /**
     * Extrait la première ligne de bus d'un arrêt et retourne le nom du drawable correspondant
     * 
     * @param stopFeature L'arrêt de transport
     * @return Le nom du drawable (sans l'extension .xml) ou null si aucune ligne trouvée
     */
    fun getIconNameForStop(stopFeature: StopFeature): String? {
        val desserte = stopFeature.properties.desserte
        
        if (desserte.isBlank()) {
            return null
        }
        
        // Parse la desserte pour extraire les lignes (format: "M:A:B" ou "C17:22:31")
        val lines = parseDesserte(desserte)
        
        if (lines.isEmpty()) {
            return null
        }
        
        // Prendre la première ligne
        val firstLine = lines.first()
        
        // Convertir le nom de la ligne en nom de drawable
        return getDrawableNameForLine(firstLine)
    }
    
    /**
     * Retourne toutes les lignes qui desservent un arrêt
     * 
     * @param stopFeature L'arrêt de transport
     * @return Liste des noms de lignes
     */
    fun getAllLinesForStop(stopFeature: StopFeature): List<String> {
        return parseDesserte(stopFeature.properties.desserte)
    }
    
    /**
     * Parse la chaîne desserte pour extraire la liste des lignes
     * 
     * @param desserte Chaîne de desserte (ex: "M:A:B", "C17:22:31")
     * @return Liste des noms de lignes
     */
    private fun parseDesserte(desserte: String): List<String> {
        if (desserte.isBlank()) {
            return emptyList()
        }
        
        // Séparer par ":" et filtrer les valeurs vides
        return desserte.split(":")
            .filter { it.isNotBlank() }
            .map { it.trim() }
    }
    
    /**
     * Convertit un nom de ligne en nom de drawable
     * Les lignes composées uniquement de chiffres sont préfixées par un underscore
     * 
     * @param lineName Le nom de la ligne (ex: "212", "C17", "A")
     * @return Le nom du drawable correspondant (ex: "_212", "c17", "a")
     */
    private fun getDrawableNameForLine(lineName: String): String {
        if (lineName.isBlank()) {
            return ""
        }
        
        // Vérifier si la ligne est composée uniquement de chiffres
        val isNumericOnly = lineName.all { it.isDigit() }
        
        return if (isNumericOnly) {
            // Préfixer par underscore pour les lignes numériques
            "_$lineName"
        } else {
            // Convertir en minuscules pour les autres lignes
            lineName.lowercase()
        }
    }
    
    /**
     * Vérifie si un drawable existe pour une ligne donnée
     * Note: Cette fonction devrait être étendue pour vérifier l'existence réelle du fichier
     * 
     * @param lineName Le nom de la ligne
     * @return true si un drawable devrait exister, false sinon
     */
    fun hasIconForLine(lineName: String): Boolean {
        if (lineName.isBlank()) {
            return false
        }
        
        // Pour l'instant, on suppose que toutes les lignes ont une icône
        // Cette logique peut être étendue selon les besoins
        return true
    }
}