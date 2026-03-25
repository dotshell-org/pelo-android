package com.pelotcl.app.generic.data.network

import com.pelotcl.app.generic.data.model.FeatureCollection
import com.pelotcl.app.generic.data.model.StopCollection
import com.pelotcl.app.generic.data.model.TrafficAlertsResponse

/**
 * Query object for retrieving transport line geometries.
 */
sealed interface TransportLinesQuery {
    /**
     * Returns all default (non-bus-by-pagination) strong line geometries that should appear
     * on the map without loading individual line datasets on demand.
     */
    data object StrongLines : TransportLinesQuery

    /**
     * Returns the geometry for a single line by its display name.
     * Implementations must handle any special aliasing (e.g. airport shuttle names).
     */
    data class LineByName(val lineName: String) : TransportLinesQuery

    /**
     * Returns a page of bus-like line geometries.
     * Used for offline downloads (pagination/OOM protection).
     */
    data class BusPage(val startIndex: Int, val count: Int) : TransportLinesQuery
}

/**
 * Abstract interface for urban transport APIs.
 * Implementations live in `specific` and encapsulate all city/network details.
 */
interface TransportApi {
    /**
     * Single entry point to retrieve line geometries.
     */
    suspend fun getLines(query: TransportLinesQuery): FeatureCollection

    /**
     * Fetches transport stops.
     */
    suspend fun getTransportStops(): StopCollection

    /**
     * Fetches traffic alerts.
     */
    suspend fun getTrafficAlerts(): TrafficAlertsResponse
}
