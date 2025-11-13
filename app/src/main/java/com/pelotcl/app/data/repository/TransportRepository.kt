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
     * Filtre les doublons pour les métros et funiculaires (ne garde qu'un arrêt par station par ligne)
     */
    suspend fun getAllStops(): Result<StopCollection> {
        return try {
            val response = api.getTransportStops()
            
            // Filtrer les arrêts de métro et funiculaire pour éviter les doublons par quai
            // On garde un arrêt par (nom de station + ligne) pour gérer les correspondances
            val filteredStops = response.features.groupBy { stop ->
                val desserte = stop.properties.desserte
                
                // Détection métro: desserte commence par A:, B:, C: ou D:
                // Détection funiculaire: desserte contient F1: ou F2:
                val isMetro = desserte.matches(Regex("^[ABCD]:.*"))
                val isFunicular = desserte.contains("F1:") || desserte.contains("F2:")
                
                if (isMetro || isFunicular) {
                    // Extraire la ligne principale (première ligne de métro/funiculaire dans la desserte)
                    val mainLine = when {
                        isMetro -> desserte.substring(0, 1) // A, B, C ou D
                        desserte.contains("F1:") -> "F1"
                        desserte.contains("F2:") -> "F2"
                        else -> desserte
                    }
                    // Grouper par nom de station + ligne pour garder les correspondances
                    "${stop.properties.nom}|$mainLine"
                } else {
                    // Pour les autres (bus, tram), garder chaque arrêt unique
                    stop.id
                }
            }.map { (_, stops) ->
                // Prioriser les arrêts avec sens Aller (desserte contient :A)
                stops.firstOrNull { it.properties.desserte.contains(":A") } ?: stops.first()
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
