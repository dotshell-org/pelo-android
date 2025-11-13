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
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.pelotcl.app.ui.components.MapLibreView
import com.pelotcl.app.ui.viewmodel.TransportLinesUiState
import com.pelotcl.app.ui.viewmodel.TransportStopsUiState
import com.pelotcl.app.ui.viewmodel.TransportViewModel
import com.pelotcl.app.utils.BusIconHelper
import com.pelotcl.app.utils.LineColorHelper
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource

private val ALWAYS_VISIBLE_LINES = setOf("F1", "F2", "B", "C", "D")
private const val SECONDARY_STOPS_MIN_ZOOM = 13.8f

@Composable
fun PlanScreen(
    modifier: Modifier = Modifier,
    viewModel: TransportViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val stopsUiState by viewModel.stopsUiState.collectAsState()
    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    val context = LocalContext.current
    
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
                addStopsToMap(map, state.stops, context)
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
 * Ajoute tous les arrêts de transport sur la carte avec les icônes correspondantes
 */
private fun addStopsToMap(
    map: MapLibreMap,
    stops: List<com.pelotcl.app.data.model.StopFeature>,
    context: android.content.Context
) {
    map.getStyle { style ->
        val sourceId = "transport-stops"
        val priorityLayerId = "transport-stops-layer-priority"
        val secondaryLayerId = "transport-stops-layer-secondary"

        // Remove old layers and sources if they exist
        listOf(priorityLayerId, secondaryLayerId).forEach { id ->
            style.getLayer(id)?.let { style.removeLayer(it) }
        }
        style.getSource(sourceId)?.let { style.removeSource(it) }

        // Collect unique icons and valid stops
        val requiredIcons = mutableSetOf<String>()
        val validStops = mutableListOf<com.pelotcl.app.data.model.StopFeature>()

        stops.forEach { stop ->
            val iconName = BusIconHelper.getIconNameForStop(stop)
            if (iconName != null) {
                val resourceId = context.resources.getIdentifier(iconName, "drawable", context.packageName)
                if (resourceId != 0) {
                    requiredIcons.add(iconName)
                    validStops.add(stop)
                }
            }
        }

        // Load all icons into the style
        requiredIcons.forEach { iconName ->
            try {
                val resourceId = context.resources.getIdentifier(iconName, "drawable", context.packageName)
                if (resourceId != 0) {
                    val drawable = ContextCompat.getDrawable(context, resourceId)
                    drawable?.let {
                        style.addImage(iconName, it)
                    }
                }
            } catch (e: Exception) {
                println("Error loading icon $iconName: ${e.message}")
            }
        }

        // Create GeoJSON for stops with icon information
        val stopsGeoJson = createStopsGeoJsonFromStops(validStops)

        // Add data source for stops
        val stopsSource = GeoJsonSource(sourceId, stopsGeoJson)
        style.addSource(stopsSource)

        // Create two layers: priority stops always visible, secondary stops visible at closer zoom
        val priorityStopsLayer = SymbolLayer(priorityLayerId, sourceId).apply {
            setProperties(
                PropertyFactory.iconImage("{icon}"),
                PropertyFactory.iconSize(0.7f),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.iconAnchor("center")
            )
            setFilter(Expression.eq(Expression.get("priority_stop"), true))
        }

        val secondaryStopsLayer = SymbolLayer(secondaryLayerId, sourceId).apply {
            setProperties(
                PropertyFactory.iconImage("{icon}"),
                PropertyFactory.iconSize(0.65f),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.iconAnchor("center")
            )
            setFilter(Expression.eq(Expression.get("priority_stop"), false))
            setMinZoom(SECONDARY_STOPS_MIN_ZOOM)
        }

        style.addLayer(priorityStopsLayer)
        style.addLayer(secondaryStopsLayer)
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
 * Crée un GeoJSON contenant des Points pour tous les arrêts de transport avec informations d'icônes
 */
private fun createStopsGeoJsonFromStops(stops: List<com.pelotcl.app.data.model.StopFeature>): String {
    val features = JsonArray()

    stops.forEach { stop ->
        val iconName = BusIconHelper.getIconNameForStop(stop)!!
        val isPriorityStop = BusIconHelper.getAllLinesForStop(stop).any { line ->
            ALWAYS_VISIBLE_LINES.contains(line.uppercase())
        }

        val pointFeature = JsonObject().apply {
            addProperty("type", "Feature")

            val pointGeometry = JsonObject().apply {
                addProperty("type", "Point")
                val coordinatesArray = JsonArray()
                coordinatesArray.add(stop.geometry.coordinates[0])
                coordinatesArray.add(stop.geometry.coordinates[1])
                add("coordinates", coordinatesArray)
            }
            add("geometry", pointGeometry)

            val properties = JsonObject().apply {
                addProperty("nom", stop.properties.nom)
                addProperty("desserte", stop.properties.desserte)
                addProperty("pmr", stop.properties.pmr)
                addProperty("type", "stop")
                addProperty("icon", iconName)
                addProperty("priority_stop", isPriorityStop)
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
