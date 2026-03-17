package com.pelotcl.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.res.Resources
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.pelotcl.app.R
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.pelotcl.app.ui.components.AllSchedulesSheetContent
import com.pelotcl.app.ui.components.InlineItinerarySheetContent
import com.pelotcl.app.ui.components.LineDetailsBottomSheet
import com.pelotcl.app.ui.components.LineInfo
import com.pelotcl.app.ui.components.LinesBottomSheet
import com.pelotcl.app.ui.components.MapLibreView
import com.pelotcl.app.ui.components.StationBottomSheet
import com.pelotcl.app.ui.components.StationInfo
import com.pelotcl.app.ui.components.StationSearchResult
import com.pelotcl.app.ui.screens.SelectedStop
import com.pelotcl.app.data.repository.MapStyle
import com.pelotcl.app.data.repository.MapStyleRepository
import com.pelotcl.app.ui.viewmodel.TransportLinesUiState
import com.pelotcl.app.ui.viewmodel.TransportStopsUiState
import com.pelotcl.app.ui.viewmodel.TransportViewModel
import com.pelotcl.app.utils.BusIconHelper
import com.pelotcl.app.utils.LineColorHelper
import com.pelotcl.app.ui.theme.Red500
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import androidx.compose.ui.text.font.FontWeight
import androidx.core.graphics.createBitmap
import androidx.lifecycle.ViewModel
import com.google.gson.JsonParser
import com.pelotcl.app.data.model.Feature
import com.pelotcl.app.data.model.StopFeature
import com.pelotcl.app.data.model.StopGeometry
import com.pelotcl.app.data.model.StopProperties
import com.pelotcl.app.utils.LocationHelper.startLocationUpdates
import com.pelotcl.app.utils.LocationHelper.stopLocationUpdates
import kotlinx.coroutines.delay
import org.maplibre.android.maps.Style

private const val PRIORITY_STOPS_MIN_ZOOM = 12.5f
private const val TRAM_STOPS_MIN_ZOOM = 14.0f
private const val SECONDARY_STOPS_MIN_ZOOM = 17.0f
private const val SELECTED_STOP_MIN_ZOOM = 9.0f
private const val LIVE_MODE_ZOOM_LEVEL = 12.0f // Zoom level for live tracking mode (below PRIORITY_STOPS_MIN_ZOOM to hide stop icons)

private fun isMetroTramOrFunicular(lineName: String): Boolean {
    val upperName = lineName.uppercase()
    return when {
        upperName in setOf("A", "B", "C", "D") -> true
        upperName in setOf("F1", "F2") -> true
        upperName.startsWith("NAV") -> true
        upperName.startsWith("T") -> true
        upperName == "RX" -> true
        else -> false
    }
}

private fun isTemporaryBus(lineName: String): Boolean {
    return !isMetroTramOrFunicular(lineName)
}

private fun isLiveTrackableLine(lineName: String): Boolean {
    val upperName = lineName.uppercase()
    return when {
        upperName in setOf("A", "B", "C", "D") -> false // metro
        upperName in setOf("F1", "F2") -> false // funicular
        upperName.startsWith("NAV") -> false // Navigone
        upperName == "RX" -> false
        else -> true // bus + tram + trambus
    }
}

private enum class VehicleMarkerType {
    BUS,
    TRAM
}

private fun getVehicleMarkerType(lineName: String): VehicleMarkerType {
    val upperName = lineName.uppercase()
    return when {
        upperName.startsWith("TB") -> VehicleMarkerType.BUS
        upperName.startsWith("T") -> VehicleMarkerType.TRAM
        else -> VehicleMarkerType.BUS
    }
}

private fun ensureVehicleMarkerImage(
    mapStyle: Style,
    context: Context,
    iconName: String,
    color: Int,
    markerType: VehicleMarkerType,
    size: Int
) {
    if (mapStyle.getImage(iconName) != null) return

    val bitmap = createBitmap(size, size)
    val canvas = android.graphics.Canvas(bitmap)

    val circlePaint = Paint().apply {
        this.color = color
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, circlePaint)

    fun drawCenteredDrawable(drawable: Drawable, maxSize: Int) {
        val intrinsicWidth = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else maxSize
        val intrinsicHeight = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else maxSize
        val scale = minOf(maxSize.toFloat() / intrinsicWidth, maxSize.toFloat() / intrinsicHeight)
        val drawWidth = (intrinsicWidth * scale).toInt()
        val drawHeight = (intrinsicHeight * scale).toInt()
        val left = (size - drawWidth) / 2
        val top = (size - drawHeight) / 2
        drawable.setBounds(left, top, left + drawWidth, top + drawHeight)
        drawable.draw(canvas)
    }

    when (markerType) {
        VehicleMarkerType.BUS -> {
            val busDrawable = ContextCompat.getDrawable(context, R.drawable.ic_bus_vehicle)
            busDrawable?.let { drawable ->
                drawCenteredDrawable(drawable, (size * 0.65f).toInt())
            }
        }

        VehicleMarkerType.TRAM -> {
            val tramDrawable = ContextCompat.getDrawable(context, R.drawable.ic_tramway_vehicle)
            tramDrawable?.let { drawable ->
                drawCenteredDrawable(drawable, (size * 0.65f).toInt())
            }
        }
    }

    mapStyle.addImage(iconName, bitmap)
}

/**
 * Returns the mode icon name for a bus line.
 * - Chrono lines (C1, C2, etc.) -> mode_chrono
 * - JD lines (JD...) -> mode_jd
 * - Regular bus -> mode_bus
 * Returns null for lignes fortes (metro, tram, funicular)
 */
private fun getModeIconForLine(lineName: String): String? {
    val upperName = lineName.uppercase()
    return when {
        isMetroTramOrFunicular(lineName) -> null // No mode icon for lignes fortes
        upperName.startsWith("C") && upperName.substring(1).toIntOrNull() != null -> "mode_chrono"
        upperName.startsWith("JD") -> "mode_jd"
        else -> "mode_bus"
    }
}

data class AllSchedulesInfo(
    val lineName: String,
    val directionName: String,
    val schedules: List<String>,
    val availableDirections: List<Int> = emptyList(),
    val headsigns: Map<Int, String> = emptyMap()
)

enum class SheetContentState {
    STATION,
    LINE_DETAILS,
    ALL_SCHEDULES,
    ITINERARY
}

private enum class ItineraryFieldTarget {
    DEPARTURE,
    ARRIVAL
}

/**
 * Data class to hold map filter state for snapshotFlow.
 * Used to batch state changes and avoid excessive recompositions.
 */
private data class MapFilterState(
    val sheetContentState: SheetContentState?,
    val selectedLine: LineInfo?,
    val uiState: TransportLinesUiState,
    val stopsUiState: TransportStopsUiState
)

@Composable
private fun mapStyleLabel(style: MapStyle): String {
    return when (style) {
        MapStyle.POSITRON -> "Clair"
        MapStyle.DARK_MATTER -> "Sombre"
        MapStyle.BRIGHT -> "OSM"
        MapStyle.LIBERTY -> "3D"
        MapStyle.SATELLITE -> "Satellite"
    }
}

@Composable
private fun MapStylePreviewTile(
    style: MapStyle,
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    val imageRes = when (style) {
        MapStyle.POSITRON -> R.drawable.visu_positron
        MapStyle.DARK_MATTER -> R.drawable.visu_dark_matter
        MapStyle.BRIGHT -> R.drawable.visu_osm_bright
        MapStyle.LIBERTY -> R.drawable.visu_liberty
        MapStyle.SATELLITE -> R.drawable.visu_satellite
    }
    val previewBitmap = rememberPreviewImage(imageRes)
    val alpha = if (isEnabled) 1f else 0.4f
    Box(
        modifier = Modifier
            .size(60.dp)
            .border(
                width = 0.5.dp,
                color = Color.Gray,
                shape = RoundedCornerShape(12.dp)
            )
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = isEnabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (previewBitmap != null) {
            Image(
                bitmap = previewBitmap,
                contentDescription = stringResource(R.string.map_style_preview),
                modifier = Modifier.fillMaxSize(),
                alpha = alpha
            )
        } else {
            // Safety fallback: avoid blank tile if bitmap decode ever fails.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFE5E7EB).copy(alpha = alpha))
            )
        }
    }
}

@Composable
private fun rememberPreviewImage(@DrawableRes imageRes: Int): ImageBitmap? {
    val context = LocalContext.current
    val targetSizePx = with(LocalDensity.current) { 60.dp.roundToPx() }
    val imageState by produceState<ImageBitmap?>(
        initialValue = null,
        key1 = context,
        key2 = imageRes,
        key3 = targetSizePx
    ) {
        value = withContext(Dispatchers.IO) {
            decodeSampledBitmapFromResource(context.resources, imageRes, targetSizePx, targetSizePx)
                ?.asImageBitmap()
        }
    }
    return imageState
}

