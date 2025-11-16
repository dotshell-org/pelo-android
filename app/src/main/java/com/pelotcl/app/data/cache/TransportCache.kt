package com.pelotcl.app.data.cache

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pelotcl.app.data.model.Feature
import com.pelotcl.app.data.model.FeatureCollection
import com.pelotcl.app.data.model.StopCollection
import com.pelotcl.app.data.model.StopFeature
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

/**
 * Classe de cache en mémoire et sur disque pour les données de transport.
 * Permet d'éviter les appels API répétés et d'améliorer les performances.
 */
class TransportCache(private val context: Context) {
    
    private val gson = Gson()
    private val prefs = context.getSharedPreferences("transport_cache", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    
    // Cache en mémoire pour un accès ultra-rapide
    private var metroLinesCache: List<Feature>? = null
    private var tramLinesCache: List<Feature>? = null
    private var busLinesCache: List<Feature>? = null
    private var stopsCache: List<StopFeature>? = null
    
    // Timestamps pour la gestion de l'expiration
    private var metroLinesTimestamp: Long = 0
    private var tramLinesTimestamp: Long = 0
    private var busLinesTimestamp: Long = 0
    private var stopsTimestamp: Long = 0
    
    companion object {
        // Durée de validité du cache : 24 heures
        private val CACHE_VALIDITY_DURATION = TimeUnit.HOURS.toMillis(24)
        
        // Clés pour SharedPreferences
        private const val KEY_METRO_LINES = "metro_lines"
        private const val KEY_METRO_LINES_TIMESTAMP = "metro_lines_timestamp"
        private const val KEY_TRAM_LINES = "tram_lines"
        private const val KEY_TRAM_LINES_TIMESTAMP = "tram_lines_timestamp"
        private const val KEY_BUS_LINES = "bus_lines"
        private const val KEY_BUS_LINES_TIMESTAMP = "bus_lines_timestamp"
        private const val KEY_STOPS = "stops"
        private const val KEY_STOPS_TIMESTAMP = "stops_timestamp"
        
        @Volatile
        private var INSTANCE: TransportCache? = null
        
        fun getInstance(context: Context): TransportCache {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TransportCache(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    /**
     * Vérifie si un timestamp est encore valide
     */
    private fun isTimestampValid(timestamp: Long): Boolean {
        return (System.currentTimeMillis() - timestamp) < CACHE_VALIDITY_DURATION
    }
    
    /**
     * Sauvegarde les lignes de métro/funiculaire dans le cache
     */
    suspend fun saveMetroLines(lines: List<Feature>) = mutex.withLock {
        metroLinesCache = lines
        metroLinesTimestamp = System.currentTimeMillis()
        
        // Sauvegarder sur disque
        prefs.edit().apply {
            putString(KEY_METRO_LINES, gson.toJson(lines))
            putLong(KEY_METRO_LINES_TIMESTAMP, metroLinesTimestamp)
            apply()
        }
    }
    
    /**
     * Récupère les lignes de métro/funiculaire depuis le cache
     */
    suspend fun getMetroLines(): List<Feature>? = mutex.withLock {
        // Vérifier d'abord le cache mémoire
        if (metroLinesCache != null && isTimestampValid(metroLinesTimestamp)) {
            return@withLock metroLinesCache
        }
        
        // Sinon, charger depuis le disque
        val timestamp = prefs.getLong(KEY_METRO_LINES_TIMESTAMP, 0)
        if (isTimestampValid(timestamp)) {
            val json = prefs.getString(KEY_METRO_LINES, null)
            if (json != null) {
                try {
                    val type = object : TypeToken<List<Feature>>() {}.type
                    val lines = gson.fromJson<List<Feature>>(json, type)
                    metroLinesCache = lines
                    metroLinesTimestamp = timestamp
                    return@withLock lines
                } catch (e: Exception) {
                    android.util.Log.e("TransportCache", "Error loading metro lines from cache", e)
                }
            }
        }
        
        null
    }
    
    /**
     * Sauvegarde les lignes de tram dans le cache
     */
    suspend fun saveTramLines(lines: List<Feature>) = mutex.withLock {
        tramLinesCache = lines
        tramLinesTimestamp = System.currentTimeMillis()
        
        // Sauvegarder sur disque
        prefs.edit().apply {
            putString(KEY_TRAM_LINES, gson.toJson(lines))
            putLong(KEY_TRAM_LINES_TIMESTAMP, tramLinesTimestamp)
            apply()
        }
    }
    
    /**
     * Récupère les lignes de tram depuis le cache
     */
    suspend fun getTramLines(): List<Feature>? = mutex.withLock {
        // Vérifier d'abord le cache mémoire
        if (tramLinesCache != null && isTimestampValid(tramLinesTimestamp)) {
            return@withLock tramLinesCache
        }
        
        // Sinon, charger depuis le disque
        val timestamp = prefs.getLong(KEY_TRAM_LINES_TIMESTAMP, 0)
        if (isTimestampValid(timestamp)) {
            val json = prefs.getString(KEY_TRAM_LINES, null)
            if (json != null) {
                try {
                    val type = object : TypeToken<List<Feature>>() {}.type
                    val lines = gson.fromJson<List<Feature>>(json, type)
                    tramLinesCache = lines
                    tramLinesTimestamp = timestamp
                    return@withLock lines
                } catch (e: Exception) {
                    android.util.Log.e("TransportCache", "Error loading tram lines from cache", e)
                }
            }
        }
        
        null
    }
    
    /**
     * Sauvegarde les lignes de bus dans le cache (MÉMOIRE UNIQUEMENT)
     * Les lignes de bus sont trop volumineuses pour SharedPreferences
     */
    suspend fun saveBusLines(lines: List<Feature>) = mutex.withLock {
        busLinesCache = lines
        busLinesTimestamp = System.currentTimeMillis()
        
        // NE PAS sauvegarder sur disque - trop volumineux
        android.util.Log.d("TransportCache", "Bus lines cached in memory only (${lines.size} lines)")
    }
    
    /**
     * Récupère les lignes de bus depuis le cache (MÉMOIRE UNIQUEMENT)
     * Les bus ne sont pas persistés sur disque car trop volumineux
     */
    suspend fun getBusLines(): List<Feature>? = mutex.withLock {
        // Vérifier uniquement le cache mémoire
        if (busLinesCache != null && isTimestampValid(busLinesTimestamp)) {
            android.util.Log.d("TransportCache", "Bus lines found in memory cache")
            return@withLock busLinesCache
        }
        
        // Les bus ne sont pas cachés sur disque
        android.util.Log.d("TransportCache", "Bus lines not in cache")
        null
    }
    
    /**
     * Sauvegarde les arrêts dans le cache
     * ATTENTION : Les arrêts sont volumineux, on ne garde que l'essentiel sur disque
     */
    suspend fun saveStops(stops: List<StopFeature>) = mutex.withLock {
        stopsCache = stops
        stopsTimestamp = System.currentTimeMillis()
        
        // Sauvegarder sur disque uniquement si pas trop volumineux
        // Limite : environ 500 arrêts pour éviter les OutOfMemoryError
        val stopsToSave = if (stops.size > 500) {
            android.util.Log.w("TransportCache", "Too many stops (${stops.size}), only caching ${stops.size} in memory, limited disk cache")
            // Sur disque, ne garder que les arrêts principaux (métro, tram, funiculaire)
            stops.filter { stop ->
                val desserte = stop.properties.desserte
                desserte.matches(Regex("^[ABCD]:.*")) || // Métro
                desserte.matches(Regex("^F[12]:.*")) || // Funiculaire
                desserte.matches(Regex(".*\\bT\\d+:[AR]\\b.*")) // Tram
            }
        } else {
            stops
        }
        
        try {
            prefs.edit().apply {
                putString(KEY_STOPS, gson.toJson(stopsToSave))
                putLong(KEY_STOPS_TIMESTAMP, stopsTimestamp)
                apply()
            }
            android.util.Log.d("TransportCache", "Saved ${stopsToSave.size} stops to disk (${stops.size} in memory)")
        } catch (e: Exception) {
            android.util.Log.e("TransportCache", "Failed to save stops to disk, keeping memory cache only", e)
            // En cas d'erreur, on garde quand même le cache mémoire
        }
    }
    
    /**
     * Récupère les arrêts depuis le cache
     */
    suspend fun getStops(): List<StopFeature>? = mutex.withLock {
        // Vérifier d'abord le cache mémoire
        if (stopsCache != null && isTimestampValid(stopsTimestamp)) {
            return@withLock stopsCache
        }
        
        // Sinon, charger depuis le disque
        val timestamp = prefs.getLong(KEY_STOPS_TIMESTAMP, 0)
        if (isTimestampValid(timestamp)) {
            val json = prefs.getString(KEY_STOPS, null)
            if (json != null) {
                try {
                    val type = object : TypeToken<List<StopFeature>>() {}.type
                    val stops = gson.fromJson<List<StopFeature>>(json, type)
                    stopsCache = stops
                    stopsTimestamp = timestamp
                    return@withLock stops
                } catch (e: Exception) {
                    android.util.Log.e("TransportCache", "Error loading stops from cache", e)
                }
            }
        }
        
        null
    }
    
    /**
     * Vide tout le cache (mémoire et disque)
     */
    suspend fun clearAll() = mutex.withLock {
        metroLinesCache = null
        tramLinesCache = null
        busLinesCache = null
        stopsCache = null
        
        metroLinesTimestamp = 0
        tramLinesTimestamp = 0
        busLinesTimestamp = 0
        stopsTimestamp = 0
        
        prefs.edit().clear().apply()
    }
    
    /**
     * Vide uniquement le cache des lignes
     */
    suspend fun clearLines() = mutex.withLock {
        metroLinesCache = null
        tramLinesCache = null
        busLinesCache = null
        
        metroLinesTimestamp = 0
        tramLinesTimestamp = 0
        busLinesTimestamp = 0
        
        prefs.edit().apply {
            remove(KEY_METRO_LINES)
            remove(KEY_METRO_LINES_TIMESTAMP)
            remove(KEY_TRAM_LINES)
            remove(KEY_TRAM_LINES_TIMESTAMP)
            remove(KEY_BUS_LINES)
            remove(KEY_BUS_LINES_TIMESTAMP)
            apply()
        }
    }
    
    /**
     * Vide uniquement le cache des arrêts
     */
    suspend fun clearStops() = mutex.withLock {
        stopsCache = null
        stopsTimestamp = 0
        
        prefs.edit().apply {
            remove(KEY_STOPS)
            remove(KEY_STOPS_TIMESTAMP)
            apply()
        }
    }
    
    /**
     * Vérifie si le cache est valide pour un type de données
     */
    fun isCacheValid(type: CacheType): Boolean {
        return when (type) {
            CacheType.METRO_LINES -> isTimestampValid(metroLinesTimestamp)
            CacheType.TRAM_LINES -> isTimestampValid(tramLinesTimestamp)
            CacheType.BUS_LINES -> isTimestampValid(busLinesTimestamp)
            CacheType.STOPS -> isTimestampValid(stopsTimestamp)
        }
    }
}

/**
 * Types de cache disponibles
 */
enum class CacheType {
    METRO_LINES,
    TRAM_LINES,
    BUS_LINES,
    STOPS
}
