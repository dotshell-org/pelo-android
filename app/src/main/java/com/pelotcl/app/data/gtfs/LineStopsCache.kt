package com.pelotcl.app.data.gtfs

/**
 * Cache statique des arrêts des lignes principales pour améliorer les performances
 * Évite de parser le fichier stop_times.txt (>50MB) à chaque fois
 */
object LineStopsCache {
    
    /**
     * Normalise un nom de station pour la comparaison
     * Garde uniquement les lettres et convertit en minuscules
     */
    private fun normalizeStationName(name: String): String {
        return name.filter { it.isLetter() }.lowercase()
    }
    
    /**
     * Normalise les mots d'un nom de station en gérant les abréviations
     * Ex: "L." devient "l", "Louis" devient "louis"
     * Filtre aussi les mots génériques comme "gare" qui peuvent être omis
     */
    private fun normalizeWords(name: String): List<String> {
        val ignoredWords = setOf("gare", "station", "arret")
        
        return name.split(Regex("[\\s\\-,.]+"))
            .filter { it.isNotEmpty() }
            .map { word -> 
                // Retirer les points et garder uniquement les lettres
                word.filter { it.isLetter() }.lowercase()
            }
            .filter { it.isNotEmpty() && it !in ignoredWords }
    }
    
    /**
     * Compare deux noms de station de manière flexible
     * Gère les abréviations : "L. Pradel" match avec "Louis Pradel"
     * Gère aussi les abréviations de mots : "Cat." match avec "Cathédrale"
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
            
            // Vérifier si tous les mots de la version courte matchent avec la version longue
            // Soit exactement, soit comme début de mot (pour les abréviations)
            // Gère aussi les mots composés (ex: "partdieu" match "part" + "dieu")
            var shorterIndex = 0
            var longerIndex = 0
            
            while (shorterIndex < shorter.size && longerIndex < longer.size) {
                val shortWord = shorter[shorterIndex]
                val longWord = longer[longerIndex]
                
                // Match exact
                if (shortWord == longWord) {
                    shorterIndex++
                    longerIndex++
                }
                // Le mot long commence par le mot court (ex: "cat" match "cathedrale")
                // Minimum 3 lettres pour éviter les faux positifs
                else if (shortWord.length >= 3 && longWord.startsWith(shortWord)) {
                    shorterIndex++
                    longerIndex++
                }
                // Le mot court est une initiale du mot long (ex: "l" match "louis")
                else if (shortWord.length == 1 && longWord.startsWith(shortWord)) {
                    shorterIndex++
                    longerIndex++
                }
                // Le mot court pourrait être un mot composé (ex: "partdieu" match "part" + "dieu")
                // Essayer de matcher le mot court avec plusieurs mots longs consécutifs
                else if (shortWord.length > longWord.length) {
                    var combinedWord = longWord
                    var tempIndex = longerIndex + 1
                    var matched = false
                    
                    // Essayer de combiner des mots consécutifs pour voir si on obtient le mot court
                    while (tempIndex < longer.size && combinedWord.length < shortWord.length) {
                        combinedWord += longer[tempIndex]
                        if (combinedWord == shortWord) {
                            // Match trouvé !
                            longerIndex = tempIndex + 1
                            shorterIndex++
                            matched = true
                            break
                        }
                        tempIndex++
                    }
                    
                    if (!matched) {
                        // Pas de match, avancer dans la version longue
                        longerIndex++
                    }
                }
                // Peut-être que le mot long n'a pas d'équivalent dans la version courte
                else {
                    longerIndex++
                }
            }
            
            // Si on a matché tous les mots de la version courte, c'est bon
            return shorterIndex == shorter.size
        }
        
        // Sinon, vérifier que tous les mots matchent dans l'ordre
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
     * Arrêts pré-définis pour les lignes de métro
     */
    private val metroStops = mapOf(
        "A" to listOf(
            "Perrache", "Ampère Victor Hugo", "Bellecour", "Cordeliers", 
            "Hôtel de Ville Louis Pradel", "Foch", "Masséna", "Charpennes Charles Hernu",
            "République Villeurbanne", "Gratte-Ciel", "Flachet", "Cusset", 
            "Laurent Bonnevay Astroballe", "Vaulx-en-Velin La Soie"
        ),
        "B" to listOf(
            "Charpennes Charles Hernu", "Brotteaux", "Part-Dieu Vivier Merle",
            "Place Guichard Bourse du Travail", "Saxe Gambetta", "Jean Macé",
            "Place Jean Jaurès", "Debourg", "Stade de Gerland", "Oullins Gare",
            "Gare d'Oullins"
        ),
        "C" to listOf(
            "Hôtel de Ville Louis Pradel", "Croix-Paquet", "Croix-Rousse",
            "Hénon", "Cuire"
        ),
        "D" to listOf(
            "Gare de Vaise", "Valmy", "Gorge de Loup", "Vieux Lyon Cathédrale St Jean",
            "Bellecour", "Guillotière Gabriel Péri", "Saxe Gambetta", "Garibaldi",
            "Sans Souci", "Monplaisir Lumière", "Grange Blanche", "Laennec",
            "Mermoz Pinel", "Parilly", "Gare de Vénissieux"
        ),
        "F1" to listOf(
            "Vieux Lyon Cathédrale St Jean", "Saint-Just", "Fourvière"
        ),
        "F2" to listOf(
            "Croix-Rousse", "Croix-Paquet"
        )
    )
    
