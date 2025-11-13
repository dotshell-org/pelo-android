package com.pelotcl.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pelotcl.app.ui.components.MapLibreView
import org.maplibre.android.geometry.LatLng

@Composable
fun PlanScreen(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        MapLibreView(
            modifier = Modifier.fillMaxSize(),
            initialPosition = LatLng(46.8182, 8.2275), // Centre de la Suisse
            initialZoom = 7.0,
            styleUrl = "https://demotiles.maplibre.org/style.json", // Style MapLibre par défaut
            onMapReady = { map ->
                // Vous pouvez ajouter des marqueurs, des couches, etc. ici
                // Exemple: map.addMarker(MarkerOptions().position(LatLng(46.8182, 8.2275)))
            }
        )
    }
}
