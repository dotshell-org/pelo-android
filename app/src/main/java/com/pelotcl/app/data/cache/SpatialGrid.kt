package com.pelotcl.app.data.cache

import com.pelotcl.app.data.model.StopFeature
import kotlin.math.floor

/**
 * Spatial grid index for fast geographic queries of stops
 * Partitions the map into cells for efficient bounding-box queries
 */
class SpatialGrid(
    private val cellSize: Double = 0.01 // ~1km at equator
) {
    private val grid = mutableMapOf<Pair<Int, Int>, MutableList<StopFeature>>()
    
    /**
     * Add a stop to the spatial grid
     */
    fun addStop(stop: StopFeature) {
        val coords = stop.geometry.coordinates
        if (coords.size >= 2) {
            val lon = coords[0]
            val lat = coords[1]
            val cell = getCellKey(lon, lat)
            grid.getOrPut(cell) { mutableListOf() }.add(stop)
        }
    }
    
    /**
     * Build the grid from a list of stops
     */
    fun build(stops: List<StopFeature>) {
        grid.clear()
        stops.forEach { addStop(it) }
    }
    
    /**
     * Query stops within a bounding box
     * @param minLon Minimum longitude
     * @param minLat Minimum latitude
     * @param maxLon Maximum longitude
     * @param maxLat Maximum latitude
     * @return List of stops within the bounding box
     */
    fun queryBoundingBox(
        minLon: Double,
        minLat: Double,
        maxLon: Double,
        maxLat: Double
    ): List<StopFeature> {
        val result = mutableListOf<StopFeature>()
        val minCell = getCellKey(minLon, minLat)
        val maxCell = getCellKey(maxLon, maxLat)
        
        for (x in minCell.first..maxCell.first) {
            for (y in minCell.second..maxCell.second) {
                grid[Pair(x, y)]?.let { stops ->
                    stops.forEach { stop ->
                        val coords = stop.geometry.coordinates
                        if (coords.size >= 2) {
                            val lon = coords[0]
                            val lat = coords[1]
                            if (lon in minLon..maxLon && lat in minLat..maxLat) {
                                result.add(stop)
                            }
                        }
                    }
                }
            }
        }
        
        return result
    }
    
    /**
     * Get all stops in the grid
     */
    fun getAllStops(): List<StopFeature> {
        return grid.values.flatten()
    }
    
    /**
     * Clear the grid
     */
    fun clear() {
        grid.clear()
    }
    
    /**
     * Get the number of stops in the grid
     */
    val size: Int
        get() = grid.values.sumOf { it.size }
    
    private fun getCellKey(lon: Double, lat: Double): Pair<Int, Int> {
        val x = floor(lon / cellSize).toInt()
        val y = floor(lat / cellSize).toInt()
        return Pair(x, y)
    }
}
