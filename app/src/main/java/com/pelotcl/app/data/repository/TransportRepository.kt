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
     * Récupère toutes les lignes de transport
     */
    suspend fun getAllLines(): Result<FeatureCollection> {
        return try {
            val response = api.getTransportLines()
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Récupère une ligne spécifique par son nom
     */
    suspend fun getLineByName(lineName: String): Result<Feature?> {
        return try {
            val response = api.getTransportLines()
            val line = response.features.find { it.properties.ligne == lineName }
            Result.success(line)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
