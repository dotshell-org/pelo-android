package com.pelotcl.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.pelotcl.app.ui.components.MapLibreView
import com.pelotcl.app.ui.viewmodel.TransportLinesUiState
import com.pelotcl.app.ui.viewmodel.TransportViewModel
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource

@Composable
fun PlanScreen(
    modifier: Modifier = Modifier,
    viewModel: TransportViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    
    // Charger la ligne A au démarrage
    LaunchedEffect(Unit) {
        viewModel.loadLineByName("A")
    }
    
    // Quand les données sont chargées et la carte est prête, afficher la ligne
    LaunchedEffect(uiState, mapInstance) {
        val map = mapInstance ?: return@LaunchedEffect
        
        when (val state = uiState) {
            is TransportLinesUiState.Success -> {
                state.lines.forEach { feature ->
                    addLineToMap(map, feature)
                }
            }
            else -> {}
        }
    }
    
    Box(modifier = modifier.fillMaxSize()) {
        MapLibreView(
            modifier = Modifier.fillMaxSize(),
            initialPosition = LatLng(45.75, 4.85),
            initialZoom = 12.0,
            styleUrl = "https://tiles.openfreemap.org/styles/positron",
            onMapReady = { map ->
                mapInstance = map
            }
        )
        
        // Afficher un indicateur de chargement
        if (uiState is TransportLinesUiState.Loading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

/**
 * Ajoute une ligne de transport sur la carte MapLibre
 */
private fun addLineToMap(
    map: MapLibreMap,
    feature: com.pelotcl.app.data.model.Feature
) {
    map.getStyle { style ->
        val sourceId = "line-${feature.properties.ligne}"
        val layerId = "layer-${feature.properties.ligne}"
        
        // Supprimer l'ancienne couche et source si elles existent
        style.getLayer(layerId)?.let { style.removeLayer(it) }
        style.getSource(sourceId)?.let { style.removeSource(it) }
        
        // Créer le GeoJSON pour la ligne
        val geoJson = createGeoJsonFromFeature(feature)
        
        // Ajouter la source de données
        val source = GeoJsonSource(sourceId, geoJson)
        style.addSource(source)
        
        // Créer la couche de ligne avec la couleur rose
        val lineLayer = LineLayer(layerId, sourceId).apply {
            setProperties(
                PropertyFactory.lineColor("#F472B6"), // Rose (Pink 400 from Tailwind)
                PropertyFactory.lineWidth(4f),
                PropertyFactory.lineOpacity(0.8f),
                PropertyFactory.lineCap("round"),
                PropertyFactory.lineJoin("round")
            )
        }
        
        style.addLayer(lineLayer)
    }
}

/**
 * Convertit une Feature en GeoJSON String pour MapLibre
 */
private fun createGeoJsonFromFeature(feature: com.pelotcl.app.data.model.Feature): String {
    val geoJsonObject = JsonObject().apply {
        addProperty("type", "Feature")
        
        // Ajouter la géométrie
        val geometryObject = JsonObject().apply {
            addProperty("type", feature.geometry.type)
            
            // Convertir les coordonnées
            val coordinatesArray = JsonArray()
            feature.geometry.coordinates.forEach { lineString ->
                val lineStringArray = JsonArray()
                lineString.forEach { point ->
                    val pointArray = JsonArray()
                    point.forEach { coord ->
                        pointArray.add(coord)
                    }
                    lineStringArray.add(pointArray)
                }
                coordinatesArray.add(lineStringArray)
            }
            add("coordinates", coordinatesArray)
        }
        add("geometry", geometryObject)
        
        // Ajouter les propriétés
        val propertiesObject = JsonObject().apply {
            addProperty("ligne", feature.properties.ligne)
            addProperty("nom_trace", feature.properties.nomTrace)
            addProperty("couleur", feature.properties.couleur)
        }
        add("properties", propertiesObject)
    }
    
    return geoJsonObject.toString()
}
