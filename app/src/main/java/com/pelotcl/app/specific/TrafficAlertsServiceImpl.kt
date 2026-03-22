package com.pelotcl.app.specific

import com.pelotcl.app.generic.data.model.TrafficAlertsResponse
import com.pelotcl.app.generic.data.network.TrafficAlertsService
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Lyon-specific implementation of TrafficAlertsService
 * Handles traffic alert operations for the Lyon transport network
 */
class TrafficAlertsServiceImpl : TrafficAlertsService {
    
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.dotshell.eu/") // Different base URL for traffic alerts
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    
    private val apiService = retrofit.create(TransportApiImpl::class.java)
    
    override fun getTrafficAlertsUrl(): String {
        return "https://api.dotshell.eu/pelo/v1/traffic/alerts"
    }
    
    override suspend fun getTrafficAlerts(): TrafficAlertsResponse {
        return apiService.getTrafficAlerts()
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