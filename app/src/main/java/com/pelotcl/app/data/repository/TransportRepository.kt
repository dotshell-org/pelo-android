package com.pelotcl.app.data.repository

import com.pelotcl.app.data.api.RetrofitInstance
import com.pelotcl.app.data.model.Feature
import com.pelotcl.app.data.model.FeatureCollection

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
            val response = api.getTransportLines()
            
            // Grouper par code_trace et ne garder que le premier de chaque groupe (sens aller)
            val uniqueLines = response.features
                .groupBy { it.properties.codeTrace }
                .map { (_, features) -> features.first() }
            
            val filteredCollection = response.copy(
                features = uniqueLines,
                numberReturned = uniqueLines.size
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
            val response = api.getTransportLines()
            
            // Trouver la ligne et prendre le premier résultat (sens aller)
            val line = response.features
                .filter { it.properties.ligne == lineName }
                .firstOrNull()
            
            Result.success(line)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
