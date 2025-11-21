package com.pelotcl.app.data.gtfs

/**
 * Static cache of main line stops to improve performance
 * Avoids parsing the stop_times.txt file (>50MB) each time
 */
object LineStopsCache {
    
    /**
     * Normalizes a station name for comparison
     * Keeps only letters and converts to lowercase
     */
    private fun normalizeStationName(name: String): String {
        return name.filter { it.isLetter() }.lowercase()
    }
    
    /**
     * Normalizes the words of a station name handling abbreviations
     * Ex: "L." becomes "l", "Louis" becomes "louis"
     * Also filters generic words like "gare" which can be omitted
     */
    private fun normalizeWords(name: String): List<String> {
        val ignoredWords = setOf("gare", "station", "arret")
        
        return name.split(Regex("[\\s\\-,.]+"))
            .filter { it.isNotEmpty() }
            .map { word -> 
                // Remove dots and keep only letters
                word.filter { it.isLetter() }.lowercase()
            }
            .filter { it.isNotEmpty() && it !in ignoredWords }
    }
    
    /**
     * Compares two station names flexibly
     * Handles abbreviations: "L. Pradel" matches "Louis Pradel"
     * Also handles word abbreviations: "Cat." matches "Cathédrale"
     */
    private fun stationNamesMatch(name1: String, name2: String): Boolean {
        // First try simple comparison
        val normalized1 = normalizeStationName(name1)
        val normalized2 = normalizeStationName(name2)
        
        if (normalized1 == normalized2) {
            return true
        }
        
        // Then try with separated words to handle abbreviations
        val words1 = normalizeWords(name1)
        val words2 = normalizeWords(name2)
        
        // If one of the two names has fewer words, it might be an abbreviated version
        if (words1.size != words2.size) {
            val shorter = if (words1.size < words2.size) words1 else words2
            val longer = if (words1.size < words2.size) words2 else words1
            
            // Check if all words from the short version match with the long version
            // Either exactly or as beginning of word (for abbreviations)
            // Also handles compound words (ex: "partdieu" matches "part" + "dieu")
            var shorterIndex = 0
            var longerIndex = 0
            
            while (shorterIndex < shorter.size && longerIndex < longer.size) {
                val shortWord = shorter[shorterIndex]
                val longWord = longer[longerIndex]
                
                // Exact match
                if (shortWord == longWord) {
                    shorterIndex++
                    longerIndex++
                }
                // The long word starts with the short word (ex: "cat" matches "cathedrale")
                // Minimum 3 letters to avoid false positives
                else if (shortWord.length >= 3 && longWord.startsWith(shortWord)) {
                    shorterIndex++
                    longerIndex++
                }
                // The short word is an initial of the long word (ex: "l" matches "louis")
                else if (shortWord.length == 1 && longWord.startsWith(shortWord)) {
                    shorterIndex++
                    longerIndex++
                }
                // Short word might be a compound word (ex: "partdieu" matches "part" + "dieu")
                // Try to match short word with multiple consecutive long words
                else if (shortWord.length > longWord.length) {
                    var combinedWord = longWord
                    var tempIndex = longerIndex + 1
                    var matched = false
                    
                    // Try to combine consecutive words to see if we get the short word
                    while (tempIndex < longer.size && combinedWord.length < shortWord.length) {
                        combinedWord += longer[tempIndex]
                        if (combinedWord == shortWord) {
                            // Match found!
                            longerIndex = tempIndex + 1
                            shorterIndex++
                            matched = true
                            break
                        }
                        tempIndex++
                    }
                    
                    if (!matched) {
                        // No match, advance in the long version
                        longerIndex++
                    }
                }
                // Maybe the long word has no equivalent in the short version
                else {
                    longerIndex++
                }
            }
            
            // If we matched all words from the short version, it's good
            return shorterIndex == shorter.size
        }
        
        // Otherwise, check that all words match in order
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
     * Predefined stops for metro lines
     */
    private val metroStops = mapOf(
        "A" to listOf(
            "Perrache", "Ampère Victor Hugo", "Bellecour", "Cordeliers", 
            "Hôtel de Ville L. Pradel", "Foch", "Masséna", "Charpennes Charles Hernu",
            "République Villeurbanne", "Gratte-Ciel", "Flachet - Alain Gilles", "Cusset",
            "Laurent Bonnevay", "Vaulx-en-Velin La Soie"
        ),
        "B" to listOf(
            "Charpennes Charles Hernu", "Brotteaux", "Gare Part-Dieu V.Merle",
            "Place Guichard", "Saxe Gambetta", "Jean Macé",
            "Place Jean Jaurès", "Debourg", "Stade de Gerland Le LOU", "Gare d'Oullins",
            "Oullins Centre", "St-Genis-Laval Hôp. Sud"
        ),
        "C" to listOf(
            "Hôtel de Ville L. Pradel", "Croix-Paquet", "Croix-Rousse",
            "Hénon", "Cuire"
        ),
        "D" to listOf(
            "Gare de Vaise-G.Collomb", "Valmy", "Gorge de Loup", "Vieux Lyon Cat. St-Jean",
            "Bellecour", "Guillotière Gabriel Péri", "Saxe Gambetta", "Garibaldi",
            "Sans Souci", "Monplaisir Lumière", "Grange Blanche", "Laennec",
            "Mermoz Pinel", "Parilly", "Gare de Vénissieux"
        ),
        "F1" to listOf(
            "Vieux Lyon Cat. St Jean", "Minimes Théatres Romains", "Saint Just"
        ),
        "F2" to listOf(
            "Vieux Lyon Cat. St Jean", "Fourvière"
        )
    )
    
    /**
     * Predefined stops for main tram lines
     */
    private val tramStops = mapOf(
        "T1" to listOf(
            "IUT Feyssine", "Croix-Luizet", "INSA - Einstein", "La Doua - Gaston Berger",
            "Université Lyon 1", "Condorcet", "Le Tonkin", "Charpennes Charles Hernu",
            "Collège Bellecombe", "Thiers - Lafayette", "Gare Part-Dieu V.Merle",
            "Part-Dieu Auditorium", "Palais Justice Mairie 3e", "Saxe - Préfecture", "Liberté",
            "Guillotière Gabriel Péri", "Saint-André", "Rue de l'Université", "Quai Claude Bernard",
            "Perrache", "Place des Archives", "Sainte-Blandine", "Hôtel Région Montrochet",
            "Musée des Confluences", "Halle Tony Garnier", "ENS Lyon", "Debourg"
        ),
        "T2" to listOf(
            "Saint-Priest Bel Air", "Cordière", "Saint-Priest Jules Ferry", "Esplanade des Arts",
            "St-Priest Hôtel de Ville", "Alfred de Vigny", "Salvador Allende", "Hauts de Feuilly",
            "Parc Technologique", "Porte des Alpes", "Europe - Université", "Rebufer", "Les Alizés",
            "Bron Hôtel de Ville", "Boutasse - C. Rousset", "Essarts - Iris", "Desgenettes",
            "Ambroise Paré", "Grange Blanche", "Jean XXIII - M. Bastié", "Bachut - Mairie du 8ème",
            "Villon", "Jet d'Eau - M. France", "Route de Vienne", "Garibaldi - Berthelot", "Jean Macé",
            "Centre Berthelot", "Perrache", "Place des Archives", "Sainte-Blandine", "Hôtel Région Montrochet"
        ),
        "T3" to listOf(
            "Meyzieu les Panettes", "Meyzieu Z.i.", "Meyzieu Lycée Beltrame", "Meyzieu Gare",
            "Décines Grand Large", "Décines Centre", "Décines Roosevelt", "Vaulx-en-Velin La Soie",
            "Bel Air - Les Brosses", "Gare de Villeurbanne", "Reconnaissance - Balzac", "Dauphiné - Lacassagne",
            "Gare Part-Dieu Villette", "Vaulx-en-Velin La Soie"
        ),
        "T4" to listOf(
            "Hôp. Feyzin Vénissieux", "Darnaise", "Lenine - Corsiere", "Maurice Thorez", "Division Leclerc",
            "Venissy Frida Kahlo", "Herriot - Cagne", "Lycée Jacques Brel", "M. Houël Hôtel de Ville",
            "Croizat - Paul Bert", "Gare de Vénissieux", "La Borelle", "Joliot-Curie - M. Sembat", "Etats-Unis Viviani",
            "Beauvisage CISL", "Etats-Unis Tony Garnier", "Lycée Lumière", "Jet d'Eau - M. France", "Lycee Colbert",
            "Manufacture Montluc", "Archives Departementales", "Gare Part-Dieu Villette", "Thiers - Lafayette",
            "Collège Bellecombe", "Charpennes Charles Hernu", "Le Tonkin", "Condorcet", "Université Lyon 1",
            "La Doua - Gaston Berger", "INSA - Einstein", "Croix-Luizet", "IUT Feyssine"
        ),
        "T5" to listOf(
            "Eurexpo Entrée Princ.", "Chassieu ZAC du Chêne", "Parc du Chêne", "Lycee J.P. Sartre", "De Tassigny - Curial",
            "Les Alizés", "Bron Hôtel de Ville", "Boutasse - C. Rousset", "Essarts - Iris", "Desgenettes", "Ambroise Paré",
            "Grange Blanche"
        ),
        "T6" to listOf(
            "Hôpitaux Est - Pinel", "Vinatier", "Desgenettes", "Essarts - Laennec", "Mermoz - Pinel", "Mermoz - Moselle",
            "Mermoz - Californie", "Grange Rouge - Santy", "Beauvisage CISL", "Beauvisage - Pressensé", "Petite Guille",
            "Moulin à Vent", "Challemel Lacour Artill.", "Debourg", "Mermoz - Pinel"
        ),
        "T7" to listOf(
            "Décines OL Vallée", "Décines Grand Large", "Décines Centre", "Décines Roosevelt", "Vaulx-en-Velin La Soie"
        ),
        "RX" to listOf(
            "Gare Part-Dieu Villette",
            "Vaulx-en-Velin La Soie",
            "Meyzieu Z.i.",
            "Aéroport St Exupéry -RX"
        ),
        "TB11" to listOf(
            "Gare Saint-Paul", "La Feuillée", "Terrasses Presqu'île", "Cordeliers", "Saxe - Lafayette", "Halles Paul Bocuse",
            "Part-Dieu Jules Favre", "Thiers - Lafayette", "Charmettes", "Instit. Art Contemporain", "Verlaine",
            "Blanqui - Le Rize", "Grandclément", "Bernaix", "Cyprian - Léon Blum", "Bon Coin - Médipôle", "Laurent Bonnevay"
        ),
        "TS" to listOf(
            "Part-Dieu Villette Sud", "Vaulx-en-Velin La Soie", "Décines OL Vallée", "Meyzieu. Z.i.", "Meyzieu les Panettes"
        )
    )
    
    /**
     * Retrieves stops for a line from the cache
     * @return List of stops or null if the line is not in the cache
     */
    fun getLineStops(lineName: String, currentStopName: String?): List<LineStopInfo>? {
        val normalizedLineName = lineName.trim().uppercase()
        val stops = metroStops[normalizedLineName] ?: tramStops[normalizedLineName]
        
        return stops?.mapIndexed { index, stopName ->
            LineStopInfo(
                stopId = "cache_${lineName}_${index}",
                stopName = stopName,
                stopSequence = index + 1,
                isCurrentStop = currentStopName?.let { stationNamesMatch(stopName, it) } ?: false
            )
        }
    }
    
    /**
     * Checks if a line is in the cache
     */
    fun hasLineInCache(lineName: String): Boolean {
        return metroStops.containsKey(lineName.uppercase()) || 
               tramStops.containsKey(lineName.uppercase())
    }
}
