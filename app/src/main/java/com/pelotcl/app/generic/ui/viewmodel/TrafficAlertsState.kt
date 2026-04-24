package com.pelotcl.app.generic.ui.viewmodel

import com.pelotcl.app.generic.data.models.TrafficAlert

/**
 * Etats pour les alertes trafic
 */
sealed class TrafficAlertsState {
    data object Loading : TrafficAlertsState()
    data class Success(val alerts: List<TrafficAlert>) : TrafficAlertsState()
    data class Error(val message: String) : TrafficAlertsState()
}