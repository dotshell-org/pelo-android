package com.pelotcl.app.utils

import com.pelotcl.app.data.model.StopFeature

enum class TransportType {
    BUS,
    METRO,
    TRAM,
    FUNICULAR,
    UNKNOWN
}

fun getTransportType(lineName: String): TransportType {
    return when {
        lineName.matches(Regex("T[1-7]")) -> TransportType.TRAM
        lineName in listOf("A", "B", "C", "D") -> TransportType.METRO
        lineName in listOf("F1", "F2") -> TransportType.FUNICULAR
        // Les lignes de bus peuvent être des numéros, des "C" suivis de numéros, ou d'autres lettres.
        // C'est plus simple de considérer tout le reste comme un bus par défaut.
        else -> TransportType.BUS
    }
}

data class Connection(val lineName: String, val transportType: TransportType)

/**
 * Utilitaire pour gérer les correspondances de transport
 */
object ConnectionsHelper {
    
    /**
     * Normalise un nom de station pour la comparaison
     * Garde uniquement les lettres et convertit en minuscules
     */
    private fun normalizeStationName(name: String): String {
        return name.filter { it.isLetter() }.lowercase()
    }
    
    /**
     * Normalise les mots d'un nom de station en gérant les abréviations
     */
    private fun normalizeWords(name: String): List<String> {
        val ignoredWords = setOf("gare", "station", "arret")
        
        return name.split(Regex("[\\s\\-,.]+"))
            .filter { it.isNotEmpty() }
            .map { word -> 
                word.filter { it.isLetter() }.lowercase()
            }
            .filter { it.isNotEmpty() && it !in ignoredWords }
    }
    
    /**
     * Compare deux noms de station de manière flexible
     * Gère les abréviations : "L. Pradel" match avec "Louis Pradel"
     */
    private fun stationNamesMatch(name1: String, name2: String): Boolean {
        // D'abord essayer la comparaison simple
        val normalized1 = normalizeStationName(name1)
        val normalized2 = normalizeStationName(name2)
        
        if (normalized1 == normalized2) {
            return true
        }
        
        // Ensuite essayer avec les mots séparés pour gérer les abréviations
        val words1 = normalizeWords(name1)
        val words2 = normalizeWords(name2)
        
        // Si un des deux noms a moins de mots, c'est peut-être une version abrégée
        if (words1.size != words2.size) {
            val shorter = if (words1.size < words2.size) words1 else words2
            val longer = if (words1.size < words2.size) words2 else words1
            
            var shorterIndex = 0
            var longerIndex = 0
            
            while (shorterIndex < shorter.size && longerIndex < longer.size) {
                val shortWord = shorter[shorterIndex]
                val longWord = longer[longerIndex]
                
                when {
                    shortWord == longWord -> {
                        shorterIndex++
                        longerIndex++
                    }
                    shortWord.length >= 3 && longWord.startsWith(shortWord) -> {
                        shorterIndex++
                        longerIndex++
                    }
                    shortWord.length == 1 && longWord.startsWith(shortWord) -> {
                        shorterIndex++
                        longerIndex++
                    }
                    shortWord.length > longWord.length -> {
                        var combinedWord = longWord
                        var tempIndex = longerIndex + 1
                        var matched = false
                        
                        while (tempIndex < longer.size && combinedWord.length < shortWord.length) {
                            combinedWord += longer[tempIndex]
                            if (combinedWord == shortWord) {
                                longerIndex = tempIndex + 1
                                shorterIndex++
                                matched = true
                                break
                            }
                            tempIndex++
                        }
                        
                        if (!matched) {
                            longerIndex++
                        }
                    }
                    else -> longerIndex++
                }
            }
            
            return shorterIndex == shorter.size
        }
        
        // Vérifier que tous les mots matchent dans l'ordre
        if (words1.size == words2.size) {
            return words1.zip(words2).all { (w1, w2) ->
                w1 == w2 || 
                (w1.length == 1 && w2.startsWith(w1)) ||
                (w2.length == 1 && w1.startsWith(w2)) ||
                (w1.length >= 3 && w2.startsWith(w1)) ||
                (w2.length >= 3 && w1.startsWith(w2))
            }
        }
        
        return false
    }
    
