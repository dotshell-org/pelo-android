package com.pelotcl.app.generic.ui.viewmodel

import com.pelotcl.app.generic.data.models.FeatureCollection

/**
 * Etats pour les lignes de transport
 */
sealed class TransportLinesState {
    data object Loading : TransportLinesState()
    data class Success(val lines: FeatureCollection) : TransportLinesState()
    data class Error(val message: String) : TransportLinesState()
}