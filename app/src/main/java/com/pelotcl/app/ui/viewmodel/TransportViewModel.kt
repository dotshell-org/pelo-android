package com.pelotcl.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pelotcl.app.data.model.Feature
import com.pelotcl.app.data.repository.TransportRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * État de l'UI pour les lignes de transport
 */
sealed class TransportLinesUiState {
    object Loading : TransportLinesUiState()
    data class Success(val lines: List<Feature>) : TransportLinesUiState()
    data class Error(val message: String) : TransportLinesUiState()
}

/**
 * ViewModel pour gérer les données des lignes de transport
 */
class TransportViewModel : ViewModel() {
    
    private val repository = TransportRepository()
    
    private val _uiState = MutableStateFlow<TransportLinesUiState>(TransportLinesUiState.Loading)
    val uiState: StateFlow<TransportLinesUiState> = _uiState.asStateFlow()
    
    /**
     * Charge toutes les lignes de transport
     */
    fun loadAllLines() {
        viewModelScope.launch {
            _uiState.value = TransportLinesUiState.Loading
            repository.getAllLines()
                .onSuccess { featureCollection ->
                    _uiState.value = TransportLinesUiState.Success(featureCollection.features)
                }
                .onFailure { exception ->
                    _uiState.value = TransportLinesUiState.Error(
                        exception.message ?: "Une erreur est survenue"
                    )
                }
        }
    }
    
    /**
     * Charge une ligne spécifique par son nom
     */
    fun loadLineByName(lineName: String) {
        viewModelScope.launch {
            _uiState.value = TransportLinesUiState.Loading
            repository.getLineByName(lineName)
                .onSuccess { feature ->
                    _uiState.value = if (feature != null) {
                        TransportLinesUiState.Success(listOf(feature))
                    } else {
                        TransportLinesUiState.Error("Ligne $lineName non trouvée")
                    }
                }
                .onFailure { exception ->
                    _uiState.value = TransportLinesUiState.Error(
                        exception.message ?: "Une erreur est survenue"
                    )
                }
        }
    }
}