    /**
     * Parse le champ desserte et extrait les lignes de métro (A, B, C, D),
     * de funiculaire (F1, F2) et de tram (T1-T7)
     * 
     * Format du champ desserte :
     * - "A:A" = Métro A en direction Aller (le :A est la direction, pas la ligne!)
     * - "5:A,86:A" = Bus 5 et 86 en direction Aller
     * - "A:A,D:A" = Métros A et D
     * - "F1:A,F2:A" = Funiculaires F1 et F2
     * - "T1:A,T2:A" = Trams T1 et T2
     * 
     * IMPORTANT: Ne pas confondre ":A" (direction Aller) avec la ligne de métro A
     * 
     * @param desserte Le champ desserte de l'arrêt
     * @return Liste des lignes de métro, funiculaire et tram
     */
    fun parseMetroFunicularAndTramConnections(desserte: String): List<String> {
        val connections = mutableSetOf<String>()
        
        if (desserte.isBlank()) return emptyList()
        
        // Si la chaîne contient des virgules, chaque entrée représente une ligne avec un sens
        val entries = desserte.split(",")
        
        for (entry in entries) {
            val trimmed = entry.trim()
            if (trimmed.isEmpty()) continue
            
            // Extraire la ligne (avant le premier ":")
            val lineName = trimmed.substringBefore(":").trim()
            
            // Vérifier si c'est une ligne de métro (A, B, C, D)
            if (lineName in listOf("A", "B", "C", "D")) {
                connections.add(lineName)
            }
            
            // Vérifier si c'est une ligne de funiculaire (F1, F2)
            if (lineName in listOf("F1", "F2")) {
                connections.add(lineName)
            }
            
            // Vérifier si c'est une ligne de tram (T1 à T7)
            if (lineName.matches(Regex("T[1-7]"))) {
                connections.add(lineName)
            }
        }
        
        // Trier pour avoir un ordre cohérent : A, B, C, D, F1, F2, T1-T7
        return connections.sortedWith(compareBy { line ->
            when {
                line in listOf("A", "B", "C", "D") -> line[0].code
                line.startsWith("F") -> 100 + line.substring(1).toIntOrNull()!! 
                line.startsWith("T") -> 200 + line.substring(1).toIntOrNull()!!
                else -> 9999
            }
        })
    }
    
    /**
     * Parse le champ desserte et extrait toutes les lignes (y compris les lignes de bus)
     * 
     * Format du champ desserte :
     * - "A:A" = Métro A en direction Aller (le :A est la direction, pas la ligne!)
     * - "5:A,86:A" = Bus 5 et 86 en direction Aller
     * - "A:A,D:A" = Métros A et D
     * - "F1:A,F2:A" = Funiculaires F1 et F2
     * - "T1:A,T2:A" = Trams T1 et T2
     * 
     * IMPORTANT: Ne pas confondre ":A" (direction Aller) avec la ligne de métro A
     * 
     * @param desserte Le champ desserte de l'arrêt
     * @return Liste de toutes les lignes présentes dans la desserte
     */
    fun parseAllConnections(desserte: String): List<Connection> {
        val connections = mutableListOf<Connection>()
        if (desserte.isBlank()) return connections

        val lines = desserte.split(",")
        for (line in lines) {
            val parts = line.trim().split(":")
            if (parts.size == 2) {
                val lineName = parts[0]
                val direction = parts[1]
                val transportType = getTransportType(lineName)
                connections.add(Connection(lineName, transportType))
            }
        }
        return connections.distinctBy { it.lineName }
    }

    /**
     * Trouve les correspondances de métro et funiculaire pour un arrêt donné
     * en cherchant dans la liste de tous les arrêts
     * 
     * @param stopName Nom de l'arrêt à rechercher
     * @param currentLine Ligne actuelle (à exclure des correspondances)
     * @param allStops Liste de tous les arrêts disponibles
     * @return Liste des lignes de métro et funiculaire en correspondance
     */
    fun findConnectionsForStop(
        stopName: String,
        currentLine: String,
        allStops: List<StopFeature>
    ): List<String> {
        // Chercher les arrêts qui correspondent au nom
        val matchingStops = allStops.filter { stop ->
            stationNamesMatch(stop.properties.nom, stopName)
        }
        
        // Extraire toutes les connexions depuis les dessertes
        val allConnections = mutableSetOf<String>()
        for (stop in matchingStops) {
            val connections = parseMetroFunicularAndTramConnections(stop.properties.desserte)
            allConnections.addAll(connections)
        }
        
        // Enlever la ligne actuelle de la liste des correspondances
        allConnections.remove(currentLine)
        
        // Trier pour avoir un ordre cohérent
        return allConnections.sortedWith(compareBy { line ->
            when (line) {
                "A" -> 1
                "B" -> 2
                "C" -> 3
                "D" -> 4
                "F1" -> 5
                "F2" -> 6
                else -> 99
            }
        })
    }
}
