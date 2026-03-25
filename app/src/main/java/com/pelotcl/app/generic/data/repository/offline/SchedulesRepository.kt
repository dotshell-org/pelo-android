package com.pelotcl.app.generic.data.repository.offline

import android.content.ComponentCallbacks2
import android.content.Context
import android.util.Log
import android.util.LruCache
import com.pelotcl.app.generic.data.repository.itinerary.RaptorRepository
import com.pelotcl.app.generic.ui.components.search.LineSearchResult
import com.pelotcl.app.generic.ui.components.search.StationSearchResult

class SchedulesRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val raptorRepository = RaptorRepository.getInstance(appContext)

    companion object {
        @Volatile
        private var INSTANCE: SchedulesRepository? = null

        private val searchCache = LruCache<String, List<StationSearchResult>>(30)

        fun getInstance(context: Context): SchedulesRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SchedulesRepository(context.applicationContext).also { INSTANCE = it }
            }
        }

        fun trimCaches(level: Int) {
            if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
                searchCache.evictAll()
            } else if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
                searchCache.trimToSize(searchCache.maxSize() / 2)
            }
        }
    }

    fun warmupDatabase() {
        // Binary-only mode: warm up by initializing raptor assets.
    }

    suspend fun searchStopsByName(query: String): List<StationSearchResult> {
        val cacheKey = query.trim().lowercase()
        searchCache.get(cacheKey)?.let { return it }

        val results = raptorRepository.searchStopsByName(query)
            .map { stop ->
                val desserte = raptorRepository.getDesserteForStop(stop.name).orEmpty()
                val lines = if (desserte.isEmpty() || desserte.equals("UNKNOWN", ignoreCase = true)) {
                    // If desserte is empty or UNKNOWN, try to enrich from Raptor
                    val assetsAvailable = raptorRepository.checkAssetsAvailable()
                    if (!assetsAvailable) {
                        Log.w("SchedulesRepository", "Stop ${stop.name} has no desserte data - Raptor assets may be missing")
                        // Return empty list but don't filter out the stop completely
                        emptyList()
                    } else {
                        // Try to get desserte from Raptor even if WFS says UNKNOWN
                        val raptorDesserte = raptorRepository.getDesserteForStop(stop.name).orEmpty()
                        if (raptorDesserte.isNotBlank() && raptorDesserte != "UNKNOWN") {
                            raptorDesserte.split(',')
                                .mapNotNull { part ->
                                    val token = part.trim()
                                    if (token.isEmpty()) null else token.substringBefore(':').trim()
                                }
                                .filter { it.isNotEmpty() }
                                .distinct()
                        } else {
                            // No desserte available from either source
                            emptyList()
                        }
                    }
                } else {
                    desserte.split(',')
                        .mapNotNull { part ->
                            val token = part.trim()
                            if (token.isEmpty()) null else token.substringBefore(':').trim()
                        }
                        .filter { it.isNotEmpty() }
                        .distinct()
                }
                StationSearchResult(stop.name, lines)
            }
            .distinctBy { it.stopName.lowercase() }
            .take(50)

        if (cacheKey.length >= 2 && results.isNotEmpty()) {
            searchCache.put(cacheKey, results)
        }
        return results
    }

    fun searchLinesByName(query: String): List<LineSearchResult> {
        return raptorRepository.searchLinesByName(query)
    }

    fun getAllRouteNames(): List<String> {
        return raptorRepository.searchLinesByName("").map { it.lineName }.distinct().sorted()
    }

    fun getAllBusLikeRouteNames(): List<String> {
        return getAllRouteNames()
    }

    fun getHeadsigns(routeName: String): Map<Int, String> {
        return raptorRepository.getHeadsigns(routeName)
    }

    fun getDesserteForStop(stopName: String): String? {
        return raptorRepository.getDesserteForStop(stopName)
    }

    fun getStopSequences(routeName: String, directionId: Int): List<Pair<String, Int>> {
        return raptorRepository.getStopSequences(routeName, directionId)
    }

    fun getSchedules(
        lineName: String,
        stopName: String,
        directionId: Int,
        isSchoolHoliday: Boolean,
        isPublicHoliday: Boolean
    ): List<String> {
        return raptorRepository.getSchedules(
            lineName = lineName,
            stopName = stopName,
            directionId = directionId,
            isSchoolHoliday = isSchoolHoliday,
            isPublicHoliday = isPublicHoliday
        )
    }
}