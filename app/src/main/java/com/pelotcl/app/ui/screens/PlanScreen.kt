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
import com.pelotcl.app.ui.viewmodel.TransportStopsUiState
import com.pelotcl.app.ui.viewmodel.TransportViewModel
import com.pelotcl.app.utils.LineColorHelper
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource

@Composable
fun PlanScreen(
    modifier: Modifier = Modifier,
    viewModel: TransportViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val stopsUiState by viewModel.stopsUiState.collectAsState()
    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    
    // Charger toutes les lignes et arrêts au démarrage
    LaunchedEffect(Unit) {
        viewModel.loadAllLines()
        viewModel.loadAllStops()
    }
    
    // Quand les données sont chargées et la carte est prête, afficher les lignes
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
    
    // Quand les arrêts sont chargés et la carte est prête, afficher les arrêts
    LaunchedEffect(stopsUiState, mapInstance) {
        val map = mapInstance ?: return@LaunchedEffect
        
        when (val state = stopsUiState) {
            is TransportStopsUiState.Success -> {
                addStopsToMap(map, state.stops)
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
        if (uiState is TransportLinesUiState.Loading || stopsUiState is TransportStopsUiState.Loading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

/**
 * Ajoute une ligne de transport sur la carte MapLibre avec la couleur appropriée
 */
private fun addLineToMap(
    map: MapLibreMap,
    feature: com.pelotcl.app.data.model.Feature
) {
    map.getStyle { style ->
        val sourceId = "line-${feature.properties.ligne}-${feature.properties.codeTrace}"
        val layerId = "layer-${feature.properties.ligne}-${feature.properties.codeTrace}"
        
        // Supprimer l'ancienne couche et source si elles existent
        style.getLayer(layerId)?.let { style.removeLayer(it) }
        style.getSource(sourceId)?.let { style.removeSource(it) }
        
        // Créer le GeoJSON pour la ligne
        val lineGeoJson = createGeoJsonFromFeature(feature)
        
        // Ajouter la source de données pour la ligne
        val lineSource = GeoJsonSource(sourceId, lineGeoJson)
        style.addSource(lineSource)
        
        // Obtenir la couleur appropriée pour cette ligne
        val lineColor = LineColorHelper.getColorForLine(feature)
        
        // Créer la couche de ligne avec la couleur appropriée
        val lineLayer = LineLayer(layerId, sourceId).apply {
            setProperties(
                PropertyFactory.lineColor(lineColor),
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
 * Ajoute tous les arrêts de transport sur la carte avec des cercles
 */
private fun addStopsToMap(
    map: MapLibreMap,
    stops: List<com.pelotcl.app.data.model.StopFeature>
) {
    map.getStyle { style ->
        val sourceId = "transport-stops"
        val layerId = "transport-stops-layer"
        
        // Supprimer l'ancienne couche et source si elles existent
        style.getLayer(layerId)?.let { style.removeLayer(it) }
        style.getSource(sourceId)?.let { style.removeSource(it) }
        
        // Créer le GeoJSON pour les arrêts
        val stopsGeoJson = createStopsGeoJsonFromStops(stops)
        
        // Ajouter la source de données pour les arrêts
        val stopsSource = GeoJsonSource(sourceId, stopsGeoJson)
        style.addSource(stopsSource)
        
        // Créer la couche de cercles pour les arrêts
        val stopsLayer = CircleLayer(layerId, sourceId).apply {
            setProperties(
                PropertyFactory.circleColor("#FFFFFF"),
                PropertyFactory.circleRadius(4f),
                PropertyFactory.circleOpacity(0.8f),
                PropertyFactory.circleStrokeColor("#333333"),
                PropertyFactory.circleStrokeWidth(2f)
            )
        }
        
        style.addLayer(stopsLayer)
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

/**
 * Crée un GeoJSON contenant des Points pour tous les arrêts de transport
 */
private fun createStopsGeoJsonFromStops(stops: List<com.pelotcl.app.data.model.StopFeature>): String {
    val features = JsonArray()
    
    // Parcourir tous les arrêts pour créer les features GeoJSON
    stops.forEach { stop ->
        val pointFeature = JsonObject().apply {
            addProperty("type", "Feature")
            
            // Géométrie du point (utiliser directement les coordonnées de l'arrêt)
            val pointGeometry = JsonObject().apply {
                addProperty("type", "Point")
                val coordinatesArray = JsonArray()
                coordinatesArray.add(stop.geometry.coordinates[0]) // longitude
                coordinatesArray.add(stop.geometry.coordinates[1]) // latitude
                add("coordinates", coordinatesArray)
            }
            add("geometry", pointGeometry)
            
            // Propriétés du point
            val properties = JsonObject().apply {
                addProperty("nom", stop.properties.nom)
                addProperty("desserte", stop.properties.desserte)
                addProperty("pmr", stop.properties.pmr)
                addProperty("type", "stop")
            }
            add("properties", properties)
        }
        features.add(pointFeature)
    }
    
    val geoJsonCollection = JsonObject().apply {
        addProperty("type", "FeatureCollection")
        add("features", features)
    }
    
    return geoJsonCollection.toString()
}
