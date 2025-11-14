package com.pelotcl.app.ui.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap

@Composable
fun MapLibreView(
    modifier: Modifier = Modifier,
    initialPosition: LatLng = LatLng(45.75, 4.85),
    initialZoom: Double = 10.0,
    styleUrl: String = "https://tiles.openfreemap.org/styles/positron",
    onMapReady: (MapLibreMap) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Initialiser MapLibre une seule fois
    remember {
        MapLibre.getInstance(context)
    }

    val mapView = remember {
        MapView(context).apply {
            getMapAsync { map ->
                // Désactiver la rotation de la carte
                map.uiSettings.isRotateGesturesEnabled = false
                
                map.setStyle(styleUrl) { style ->
                    // Configuration de la position initiale de la caméra
                    map.cameraPosition = CameraPosition.Builder()
                        .target(initialPosition)
                        .zoom(initialZoom)
                        .build()
                    
                    onMapReady(map)
                }
            }
        }
    }

    // Gérer le cycle de vie du MapView
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(null)
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier
    )
}
