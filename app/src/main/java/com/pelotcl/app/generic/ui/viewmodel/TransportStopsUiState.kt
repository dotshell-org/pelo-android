package com.pelotcl.app.generic.ui.viewmodel

import com.pelotcl.app.generic.data.models.geojson.StopFeature

/**
 * Etats pour les arrets de transport (Compatibilite PlanScreen)
 */
sealed class TransportStopsUiState {
    data object Loading : TransportStopsUiState()
    data class Success(val stops: List<StopFeature>) : TransportStopsUiState()
    data class Error(val message: String) : TransportStopsUiState()
}