private fun decodeSampledBitmapFromResource(
    resources: Resources,
    @DrawableRes resourceId: Int,
    reqWidth: Int,
    reqHeight: Int
): Bitmap? {
    val bounds = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    BitmapFactory.decodeResource(resources, resourceId, bounds)
    val decodeOptions = BitmapFactory.Options().apply {
        inSampleSize = calculateInSampleSize(bounds, reqWidth, reqHeight)
        inJustDecodeBounds = false
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return BitmapFactory.decodeResource(resources, resourceId, decodeOptions)
}

private fun calculateInSampleSize(
    options: BitmapFactory.Options,
    reqWidth: Int,
    reqHeight: Int
): Int {
    val height = options.outHeight
    val width = options.outWidth
    var inSampleSize = 1

    if (height > reqHeight || width > reqWidth) {
        var halfHeight = height / 2
        var halfWidth = width / 2

        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }

    return inSampleSize.coerceAtLeast(1)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MapStyleSelectionSheet(
    isOffline: Boolean,
    downloadedMapStyles: Set<String>,
    selectedMapStyle: MapStyle,
    onDismiss: () -> Unit,
    onStyleSelected: (MapStyle) -> Unit
) {
    val firstRowStyles = remember { MapStyle.entries.take(4) }
    val secondRowStyles = remember { MapStyle.entries.drop(4) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Fond de carte",
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.size(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                firstRowStyles.forEach { style ->
                    val enabled = !isOffline || style.key in downloadedMapStyles
                    val isSelected = style == selectedMapStyle

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .border(
                                    2.dp,
                                    if (isSelected) Color(0xFF3B82F6) else Color.Transparent,
                                    RoundedCornerShape(14.dp)
                                )
                                .padding(2.dp)
                        ) {
                            MapStylePreviewTile(
                                style = style,
                                isEnabled = enabled,
                                onClick = { onStyleSelected(style) }
                            )
                        }

                        Text(
                            text = mapStyleLabel(style),
                            color = if (enabled) Color.Black else Color(0xFF9CA3AF)
                        )
                    }
                }
            }

            if (secondRowStyles.isNotEmpty()) {
                Spacer(modifier = Modifier.size(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.Start),
                    verticalAlignment = Alignment.Top
                ) {
                    secondRowStyles.forEach { style ->
                        val enabled = !isOffline || style.key in downloadedMapStyles
                        val isSelected = style == selectedMapStyle

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .border(
                                        2.dp,
                                        if (isSelected) Color(0xFF3B82F6) else Color.Transparent,
                                        RoundedCornerShape(14.dp)
                                    )
                                    .padding(2.dp)
                            ) {
                                MapStylePreviewTile(
                                    style = style,
                                    isEnabled = enabled,
                                    onClick = { onStyleSelected(style) }
                                )
                            }

                            Text(
                                text = mapStyleLabel(style),
                                color = if (enabled) Color.Black else Color(0xFF9CA3AF)
                            )
                        }
                    }
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: TransportViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
                extras: CreationExtras
            ): T {
                val application = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    ?: throw IllegalStateException("Application not found in CreationExtras")
                @Suppress("UNCHECKED_CAST")
                return TransportViewModel(application) as T
            }
        }
    ),
    onSheetStateChanged: (Boolean) -> Unit = {},
    showLinesSheet: Boolean = false,
    onLinesSheetDismiss: () -> Unit = {},
    searchSelectedStop: StationSearchResult? = null,
    onSearchSelectionHandled: () -> Unit = {},
    itinerarySelectedStopName: String? = null,
    onItinerarySelectionHandled: () -> Unit = {},
    optionsSelectedStop: StationSearchResult? = null,
    onOptionsSelectionHandled: () -> Unit = {},
    onItineraryClick: (stopName: String) -> Unit = {},
    initialUserLocation: LatLng? = null,
    isVisible: Boolean = true,
    onMapStyleChanged: (MapStyle) -> Unit = {},
    isSearchExpanded: Boolean = false,
    onItineraryModeChanged: (Boolean) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val stopsUiState by viewModel.stopsUiState.collectAsState()
    val favoriteStops by viewModel.favoriteStops.collectAsState()
    val vehiclePositions by viewModel.vehiclePositions.collectAsState()
    val isLiveTrackingEnabled by viewModel.isLiveTrackingEnabled.collectAsState()
    val isOffline by viewModel.isOffline.collectAsState()
    val isGlobalLiveEnabled by viewModel.isGlobalLiveEnabled.collectAsState()
    val globalVehiclePositions by viewModel.globalVehiclePositions.collectAsState()
    val headsigns by viewModel.headsigns.collectAsState()
    val availableDirections by viewModel.availableDirections.collectAsState()
    val allSchedules by viewModel.allSchedules.collectAsState()
    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    // Incremented each time the map style is reloaded, to force LaunchedEffects to re-run
    var mapStyleVersion by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Location state
    var userLocation by remember { mutableStateOf(initialUserLocation) }
    // Center on user immediately if we have initial location, otherwise wait for first location update
    var shouldCenterOnUser by remember { mutableStateOf(initialUserLocation != null) }
    var isCenteredOnUser by remember { mutableStateOf(initialUserLocation != null) }
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    // Handle when initial location becomes available from NavBar after first composition
    LaunchedEffect(initialUserLocation) {
        if (initialUserLocation != null && userLocation == null) {
            userLocation = initialUserLocation
            shouldCenterOnUser = true
            isCenteredOnUser = true
        }
    }

    // Map style from settings — re-read when returning to the Plan tab
    // When offline, use the effective style (fallback to a downloaded style if needed)
    val mapStyleRepository = remember { MapStyleRepository(context) }
    val offlineDataInfo by viewModel.offlineDataInfo.collectAsState()
    var mapStyleUrl by remember { mutableStateOf(mapStyleRepository.getSelectedStyle().styleUrl) }
    var selectedMapStyle by remember {
        mutableStateOf(mapStyleRepository.getEffectiveStyle(isOffline, offlineDataInfo.downloadedMapStyles))
    }
    var isMapStyleMenuExpanded by rememberSaveable { mutableStateOf(false) }
    val isDarkMatterStyle = selectedMapStyle == MapStyle.DARK_MATTER
    LaunchedEffect(isVisible, isOffline, offlineDataInfo.downloadedMapStyles) {
        if (isVisible) {
            val effectiveStyle = mapStyleRepository.getEffectiveStyle(
                isOffline, offlineDataInfo.downloadedMapStyles
            )
            if (effectiveStyle.styleUrl != mapStyleUrl) {
                mapStyleUrl = effectiveStyle.styleUrl
            }
            selectedMapStyle = effectiveStyle
        }
    }
    LaunchedEffect(selectedMapStyle) {
        onMapStyleChanged(selectedMapStyle)
    }

    // Bottom sheet state for BottomSheetScaffold
    val bottomSheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.Hidden,
        skipHiddenState = false
    )
    val scaffoldSheetState = rememberBottomSheetScaffoldState(
        bottomSheetState = bottomSheetState
    )
    var selectedStation by remember { mutableStateOf<StationInfo?>(null) }
    var selectedLine by remember { mutableStateOf<LineInfo?>(null) }
    var requestedSheetValueForNextContent by remember { mutableStateOf<SheetValue?>(null) }
    var itineraryInitialStopName by remember { mutableStateOf<String?>(null) }
    var itineraryDepartureStop by remember { mutableStateOf<SelectedStop?>(null) }
    var itineraryArrivalStop by remember { mutableStateOf<SelectedStop?>(null) }
    var itineraryDepartureQuery by remember { mutableStateOf("") }
    var itineraryArrivalQuery by remember { mutableStateOf("") }
    var itineraryDepartureResults by remember { mutableStateOf<List<StationSearchResult>>(emptyList()) }
    var itineraryArrivalResults by remember { mutableStateOf<List<StationSearchResult>>(emptyList()) }
    var itineraryNearbyDepartureStops by remember { mutableStateOf<List<String>>(emptyList()) }

    var allSchedulesInfo by remember { mutableStateOf<AllSchedulesInfo?>(null) }

    // Preserve selected direction when navigating to/from schedule details
    var selectedDirection by remember { mutableIntStateOf(0) }

    var temporaryLoadedBusLines by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Save zoom level before live tracking to restore it when disabled
    var zoomBeforeLiveTracking by remember { mutableStateOf<Double?>(null) }

    var sheetContentState by remember { mutableStateOf<SheetContentState?>(null) }
    var headerLineCount by remember { mutableIntStateOf(2) }
    val selectedLineNameFromViewModel by viewModel.selectedLineName.collectAsState()

    // Track previous sheetContentState to detect transitions
    var previousSheetContentState by remember { mutableStateOf<SheetContentState?>(null) }
    val isSheetExpandedOrExpanding =
        scaffoldSheetState.bottomSheetState.currentValue == SheetValue.Expanded ||
            scaffoldSheetState.bottomSheetState.targetValue == SheetValue.Expanded

    LaunchedEffect(sheetContentState, selectedStation) {
        onSheetStateChanged(sheetContentState != null)
        onItineraryModeChanged(sheetContentState == SheetContentState.ITINERARY)

        val requestedValue = requestedSheetValueForNextContent
        if (requestedValue != null &&
            sheetContentState != null &&
            sheetContentState != previousSheetContentState) {
            scope.launch {
                when (requestedValue) {
                    SheetValue.Expanded -> scaffoldSheetState.bottomSheetState.expand()
                    SheetValue.PartiallyExpanded -> scaffoldSheetState.bottomSheetState.partialExpand()
                    SheetValue.Hidden -> scaffoldSheetState.bottomSheetState.hide()
                }
            }
            requestedSheetValueForNextContent = null
            previousSheetContentState = sheetContentState
            return@LaunchedEffect
        }

        if (sheetContentState == SheetContentState.STATION &&
            selectedStation != null &&
            previousSheetContentState != SheetContentState.STATION) {
            scope.launch {
                if (previousSheetContentState == SheetContentState.LINE_DETAILS &&
                    isSheetExpandedOrExpanding) {
                    scaffoldSheetState.bottomSheetState.expand()
                } else {
                    scaffoldSheetState.bottomSheetState.partialExpand()
                }
            }
        }
        // Open line details in partially expanded mode by default to preserve
        // visual continuity while still allowing users to fully expand or hide.
        // - from STATION (clicked on a line from station details)
        // - or from null but with a station selected (clicked on a stop with only one line)
        // Don't auto-expand when coming from lines menu (currentStationName is empty)
        if (sheetContentState == SheetContentState.LINE_DETAILS &&
            previousSheetContentState != SheetContentState.LINE_DETAILS &&
            (previousSheetContentState == SheetContentState.STATION ||
                    selectedLine?.currentStationName?.isNotBlank() == true)) {
            scope.launch {
                if (previousSheetContentState == SheetContentState.STATION &&
                    isSheetExpandedOrExpanding) {
                    scaffoldSheetState.bottomSheetState.expand()
                } else {
                    scaffoldSheetState.bottomSheetState.partialExpand()
                }
            }
        }
        // Partial expand (show sheet but collapsed) when clicking directly on a line from the map
        // (coming from null state with no station selected)
        if (sheetContentState == SheetContentState.LINE_DETAILS &&
            previousSheetContentState == null &&
            selectedLine?.currentStationName?.isBlank() == true) {
            scope.launch {
                scaffoldSheetState.bottomSheetState.partialExpand()
            }
        }

        if (sheetContentState == SheetContentState.ITINERARY &&
            previousSheetContentState != SheetContentState.ITINERARY) {
            scope.launch {
                if (isSheetExpandedOrExpanding) {
                    scaffoldSheetState.bottomSheetState.expand()
                } else {
                    scaffoldSheetState.bottomSheetState.partialExpand()
                }
            }
        }

        // Keep transition history in sync for the next state change.
        previousSheetContentState = sheetContentState
    }

    var itinerarySearchTarget by remember { mutableStateOf<ItineraryFieldTarget?>(null) }
    var itinerarySearchFocusNonce by remember { mutableIntStateOf(0) }

    LaunchedEffect(itinerarySearchTarget, itineraryDepartureQuery, itineraryArrivalQuery) {
        val target = itinerarySearchTarget ?: return@LaunchedEffect
        val query = if (target == ItineraryFieldTarget.DEPARTURE) {
            itineraryDepartureQuery
        } else {
            itineraryArrivalQuery
        }

        if (query.length < 2) {
            if (target == ItineraryFieldTarget.DEPARTURE) {
                itineraryDepartureResults = emptyList()
            } else {
                itineraryArrivalResults = emptyList()
            }
            return@LaunchedEffect
        }

        delay(250)
        val results = viewModel.searchStops(query)
        if (target == ItineraryFieldTarget.DEPARTURE) {
            itineraryDepartureResults = results
        } else {
            itineraryArrivalResults = results
        }
    }

    // Initialize itinerary defaults when opening inline itinerary mode:
    // - arrival = selected stop used to launch itinerary
    // - departure = nearest stop to current user location
    LaunchedEffect(sheetContentState, itineraryInitialStopName, userLocation, stopsUiState) {
        if (sheetContentState != SheetContentState.ITINERARY) return@LaunchedEffect

        if (itineraryArrivalStop == null) {
            val arrivalName = itineraryInitialStopName?.takeIf { it.isNotBlank() }
            if (arrivalName != null) {
                val ids = viewModel.raptorRepository.searchStopsByName(arrivalName).map { it.id }
                if (ids.isNotEmpty()) {
                    itineraryArrivalStop = SelectedStop(name = arrivalName, stopIds = ids)
                }
            }
        }

        if (itineraryDepartureStop == null) {
            val location = userLocation
            val stops = (stopsUiState as? TransportStopsUiState.Success)?.stops
            if (location != null && !stops.isNullOrEmpty()) {
                val nearestStops = viewModel.raptorRepository.findNearestStops(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    limit = 5
                )
                val nearestStopNames = nearestStops.map { it.name }.distinct()
                itineraryNearbyDepartureStops = nearestStopNames

                val nearestStopName = nearestStopNames.firstOrNull() ?: findNearestStopName(location, stops)
                if (!nearestStopName.isNullOrBlank()) {
                    val ids = viewModel.raptorRepository.searchStopsByName(nearestStopName).map { it.id }
                    if (ids.isNotEmpty()) {
                        itineraryDepartureStop = SelectedStop(name = nearestStopName, stopIds = ids)
                    }
                }
            }
        }
    }

    // Auto-hide the bottom sheet when content state is null but sheet is still visible
    // This happens when navigating away (e.g. to Settings) and back: the sheet's visual state
    // (rememberSaveable) is restored as Expanded/PartiallyExpanded, but content state (remember)
    // resets to null, leaving an empty expanded sheet.
    LaunchedEffect(sheetContentState, scaffoldSheetState.bottomSheetState.currentValue) {
        if (sheetContentState == null &&
            scaffoldSheetState.bottomSheetState.currentValue != SheetValue.Hidden) {
            scaffoldSheetState.bottomSheetState.hide()
        }
    }

    val latestSheetContentState by rememberUpdatedState(sheetContentState)
    var previousSheetValue by remember { mutableStateOf<SheetValue?>(null) }
    LaunchedEffect(scaffoldSheetState.bottomSheetState.currentValue) {
        val current = scaffoldSheetState.bottomSheetState.currentValue
        val previous = previousSheetValue

        if (current != previous) {
            val justBecameHidden = current == SheetValue.Hidden

            if (justBecameHidden) {
                sheetContentState = null
            }
        }

        previousSheetValue = current
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.any { it.value }
        if (granted) {
            startLocationUpdates(fusedLocationClient) { location ->
                if (userLocation == null) {
                    shouldCenterOnUser = true
                }
                userLocation = location
            }
        }
    }

    LaunchedEffect(Unit) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            startLocationUpdates(fusedLocationClient) { location ->
                if (!shouldCenterOnUser && userLocation == null) {
                    shouldCenterOnUser = true
                }
                userLocation = location
            }
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // Stop location updates when the composable is disposed
    DisposableEffect(Unit) {
        onDispose {
            stopLocationUpdates(fusedLocationClient)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadAllLines()
        viewModel.preloadStops()
    }



    // Track the number of lines currently displayed to avoid unnecessary map updates
    var lastDisplayedLinesCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(uiState, mapInstance, mapStyleVersion) {
        val map = mapInstance ?: return@LaunchedEffect

        // Extract lines from both Success and PartialSuccess states
        val lines: List<Feature> = when (val state = uiState) {
            is TransportLinesUiState.Success -> state.lines
            is TransportLinesUiState.PartialSuccess -> state.lines
            else -> return@LaunchedEffect
        }

        // Skip if no new lines to display
        if (lines.isEmpty()) return@LaunchedEffect

        // Only update map if we have new lines (optimization to avoid redundant updates)
        if (lines.size == lastDisplayedLinesCount) return@LaunchedEffect

        // Prepare GeoJSON in background
        val allLinesGeoJson = withContext(Dispatchers.Default) {
            val featuresMeta = JsonObject().apply {
                addProperty("type", "FeatureCollection")
                val featuresArray = JsonArray()
                lines.forEach { lineFeature ->
                    val featObj = JsonObject()
                    featObj.addProperty("type", "Feature")

                    val geomObj = JsonObject()
                    geomObj.addProperty("type", lineFeature.geometry.type)
                    val coordsArray = JsonArray()
                    lineFeature.geometry.coordinates.forEach { segment ->
                        val segmentArray = JsonArray()
                        segment.forEach { point ->
                            val pointArray = JsonArray()
                            point.forEach { c -> pointArray.add(c) }
                            segmentArray.add(pointArray)
                        }
                        coordsArray.add(segmentArray)
                    }
                    geomObj.add("coordinates", coordsArray)
                    featObj.add("geometry", geomObj)

                    val propsObj = JsonObject()
                    propsObj.addProperty("ligne", lineFeature.properties.ligne)
                    propsObj.addProperty("nom_trace", lineFeature.properties.nomTrace)
                    propsObj.addProperty("couleur", LineColorHelper.getColorForLine(lineFeature))
                    // Determine line width property based on type
                    val upperName = lineFeature.properties.ligne.uppercase()
                    val width = when {
                        lineFeature.properties.familleTransport == "BAT" || upperName.startsWith("NAV") -> 2f
                        lineFeature.properties.familleTransport == "TRA" || lineFeature.properties.familleTransport == "TRAM" || upperName.startsWith("TB") -> 2f
                        else -> 4f
                    }
                    propsObj.addProperty("line_width", width)
                    featObj.add("properties", propsObj)

                    featuresArray.add(featObj)
                }
                add("features", featuresArray)
            }
            featuresMeta.toString()
        }

        // Update Map on Main Thread
        map.getStyle { style ->
            val sourceId = "all-lines-source"
            val layerId = "all-lines-layer"

            // Clean up individual layers if they exist (migration)
            lines.forEach { feature ->
                val oldLayerId = "layer-${feature.properties.ligne}-${feature.properties.codeTrace}"
                val oldSourceId = "line-${feature.properties.ligne}-${feature.properties.codeTrace}"
                style.getLayer(oldLayerId)?.let { style.removeLayer(it) }
                style.getSource(oldSourceId)?.let { style.removeSource(it) }
            }

            // Check if source already exists (for incremental updates)
            val existingSource = style.getSource(sourceId) as? GeoJsonSource
            if (existingSource != null) {
                // Update existing source with new GeoJSON (incremental update)
                existingSource.setGeoJson(allLinesGeoJson)
            } else {
                // First time: create source and layer
                style.getLayer(layerId)?.let { style.removeLayer(it) }
                style.addSource(GeoJsonSource(sourceId, allLinesGeoJson))

                val lineLayer = LineLayer(layerId, sourceId).apply {
                    setProperties(
                        PropertyFactory.lineColor(Expression.get("couleur")),
                        PropertyFactory.lineWidth(Expression.get("line_width")),
                        PropertyFactory.lineOpacity(0.8f),
                        PropertyFactory.lineCap("round"),
                        PropertyFactory.lineJoin("round")
                    )
                }

                // Ensure lines are below stops
                val firstStopLayer = style.layers.find { it.id.startsWith("transport-stops-layer") }
                if (firstStopLayer != null) {
                    style.addLayerBelow(lineLayer, firstStopLayer.id)
                } else {
                    style.addLayer(lineLayer)
                }
            }
        }
    }

    // Handle selection from Search Bar
    LaunchedEffect(searchSelectedStop, stopsUiState, mapInstance) {
        if (searchSelectedStop != null && mapInstance != null && stopsUiState is TransportStopsUiState.Success) {
            val allStops = (stopsUiState as TransportStopsUiState.Success).stops

            val targetStop = allStops.find {
                it.properties.nom.equals(searchSelectedStop.stopName, ignoreCase = true)
            }

            if (targetStop != null) {
                val lines = BusIconHelper.getAllLinesForStop(targetStop)
                val stationInfo = StationInfo(
                    nom = targetStop.properties.nom,
                    lignes = lines,
                    isPmr = targetStop.properties.pmr,
                    desserte = targetStop.properties.desserte
                )

                if (sheetContentState == SheetContentState.LINE_DETAILS || sheetContentState == SheetContentState.ALL_SCHEDULES) {
                    selectedLine?.let { lineInfo ->
                        if (!isMetroTramOrFunicular(lineInfo.lineName)) {
                            viewModel.removeLineFromLoaded(lineInfo.lineName)
                        }
                    }
                    selectedLine = null
                    sheetContentState = null
                    delay(100)
                }

                zoomToStop(mapInstance!!, stationInfo.nom, allStops)

                selectedStation = stationInfo
                sheetContentState = SheetContentState.STATION

                onSearchSelectionHandled()
            }
        }
    }

    // Handle itinerary selection from top search bar to keep continuity in PlanScreen
    LaunchedEffect(itinerarySelectedStopName) {
        val stopName = itinerarySelectedStopName
        if (!stopName.isNullOrBlank()) {
            requestedSheetValueForNextContent = if (isSheetExpandedOrExpanding) {
                SheetValue.Expanded
            } else {
                SheetValue.PartiallyExpanded
            }
            itineraryInitialStopName = stopName
            itineraryArrivalQuery = stopName
            val raptorStops = viewModel.raptorRepository.searchStopsByName(stopName)
            itineraryArrivalStop = SelectedStop(name = stopName, stopIds = raptorStops.map { it.id })
            sheetContentState = SheetContentState.ITINERARY
            onItinerarySelectionHandled()
        }
    }

    // Handle selection from stop options (target) - mimic map click behavior
    LaunchedEffect(optionsSelectedStop, stopsUiState, mapInstance) {
        if (optionsSelectedStop != null && mapInstance != null && stopsUiState is TransportStopsUiState.Success) {
            val allStops = (stopsUiState as TransportStopsUiState.Success).stops

            val targetStop = allStops.find {
                it.properties.nom.equals(optionsSelectedStop.stopName, ignoreCase = true)
            }

            if (targetStop != null) {
                val lines = BusIconHelper.getAllLinesForStop(targetStop)
                val stationInfo = StationInfo(
                    nom = targetStop.properties.nom,
                    lignes = lines,
                    isPmr = targetStop.properties.pmr,
                    desserte = targetStop.properties.desserte
                )

                if (sheetContentState == SheetContentState.LINE_DETAILS || sheetContentState == SheetContentState.ALL_SCHEDULES) {
                    selectedLine?.let { lineInfo ->
                        if (!isMetroTramOrFunicular(lineInfo.lineName)) {
                            viewModel.removeLineFromLoaded(lineInfo.lineName)
                        }
                    }
                    selectedLine = null
                    sheetContentState = null
                    delay(100)
                }

                zoomToStop(mapInstance!!, stationInfo.nom, allStops)

                selectedStation = stationInfo
                sheetContentState = SheetContentState.STATION

                onOptionsSelectionHandled()
            }
        }
    }

    LaunchedEffect(stopsUiState, mapInstance, mapStyleVersion) {
        val map = mapInstance ?: return@LaunchedEffect

        when (val state = stopsUiState) {
            is TransportStopsUiState.Success -> {
                addStopsToMap(map, state.stops, context, onStationClick = { clickedStationInfo ->
                    scope.launch {
                        if (sheetContentState == SheetContentState.LINE_DETAILS || sheetContentState == SheetContentState.ALL_SCHEDULES) {
                            selectedLine?.let { lineInfo ->
                                val lineName = lineInfo.lineName
                                if (!isMetroTramOrFunicular(lineName)) {
                                    viewModel.removeLineFromLoaded(lineName)
                                }
                            }

                            selectedLine = null
                            sheetContentState = null

                            scaffoldSheetState.bottomSheetState.partialExpand()

                            delay(300)
                        }

                        selectedStation = clickedStationInfo
                        sheetContentState = SheetContentState.STATION
                    }
                }, onLineClick = { lineName ->
                    scope.launch {
                        // Cancel pending operations and clear states from previous line to prevent OOM
                        viewModel.resetLineDetailState()

                        // Close any existing sheet content
                        if (sheetContentState == SheetContentState.LINE_DETAILS || sheetContentState == SheetContentState.ALL_SCHEDULES) {
                            selectedLine?.let { lineInfo ->
                                val currentLineName = lineInfo.lineName
                                if (!isMetroTramOrFunicular(currentLineName)) {
                                    viewModel.removeLineFromLoaded(currentLineName)
                                }
                            }
                        }

                        selectedLine = LineInfo(
                            lineName = lineName,
                            currentStationName = ""
                        )

                        if (!isMetroTramOrFunicular(lineName)) {
                            viewModel.addLineToLoaded(lineName)
                            if (isTemporaryBus(lineName)) {
                                temporaryLoadedBusLines = temporaryLoadedBusLines + lineName
                            }
                            delay(100)
                        }

                        sheetContentState = SheetContentState.LINE_DETAILS
                    }
                }, scope = scope, viewModel = viewModel)
            }
            else -> {}
        }
    }

    LaunchedEffect(sheetContentState, selectedLine) {
        if (sheetContentState == SheetContentState.LINE_DETAILS && selectedLine != null) {
            val lineName = selectedLine!!.lineName

            if (!isMetroTramOrFunicular(lineName)) {
                viewModel.addLineToLoaded(lineName)
                if (isTemporaryBus(lineName)) {
                    temporaryLoadedBusLines = temporaryLoadedBusLines + lineName
                }
            }
        }
    }

    // Reset direction when line or stop changes (not when navigating to/from schedule details)
    LaunchedEffect(selectedLine?.lineName, selectedLine?.currentStationName) {
        selectedDirection = 0
    }

    LaunchedEffect(sheetContentState) {
        if (sheetContentState != SheetContentState.LINE_DETAILS && sheetContentState != SheetContentState.ALL_SCHEDULES && temporaryLoadedBusLines.isNotEmpty()) {
            temporaryLoadedBusLines.forEach { busLine ->
                viewModel.removeLineFromLoaded(busLine)
            }
            temporaryLoadedBusLines = emptySet()
        }
    }

    // Keep LIVE mode active while switching between global and per-line context.
    LaunchedEffect(selectedLine?.lineName, sheetContentState, isLiveTrackingEnabled, isGlobalLiveEnabled, isOffline) {
        if (isOffline) return@LaunchedEffect

        val isLiveModeEnabled = isLiveTrackingEnabled || isGlobalLiveEnabled
        if (!isLiveModeEnabled) return@LaunchedEffect

        val isLineContext = sheetContentState == SheetContentState.LINE_DETAILS || sheetContentState == SheetContentState.ALL_SCHEDULES
        val selectedTrackableLine = selectedLine?.lineName?.takeIf { isLineContext && isLiveTrackableLine(it) }
        val selectedNotTrackableLine = selectedLine?.lineName?.takeIf { isLineContext && !isLiveTrackableLine(it) }

        if (selectedTrackableLine != null) {
            if (isGlobalLiveEnabled) {
                viewModel.startLiveTracking(selectedTrackableLine)
            }
        } else if (selectedNotTrackableLine != null) {
            if (isLiveTrackingEnabled) {
                viewModel.stopLiveTracking()
            }
            if (isGlobalLiveEnabled) {
                viewModel.stopGlobalLive()
            }
        } else {
            if (isLiveTrackingEnabled) {
                viewModel.stopLiveTracking()
            }
            if (!isGlobalLiveEnabled) {
                viewModel.toggleGlobalLive()
            }
        }
    }

    // Auto-zoom out when live tracking is enabled, restore zoom when disabled
    LaunchedEffect(isLiveTrackingEnabled) {
        val map = mapInstance ?: return@LaunchedEffect
        if (isLiveTrackingEnabled) {
            val currentZoom = map.cameraPosition.zoom
            // Save current zoom level before zooming out
            // Only zoom out if current zoom is higher than LIVE_MODE_ZOOM_LEVEL
            if (currentZoom > LIVE_MODE_ZOOM_LEVEL) {
                map.animateCamera(
                    CameraUpdateFactory.zoomTo(LIVE_MODE_ZOOM_LEVEL.toDouble()),
                    500 // Animation duration in ms
                )
            }
        } else {
            // Restore previous zoom level when live tracking is disabled
            zoomBeforeLiveTracking?.let { savedZoom ->
                map.animateCamera(
                    CameraUpdateFactory.zoomTo(savedZoom),
                    500 // Animation duration in ms
                )
            }
        }
    }

    // Update vehicle markers on the map when vehicle positions change
    LaunchedEffect(vehiclePositions, mapInstance, selectedLine, mapStyleVersion, isMapStyleMenuExpanded) {
        if (isMapStyleMenuExpanded) return@LaunchedEffect
        val map = mapInstance ?: return@LaunchedEffect
        val positions = vehiclePositions
        val line = selectedLine

        // Ajouter un délai pour éviter les mises à jour trop fréquentes
        delay(100)

        map.getStyle { style ->
            // Remove existing vehicle layers and sources
            style.getLayer("vehicle-positions-layer")?.let { style.removeLayer(it) }
            style.getSource("vehicle-positions-source")?.let { style.removeSource(it) }

            if (positions.isEmpty() || line == null) return@getStyle

            // Create GeoJSON for vehicle positions
            val vehiclesGeoJson = JsonObject().apply {
                addProperty("type", "FeatureCollection")
                val featuresArray = JsonArray()
                positions.forEach { vehicle ->
                    val feature = JsonObject().apply {
                        addProperty("type", "Feature")
                        val geometry = JsonObject().apply {
                            addProperty("type", "Point")
                            val coords = JsonArray()
                            coords.add(vehicle.longitude)
                            coords.add(vehicle.latitude)
                            add("coordinates", coords)
                        }
                        add("geometry", geometry)
                        val props = JsonObject().apply {
                            addProperty("vehicleId", vehicle.vehicleId)
                            addProperty("lineName", vehicle.lineName)
                            addProperty("destination", vehicle.destinationName ?: "")
                        }
                        add("properties", props)
                    }
                    featuresArray.add(feature)
                }
                add("features", featuresArray)
            }.toString()

            // Add source
            val source = GeoJsonSource("vehicle-positions-source", vehiclesGeoJson)
            style.addSource(source)

            val markerColor = LineColorHelper.getColorForLineString(line.lineName)
            val markerType = getVehicleMarkerType(line.lineName)
            val iconName = "vehicle-marker-line-${markerType.name.lowercase()}-${Integer.toHexString(markerColor)}"
            ensureVehicleMarkerImage(
                mapStyle = style,
                context = context,
                iconName = iconName,
                color = markerColor,
                markerType = markerType,
                size = 72
            )

            // Add symbol layer with bus marker
            val symbolLayer = SymbolLayer("vehicle-positions-layer", "vehicle-positions-source").apply {
                setProperties(
                    PropertyFactory.iconImage(iconName),
                    PropertyFactory.iconSize(1.0f),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconIgnorePlacement(true)
                )
            }
            style.addLayer(symbolLayer)
        }
    }

    // Global live map: render ALL vehicles with per-line colored markers
    LaunchedEffect(globalVehiclePositions, mapInstance, mapStyleVersion, isMapStyleMenuExpanded) {
        if (isMapStyleMenuExpanded) return@LaunchedEffect
        val map = mapInstance ?: return@LaunchedEffect
        val positions = globalVehiclePositions

        // Ajouter un délai pour éviter les mises à jour trop fréquentes
        delay(100)

        map.getStyle { style ->
            // Clean up existing global layers/sources
            style.getLayer("global-vehicle-positions-layer")?.let { style.removeLayer(it) }
            style.getSource("global-vehicle-positions-source")?.let { style.removeSource(it) }

            if (positions.isEmpty()) return@getStyle

            // Generate colored/type-specific marker icons per unique (type,color)
            val iconCache = mutableMapOf<String, String>()

            // Build GeoJSON with per-vehicle icon property
            val vehiclesGeoJson = JsonObject().apply {
                addProperty("type", "FeatureCollection")
                val featuresArray = JsonArray()
                positions.forEach { vehicle ->
                    val lineColor = LineColorHelper.getColorForLineString(vehicle.lineName)
                    val markerType = getVehicleMarkerType(vehicle.lineName)
                    val cacheKey = "${markerType.name}-${lineColor}"
                    val iconName = iconCache.getOrPut(cacheKey) {
                        val name = "global-vehicle-marker-${markerType.name.lowercase()}-${Integer.toHexString(lineColor)}"
                        ensureVehicleMarkerImage(
                            mapStyle = style,
                            context = context,
                            iconName = name,
                            color = lineColor,
                            markerType = markerType,
                            size = 56
                        )
                        name
                    }

                    val feature = JsonObject().apply {
                        addProperty("type", "Feature")
                        val geometry = JsonObject().apply {
                            addProperty("type", "Point")
                            val coords = JsonArray()
                            coords.add(vehicle.longitude)
                            coords.add(vehicle.latitude)
                            add("coordinates", coords)
                        }
                        add("geometry", geometry)
                        val props = JsonObject().apply {
                            addProperty("vehicleId", vehicle.vehicleId)
                            addProperty("lineName", vehicle.lineName)
                            addProperty("destination", vehicle.destinationName ?: "")
                            addProperty("icon", iconName)
                        }
                        add("properties", props)
                    }
                    featuresArray.add(feature)
                }
                add("features", featuresArray)
            }.toString()

            val source = GeoJsonSource("global-vehicle-positions-source", vehiclesGeoJson)
            style.addSource(source)

            val symbolLayer = SymbolLayer("global-vehicle-positions-layer", "global-vehicle-positions-source").apply {
                setProperties(
                    PropertyFactory.iconImage(Expression.get("icon")),
                    PropertyFactory.iconSize(0.85f),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconIgnorePlacement(true)
                )
            }
            style.addLayer(symbolLayer)
        }
    }

    // Auto-zoom when global live is toggled
    var zoomBeforeGlobalLive by remember { mutableStateOf<Double?>(null) }
    LaunchedEffect(isGlobalLiveEnabled) {
        val map = mapInstance ?: return@LaunchedEffect
        if (isGlobalLiveEnabled) {
            val currentZoom = map.cameraPosition.zoom
            if (currentZoom > 11.0) {
                map.animateCamera(CameraUpdateFactory.zoomTo(11.0), 500)
            }
        } else {
            zoomBeforeGlobalLive?.let { savedZoom ->
                map.animateCamera(CameraUpdateFactory.zoomTo(savedZoom), 500)
            }
        }
    }

    LaunchedEffect(showLinesSheet, sheetContentState) {
        if (!showLinesSheet && sheetContentState != SheetContentState.LINE_DETAILS && sheetContentState != SheetContentState.ALL_SCHEDULES && temporaryLoadedBusLines.isNotEmpty()) {
            temporaryLoadedBusLines.forEach { busLine ->
                viewModel.removeLineFromLoaded(busLine)
            }
            temporaryLoadedBusLines = emptySet()
        }
    }

    // Use snapshotFlow with debounce to avoid overwhelming the map when user changes stations rapidly.
    // collectLatest automatically cancels previous collection when new values arrive.
    @OptIn(FlowPreview::class)
    LaunchedEffect(mapInstance, mapStyleVersion, isMapStyleMenuExpanded) {
        if (isMapStyleMenuExpanded) return@LaunchedEffect
        val map = mapInstance ?: return@LaunchedEffect

        snapshotFlow {
            // Capture all relevant state as a tuple
            MapFilterState(
                sheetContentState = sheetContentState,
                selectedLine = selectedLine,
                uiState = uiState,
                stopsUiState = stopsUiState
            )
        }
            .debounce(500) // Augmenter à 500ms pour moins de réactivité mais plus de stabilité
            .distinctUntilChanged() // Skip redundant emissions
            .collectLatest { filterState ->
                // Ajouter un petit délai avant de traiter
                delay(50)
                // This block is automatically cancelled if a new state arrives
                // Extract lines from both Success and PartialSuccess states
                val lines: List<Feature> = when (val state = filterState.uiState) {
                    is TransportLinesUiState.Success -> state.lines
                    is TransportLinesUiState.PartialSuccess -> state.lines
                    else -> return@collectLatest
                }

                val currentSelectedLine = filterState.selectedLine
                val currentSheetState = filterState.sheetContentState

                if ((currentSheetState == SheetContentState.LINE_DETAILS || currentSheetState == SheetContentState.ALL_SCHEDULES) && currentSelectedLine != null) {
                    val selectedName = currentSelectedLine.lineName
                    val hasSelectedInState = lines.any { it.properties.ligne.equals(selectedName, ignoreCase = true) }

                    if (!hasSelectedInState && isMetroTramOrFunicular(selectedName)) {
                        viewModel.reloadStrongLines()
                    }

                    filterMapLines(map, lines, currentSelectedLine.lineName)

                    val selectedStopName = currentSelectedLine.currentStationName.takeIf { it.isNotBlank() }
                    when (val stopsState = filterState.stopsUiState) {
                        is TransportStopsUiState.Success -> {
                            filterMapStopsWithSelectedStop(
                                map,
                                currentSelectedLine.lineName,
                                selectedStopName,
                                stopsState.stops,
                                lines,
                                viewModel
                            )

                            if (selectedStopName != null) {
                                zoomToStop(map, selectedStopName, stopsState.stops)
                            } else {
                                zoomToLine(map, lines, currentSelectedLine.lineName)
                            }
                        }
                        else -> {}
                    }
                } else {
                    showAllMapLines(map, lines)
                }
            }
    }

    // Observe selection from viewModel (e.g. when Lines screen clicks a line)
    LaunchedEffect(selectedLineNameFromViewModel) {
        val name = selectedLineNameFromViewModel
        if (!name.isNullOrEmpty()) {
            selectedLine = LineInfo(
                lineName = name,
                currentStationName = ""
            )

            // if not a strong line, add it to loaded lines
            if (!isMetroTramOrFunicular(name)) {
                viewModel.addLineToLoaded(name)
                if (isTemporaryBus(name)) {
                    temporaryLoadedBusLines = temporaryLoadedBusLines + name
                }
                delay(100)
            }

            sheetContentState = SheetContentState.LINE_DETAILS
            viewModel.clearSelectedLine()
        }
    }

    val bottomPadding = contentPadding.calculateBottomPadding()
    val configuration = LocalConfiguration.current
    val itinerarySearchOverlayHeight = 174.dp
    val itinerarySheetSafetyOffset = 90.dp
    val itinerarySheetMaxHeight =
        (configuration.screenHeightDp.dp - itinerarySearchOverlayHeight - bottomPadding - itinerarySheetSafetyOffset)
            .coerceAtLeast(280.dp)

    // Handle back button press - close sheets/selections before exiting app
    BackHandler(enabled = sheetContentState != null || selectedLine != null || selectedStation != null) {
        when (sheetContentState) {
            SheetContentState.ALL_SCHEDULES -> {
                requestedSheetValueForNextContent = if (isSheetExpandedOrExpanding) {
                    SheetValue.Expanded
                } else {
                    SheetValue.PartiallyExpanded
                }
                allSchedulesInfo = null
                sheetContentState = SheetContentState.LINE_DETAILS
            }
            SheetContentState.ITINERARY -> {
                sheetContentState = null
                itineraryInitialStopName = null
                itineraryDepartureStop = null
                itineraryArrivalStop = null
                itineraryDepartureQuery = ""
                itineraryArrivalQuery = ""
                itineraryDepartureResults = emptyList()
                itineraryArrivalResults = emptyList()
            }
            // If viewing line details, go back to station (if came from station) or close
            SheetContentState.LINE_DETAILS -> {
                // Clean up temporary bus lines
                selectedLine?.let { lineInfo ->
                    val lineName = lineInfo.lineName
                    if (!isMetroTramOrFunicular(lineName)) {
                        viewModel.removeLineFromLoaded(lineName)
                    }
                }
                if (selectedStation != null) {
                    // Go back to station view when line details were opened from a stop
                    selectedLine = null
                    sheetContentState = SheetContentState.STATION
                } else {
                    // Close everything
                    selectedLine = null
                    selectedStation = null
                    sheetContentState = null
                }
            }
            // If viewing station, close it
            SheetContentState.STATION -> {
                selectedStation = null
                sheetContentState = null
            }
            // Default: close any selection
            else -> {
                selectedLine = null
                selectedStation = null
                sheetContentState = null
            }
        }
    }

    val extraHeaderPeek = if (sheetContentState == SheetContentState.LINE_DETAILS && headerLineCount > 2) {
        ((headerLineCount - 2).coerceAtLeast(0) * 20).dp
    } else {
        0.dp
    }
    val stationCollapsedPeekHeight = bottomPadding + 300.dp
    val peekHeight = when(sheetContentState) {
        SheetContentState.LINE_DETAILS -> stationCollapsedPeekHeight
        SheetContentState.ALL_SCHEDULES -> stationCollapsedPeekHeight
        SheetContentState.STATION -> stationCollapsedPeekHeight
        SheetContentState.ITINERARY -> stationCollapsedPeekHeight
        else -> 0.dp
    }
    val unifiedSheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

    BottomSheetScaffold(
        scaffoldState = scaffoldSheetState,
        sheetPeekHeight = peekHeight,
        sheetShape = unifiedSheetShape,
        modifier = modifier,
        sheetContainerColor = Color.White,
        sheetContent = {
            Column(
                modifier = Modifier
                    .padding(bottom = bottomPadding)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when(sheetContentState) {
                    SheetContentState.LINE_DETAILS -> {
                        if (selectedLine != null) {
                            LineDetailsSheetContent(
                                lineInfo = selectedLine!!,
                                viewModel = viewModel,
                                selectedDirection = selectedDirection,
                                onDirectionChange = { newDirection -> selectedDirection = newDirection },
                                onBackToStation = {
                                    selectedLine?.let { lineInfo ->
                                        val lineName = lineInfo.lineName
                                        if (!isMetroTramOrFunicular(lineName)) {
                                            viewModel.removeLineFromLoaded(lineName)
                                        }
                                    }

                                    if (selectedStation != null) {
                                        requestedSheetValueForNextContent = if (isSheetExpandedOrExpanding) {
                                            SheetValue.Expanded
                                        } else {
                                            SheetValue.PartiallyExpanded
                                        }
                                        selectedLine = null
                                        sheetContentState = SheetContentState.STATION
                                    } else {
                                        scope.launch {
                                            scaffoldSheetState.bottomSheetState.hide()
                                        }
                                        selectedLine = null
                                        selectedStation = null
                                        sheetContentState = null
                                    }
                                },
                                onLineClick = { lineName ->
                                    // Cancel pending operations and clear states from previous line to prevent OOM
                                    viewModel.resetLineDetailState()

                                    selectedLine = LineInfo(
                                        lineName = lineName,
                                        currentStationName = selectedLine?.currentStationName ?: ""
                                    )

                                    if (!isMetroTramOrFunicular(lineName)) {
                                        scope.launch {
                                            viewModel.addLineToLoaded(lineName)
                                            if (isTemporaryBus(lineName)) {
                                                temporaryLoadedBusLines = temporaryLoadedBusLines + lineName
                                            }
                                            delay(100)
                                            sheetContentState = SheetContentState.LINE_DETAILS
                                        }
                                    } else {
                                        sheetContentState = SheetContentState.LINE_DETAILS
                                    }
                                },
                                onStopClick = { stopName ->
                                    // Clear schedule state to prevent stale "Aucun horaire" message
                                    viewModel.clearScheduleState()

                                    selectedLine = LineInfo(
                                        lineName = selectedLine!!.lineName,
                                        currentStationName = stopName
                                    )
                                    scope.launch {
                                        scaffoldSheetState.bottomSheetState.partialExpand()
                                    }
                                },
                                onShowAllSchedules = { lineName, directionName, schedules ->
                                    requestedSheetValueForNextContent = if (isSheetExpandedOrExpanding) {
                                        SheetValue.Expanded
                                    } else {
                                        SheetValue.PartiallyExpanded
                                    }
                                    allSchedulesInfo = AllSchedulesInfo(
                                        lineName = lineName,
                                        directionName = directionName,
                                        schedules = schedules,
                                        availableDirections = availableDirections,
                                        headsigns = headsigns
                                    )
                                    sheetContentState = SheetContentState.ALL_SCHEDULES
                                },
                                onItineraryClick = { stopName ->
                                    requestedSheetValueForNextContent = if (isSheetExpandedOrExpanding) {
                                        SheetValue.Expanded
                                    } else {
                                        SheetValue.PartiallyExpanded
                                    }
                                    itineraryInitialStopName = stopName
                                    sheetContentState = SheetContentState.ITINERARY
                                },
                                onHeaderClick = {
                                    scope.launch {
                                        scaffoldSheetState.bottomSheetState.expand()
                                    }
                                },
                                favoriteStops = favoriteStops,
                                onToggleFavoriteStop = { viewModel.toggleFavoriteStop(it) },
                                onHeaderLineCountChanged = { count -> }
                            )
                        }
                    }
                    SheetContentState.STATION -> {
                        if (selectedStation != null) {
                            StationSheetContent(
                                stationInfo = selectedStation!!,
                                viewModel = viewModel,
                                onDismiss = {
                                    scope.launch {
                                        scaffoldSheetState.bottomSheetState.hide()
                                    }
                                    sheetContentState = null
                                },
                                onDepartureClick = { lineName, directionId, _ ->
                                    // Cancel pending operations and clear states from previous line to prevent OOM
                                    viewModel.resetLineDetailState()
                                    val shouldKeepExpanded =
                                        scaffoldSheetState.bottomSheetState.currentValue == SheetValue.Expanded ||
                                            scaffoldSheetState.bottomSheetState.targetValue == SheetValue.Expanded
                                    requestedSheetValueForNextContent = if (shouldKeepExpanded) {
                                        SheetValue.Expanded
                                    } else {
                                        SheetValue.PartiallyExpanded
                                    }

                                    selectedDirection = directionId

                                    selectedLine = LineInfo(
                                        lineName = lineName,
                                        currentStationName = selectedStation?.nom ?: ""
                                    )

                                    if (!isMetroTramOrFunicular(lineName)) {
                                        scope.launch {
                                            viewModel.addLineToLoaded(lineName)
                                            if (isTemporaryBus(lineName)) {
                                                temporaryLoadedBusLines = temporaryLoadedBusLines + lineName
                                            }
                                            delay(100)
                                            sheetContentState = SheetContentState.LINE_DETAILS
                                        }
                                    } else {
                                        sheetContentState = SheetContentState.LINE_DETAILS
                                    }
                                },
                                isFavoriteStop = favoriteStops.any { it.equals(selectedStation!!.nom, ignoreCase = true) },
                                onToggleFavoriteStop = { viewModel.toggleFavoriteStop(selectedStation!!.nom) },
                                onItineraryClick = { stopName ->
                                    requestedSheetValueForNextContent = if (isSheetExpandedOrExpanding) {
                                        SheetValue.Expanded
                                    } else {
                                        SheetValue.PartiallyExpanded
                                    }
                                    itineraryInitialStopName = stopName
                                    sheetContentState = SheetContentState.ITINERARY
                                }
                            )
                        }
                    }
                    SheetContentState.ALL_SCHEDULES -> {
                        if (allSchedulesInfo != null) {
                            val schedulesForCurrentDirection =
                                if (allSchedules.isNotEmpty()) allSchedules else allSchedulesInfo!!.schedules
                            val resolvedAllSchedulesInfo = allSchedulesInfo!!.copy(
                                directionName = headsigns[selectedDirection] ?: allSchedulesInfo!!.directionName,
                                schedules = schedulesForCurrentDirection
                            )
                            val allSchedulesDirections = if (allSchedulesInfo!!.availableDirections.isNotEmpty()) {
                                allSchedulesInfo!!.availableDirections
                            } else {
                                availableDirections
                            }
                            val allSchedulesHeadsigns = if (allSchedulesInfo!!.headsigns.isNotEmpty()) {
                                allSchedulesInfo!!.headsigns
                            } else {
                                headsigns
                            }
                            AllSchedulesSheetContent(
                                allSchedulesInfo = resolvedAllSchedulesInfo,
                                lineInfo = selectedLine!!,
                                selectedDirection = selectedDirection,
                                availableDirections = allSchedulesDirections,
                                headsigns = allSchedulesHeadsigns,
                                onDirectionChange = { newDirection ->
                                    selectedDirection = newDirection
                                    selectedLine?.currentStationName?.takeIf { it.isNotBlank() }?.let { stopName ->
                                        scope.launch {
                                            viewModel.loadSchedulesForDirection(
                                                lineName = selectedLine!!.lineName,
                                                stopName = stopName,
                                                directionId = newDirection
                                            )
                                        }
                                    }
                                },
                                onBack = {
                                    requestedSheetValueForNextContent = if (isSheetExpandedOrExpanding) {
                                        SheetValue.Expanded
                                    } else {
                                        SheetValue.PartiallyExpanded
                                    }
                                    sheetContentState = SheetContentState.LINE_DETAILS
                                }
                            )
                        }
                    }
                    SheetContentState.ITINERARY -> {
                        InlineItinerarySheetContent(
                            viewModel = viewModel,
                            departureStop = itineraryDepartureStop,
                            arrivalStop = itineraryArrivalStop,
                            maxHeight = itinerarySheetMaxHeight,
                            nearbyDepartureStops = itineraryNearbyDepartureStops,
                            onDepartureFallbackSelected = { fallbackDeparture ->
                                itineraryDepartureStop = fallbackDeparture
                            },
                            onClose = {
                                scope.launch {
                                    scaffoldSheetState.bottomSheetState.hide()
                                }
                                itineraryInitialStopName = null
                                sheetContentState = null
                            }
                        )
                    }
                    null -> {}
                }
            }
        }
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            MapLibreView(
                modifier = Modifier.fillMaxSize(),
                initialPosition = LatLng(45.75, 4.85),
                initialZoom = 12.0,
                styleUrl = mapStyleUrl,
                onMapReady = { map ->
                    if (mapInstance === map) {
                        // Same map instance → style was reloaded, bump version to re-trigger LaunchedEffects
                        mapStyleVersion++
                    } else {
                        mapInstance = map
                        // Add listener to detect when user moves the map
                        map.addOnCameraMoveStartedListener { reason ->
                            if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                                isCenteredOnUser = false
                            }
                        }
                    }
                },
                userLocation = userLocation,
                centerOnUserLocation = shouldCenterOnUser
            )

            if (uiState is TransportLinesUiState.Loading || stopsUiState is TransportStopsUiState.Loading) {
                // Show skeleton loading instead of spinner for better UX
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp)
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFF3B82F6)
                    )
                }
            }

            // Recenter button
            AnimatedVisibility(
                visible = userLocation != null && !isCenteredOnUser,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 120.dp, end = 16.dp)
            ) {
                FloatingActionButton(
                    onClick = {
                        userLocation?.let { location ->
                            mapInstance?.animateCamera(
                                CameraUpdateFactory.newLatLngZoom(location, 17.0),
                                1000
                            )
                            isCenteredOnUser = true
                        }
                    },
                    modifier = Modifier
                        .size(56.dp)
                        .then(
                            if (isDarkMatterStyle && !isSearchExpanded) {
                                Modifier
                                    .clip(CircleShape)
                                    .border(1.dp, Color.Gray, CircleShape)
                            } else {
                                Modifier
                            }
                        ),
                    containerColor = Color.Black,
                    shape = CircleShape,
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 0.dp
                    )
                ) {
                    Canvas(
                        modifier = Modifier.size(24.dp)
                    ) {
                        drawCircle(
                            color = Color(0xFF3B82F6),
                            radius = size.minDimension / 2.5f
                        )
                        drawCircle(
                            color = Color.White,
                            radius = size.minDimension / 2.5f,
                            style = Stroke(width = 7f)
                        )
                    }
                }
            }

            // Unified LIVE button (global when no selected bus line, line-specific otherwise)
            val isLineContext = sheetContentState == SheetContentState.LINE_DETAILS || sheetContentState == SheetContentState.ALL_SCHEDULES
            // When a sheet is open, place controls where favorites row usually sits.
            val controlsTopPadding = if (sheetContentState != null) 100.dp else 146.dp
            val selectedTrackableLineName = selectedLine?.lineName?.takeIf { isLineContext && isLiveTrackableLine(it) }
            val hasSelectedNotTrackableLine = selectedLine?.lineName?.let { isLineContext && !isLiveTrackableLine(it) } == true
            val showLiveButton = !isOffline && !hasSelectedNotTrackableLine

            if (sheetContentState != SheetContentState.ITINERARY) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(
                            top = controlsTopPadding,
                            end = 12.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                Button(
                    onClick = { isMapStyleMenuExpanded = true },
                    border = if (isDarkMatterStyle && !isSearchExpanded) BorderStroke(1.dp, Color.Gray) else null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Black
                    ),
                    shape = CircleShape,
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 4.dp
                    ),
                    contentPadding = PaddingValues(
                        top = 6.dp,
                        bottom = 6.dp
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Layers,
                        contentDescription = "Layers",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                AnimatedVisibility(
                    visible = showLiveButton,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    val isLiveModeEnabled = isLiveTrackingEnabled || isGlobalLiveEnabled
                    val hasVehicles = when {
                        isLiveTrackingEnabled -> vehiclePositions.isNotEmpty()
                        isGlobalLiveEnabled -> globalVehiclePositions.isNotEmpty()
                        else -> false
                    }
                    val isActiveNoVehicles = isLiveModeEnabled && !hasVehicles

                    // Animation for the bouncing dot (goes up and down)
                    val infiniteTransition = rememberInfiniteTransition(label = "live_dot")
                    val dotOffset by infiniteTransition.animateFloat(
                        initialValue = if (hasVehicles) -2f else 0f,
                        targetValue = if (hasVehicles) 2f else 0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(400),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "dot_bounce"
                    )

                    val buttonColor = when {
                        hasVehicles -> Color(0xFFEF4444) // Red when active with vehicles
                        isActiveNoVehicles -> Color(0xFF9CA3AF) // Gray when active but no vehicles
                        else -> Color.Black // Black when inactive
                    }
                    val showLiveBorder = isDarkMatterStyle && buttonColor == Color.Black && !isSearchExpanded
                    Button(
                        onClick = {
                            if (isLiveModeEnabled) {
                                if (isLiveTrackingEnabled) {
                                    viewModel.stopLiveTracking()
                                }
                                if (isGlobalLiveEnabled) {
                                    viewModel.stopGlobalLive()
                                }
                            } else {
                                selectedTrackableLineName?.let { lineName ->
                                    viewModel.startLiveTracking(lineName)
                                } ?: viewModel.toggleGlobalLive()
                            }
                        },
                        border = if (showLiveBorder) BorderStroke(1.dp, Color.Gray) else null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = buttonColor
                        ),
                        shape = RoundedCornerShape(20.dp),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 4.dp
                        ),
                        contentPadding = PaddingValues(
                            start = 15.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = 8.dp
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Always show dot, animate when active with vehicles
                            Canvas(
                                modifier = Modifier
                                    .size(8.dp)
                                    .graphicsLayer { translationY = dotOffset }
                            ) {
                                drawCircle(color = Color.White)
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "LIVE",
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
                }
            }

            if (sheetContentState == SheetContentState.ITINERARY) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(top = 8.dp, start = 10.dp, end = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    ItinerarySearchBarField(
                        selectedStop = itineraryDepartureStop,
                        onClick = {
                            itinerarySearchTarget = ItineraryFieldTarget.DEPARTURE
                            itineraryDepartureQuery = itineraryDepartureQuery.ifBlank {
                                itineraryDepartureStop?.name ?: ""
                            }
                            itineraryDepartureResults = emptyList()
                            itinerarySearchFocusNonce++
                        },
                        icon = Icons.Default.MyLocation,
                        placeholder = "Arret de depart"
                    )

                    Row(
                        modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapVert,
                            contentDescription = "Inverser",
                            tint = Color.Black,
                            modifier = Modifier
                                .size(26.dp)
                                .clickable {
                                    val previousDeparture = itineraryDepartureStop
                                    itineraryDepartureStop = itineraryArrivalStop
                                    itineraryArrivalStop = previousDeparture
                                }
                        )
                    }

                    ItinerarySearchBarField(
                        modifier = Modifier.offset(y = (-30).dp),
                        selectedStop = itineraryArrivalStop,
                        onClick = {
                            itinerarySearchTarget = ItineraryFieldTarget.ARRIVAL
                            itineraryArrivalQuery = itineraryArrivalQuery.ifBlank {
                                itineraryArrivalStop?.name ?: ""
                            }
                            itineraryArrivalResults = emptyList()
                            itinerarySearchFocusNonce++
                        },
                        icon = Icons.Default.Search,
                        placeholder = "Arret d'arrivee"
                    )
                }
            }

        }
    }

    if (sheetContentState == SheetContentState.ITINERARY && itinerarySearchTarget != null) {
        val isDepartureSearch = itinerarySearchTarget == ItineraryFieldTarget.DEPARTURE
        val query = if (isDepartureSearch) itineraryDepartureQuery else itineraryArrivalQuery
        val searchResults = if (isDepartureSearch) itineraryDepartureResults else itineraryArrivalResults

        ItineraryFullscreenSearchOverlay(
            query = query,
            searchResults = searchResults,
            placeholder = if (isDepartureSearch) "Rechercher un depart" else "Rechercher une arrivee",
            autofocusNonce = itinerarySearchFocusNonce,
            onQueryChange = { newValue ->
                if (isDepartureSearch) {
                    itineraryDepartureQuery = newValue
                } else {
                    itineraryArrivalQuery = newValue
                }
            },
            onDismiss = { itinerarySearchTarget = null },
            onResultSelected = { result ->
                scope.launch {
                    val raptorStops = viewModel.raptorRepository.searchStopsByName(result.stopName)
                    val selectedStop = SelectedStop(
                        name = result.stopName,
                        stopIds = raptorStops.map { it.id }
                    )
                    if (isDepartureSearch) {
                        itineraryDepartureStop = selectedStop
                        itineraryDepartureQuery = ""
                        itineraryDepartureResults = emptyList()
                    } else {
                        itineraryArrivalStop = selectedStop
                        itineraryArrivalQuery = ""
                        itineraryArrivalResults = emptyList()
                    }
                    itinerarySearchTarget = null
                }
            }
        )
    }

    if (isMapStyleMenuExpanded) {
        MapStyleSelectionSheet(
            isOffline = isOffline,
            downloadedMapStyles = offlineDataInfo.downloadedMapStyles,
            selectedMapStyle = selectedMapStyle,
            onDismiss = { isMapStyleMenuExpanded = false },
            onStyleSelected = { style ->
                mapStyleRepository.saveSelectedStyle(style)
                val effectiveStyle = mapStyleRepository.getEffectiveStyle(
                    isOffline,
                    offlineDataInfo.downloadedMapStyles
                )
                selectedMapStyle = effectiveStyle
                mapStyleUrl = effectiveStyle.styleUrl
            }
        )
    }

    LaunchedEffect(shouldCenterOnUser) {
        if (shouldCenterOnUser) {
            shouldCenterOnUser = false
        }
    }

    if (showLinesSheet) {
        val modalBottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        LaunchedEffect(true) {
            if (!showLinesSheet) {
                modalBottomSheetState.hide()
            }
        }

        ModalBottomSheet(
            onDismissRequest = onLinesSheetDismiss,
            containerColor = Color.White,
            sheetState = modalBottomSheetState
        ) {
            LinesBottomSheet(
                allLines = viewModel.getAllAvailableLines(),
                onLineClick = { lineName ->
                    // Cancel pending operations and clear states from previous line to prevent OOM
                    viewModel.resetLineDetailState()

                    onLinesSheetDismiss()

                    if (!isMetroTramOrFunicular(lineName)) {
                        scope.launch {
                            viewModel.addLineToLoaded(lineName)
                            if (isTemporaryBus(lineName)) {
                                temporaryLoadedBusLines = temporaryLoadedBusLines + lineName
                            }
                            delay(100)

                            selectedLine = LineInfo(
                                lineName = lineName,
                                currentStationName = ""
                            )
                            sheetContentState = SheetContentState.LINE_DETAILS
                            delay(50)
                            scaffoldSheetState.bottomSheetState.partialExpand()
                        }
                    } else {
                        scope.launch {
                            val currentLines = when (val currentState = uiState) {
                                is TransportLinesUiState.Success -> currentState.lines
                                is TransportLinesUiState.PartialSuccess -> currentState.lines
                                else -> emptyList()
                            }
                            val isLoaded = currentLines.any { it.properties.ligne.equals(lineName, ignoreCase = true) }

                            if (!isLoaded) {
                                viewModel.addLineToLoaded(lineName)
                                delay(100)
                            }

                            selectedLine = LineInfo(
                                lineName = lineName,
                                currentStationName = ""
                            )
                            sheetContentState = SheetContentState.LINE_DETAILS
                            delay(50)
                            scaffoldSheetState.bottomSheetState.partialExpand()
                        }
                    }
                },
                viewModel = viewModel
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItinerarySearchBarField(
    modifier: Modifier = Modifier,
    selectedStop: SelectedStop?,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    placeholder: String
) {
    val displayedValue = selectedStop?.name ?: ""

    SearchBar(
        modifier = modifier
            .fillMaxWidth(),
        inputField = {
            SearchBarDefaults.InputField(
                query = displayedValue,
                onQueryChange = { onClick() },
                onSearch = { onClick() },
                expanded = false,
                onExpandedChange = { if (it) onClick() },
                placeholder = {
                    Text(
                        text = placeholder,
                        color = Color.White
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Red500
                    )
                },
                colors = TextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color.White,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    focusedContainerColor = Color.Black,
                    unfocusedContainerColor = Color.Black,
                    focusedPlaceholderColor = Color.White.copy(alpha = 0.6f),
                    unfocusedPlaceholderColor = Color.White.copy(alpha = 0.6f)
                )
            )
        },
        expanded = false,
        onExpandedChange = { if (it) onClick() },
        colors = SearchBarDefaults.colors(
            containerColor = Color.Black,
            dividerColor = Color.Transparent
        )
    ) {}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItineraryFullscreenSearchOverlay(
    query: String,
    searchResults: List<StationSearchResult>,
    placeholder: String,
    autofocusNonce: Int,
    onQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onResultSelected: (StationSearchResult) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var queryField by remember {
        mutableStateOf(
            TextFieldValue(
                text = query,
                selection = TextRange(query.length)
            )
        )
    }

    LaunchedEffect(autofocusNonce, query) {
        queryField = TextFieldValue(
            text = query,
            selection = TextRange(query.length)
        )
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(onClick = onDismiss)
    ) {
        SearchBar(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(),
            inputField = {
                TextField(
                    value = queryField,
                    onValueChange = { updated ->
                        queryField = updated
                        onQueryChange(updated.text)
                    },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = ImeAction.Search
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onSearch = {
                            keyboardController?.hide()
                            if (queryField.text.length >= 2) {
                                searchResults.firstOrNull()?.let(onResultSelected)
                            }
                        }
                    ),
                    placeholder = { Text(placeholder, color = Color.White) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = Red500,
                            modifier = Modifier.padding(start = 32.dp, end = 12.dp)
                        )
                    },
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color.White,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        focusedContainerColor = Color.Black,
                        unfocusedContainerColor = Color.Black,
                        focusedPlaceholderColor = Color.White.copy(alpha = 0.6f),
                        unfocusedPlaceholderColor = Color.White.copy(alpha = 0.6f)
                    )
                )
            },
            expanded = true,
            onExpandedChange = { shouldExpand ->
                if (!shouldExpand) onDismiss()
            },
            colors = SearchBarDefaults.colors(
                containerColor = Color.Black,
                dividerColor = Color.Transparent
            )
        ) {
            if (query.length >= 2 && searchResults.isEmpty()) {
                Text(
                    text = "Aucun resultat",
                    color = Color.White.copy(alpha = 0.65f),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                )
            } else if (query.length >= 2) {
                searchResults.forEach { result ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onResultSelected(result) }
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.padding(end = 10.dp)
                        )
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = result.stopName,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (result.lines.isNotEmpty()) {
                                Spacer(modifier = Modifier.size(4.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    result.lines.take(8).forEach { lineName ->
                                        val resourceId = BusIconHelper.getResourceIdForLine(LocalContext.current, lineName)
                                        if (resourceId != 0) {
                                            Image(
                                                painter = painterResource(id = resourceId),
                                                contentDescription = stringResource(R.string.line_icon, lineName),
                                                modifier = Modifier.size(22.dp)
                                            )
                                        } else {
                                            Text(
                                                text = lineName,
                                                color = Color.White.copy(alpha = 0.85f),
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun findNearestStopName(userLocation: LatLng, stops: List<StopFeature>): String? {
    var nearestName: String? = null
    var nearestDistance = Double.MAX_VALUE

    stops.forEach { stop ->
        val coordinates = stop.geometry.coordinates
        if (coordinates.size >= 2) {
            val lon = coordinates[0]
            val lat = coordinates[1]
            val distance = squaredDistance(
                lat1 = userLocation.latitude,
                lon1 = userLocation.longitude,
                lat2 = lat,
                lon2 = lon
            )
            if (distance < nearestDistance) {
                nearestDistance = distance
                nearestName = stop.properties.nom
            }
        }
    }

    return nearestName
}

private fun squaredDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = lat1 - lat2
    val dLon = lon1 - lon2
    return dLat * dLat + dLon * dLon
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StationSheetContent(
    stationInfo: StationInfo,
    viewModel: TransportViewModel,
    onDismiss: () -> Unit,
    onDepartureClick: (lineName: String, directionId: Int, departureTime: String) -> Unit,
    isFavoriteStop: Boolean = false,
    onToggleFavoriteStop: () -> Unit = {},
    onItineraryClick: (String) -> Unit = {}
) {
    StationBottomSheet(
        stationInfo = stationInfo,
        sheetState = null,
        onDismiss = onDismiss,
        viewModel = viewModel,
        onDepartureClick = onDepartureClick,
        isFavoriteStop = isFavoriteStop,
        onToggleFavoriteStop = onToggleFavoriteStop,
        onItineraryClick = { onItineraryClick(stationInfo.nom) }
    )
}

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LineDetailsSheetContent(
    lineInfo: LineInfo,
    viewModel: TransportViewModel,
    selectedDirection: Int,
    onDirectionChange: (Int) -> Unit,
    onBackToStation: () -> Unit,
    onLineClick: (String) -> Unit = {},
    onStopClick: (String) -> Unit = {},
    onShowAllSchedules: (lineName: String, directionName: String, schedules: List<String>) -> Unit,
    onItineraryClick: (stopName: String) -> Unit = {},
    onHeaderClick: () -> Unit = {},
    favoriteStops: Set<String> = emptySet(),
    onToggleFavoriteStop: (String) -> Unit = {},
    onHeaderLineCountChanged: (Int) -> Unit = {}
) {
    LineDetailsBottomSheet(
        viewModel = viewModel,
        lineInfo = lineInfo,
        sheetState = null,
        selectedDirection = selectedDirection,
        onDirectionChange = onDirectionChange,
        onDismiss = {},
        onBackToStation = onBackToStation,
        onLineClick = onLineClick,
        onStopClick = onStopClick,
        onShowAllSchedules = onShowAllSchedules,
        onItineraryClick = onItineraryClick,
        onHeaderClick = onHeaderClick,
        favoriteStops = favoriteStops,
        onToggleFavoriteStop = onToggleFavoriteStop,
        onHeaderLineCountChanged = onHeaderLineCountChanged
    )
}

private fun filterMapLines(
    map: MapLibreMap,
    allLines: List<Feature>,
    selectedLineName: String
): Int {
    map.getStyle { style ->
        val layerId = "all-lines-layer"
        val existingLayer = style.getLayer(layerId)

        if (existingLayer != null) {
            (existingLayer as? LineLayer)?.setFilter(
                Expression.eq(Expression.get("ligne"), selectedLineName)
            )
        }

        // Also hide/show individual line layers (for lignes fortes)
        allLines.forEach { feature ->
            val ligne = feature.properties.ligne
            val codeTrace = feature.properties.codeTrace
            
            val individualLayerId = "layer-${ligne}-${codeTrace}"
            style.getLayer(individualLayerId)?.let { layer ->
                val shouldBeVisible = ligne.equals(selectedLineName, ignoreCase = true)
                layer.setProperties(
                    PropertyFactory.visibility(if (shouldBeVisible) "visible" else "none")
                )
            }
        }
    }
    val visibleCandidates = allLines.count { it.properties.ligne.equals(selectedLineName, ignoreCase = true) }
    return visibleCandidates
}

private fun zoomToLine(
    map: MapLibreMap,
    allLines: List<Feature>,
    selectedLineName: String
) {
    val lineFeatures = allLines.filter {
        it.properties.ligne?.equals(selectedLineName, ignoreCase = true) == true
    }

    if (lineFeatures.isEmpty()) return

    val boundsBuilder = LatLngBounds.Builder()
    var hasCoordinates = false

    lineFeatures.forEach { feature ->
        feature.geometry.coordinates.forEach { lineString ->
            lineString.forEach { coord ->
                boundsBuilder.include(LatLng(coord[1], coord[0]))
                hasCoordinates = true
            }
        }
    }

    if (!hasCoordinates) return

    val bounds = boundsBuilder.build()

    val paddingLeft = 200
    val paddingTop = 100
    val paddingRight = 200
    val paddingBottom = 600

    map.animateCamera(
        CameraUpdateFactory.newLatLngBounds(bounds, paddingLeft, paddingTop, paddingRight, paddingBottom),
        1000
    )
}

private fun zoomToStop(
    map: MapLibreMap,
    stopName: String,
    allStops: List<StopFeature>
) {
    fun normalizeStopName(name: String): String {
        return name.filter { it.isLetter() }.lowercase()
    }

    val normalizedStopName = normalizeStopName(stopName)

    var stop = allStops.find {
        it.properties.nom.equals(stopName, ignoreCase = true)
    }

    if (stop == null) {
        stop = allStops.find {
            normalizeStopName(it.properties.nom) == normalizedStopName
        }
    }

    if (stop == null) {
        return
    }

    val lat = stop.geometry.coordinates[1]
    val lon = stop.geometry.coordinates[0]
    val stopLocation = LatLng(lat, lon)

    map.animateCamera(
        CameraUpdateFactory.newLatLngZoom(stopLocation, 15.0),
        1000
    )
}

private fun filterMapStops(
    style: Style,
    selectedLineName: String
) {
    val priorityLayerPrefix = "transport-stops-layer-priority"
    val tramLayerPrefix = "transport-stops-layer-tram"
    val secondaryLayerPrefix = "transport-stops-layer-secondary"

    val linePropertyName = "has_line_${selectedLineName.uppercase()}"

    // Filter layers by slot
    (-25..25).forEach { idx ->
        (style.getLayer("$priorityLayerPrefix-$idx") as? SymbolLayer)?.setFilter(
            Expression.all(
                Expression.eq(Expression.get("stop_priority"), 2),
                Expression.eq(Expression.get("slot"), idx),
                Expression.eq(Expression.get(linePropertyName), true)
            )
        )

        (style.getLayer("$tramLayerPrefix-$idx") as? SymbolLayer)?.let { layer ->
            layer.setFilter(
                Expression.all(
                    Expression.eq(Expression.get("stop_priority"), 1),
                    Expression.eq(Expression.get("slot"), idx),
                    Expression.eq(Expression.get(linePropertyName), true)
                )
            )
            layer.minZoom = PRIORITY_STOPS_MIN_ZOOM
        }

        (style.getLayer("$secondaryLayerPrefix-$idx") as? SymbolLayer)?.let { layer ->
            layer.setFilter(
                Expression.all(
                    Expression.eq(Expression.get("stop_priority"), 0),
                    Expression.eq(Expression.get("slot"), idx),
                    Expression.eq(Expression.get(linePropertyName), true)
                )
            )
            layer.minZoom = PRIORITY_STOPS_MIN_ZOOM
        }
    }
}

private fun filterMapStopsWithSelectedStop(
    map: MapLibreMap,
    selectedLineName: String,
    selectedStopName: String?,
    allStops: List<StopFeature>,
    allLines: List<Feature>,
    viewModel: TransportViewModel? = null
) {
    map.getStyle { style ->
        if (selectedStopName.isNullOrBlank()) {
            filterMapStops(style, selectedLineName)
            style.getLayer("line-stops-circles")?.let { style.removeLayer(it) }
            style.getSource("line-stops-circles-source")?.let { style.removeSource(it) }
            return@getStyle
        }

        fun normalizeStopName(name: String): String {
            return name.filter { it.isLetter() }.lowercase()
        }

        val normalizedSelectedStop = normalizeStopName(selectedStopName)
        val priorityLayerPrefix = "transport-stops-layer-priority"
        val tramLayerPrefix = "transport-stops-layer-tram"
        val secondaryLayerPrefix = "transport-stops-layer-secondary"
        val linePropertyName = "has_line_${selectedLineName.uppercase()}"

        // Filter layers by slot
        (-25..25).forEach { idx ->
            (style.getLayer("$priorityLayerPrefix-$idx") as? SymbolLayer)?.let { layer ->
                layer.setFilter(
                    Expression.all(
                        Expression.eq(Expression.get("stop_priority"), 2),
                        Expression.eq(Expression.get("slot"), idx),
                        Expression.eq(Expression.get(linePropertyName), true),
                        Expression.eq(Expression.get("normalized_nom"), normalizedSelectedStop)
                    )
                )
                layer.minZoom = SELECTED_STOP_MIN_ZOOM
            }

            (style.getLayer("$tramLayerPrefix-$idx") as? SymbolLayer)?.let { layer ->
                layer.setFilter(
                    Expression.all(
                        Expression.eq(Expression.get("stop_priority"), 1),
                        Expression.eq(Expression.get("slot"), idx),
                        Expression.eq(Expression.get(linePropertyName), true),
                        Expression.eq(Expression.get("normalized_nom"), normalizedSelectedStop)
                    )
                )
                layer.minZoom = SELECTED_STOP_MIN_ZOOM
            }

            (style.getLayer("$secondaryLayerPrefix-$idx") as? SymbolLayer)?.let { layer ->
                layer.setFilter(
                    Expression.all(
                        Expression.eq(Expression.get("stop_priority"), 0),
                        Expression.eq(Expression.get("slot"), idx),
                        Expression.eq(Expression.get(linePropertyName), true),
                        Expression.eq(Expression.get("normalized_nom"), normalizedSelectedStop)
                    )
                )
                layer.minZoom = SELECTED_STOP_MIN_ZOOM
            }
        }

        addCircleLayerForLineStops(
            style,
            selectedLineName,
            selectedStopName,
            allStops,
            allLines,
            viewModel
        )
    }
}

private fun addCircleLayerForLineStops(
    style: Style,
    selectedLineName: String,
    selectedStopName: String,
    allStops: List<StopFeature>,
    allLines: List<Feature>,
    viewModel: TransportViewModel? = null
) {
    fun normalizeStopName(name: String): String {
        return name.filter { it.isLetter() }.lowercase()
    }

    val normalizedSelectedStop = normalizeStopName(selectedStopName)

    val lineColor = allLines
        .find { it.properties.ligne.equals(selectedLineName, ignoreCase = true) }
        ?.let { LineColorHelper.getColorForLine(it) }
        ?: "#EF4444"

    // OPTIMIZATION: Use pre-computed index from ViewModel if available (O(1) lookup)
    // Falls back to filtering all stops if index is not ready
    val lineStops = if (viewModel != null && viewModel.isStopsByLineIndexReady()) {
        // O(1) lookup from index, then filter only the selected stop
        viewModel.getStopsFeaturesForLine(selectedLineName)
            .filter { stop -> normalizeStopName(stop.properties.nom) != normalizedSelectedStop }
    } else {
        // Fallback: filter all stops (slower, but works if index not ready)
        allStops.filter { stop ->
            val lines = BusIconHelper.getAllLinesForStop(stop)
            val hasLine = lines.any { it.equals(selectedLineName, ignoreCase = true) }
            val isNotSelected = normalizeStopName(stop.properties.nom) != normalizedSelectedStop
            hasLine && isNotSelected
        }
    }

    val circlesGeoJson = JsonObject().apply {
        addProperty("type", "FeatureCollection")
        val features = JsonArray()

        lineStops.forEach { stop ->
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
                }
                add("properties", properties)
            }
            features.add(pointFeature)
        }

        add("features", features)
    }

    // OPTIMIZATION: Use setGeoJson if source exists, otherwise create new source
    val existingSource = style.getSource("line-stops-circles-source") as? GeoJsonSource
    if (existingSource != null) {
        // Update existing source data without recreating
        existingSource.setGeoJson(circlesGeoJson.toString())
        // Update layer color (stroke color may have changed for different line)
        (style.getLayer("line-stops-circles") as? CircleLayer)?.setProperties(
            PropertyFactory.circleStrokeColor(lineColor)
        )
    } else {
        // Create new source and layer
        val circlesSource = GeoJsonSource("line-stops-circles-source", circlesGeoJson.toString())
        style.addSource(circlesSource)

        val circlesLayer = CircleLayer("line-stops-circles", "line-stops-circles-source").apply {
            setProperties(
                PropertyFactory.circleRadius(6f),
                PropertyFactory.circleColor("#FFFFFF"),
                PropertyFactory.circleStrokeWidth(4.5f),
                PropertyFactory.circleStrokeColor(lineColor),
                PropertyFactory.circleOpacity(1.0f),
                PropertyFactory.circleStrokeOpacity(1.0f)
            )
            minZoom = SELECTED_STOP_MIN_ZOOM
        }
        style.addLayer(circlesLayer)
    }
}

private fun showAllMapLines(
    map: MapLibreMap,
    allLines: List<Feature>
) {
    map.getStyle { style ->
        allLines.forEach { feature ->
            val ligne = feature.properties.ligne
            val codeTrace = feature.properties.codeTrace

            val layerId = "layer-${ligne}-${codeTrace}"
            val sourceId = "line-${ligne}-${codeTrace}"

            val existingLayer = style.getLayer(layerId)
            if (existingLayer == null) {
                addLineToMap(map, feature)
            } else {
                existingLayer.setProperties(PropertyFactory.visibility("visible"))
            }

            if (style.getSource(sourceId) == null) {
                addLineToMap(map, feature)
            }
        }

        showAllMapStops(style)

        style.getLayer("line-stops-circles")?.let { style.removeLayer(it) }
        style.getSource("line-stops-circles-source")?.let { style.removeSource(it) }
    }
}

private fun showAllMapStops(
    style: Style
) {
    val priorityLayerPrefix = "transport-stops-layer-priority"
    val tramLayerPrefix = "transport-stops-layer-tram"
    val secondaryLayerPrefix = "transport-stops-layer-secondary"

    // Reset filters to show all stops (by slot)
    (-25..25).forEach { idx ->
        (style.getLayer("$priorityLayerPrefix-$idx") as? SymbolLayer)?.let { layer ->
            layer.setFilter(
                Expression.all(
                    Expression.eq(Expression.get("stop_priority"), 2),
                    Expression.eq(Expression.get("slot"), idx)
                )
            )
            layer.minZoom = PRIORITY_STOPS_MIN_ZOOM
        }

        (style.getLayer("$tramLayerPrefix-$idx") as? SymbolLayer)?.let { layer ->
            layer.setFilter(
                Expression.all(
                    Expression.eq(Expression.get("stop_priority"), 1),
                    Expression.eq(Expression.get("slot"), idx)
                )
            )
            layer.minZoom = TRAM_STOPS_MIN_ZOOM
        }

        (style.getLayer("$secondaryLayerPrefix-$idx") as? SymbolLayer)?.let { layer ->
            layer.setFilter(
                Expression.all(
                    Expression.eq(Expression.get("stop_priority"), 0),
                    Expression.eq(Expression.get("slot"), idx)
                )
            )
            layer.minZoom = SECONDARY_STOPS_MIN_ZOOM
        }
    }
}

private fun addLineToMap(
    map: MapLibreMap,
    feature: Feature
) {
    map.getStyle { style ->
        val ligne = feature.properties.ligne
        val codeTrace = feature.properties.codeTrace
        
        // Skip if essential properties are null
        if (ligne == null || codeTrace == null) {
            return@getStyle
        }

        val sourceId = "line-${ligne}-${codeTrace}"
        val layerId = "layer-${ligne}-${codeTrace}"

        style.getLayer(layerId)?.let { style.removeLayer(it) }
        style.getSource(sourceId)?.let { style.removeSource(it) }

        val lineGeoJson = createGeoJsonFromFeature(feature)

        val lineSource = GeoJsonSource(sourceId, lineGeoJson)
        style.addSource(lineSource)

        val lineColor = LineColorHelper.getColorForLine(feature)

        val upperLineName = ligne.uppercase()
        val familleTransport = feature.properties.familleTransport
        val lineWidth = when {
            familleTransport == "BAT" || upperLineName.startsWith("NAV") -> 2f
            familleTransport == "TRA" || familleTransport == "TRAM" || upperLineName.startsWith("TB") -> 2f
            else -> 4f
        }

        val lineLayer = LineLayer(layerId, sourceId).apply {
            setProperties(
                PropertyFactory.lineColor(lineColor),
                PropertyFactory.lineWidth(lineWidth),
                PropertyFactory.lineOpacity(0.8f),
                PropertyFactory.lineCap("round"),
                PropertyFactory.lineJoin("round")
            )
        }

        val firstStopLayer = style.layers.find { it.id.startsWith("transport-stops-layer") }
        if (firstStopLayer != null) {
            style.addLayerBelow(lineLayer, firstStopLayer.id)
        } else {
            style.addLayer(lineLayer)
        }
    }
}



// Holder for the current map click listener to allow removal before adding a new one
private var currentMapClickListener: MapLibreMap.OnMapClickListener? = null

private suspend fun addStopsToMap(
    map: MapLibreMap,
    stops: List<StopFeature>,
    context: Context,
    onStationClick: (StationInfo) -> Unit = {},
    onLineClick: (String) -> Unit = {},
    scope: CoroutineScope,
    viewModel: TransportViewModel? = null
) {
    // OPTIMIZATION: Try to use cached GeoJSON + usedSlots if available
    val cachedData = viewModel?.getCachedStopsGeoJson(stops)

    val (stopsGeoJson, requiredIcons, usedSlots) = // Full cache hit — GeoJSON, icons, AND usedSlots are all cached
        cachedData
            ?: // Compute GeoJSON and cache it
            withContext(Dispatchers.Default) {
                val requiredIcons = mutableSetOf<String>()
                val usedSlots = mutableSetOf<Int>()

                // Use centralized BusIconHelper cache for resource ID lookups
                fun checkIconAvailable(name: String): Boolean {
                    return BusIconHelper.getResourceIdForDrawableName(context, name) != 0
                }

                // Add mode icons to required icons
                listOf("mode_bus", "mode_chrono", "mode_jd").forEach { modeIcon ->
                    if (checkIconAvailable(modeIcon)) {
                        requiredIcons.add(modeIcon)
                    }
                }

                stops.forEach { stop ->
                    val lineNames = BusIconHelper.getAllLinesForStop(stop)
                    if (lineNames.isEmpty()) return@forEach

                    // Separate lignes fortes from bus lines
                    val lignesFortes = lineNames.filter { isMetroTramOrFunicular(it) }
                    val busLines = lineNames.filter { !isMetroTramOrFunicular(it) }

                    // Add line icons for lignes fortes only
                    lignesFortes.forEach { lineName ->
                        val drawableName = BusIconHelper.getDrawableNameForLineName(lineName)
                        if (checkIconAvailable(drawableName)) {
                            requiredIcons.add(drawableName)
                        }
                    }

                    // Calculate usedSlots
                    val uniqueModes = busLines.mapNotNull { getModeIconForLine(it) }.distinct()
                        .filter { checkIconAvailable(it) }
                    val validLignesFortes = lignesFortes.count { lineName ->
                        val drawableName = BusIconHelper.getDrawableNameForLineName(lineName)
                        checkIconAvailable(drawableName)
                    }
                    val n = validLignesFortes + uniqueModes.size
                    if (n > 0) {
                        var slot = -(n - 1)
                        repeat(n) {
                            usedSlots.add(slot)
                            slot += 2
                        }
                    }
                }

                // Pass all stops to merge function, using StringBuilder for fast GeoJSON creation
                val stopsGeoJson = createStopsGeoJsonFromStops(stops, requiredIcons)

                // Cache the result for future use (including usedSlots)
                viewModel?.cacheStopsGeoJson(stops, stopsGeoJson, requiredIcons, usedSlots)

                Triple(stopsGeoJson, requiredIcons, usedSlots)
            }

    map.getStyle { style ->
        val sourceId = "transport-stops"
        val priorityLayerPrefix = "transport-stops-layer-priority"
        val tramLayerPrefix = "transport-stops-layer-tram"
        val secondaryLayerPrefix = "transport-stops-layer-secondary"


        (-25..25).forEach { idx ->
            style.getLayer("$priorityLayerPrefix-$idx")?.let { style.removeLayer(it) }
            style.getLayer("$tramLayerPrefix-$idx")?.let { style.removeLayer(it) }
            style.getLayer("$secondaryLayerPrefix-$idx")?.let { style.removeLayer(it) }
        }
        style.getLayer("clusters")?.let { style.removeLayer(it) }
        style.getLayer("cluster-count")?.let { style.removeLayer(it) }

        style.getSource(sourceId)?.let { style.removeSource(it) }

        // OPTIMIZATION: Use cached bitmaps if available, otherwise load and cache
        // Uses direct LruCache accessors to avoid snapshot() full-copy allocation
        scope.launch(Dispatchers.IO) {
            val allCached = viewModel?.hasAllIcons(requiredIcons) == true

            val bitmaps: Map<String, Bitmap> = if (allCached) {
                // All icons are cached - retrieve them directly without snapshot copy
                requiredIcons.mapNotNull { iconName ->
                    viewModel?.getIconBitmap(iconName)?.let { iconName to it }
                }.toMap()
            } else {
                // Load missing bitmaps and cache them individually
                requiredIcons.mapNotNull { iconName ->
                    // Check cache first for this specific icon
                    viewModel?.getIconBitmap(iconName)?.let { return@mapNotNull iconName to it }

                    try {
                        val resourceId = BusIconHelper.getResourceIdForDrawableName(context, iconName)
                        if (resourceId != 0) {
                            val drawable = ContextCompat.getDrawable(context, resourceId)
                            drawable?.let { d ->
                                val bitmap = if (d is BitmapDrawable) {
                                    d.bitmap
                                } else {
                                    val bitmap = createBitmap(
                                        d.intrinsicWidth.coerceAtLeast(1),
                                        d.intrinsicHeight.coerceAtLeast(1),
                                        Bitmap.Config.ARGB_8888
                                    )
                                    val canvas = android.graphics.Canvas(bitmap)
                                    d.setBounds(0, 0, canvas.width, canvas.height)
                                    d.draw(canvas)
                                    bitmap
                                }
                                // Cache individually as loaded
                                viewModel?.cacheIconBitmap(iconName, bitmap)
                                iconName to bitmap
                            }
                        } else null
                    } catch (_: Exception) {
                        null
                    }
                }.toMap()
            }

            withContext(Dispatchers.Main) {
                // Batch add images if possible, otherwise simple loop
                bitmaps.forEach { (name, bitmap) ->
                    if (style.getImage(name) == null) { // Avoid re-adding if existing
                        style.addImage(name, bitmap)
                    }
                }

                // Add source and layers only AFTER images are added
                val stopsSource = GeoJsonSource(
                    sourceId,
                    stopsGeoJson,
                    GeoJsonOptions()
                        .withCluster(true)
                        .withClusterRadius(50)
                        .withClusterMaxZoom(11) // Below PRIORITY_STOPS_MIN_ZOOM (12.5) to ensure stops are unclustered when they become visible
                )
                style.addSource(stopsSource)

                // 1. Cluster Circles (Aggregated stops)
                val clusterLayer = CircleLayer("clusters", sourceId).apply {
                    setProperties(
                        PropertyFactory.circleColor(
                            Expression.step(
                                Expression.get("point_count"),
                                Expression.literal("#E60000"), // Default TCL Red
                                Expression.stop(10, "#E60000"),
                                Expression.stop(50, "#B71C1C")
                            )
                        ),
                        PropertyFactory.circleRadius(18f)
                    )
                    setFilter(Expression.has("point_count"))
                }
                style.addLayer(clusterLayer)

                val countLayer = SymbolLayer("cluster-count", sourceId).apply {
                    setProperties(
                        PropertyFactory.textField(Expression.toString(Expression.get("point_count_abbreviated"))),
                        PropertyFactory.textSize(12f),
                        PropertyFactory.textColor(android.graphics.Color.WHITE),
                        PropertyFactory.textIgnorePlacement(true),
                        PropertyFactory.textAllowOverlap(true)
                    )
                    setFilter(Expression.has("point_count"))
                }
                style.addLayer(countLayer)

                // 2. Individual Stops Icons (Unclustered)
                // OPTIMIZED: Create layers only for slots that are actually used
                val iconSizesPriority = 0.7f
                val iconSizesSecondary = 0.62f

                usedSlots.sorted().forEach { idx ->
                    val yOffset = idx * 13f

                    // Priority Stops (Metro, Funiculaire - stop_priority = 2)
                    val priorityLayer = SymbolLayer("$priorityLayerPrefix-$idx", sourceId).apply {
                        setProperties(
                            PropertyFactory.iconImage(Expression.get("icon")),
                            PropertyFactory.iconSize(iconSizesPriority),
                            PropertyFactory.iconAllowOverlap(true),
                            PropertyFactory.iconIgnorePlacement(true),
                            PropertyFactory.iconAnchor("center"),
                            PropertyFactory.iconOffset(arrayOf(0f, yOffset))
                        )
                        setFilter(
                            Expression.all(
                                Expression.not(Expression.has("point_count")),
                                Expression.eq(Expression.get("stop_priority"), 2),
                                Expression.eq(Expression.get("slot"), idx)
                            )
                        )
                        minZoom = PRIORITY_STOPS_MIN_ZOOM
                    }
                    style.addLayerBelow(priorityLayer, "clusters")

                    // Tram Stops (stop_priority = 1)
                    val tramLayer = SymbolLayer("$tramLayerPrefix-$idx", sourceId).apply {
                        setProperties(
                            PropertyFactory.iconImage(Expression.get("icon")),
                            PropertyFactory.iconSize(iconSizesPriority),
                            PropertyFactory.iconAllowOverlap(true),
                            PropertyFactory.iconIgnorePlacement(true),
                            PropertyFactory.iconAnchor("center"),
                            PropertyFactory.iconOffset(arrayOf(0f, yOffset))
                        )
                        setFilter(
                            Expression.all(
                                Expression.not(Expression.has("point_count")),
                                Expression.eq(Expression.get("stop_priority"), 1),
                                Expression.eq(Expression.get("slot"), idx)
                            )
                        )
                        minZoom = TRAM_STOPS_MIN_ZOOM
                    }
                    style.addLayerBelow(tramLayer, "clusters")

                    // Secondary Stops (Bus - stop_priority = 0)
                    val secondaryLayer = SymbolLayer("$secondaryLayerPrefix-$idx", sourceId).apply {
                        setProperties(
                            PropertyFactory.iconImage(Expression.get("icon")),
                            PropertyFactory.iconSize(iconSizesSecondary),
                            PropertyFactory.iconAllowOverlap(true),
                            PropertyFactory.iconIgnorePlacement(true),
                            PropertyFactory.iconAnchor("center"),
                            PropertyFactory.iconOffset(arrayOf(0f, yOffset))
                        )
                        setFilter(
                            Expression.all(
                                Expression.not(Expression.has("point_count")),
                                Expression.eq(Expression.get("stop_priority"), 0),
                                Expression.eq(Expression.get("slot"), idx)
                            )
                        )
                        minZoom = SECONDARY_STOPS_MIN_ZOOM
                    }
                    style.addLayerBelow(secondaryLayer, "clusters")
                }

                // Remove previous listener before adding a new one to prevent duplicates
                currentMapClickListener?.let { map.removeOnMapClickListener(it) }

                // Interaction listener for stops and lines
                val clickListener = MapLibreMap.OnMapClickListener { point ->
                    val screenPoint = map.projection.toScreenLocation(point)

                    // Check clusters first
                    val clusterFeatures = map.queryRenderedFeatures(screenPoint, "clusters")
                    if (clusterFeatures.isNotEmpty()) {
                        val cameraUpdate = CameraUpdateFactory.newLatLngZoom(point, map.cameraPosition.zoom + 2)
                        map.animateCamera(cameraUpdate)
                        return@OnMapClickListener true
                    }

                    // In global LIVE mode, clicking a vehicle opens its line details.
                    val globalVehicleFeatures = map.queryRenderedFeatures(screenPoint, "global-vehicle-positions-layer")
                    if (globalVehicleFeatures.isNotEmpty()) {
                        val feature = globalVehicleFeatures.first()
                        val props = feature.properties()
                        if (props != null) {
                            try {
                                val lineName = if (props.has("lineName")) props.get("lineName").asString else ""
                                if (lineName.isNotEmpty()) {
                                    onLineClick(lineName)
                                    return@OnMapClickListener true
                                }
                            } catch (_: Exception) {
                                // Ignore parse errors
                            }
                        }
                    }

                    // Check individual stops first (higher priority than lines)
                    val interactableLayers = usedSlots.flatMap { idx ->
                        listOf("$priorityLayerPrefix-$idx", "$tramLayerPrefix-$idx", "$secondaryLayerPrefix-$idx")
                    }.toTypedArray()

                    val stopFeatures = map.queryRenderedFeatures(screenPoint, *interactableLayers)
                    if (stopFeatures.isNotEmpty()) {
                        val feature = stopFeatures.first()
                        val props = feature.properties()
                        if (props != null) {
                            try {
                                val stopName = if (props.has("nom")) props.get("nom").asString else ""
                                val lignesJson = if (props.has("lignes")) props.get("lignes").asString else "[]"

                                val lignes = try {
                                    val jsonArray = JsonParser.parseString(lignesJson).asJsonArray
                                    jsonArray.map { it.asString }
                                } catch (_: Exception) {
                                    emptyList()
                                }

                                val stationInfo = StationInfo(
                                    nom = stopName,
                                    lignes = lignes
                                )
                                onStationClick(stationInfo)
                                return@OnMapClickListener true
                            } catch (_: Exception) {
                                // Ignore parse errors
                            }
                        }
                    }

                    // Check for line clicks (only if no stop was clicked)
                    // Use a larger hitbox for easier line selection (30px padding around touch point)
                    val hitboxPadding = 30f
                    val lineHitbox = RectF(
                        screenPoint.x - hitboxPadding,
                        screenPoint.y - hitboxPadding,
                        screenPoint.x + hitboxPadding,
                        screenPoint.y + hitboxPadding
                    )
                    
                    // Query all-lines-layer and individual line layers
                    // Get all layer IDs that could contain line features
                    val currentStyle = map.style
                    val allLineLayerIds = mutableListOf("all-lines-layer")
                    currentStyle?.layers?.forEach { layer ->
                        if (layer.id.startsWith("layer-") && !layer.id.startsWith("layer-stops")) {
                            allLineLayerIds.add(layer.id)
                        }
                    }
                    
                    val lineFeatures = map.queryRenderedFeatures(lineHitbox, *allLineLayerIds.toTypedArray())
                    
                    if (lineFeatures.isNotEmpty()) {
                        val feature = lineFeatures.first()
                        val props = feature.properties()
                        if (props != null) {
                            try {
                                val lineName = if (props.has("ligne")) props.get("ligne").asString else ""
                                if (lineName.isNotEmpty()) {
                                    onLineClick(lineName)
                                    return@OnMapClickListener true
                                }
                            } catch (_: Exception) {
                                // Ignore parse errors
                            }
                        }
                    }
                    false
                }
                
                currentMapClickListener = clickListener
                map.addOnMapClickListener(clickListener)
            }
        }
    }
}

private fun createGeoJsonFromFeature(feature: Feature): String {
    val geoJsonObject = JsonObject().apply {
        addProperty("type", "Feature")

        val geometryObject = JsonObject().apply {
            addProperty("type", feature.geometry.type)

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

        val propertiesObject = JsonObject().apply {
            addProperty("ligne", feature.properties.ligne ?: "")
            addProperty("nom_trace", feature.properties.nomTrace ?: "")
            addProperty("couleur", feature.properties.couleur ?: "")
        }
        add("properties", propertiesObject)
    }

    return geoJsonObject.toString()
}

private fun mergeStopsByName(stops: List<StopFeature>): List<StopFeature> {
    fun normalizeStopName(name: String): String {
        return name.filter { it.isLetter() }.lowercase()
    }

    val strongLineStops = mutableListOf<StopFeature>()
    val weakLineStops = mutableListOf<StopFeature>()

    stops.forEach { stop ->
        val allLines = BusIconHelper.getAllLinesForStop(stop)
        val strongLines = allLines.filter { isMetroTramOrFunicular(it) }
        val weakLines = allLines.filter { !isMetroTramOrFunicular(it) }

        if (strongLines.isNotEmpty()) {
            val strongDesserte = strongLines.joinToString(", ")
            strongLineStops.add(
                StopFeature(
                    type = stop.type,
                    id = stop.id,
                    geometry = stop.geometry,
                    properties = StopProperties(
                        id = stop.properties.id,
                        nom = stop.properties.nom,
                        desserte = strongDesserte,
                        pmr = stop.properties.pmr,
                        ascenseur = stop.properties.ascenseur,
                        escalator = stop.properties.escalator,
                        gid = stop.properties.gid,
                        lastUpdate = stop.properties.lastUpdate,
                        lastUpdateFme = stop.properties.lastUpdateFme,
                        adresse = stop.properties.adresse,
                        localiseFaceAAdresse = stop.properties.localiseFaceAAdresse,
                        commune = stop.properties.commune,
                        insee = stop.properties.insee,
                        zone = stop.properties.zone
                    )
                )
            )
        }

        if (weakLines.isNotEmpty()) {
            val weakDesserte = weakLines.joinToString(", ")
            weakLineStops.add(
                StopFeature(
                    type = stop.type,
                    id = "${stop.id}-weak",
                    geometry = stop.geometry,
                    properties = StopProperties(
                        id = stop.properties.id,
                        nom = stop.properties.nom,
                        desserte = weakDesserte,
                        pmr = stop.properties.pmr,
                        ascenseur = stop.properties.ascenseur,
                        escalator = stop.properties.escalator,
                        gid = stop.properties.gid,
                        lastUpdate = stop.properties.lastUpdate,
                        lastUpdateFme = stop.properties.lastUpdateFme,
                        adresse = stop.properties.adresse,
                        localiseFaceAAdresse = stop.properties.localiseFaceAAdresse,
                        commune = stop.properties.commune,
                        insee = stop.properties.insee,
                        zone = stop.properties.zone
                    )
                )
            )
        }
    }

    val strongStopsByName = strongLineStops.groupBy { normalizeStopName(it.properties.nom) }

    val mergedStrongStops = strongStopsByName.map { (_, stopsGroup) ->
        if (stopsGroup.size == 1) {
            stopsGroup.first()
        } else {
            val mergedDesserte = stopsGroup
                .flatMap { BusIconHelper.getAllLinesForStop(it) }
                .distinct()
                .sorted()
                .joinToString(", ")

            val firstStop = stopsGroup.first()
            val isPmr = stopsGroup.any { it.properties.pmr }

            // Calculate average position (centroid) for all stops with same name
            val avgLon = stopsGroup.map { it.geometry.coordinates[0] }.average()
            val avgLat = stopsGroup.map { it.geometry.coordinates[1] }.average()
            val mergedGeometry = StopGeometry(
                type = "Point",
                coordinates = listOf(avgLon, avgLat)
            )

            StopFeature(
                type = firstStop.type,
                id = firstStop.id,
                geometry = mergedGeometry,
                properties = StopProperties(
                    id = firstStop.properties.id,
                    nom = firstStop.properties.nom,
                    desserte = mergedDesserte,
                    pmr = isPmr,
                    ascenseur = firstStop.properties.ascenseur,
                    escalator = firstStop.properties.escalator,
                    gid = firstStop.properties.gid,
                    lastUpdate = firstStop.properties.lastUpdate,
                    lastUpdateFme = firstStop.properties.lastUpdateFme,
                    adresse = firstStop.properties.adresse,
                    localiseFaceAAdresse = firstStop.properties.localiseFaceAAdresse,
                    commune = firstStop.properties.commune,
                    insee = firstStop.properties.insee,
                    zone = firstStop.properties.zone
                )
            )
        }
    }

    return mergedStrongStops + weakLineStops
}

/**
 * Creates a GeoJSON FeatureCollection string from stops using StringBuilder.
 * This avoids creating thousands of JsonObject/JsonArray instances, reducing
 * GC pressure and allocation time by ~60-70% compared to the Gson approach.
 */
private fun createStopsGeoJsonFromStops(
    stops: List<StopFeature>,
    validIcons: Set<String>
): String {
    val mergedStops = mergeStopsByName(stops)

    // Pre-size StringBuilder: ~300 bytes per feature, ~2 features per stop on average
    val sb = StringBuilder(mergedStops.size * 600)
    sb.append("{\"type\":\"FeatureCollection\",\"features\":[")

    var firstFeature = true

    for (stop in mergedStops) {
        val lineNamesAll = BusIconHelper.getAllLinesForStop(stop)
        if (lineNamesAll.isEmpty()) continue

        val hasTram = lineNamesAll.any { it.uppercase().startsWith("T") }

        val lignesFortes = lineNamesAll.filter { isMetroTramOrFunicular(it) }
        val busLines = lineNamesAll.filter { !isMetroTramOrFunicular(it) }
        val uniqueModes = busLines.mapNotNull { getModeIconForLine(it) }.distinct()

        // Build the list of icons to display
        val iconsToDisplay = ArrayList<Pair<String, Int>>(lignesFortes.size + uniqueModes.size)

        for (lineName in lignesFortes) {
            val upperName = lineName.uppercase()
            val drawableName = BusIconHelper.getDrawableNameForLineName(lineName)
            if (validIcons.contains(drawableName)) {
                val priority = when {
                    isMetroTramOrFunicular(upperName) && !upperName.startsWith("T") -> 2
                    upperName.startsWith("T") -> 1
                    else -> 0
                }
                iconsToDisplay.add(drawableName to priority)
            }
        }

        for (modeIcon in uniqueModes) {
            if (validIcons.contains(modeIcon)) {
                iconsToDisplay.add(modeIcon to 0)
            }
        }

        if (iconsToDisplay.isEmpty()) continue

        val lon = stop.geometry.coordinates[0]
        val lat = stop.geometry.coordinates[1]
        val nom = escapeJsonString(stop.properties.nom)
        val desserte = escapeJsonString(stop.properties.desserte)
        val normalizedNom = stop.properties.nom.filter { it.isLetter() }.lowercase()

        // Pre-build lignes JSON array string and has_line_ properties
        val lignesJsonSb = StringBuilder()
        lignesJsonSb.append("[")
        lineNamesAll.forEachIndexed { i, l ->
            if (i > 0) lignesJsonSb.append(",")
            lignesJsonSb.append("\"").append(escapeJsonString(l)).append("\"")
        }
        lignesJsonSb.append("]")
        val lignesJson = escapeJsonString(lignesJsonSb.toString())

        val hasLineProps = StringBuilder()
        for (line in lineNamesAll) {
            hasLineProps.append(",\"has_line_${line.uppercase()}\":true")
        }

        val n = iconsToDisplay.size
        var slot = -(n - 1)

        for ((iconName, stopPriority) in iconsToDisplay) {
            if (!firstFeature) sb.append(",")
            firstFeature = false

            sb.append("{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[")
            sb.append(lon).append(",").append(lat)
            sb.append("]},\"properties\":{")
            sb.append("\"nom\":\"").append(nom).append("\",")
            sb.append("\"desserte\":\"").append(desserte).append("\",")
            sb.append("\"pmr\":").append(stop.properties.pmr).append(",")
            sb.append("\"type\":\"stop\",")
            sb.append("\"stop_priority\":").append(stopPriority).append(",")
            sb.append("\"has_tram\":").append(hasTram).append(",")
            sb.append("\"icon\":\"").append(iconName).append("\",")
            sb.append("\"slot\":").append(slot).append(",")
            sb.append("\"lignes\":\"").append(lignesJson).append("\",")
            sb.append("\"normalized_nom\":\"").append(normalizedNom).append("\"")
            sb.append(hasLineProps)
            sb.append("}}")

            slot += 2
        }
    }

    sb.append("]}")
    return sb.toString()
}

/**
 * Escapes special characters in a string for safe JSON embedding.
 */
private fun escapeJsonString(s: String): String {
    if (s.isEmpty()) return s
    // Fast path: most strings don't need escaping
    var needsEscape = false
    for (c in s) {
        if (c == '"' || c == '\\' || c == '\n' || c == '\r' || c == '\t') {
            needsEscape = true
            break
        }
    }
    if (!needsEscape) return s

    val sb = StringBuilder(s.length + 8)
    for (c in s) {
        when (c) {
            '"' -> sb.append("\\\"")
            '\\' -> sb.append("\\\\")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            else -> sb.append(c)
        }
    }
    return sb.toString()
}

private var locationCallback: LocationCallback? = null

@Suppress("MissingPermission") // Permission is checked before calling this function
private fun startLocationUpdates(
    fusedLocationClient: FusedLocationProviderClient,
    onLocationUpdate: (LatLng) -> Unit
) {
    try {
        // Create location request for real-time updates
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            1000L // Update every seconds
        ).apply {
            setMinUpdateIntervalMillis(2000L) // Fastest update interval: 2 seconds
            setWaitForAccurateLocation(false)
        }.build()

        // Create location callback
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    onLocationUpdate(LatLng(location.latitude, location.longitude))
                }
            }
        }

        // Start receiving location updates
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback!!,
            Looper.getMainLooper()
        )
    } catch (_: SecurityException) {
        // Permission denied
    }
}

private fun stopLocationUpdates(fusedLocationClient: FusedLocationProviderClient) {
    locationCallback?.let {
        fusedLocationClient.removeLocationUpdates(it)
        locationCallback = null
    }
}
