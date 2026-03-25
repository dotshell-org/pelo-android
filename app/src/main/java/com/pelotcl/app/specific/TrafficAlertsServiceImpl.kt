package com.pelotcl.app.specific

import com.pelotcl.app.generic.data.model.TrafficAlertsResponse
import com.pelotcl.app.generic.data.network.TrafficAlertsService
import com.pelotcl.app.specific.data.network.LyonTransportApi

/**
 * Lyon-specific implementation of TrafficAlertsService
 * Handles traffic alert operations for the Lyon transport network
 */
class TrafficAlertsServiceImpl : TrafficAlertsService {
    
    private val lyonTransportApi = LyonTransportApi("https://api.dotshell.eu/")
    
    override fun getTrafficAlertsUrl(): String {
        return "https://api.dotshell.eu/pelo/v1/traffic/alerts"
    }
    
    override suspend fun getTrafficAlerts(): TrafficAlertsResponse {
        return lyonTransportApi.getTrafficAlerts()
    }
    
    override suspend fun getTrafficAlertsByLine(lineName: String): TrafficAlertsResponse {
        val allAlerts = getTrafficAlerts()
        // Filter alerts by line name (check both lineCode and lineName)
        val filteredAlerts = allAlerts.copy(
            alerts = allAlerts.alerts.filter { alert ->
                alert.lineName.equals(lineName, ignoreCase = true) ||
                alert.lineCode.equals(lineName, ignoreCase = true)
            }
        )
        return filteredAlerts
    }
}