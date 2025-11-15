package com.pelotcl.app.data.repository

import com.pelotcl.app.data.api.RetrofitInstance
import com.pelotcl.app.data.model.Feature
import com.pelotcl.app.data.model.FeatureCollection
import com.pelotcl.app.data.model.StopCollection

/**
 * Repository pour gérer les données des lignes de transport
 */
class TransportRepository {
    
    private val api = RetrofitInstance.api
    
    /**
     * Récupère toutes les lignes de transport (métro, funiculaire et tram UNIQUEMENT)
     * Les lignes de BUS ne sont PAS chargées par défaut pour éviter de surcharger le téléphone
     * Pour charger une ligne de bus spécifique, utiliser getLineByName()
     */
    suspend fun getAllLines(): Result<FeatureCollection> {
        return try {
            val metroFuniculaire = api.getTransportLines()
            val trams = api.getTramLines()

            // Fusionner uniquement métro/funiculaire et trams (PAS les bus)
            val allFeatures = (metroFuniculaire.features + trams.features)

            // Grouper par code_trace et ne garder que le premier de chaque groupe (sens aller)
            val uniqueLines = allFeatures
                .groupBy { it.properties.codeTrace }
                .map { (_, features) -> features.first() }

            val filteredCollection = metroFuniculaire.copy(
                features = uniqueLines,
                numberReturned = uniqueLines.size,
                totalFeatures = (metroFuniculaire.totalFeatures ?: 0) + (trams.totalFeatures ?: 0),
                numberMatched = (metroFuniculaire.numberMatched ?: 0) + (trams.numberMatched ?: 0)
            )

            Result.success(filteredCollection)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Récupère une ligne spécifique par son nom (sens aller uniquement)
     * Cherche d'abord dans métro/funiculaire/tram, puis dans les bus si non trouvé
     * Cela permet de charger les lignes de bus uniquement à la demande
     */
    suspend fun getLineByName(lineName: String): Result<Feature?> {
        return try {
            val metroFuniculaire = api.getTransportLines()
            val trams = api.getTramLines()

            val priorityFeatures = (metroFuniculaire.features + trams.features)

            // Chercher d'abord dans métro/funiculaire/tram
            val line = priorityFeatures
                .filter { it.properties.ligne.equals(lineName, ignoreCase = true) }
                .firstOrNull()
            
            // Si trouvé, retourner
            if (line != null) {
                return Result.success(line)
            }
            
            // Sinon, chercher dans les bus (chargement à la demande)
            val bus = api.getBusLines()
            val busLine = bus.features
                .filter { it.properties.ligne.equals(lineName, ignoreCase = true) }
                .firstOrNull()
            
            Result.success(busLine)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Récupère tous les arrêts de transport
     * Filtre les doublons pour les métros et funiculaires (ne garde qu'un arrêt par station)
     * Les stations de correspondance (plusieurs lignes) s'affichent empilées comme les bus
     * Filtre intelligemment les arrêts de tram : garde les arrêts aller, mais aussi les arrêts
     * retour qui n'ont pas d'équivalent aller (certains terminus)
     */
    suspend fun getAllStops(): Result<StopCollection> {
        return try {
            val response = api.getTransportStops()
            
            // Filtrer intelligemment les arrêts de tram : 
            // Grouper par nom et ligne de tram, puis garder :A en priorité, sinon :R
            val tramStopsGrouped = response.features
                .filter { stop -> 
                    stop.properties.desserte.matches(Regex(".*\\bT\\d+:[AR]\\b.*"))
                }
                .groupBy { stop ->
                    // Grouper par nom d'arrêt et numéro de ligne de tram
                    val tramLineMatch = Regex("\\bT(\\d+):[AR]\\b").find(stop.properties.desserte)
                    if (tramLineMatch != null) {
                        "${stop.properties.nom}_T${tramLineMatch.groupValues[1]}"
                    } else {
                        stop.id.toString()
                    }
                }
            
            // Pour chaque groupe, garder :A en priorité, sinon :R
            val dedupedTramStops = tramStopsGrouped.values.map { stops ->
                stops.firstOrNull { it.properties.desserte.contains(":A") } 
                    ?: stops.first()
            }
            
            // Garder tous les arrêts non-tram
            val nonTramStops = response.features.filter { stop ->
                !stop.properties.desserte.matches(Regex(".*\\bT\\d+:[AR]\\b.*"))
            }
            
            // Combiner les arrêts de tram dédupliqués avec les autres arrêts
            val stopsWithoutTramRetour = dedupedTramStops + nonTramStops
            
            // Filtrer les arrêts de métro et funiculaire pour éviter les doublons par quai
            // Pour les correspondances, on fusionne toutes les dessertes en une seule
            val filteredStops = stopsWithoutTramRetour.groupBy { stop ->
                val desserte = stop.properties.desserte
                
                // Détection stricte métro: desserte commence par A:, B:, C: ou D:
                // Détection stricte funiculaire: desserte commence par F1: ou F2:
                // Cela exclut les bus qui ont ces lignes plus tard dans leur desserte
                val isMetro = desserte.matches(Regex("^[ABCD]:.*"))
                val isFunicular = desserte.matches(Regex("^F[12]:.*"))
                
                if (isMetro || isFunicular) {
                    // Grouper uniquement par nom de station pour que toutes les lignes
                    // d'une même station (correspondances) s'affichent empilées au même endroit
                    stop.properties.nom
                } else {
                    // Pour les autres (bus, tram), garder chaque arrêt unique
                    stop.id
                }
            }.map { (_, stops) ->
                if (stops.size == 1) {
                    // Un seul arrêt pour ce groupe, le retourner tel quel
                    stops.first()
                } else {
                    // Plusieurs arrêts (correspondance): fusionner les dessertes
                    val baseStop = stops.firstOrNull { it.properties.desserte.contains(":A") } ?: stops.first()
                    
                    // Collecter toutes les lignes uniques de tous les arrêts de cette station
                    val allDessertes = stops.map { it.properties.desserte }.toSet()
                    
                    // Fusionner les dessertes en une seule chaîne avec virgules
                    // Ex: "A:A" + "D:A" -> "A:A,D:A"
                    val mergedDesserte = allDessertes.joinToString(",")
                    
                    // Créer un nouvel arrêt avec la desserte fusionnée
                    baseStop.copy(
                        properties = baseStop.properties.copy(
                            desserte = mergedDesserte
                        )
                    )
                }
            }
            
            val filteredCollection = response.copy(
                features = filteredStops,
                numberReturned = filteredStops.size
            )
            
            Result.success(filteredCollection)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
