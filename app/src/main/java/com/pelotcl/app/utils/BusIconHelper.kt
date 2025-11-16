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
        val lines = getAllLinesForStop(stopFeature)
        if (lines.isEmpty()) return null
        val firstLine = lines.first()
        return getDrawableNameForLine(firstLine)
    }
    
    /**
     * Retourne toutes les lignes qui desservent un arrêt (noms des lignes)
     */
    fun getAllLinesForStop(stopFeature: StopFeature): List<String> {
        return parseDesserte(stopFeature.properties.desserte)
    }

    /**
     * Retourne les noms des drawables pour toutes les lignes d'un arrêt, in order
     */
    fun getAllDrawableNamesForStop(stopFeature: StopFeature): List<String> {
        return getAllLinesForStop(stopFeature).map { getDrawableNameForLine(it) }
    }
    
    /**
     * Parse la chaîne desserte pour extraire la liste des lignes.
     * Cas gérés:
     *  - "5:A,86:A,JD844:R" -> ["5", "86", "JD844"] (bus avec directions)
     *  - "A:A,D:A" -> ["A", "D"] (métro A et D, :A = direction Aller)
     *  - "F1:A,F2:A" -> ["F1", "F2"] (funiculaires)
     *  - "M:A:B" -> ["M", "B"] (bus M avec plusieurs destinations, ignorer :A/:R)
     *  - "C17:22:31" (ancien format) -> ["C17", "22", "31"]
     * 
     * IMPORTANT: Ne pas confondre ":A" (direction Aller) avec la ligne de métro A
     */
    private fun parseDesserte(desserte: String): List<String> {
        if (desserte.isBlank()) return emptyList()
        
        // If string contains commas, each entry represents a line with direction (e.g.: 5:A or A:A)
        val entries = desserte.split(",")
        val rawLines: List<String> = if (entries.size > 1) {
            // Format avec virgules: "5:A,86:A" ou "A:A,D:A"
            entries.mapNotNull { part ->
                val trimmed = part.trim()
                if (trimmed.isEmpty()) null else {
                    // Extraire la ligne (avant le premier ":")
                    trimmed.substringBefore(":").trim()
                }
            }.filter { it.isNotEmpty() }
        } else {
            // Pas de virgule: format ancien ou simple
            // E.g.: "M:A:B" ou "C17:22:31" or "A:A" (single stop)
            val tokens = desserte.split(":")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            if (tokens.isEmpty()) return emptyList()
            
            // For a single entry without comma comme "A:A" (metro A outbound direction),
            // on ne prend que le premier token
            if (tokens.size == 2) {
                val first = tokens[0]
                val second = tokens[1]
                // Si le second token est "A" ou "R" (direction), on ne garde que le premier
                if (second.uppercase() == "A" || second.uppercase() == "R") {
                    listOf(first)
                } else {
                    // Otherwise it might be an old format avec plusieurs lignes
                    tokens
                }
            } else if (tokens.size > 2) {
                // Format du type "M:A:B" -> garder M et B, ignorer A/R qui sont des directions
                val first = tokens.first()
                val rest = tokens.drop(1).filter { t ->
                    val up = t.uppercase()
                    // Ne pas garder les directions Aller/Retour seules
                    up != "A" && up != "R"
                }
                listOf(first) + rest
            } else {
                // Un seul token, pas de ":", on le garde tel quel
                tokens
            }
        }

        // Deduplicate while preserving order, case-insensitive comparison
        val seen = HashSet<String>()
        val unique = ArrayList<String>(rawLines.size)
        rawLines.forEach { line ->
            val key = line.uppercase()
            if (seen.add(key)) {
                unique.add(line)
            }
        }
        return unique
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
        
        // Check if line is composed only of digits
        val isNumericOnly = lineName.all { it.isDigit() }
        
        return if (isNumericOnly) {
            // Prefix with underscore for numeric lines
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
        
        // For now, assume all lines have an icon
        // This logic can be extended selon les besoins
        return true
    }
}