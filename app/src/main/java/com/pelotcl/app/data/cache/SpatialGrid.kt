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
