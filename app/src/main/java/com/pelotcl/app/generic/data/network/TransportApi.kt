package com.pelotcl.app.generic.data.network

import com.pelotcl.app.generic.data.models.FeatureCollection
import com.pelotcl.app.generic.data.models.StopCollection
import com.pelotcl.app.generic.data.models.TrafficAlertsResponse

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