    /**
     * Arrêts pré-définis pour les lignes de tram principales
     */
    private val tramStops = mapOf(
        "T1" to listOf(
            "IUT Feyssine", "La Doua Gaston Berger", "Université Lyon 1",
            "Flachet", "Charpennes", "Reconnaissance Balzac", "Les Brosses",
            "Cusset", "Tolstoï", "Gratte-Ciel", "Hôtel de Région Montrochet",
            "Maisons Neuves", "Gare de Vénissieux", "Vénissieux Centre"
        ),
        "T2" to listOf(
            "Porte des Alpes Parc Technologique", "Le Soler", "Centre Médical",
            "Jean Macé", "Rue de l'Université", "Debourg", "Jet d'Eau Mendès France",
            "Stade de Gerland", "Tony Garnier", "Place Jean Jaurès",
            "Sainte-Blandine", "Perrache"
        ),
        "T3" to listOf(
            "Gare Part-Dieu Vivier Merle", "Dauphiné Lacassagne", "Reconnaissance Balzac",
            "Charpennes", "République Villeurbanne", "Gratte-Ciel",
            "Dedieu", "Fleur", "La Sucrière", "Gare de Vaise"
        ),
        "T4" to listOf(
            "Hôpital Feyzin Vénissieux", "Vénissieux Centre", "Joliot Curie",
            "Division Leclerc", "Maisons Neuves", "Hôtel de Région Montrochet"
        ),
        "T5" to listOf(
            "Grange Blanche", "Vinatier", "Neuilly Gambetta", "Pressensé",
            "Les Essarts Iris", "Lycée Lumière Jean Moulin"
        ),
        "T6" to listOf(
            "Gare de Vénissieux", "Vénissieux Le Puisoz", "Vénissieux La Borelle",
            "Vénissieux Moulin à Vent", "Château de Feyzin", "Hôpital Feyzin Vénissieux"
        ),
        "T7" to listOf(
            "Vaulx-en-Velin La Soie", "Carrel", "Léon Blum", "Bonnevay",
            "Charmettes Paul Santy", "Boutasse Camille Rousset"
        )
    )
    
    /**
     * Récupère les arrêts d'une ligne depuis le cache
     * @return Liste d'arrêts ou null si la ligne n'est pas dans le cache
     */
    fun getLineStops(lineName: String, currentStopName: String?): List<LineStopInfo>? {
        val stops = metroStops[lineName.uppercase()] ?: tramStops[lineName.uppercase()]
        
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
     * Vérifie si une ligne est dans le cache
     */
    fun hasLineInCache(lineName: String): Boolean {
        return metroStops.containsKey(lineName.uppercase()) || 
               tramStops.containsKey(lineName.uppercase())
    }
}
