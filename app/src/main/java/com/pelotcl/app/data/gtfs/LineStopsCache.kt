package com.pelotcl.app.data.gtfs

/**
 * Cache statique des arrêts des lignes principales pour améliorer les performances
 * Évite de parser le fichier stop_times.txt (>50MB) à chaque fois
 */
object LineStopsCache {
    
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
                isCurrentStop = stopName.equals(currentStopName, ignoreCase = true)
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
