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
            "Hôtel de Ville L. Pradel", "Foch", "Masséna", "Charpennes Charles Hernu",
            "République Villeurbanne", "Gratte-Ciel", "Flachet", "Cusset", 
            "Laurent Bonnevay Astroballe", "Vaulx-en-Velin La Soie"
        ),
        "B" to listOf(
            "Charpennes Charles Hernu", "Brotteaux", "Gare Part-Dieu V.Merle",
            "Place Guichard Bourse du Travail", "Saxe Gambetta", "Jean Macé",
            "Place Jean Jaurès", "Debourg", "Stade de Gerland", "Gare d'Oullins",
            "Oullins Centre", "St-Genis-Laval Hôpital Sud"
        ),
        "C" to listOf(
            "Hôtel de Ville L. Pradel", "Croix-Paquet", "Croix-Rousse",
            "Hénon", "Cuire"
        ),
        "D" to listOf(
            "Gare de Vaise", "Valmy", "Gorge de Loup", "Vieux Lyon Cat. St Jean",
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
     * Arrêts pré-définis pour les lignes de tram principales
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
            "Ambroise Paré", "Grande Blanche", "Jean XXIII - M. Bastié", "Bachut - Mairie du 8ème",
            "Villon", "Jet d'Eau - M. France", "Route de Vienne", "Garibaldi - Berthelot", "Jean Macé",
            "Centre Berthelot", "Perrache", "Place des Archives", "Sainte-Blandine", "Hôtel Région Montrochet"
        ),
        "T3" to listOf(
            "Meyzieu les Panettes", "Meyzieu Z.i.", "Meyzieu Lycée Beltrame", "Meyzieu Gare",
            "Décines Grand Large", "Décines Centre", "Décines Roosevelt", "Vaulx-en-Velin La Soie",
            "Bel Air - Les Brosses", "Gare de Villeurbane", "Reconnaissance - Balzac", "Dauphiné - Lacassagne",
            "Gare Part-Dieu Villette", "Vaulx-en-Velin La Soie"
        ),
        "T4" to listOf(
            "Hôp. Feyzin Vénissieux", "Darnaise", "Lenine - Corsiere", "Maurice Thorez", "Division Leclerc",
            "Venissy Frida Kahlo", "Herriot - Cagne", "Lycée Jacques Brel", "M. Houël Hôtel de Ville",
            "Croizat - Paul Bert", "Gare de Vénissieux", "La Borelle", "Joliot-Curie - M. Sembat", "Etats-Unis Viviani",
            "Beauvisage CISL", "Etats-Unis Tony Garnier", "Lycée Lumière", "Jet d'Eau - M. France", "Lycee Colbert",
            "Manufacture Montluc", "Archives Departementales", "Gare Part-Dieu Vilette", "Thiers - Lafayette",
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
            "Mermoz - Californie", "Grande Rouge - Santy", "Beauvisage CISL", "Beauvisage - Pressensé", "Petite Guille",
            "Moulin à Vent", "Challemel Lacour Artill.", "Debourg", "Mermoz - Pinel"
        ),
        "T7" to listOf(
            "Décines OL Vallée", "Décines Grand Large", "Décines Centre", "Décines Roosevelt", "Vaulx-en-Velin La Soie"
        )
    )
    
    /**
     * Récupère les arrêts d'une lgigne depuis le cache
     * @return Liste d'arrêts ou null si la ligne n'est pas dans le cache
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
     * Vérifie si une ligne est dans le cache
     */
    fun hasLineInCache(lineName: String): Boolean {
        return metroStops.containsKey(lineName.uppercase()) || 
               tramStops.containsKey(lineName.uppercase())
    }
}
