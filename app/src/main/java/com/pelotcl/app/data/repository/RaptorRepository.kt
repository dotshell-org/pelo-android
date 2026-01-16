package com.pelotcl.app.data.repository

import android.content.Context
import android.util.Log
import io.raptor.RaptorLibrary
import io.raptor.core.JourneyLeg as RaptorJourneyLeg
import io.raptor.model.Stop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Calendar
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Repository to handle raptor-kt route calculations
 */
class RaptorRepository(private val context: Context) {

    private var raptorLibrary: RaptorLibrary? = null
    private var stopsCache: List<Stop> = emptyList()
    private val mutex = Mutex()
    private var isInitialized = false

    /**
     * Initialize the Raptor library with stops.bin and routes.bin from assets
     */
    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (isInitialized && raptorLibrary != null) {
                return@withContext Result.success(Unit)
            }

            try {
                val stopsInputStream = context.assets.open("stops.bin")
                val routesInputStream = context.assets.open("routes.bin")

                raptorLibrary = RaptorLibrary(
                    stopsInputStream = stopsInputStream,
                    routesInputStream = routesInputStream
                )
                
                // Cache all stops for lookup
                stopsCache = raptorLibrary?.searchStopsByName("") ?: emptyList()

                isInitialized = true
                Log.d("RaptorRepository", "Raptor library initialized successfully with ${stopsCache.size} stops")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e("RaptorRepository", "Failed to initialize Raptor library: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Search for stops by name
     */
    suspend fun searchStopsByName(query: String): List<RaptorStop> = withContext(Dispatchers.IO) {
        ensureInitialized()
        try {
            raptorLibrary?.searchStopsByName(query)?.map { stop ->
                RaptorStop(
                    id = stop.id,
                    name = stop.name,
                    lat = stop.lat,
                    lon = stop.lon
                )
            } ?: emptyList()
        } catch (e: Exception) {
            Log.e("RaptorRepository", "Error searching stops: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Find the closest stop to the given GPS coordinates
     */
    suspend fun findClosestStop(latitude: Double, longitude: Double): RaptorStop? = withContext(Dispatchers.IO) {
        ensureInitialized()
        try {
            // Find the closest stop by calculating distance
            stopsCache.minByOrNull { stop ->
                val latDiff = stop.lat - latitude
                val lonDiff = stop.lon - longitude
                sqrt(latDiff.pow(2) + lonDiff.pow(2))
            }?.let { stop ->
                RaptorStop(
                    id = stop.id,
                    name = stop.name,
                    lat = stop.lat,
                    lon = stop.lon
                )
            }
        } catch (e: Exception) {
            Log.e("RaptorRepository", "Error finding closest stop: ${e.message}", e)
            null
        }
    }

    /**
     * Calculate optimized journeys between origin and destination stops
     * @param originStopIds List of origin stop IDs (to handle stops with multiple platforms)
     * @param destinationStopIds List of destination stop IDs
     * @param departureTimeSeconds Departure time in seconds from midnight (default: current time)
     */
    suspend fun getOptimizedPaths(
        originStopIds: List<Int>,
        destinationStopIds: List<Int>,
        departureTimeSeconds: Int? = null
    ): List<JourneyResult> = withContext(Dispatchers.IO) {
        ensureInitialized()
        try {
            val depTime = departureTimeSeconds ?: getCurrentTimeInSeconds()
            
            // getOptimizedPaths returns List<List<JourneyLeg>> - list of journeys
            val journeys = raptorLibrary?.getOptimizedPaths(
                originStopIds = originStopIds,
                destinationStopIds = destinationStopIds,
                departureTime = depTime
            ) ?: emptyList()

            // Build a map from stop ID to Stop for name lookup
            val stopById = stopsCache.associateBy { it.id }

            journeys.mapNotNull { legs ->
                if (legs.isEmpty()) return@mapNotNull null
                
                val journeyLegs = legs.mapNotNull { leg ->
                    // Find stop names from the cached stops by matching index position
                    // The library uses stop indices, so we need to match by position in stopsCache
                    val fromStop = stopsCache.getOrNull(leg.fromStopIndex)
                    val toStop = stopsCache.getOrNull(leg.toStopIndex)
                    
                    if (fromStop == null || toStop == null) return@mapNotNull null
                    
                    JourneyLeg(
                        fromStopId = fromStop.id.toString(),
                        fromStopName = fromStop.name,
                        toStopId = toStop.id.toString(),
                        toStopName = toStop.name,
                        departureTime = leg.departureTime,
                        arrivalTime = leg.arrivalTime,
                        routeName = leg.routeName,
                        routeColor = null, // Library doesn't provide color
                        isWalking = leg.isTransfer,
                        direction = leg.direction
                    )
                }
                
                if (journeyLegs.isEmpty()) return@mapNotNull null
                
                JourneyResult(
                    departureTime = legs.first().departureTime,
                    arrivalTime = legs.last().arrivalTime,
                    legs = journeyLegs
                )
            }
        } catch (e: Exception) {
            Log.e("RaptorRepository", "Error calculating paths: ${e.message}", e)
            emptyList()
        }
    }

    private suspend fun ensureInitialized() {
        if (!isInitialized) {
            initialize()
        }
    }

    private fun getCurrentTimeInSeconds(): Int {
        val calendar = Calendar.getInstance()
        val hours = calendar.get(Calendar.HOUR_OF_DAY)
        val minutes = calendar.get(Calendar.MINUTE)
        val seconds = calendar.get(Calendar.SECOND)
        return hours * 3600 + minutes * 60 + seconds
    }
}

/**
 * Data class representing a stop from Raptor
 */
data class RaptorStop(
    val id: Int,
    val name: String,
    val lat: Double = 0.0,
    val lon: Double = 0.0
)

/**
 * Data class representing a journey result
 */
data class JourneyResult(
    val departureTime: Int, // in seconds from midnight
    val arrivalTime: Int,   // in seconds from midnight
    val legs: List<JourneyLeg>
) {
    val durationMinutes: Int
        get() = (arrivalTime - departureTime) / 60

    fun formatDepartureTime(): String = formatTime(departureTime)
    fun formatArrivalTime(): String = formatTime(arrivalTime)

    private fun formatTime(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        return String.format("%02d:%02d", hours, minutes)
    }
}

/**
 * Data class representing a leg of a journey
 */
data class JourneyLeg(
    val fromStopId: String,
    val fromStopName: String,
    val toStopId: String,
    val toStopName: String,
    val departureTime: Int,
    val arrivalTime: Int,
    val routeName: String?,
    val routeColor: String?,
    val isWalking: Boolean,
    val direction: String? = null
) {
    val durationMinutes: Int
        get() = (arrivalTime - departureTime) / 60

    fun formatDepartureTime(): String = formatTime(departureTime)
    fun formatArrivalTime(): String = formatTime(arrivalTime)

    private fun formatTime(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        return String.format("%02d:%02d", hours, minutes)
    }
}
