package com.pelotcl.app.data.repository

import android.content.Context
import com.pelotcl.app.data.api.RetrofitInstance
import com.pelotcl.app.data.cache.TransportCache
import com.pelotcl.app.data.model.Feature
import com.pelotcl.app.data.model.FeatureCollection
import com.pelotcl.app.data.model.StopCollection

/**
 * Repository pour gérer les données des lignes de transport
 */
class TransportRepository(context: Context? = null) {
    
    private val api = RetrofitInstance.api
    private val cache = context?.let { TransportCache.getInstance(it) }
    
    /**
     * Récupère toutes les lignes de transport (métro, funiculaire et tram UNIQUEMENT)
     * Les lignes de BUS ne sont PAS chargées par défaut pour éviter de surcharger le téléphone
     * Pour charger une ligne de bus spécifique, utiliser getLineByName()
     * Utilise le cache pour améliorer les performances
     */
    suspend fun getAllLines(): Result<FeatureCollection> {
        return try {
            // Essayer de charger depuis le cache
            val cachedMetro = cache?.getMetroLines()
            val cachedTram = cache?.getTramLines()
            
            val metroFuniculaire: FeatureCollection
            val trams: FeatureCollection
            
            if (cachedMetro != null && cachedTram != null) {
                // Cache hit : utiliser les données en cache
                android.util.Log.d("TransportRepository", "Cache HIT: Loading lines from cache")
                metroFuniculaire = FeatureCollection(
                    type = "FeatureCollection",
                    features = cachedMetro,
                    totalFeatures = cachedMetro.size,
                    numberMatched = cachedMetro.size,
                    numberReturned = cachedMetro.size
                )
                trams = FeatureCollection(
                    type = "FeatureCollection",
                    features = cachedTram,
                    totalFeatures = cachedTram.size,
                    numberMatched = cachedTram.size,
                    numberReturned = cachedTram.size
                )
            } else {
                // Cache miss : charger depuis l'API
                android.util.Log.d("TransportRepository", "Cache MISS: Loading lines from API")
                metroFuniculaire = api.getTransportLines()
                trams = api.getTramLines()
                
                // Sauvegarder dans le cache
                cache?.saveMetroLines(metroFuniculaire.features)
                cache?.saveTramLines(trams.features)
            }

            // Fusionner uniquement métro/funiculaire et trams (PAS les bus)
            val allFeatures = (metroFuniculaire.features + trams.features)

            // Log des lignes de tram avant grouping
            val tramLines = trams.features.map { it.properties.ligne }.distinct().sorted()
            android.util.Log.d("TransportRepository", "Tram lines from API: $tramLines")
            android.util.Log.d("TransportRepository", "Total tram features before grouping: ${trams.features.size}")
            
            // Log des code_trace pour T1
            val t1Features = trams.features.filter { it.properties.ligne.equals("T1", ignoreCase = true) }
            android.util.Log.d("TransportRepository", "T1 features count: ${t1Features.size}")
            t1Features.forEach { feature ->
                android.util.Log.d("TransportRepository", "T1 code_trace: ${feature.properties.codeTrace}, sens: ${feature.properties.sens}")
            }

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

            // Log des lignes chargées
            val lineNames = uniqueLines.map { it.properties.ligne }.sorted()
            android.util.Log.d("TransportRepository", "Loaded lines: $lineNames")
            android.util.Log.d("TransportRepository", "Metro/Funicular count: ${metroFuniculaire.features.size}")
            android.util.Log.d("TransportRepository", "Tram count: ${trams.features.size}")
            android.util.Log.d("TransportRepository", "Total unique lines: ${uniqueLines.size}")

            Result.success(filteredCollection)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Récupère une ligne spécifique par son nom (sens aller uniquement)
     * Cherche d'abord dans métro/funiculaire/tram, puis dans les bus si non trouvé
     * Cela permet de charger les lignes de bus uniquement à la demande
     * Utilise le cache pour améliorer les performances
     */
    suspend fun getLineByName(lineName: String): Result<Feature?> {
        return try {
            // Essayer de charger depuis le cache
            val cachedMetro = cache?.getMetroLines()
            val cachedTram = cache?.getTramLines()
            
            val priorityFeatures: List<Feature>
            
            if (cachedMetro != null && cachedTram != null) {
                // Cache hit : utiliser les données en cache
                android.util.Log.d("TransportRepository", "Cache HIT: Loading line $lineName from cache")
                priorityFeatures = cachedMetro + cachedTram
            } else {
                // Cache miss : charger depuis l'API
                android.util.Log.d("TransportRepository", "Cache MISS: Loading line $lineName from API")
                val metroFuniculaire = api.getTransportLines()
                val trams = api.getTramLines()
                priorityFeatures = metroFuniculaire.features + trams.features
                
                // Sauvegarder dans le cache
                cache?.saveMetroLines(metroFuniculaire.features)
                cache?.saveTramLines(trams.features)
            }

            // Chercher d'abord dans métro/funiculaire/tram
            val line = priorityFeatures
                .filter { it.properties.ligne.equals(lineName, ignoreCase = true) }
                .firstOrNull()
            
            // Si trouvé, retourner
            if (line != null) {
                return Result.success(line)
            }
            
            // Sinon, chercher dans les bus (chargement à la demande)
            val cachedBus = cache?.getBusLines()
            val busLine = if (cachedBus != null) {
                // Cache hit pour les bus
                android.util.Log.d("TransportRepository", "Cache HIT: Loading bus line $lineName from cache")
                cachedBus.filter { it.properties.ligne.equals(lineName, ignoreCase = true) }
                    .firstOrNull()
            } else {
                // Cache miss : charger tous les bus depuis l'API
                android.util.Log.d("TransportRepository", "Cache MISS: Loading all bus lines from API")
                try {
                    val bus = api.getBusLines()
                    // Sauvegarder dans le cache (mémoire uniquement, pas sur disque)
                    cache?.saveBusLines(bus.features)
                    
                    bus.features
                        .filter { it.properties.ligne.equals(lineName, ignoreCase = true) }
                        .firstOrNull()
                } catch (e: OutOfMemoryError) {
                    android.util.Log.e("TransportRepository", "OutOfMemoryError loading bus lines, trying to find line without caching", e)
                    // En cas d'OutOfMemoryError, charger juste la ligne demandée sans tout cacher
                    val bus = api.getBusLines()
                    bus.features
                        .filter { it.properties.ligne.equals(lineName, ignoreCase = true) }
                        .firstOrNull()
                }
            }
            
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
     * Utilise le cache pour améliorer les performances
     */
    suspend fun getAllStops(): Result<StopCollection> {
        return try {
            // Essayer de charger depuis le cache
            val cachedStops = cache?.getStops()
            
            val response: StopCollection
            
            if (cachedStops != null) {
                // Cache hit : utiliser les données en cache
                android.util.Log.d("TransportRepository", "Cache HIT: Loading stops from cache (${cachedStops.size} stops)")
                response = StopCollection(
                    type = "FeatureCollection",
                    features = cachedStops,
                    totalFeatures = cachedStops.size,
                    numberMatched = cachedStops.size,
                    numberReturned = cachedStops.size
                )
            } else {
                // Cache miss : charger depuis l'API
                android.util.Log.d("TransportRepository", "Cache MISS: Loading stops from API")
                response = api.getTransportStops()
            }
            
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
            
            // Sauvegarder dans le cache seulement si ce n'était pas déjà en cache
            if (cachedStops == null) {
                try {
                    cache?.saveStops(filteredCollection.features)
                } catch (e: OutOfMemoryError) {
                    android.util.Log.e("TransportRepository", "OutOfMemoryError saving stops to cache, continuing without cache", e)
                    // Continuer sans cache en cas d'erreur mémoire
                }
            }
            
            Result.success(filteredCollection)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Force le rechargement des arrêts depuis l'API et met à jour le cache
     */
    suspend fun refreshStops(): Result<StopCollection> {
        return try {
            android.util.Log.d("TransportRepository", "Forcing refresh of stops from API")
            cache?.clearStops()
            getAllStops()
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Force le rechargement des lignes depuis l'API et met à jour le cache
     */
    suspend fun refreshLines(): Result<FeatureCollection> {
        return try {
            android.util.Log.d("TransportRepository", "Forcing refresh of lines from API")
            cache?.clearLines()
            getAllLines()
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Vide tout le cache
     */
    suspend fun clearCache() {
        cache?.clearAll()
    }
}
