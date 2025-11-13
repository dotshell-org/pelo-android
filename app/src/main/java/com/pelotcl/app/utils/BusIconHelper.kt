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
     * Retourne les noms des drawables pour toutes les lignes d'un arrêt, dans l'ordre
     */
    fun getAllDrawableNamesForStop(stopFeature: StopFeature): List<String> {
        return getAllLinesForStop(stopFeature).map { getDrawableNameForLine(it) }
    }
    
    /**
     * Parse la chaîne desserte pour extraire la liste des lignes.
     * Cas gérés:
     *  - "5:A,86:A,JD844:R" -> ["5", "86", "JD844"]
     *  - "M:A:B" -> ["M"] (les suffixes ":A"/":R" signifient Aller/Retour, à ignorer)
     *  - "A:B" (métro) -> ["A"]
     *  - "C17:22:31" (ancien format) -> ["C17", "22", "31"]
     */
    private fun parseDesserte(desserte: String): List<String> {
        if (desserte.isBlank()) return emptyList()
        
        // Si la chaîne contient des virgules, chaque entrée représente une ligne avec un sens (ex: 5:A)
        val entries = desserte.split(",")
        val rawLines: List<String> = if (entries.size > 1) {
            entries.mapNotNull { part ->
                val trimmed = part.trim()
                if (trimmed.isEmpty()) null else trimmed.substringBefore(":").trim()
            }.filter { it.isNotEmpty() }
        } else {
            // Pas de virgule: séparer par ":". Les tokens "A"/"R" (Aller/Retour) après le premier sont ignorés.
            val tokens = desserte.split(":")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            if (tokens.isEmpty()) return emptyList()
            val first = tokens.first()
            val rest = tokens.drop(1)
            val filteredRest = rest.filter { t ->
                val up = t.uppercase()
                // Ne pas garder les directions Aller/Retour
                up != "A" && up != "R"
            }
            // Conserver l'ordre: première vraie ligne + autres lignes valides éventuelles
            listOf(first) + filteredRest
        }

        // Dédupliquer en conservant l'ordre, comparaison insensible à la casse
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