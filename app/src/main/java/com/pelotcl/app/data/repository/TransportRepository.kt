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
     * Récupère toutes les lignes de transport (sens aller uniquement)
     * Filtre pour ne garder qu'une ligne sur deux (évite les doublons aller/retour)
     */
    suspend fun getAllLines(): Result<FeatureCollection> {
        return try {
            val metroFuniculaire = api.getTransportLines()
            val trams = api.getTramLines()

            // Fusionner les features des deux couches
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
     */
    suspend fun getLineByName(lineName: String): Result<Feature?> {
        return try {
            val metroFuniculaire = api.getTransportLines()
            val trams = api.getTramLines()

            val allFeatures = (metroFuniculaire.features + trams.features)

            // Trouver la ligne et prendre le premier résultat (sens aller)
            val line = allFeatures
                .filter { it.properties.ligne.equals(lineName, ignoreCase = true) }
                .firstOrNull()
            
            Result.success(line)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Récupère tous les arrêts de transport
     * Filtre les doublons pour les métros et funiculaires (ne garde qu'un arrêt par station)
     * Les stations de correspondance (plusieurs lignes) s'affichent empilées comme les bus
     * Filtre également les arrêts de tram en sens retour (ne garde que les arrêts aller)
     */
    suspend fun getAllStops(): Result<StopCollection> {
        return try {
            val response = api.getTransportStops()
            
            // Filtrer d'abord les arrêts de tram en direction retour (finissant par :R)
            val stopsWithoutTramRetour = response.features.filter { stop ->
                val desserte = stop.properties.desserte
                // Détecter si c'est un arrêt de tram en direction retour
                // Les lignes de tram sont T1, T2, T3, etc. et se terminent par :R pour retour
                val isTramRetour = desserte.matches(Regex(".*\\bT\\d+:R\\b.*"))
                !isTramRetour // Garder seulement les arrêts qui ne sont pas des trams retour
            }
            
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
