package com.pelotcl.app.specific.data.mapper

import com.pelotcl.app.generic.data.model.TrafficAlert
import com.pelotcl.app.generic.data.model.TrafficAlertsResponse
import com.pelotcl.app.specific.data.model.LyonTrafficAlert
import com.pelotcl.app.specific.data.model.LyonTrafficAlertsResponse

/**
 * Mapper to convert between Lyon-specific traffic alert models and generic models
 */
object TrafficAlertMapper {

    /**
     * Convert a Lyon-specific traffic alert to a generic traffic alert
     */
    fun mapToGeneric(lyonAlert: LyonTrafficAlert): TrafficAlert {
        return TrafficAlert(
            cause = lyonAlert.cause,
            startDate = lyonAlert.startDate,
            endDate = lyonAlert.endDate,
            lastUpdate = lyonAlert.lastUpdate,
            lineCode = lyonAlert.lineCode,
            lineName = lyonAlert.lineName,
            objectList = lyonAlert.objectList,
            message = lyonAlert.message,
            mode = lyonAlert.mode,
            alertNumber = lyonAlert.alertNumber,
            severityLevel = lyonAlert.severityLevel,
            title = lyonAlert.title,
            alertType = lyonAlert.alertType,
            objectType = lyonAlert.objectType,
            severityType = lyonAlert.severityType
        )
    }

    /**
     * Convert a list of Lyon-specific traffic alerts to generic traffic alerts
     */
    fun mapToGenericList(lyonAlerts: List<LyonTrafficAlert>): List<TrafficAlert> {
        return lyonAlerts.map { mapToGeneric(it) }
    }

    /**
     * Convert a Lyon-specific traffic alerts response to a generic response
     */
    fun mapResponseToGeneric(lyonResponse: LyonTrafficAlertsResponse): TrafficAlertsResponse {
        return TrafficAlertsResponse(
            success = lyonResponse.success,
            alerts = mapToGenericList(lyonResponse.alerts),
            timestamp = lyonResponse.timestamp,
            lastUpdated = lyonResponse.lastUpdated
        )
    }

    /**
     * Convert a generic traffic alert to Lyon-specific format (for caching/offline storage)
     */
    fun mapToLyon(genericAlert: TrafficAlert): LyonTrafficAlert {
        return LyonTrafficAlert(
            cause = genericAlert.cause,
            startDate = genericAlert.startDate,
            endDate = genericAlert.endDate,
            lastUpdate = genericAlert.lastUpdate,
            lineCode = genericAlert.lineCode,
            lineName = genericAlert.lineName,
            objectList = genericAlert.objectList,
            message = genericAlert.message,
            mode = genericAlert.mode,
            alertNumber = genericAlert.alertNumber,
            severityLevel = genericAlert.severityLevel,
            title = genericAlert.title,
            alertType = genericAlert.alertType,
            objectType = genericAlert.objectType,
            severityType = genericAlert.severityType
        )
    }

    /**
     * Convert a list of generic traffic alerts to Lyon-specific format
     */
    fun mapToLyonList(genericAlerts: List<TrafficAlert>): List<LyonTrafficAlert> {
        return genericAlerts.map { mapToLyon(it) }
    }
}