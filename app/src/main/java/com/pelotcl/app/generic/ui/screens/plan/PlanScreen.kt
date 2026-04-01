package com.pelotcl.app.generic.ui.screens.plan

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.AddLocationAlt
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.pelotcl.app.R
import com.pelotcl.app.generic.data.model.Feature
import com.pelotcl.app.generic.data.model.StopFeature
import com.pelotcl.app.generic.data.model.StopGeometry
import com.pelotcl.app.generic.data.model.StopProperties
import com.pelotcl.app.generic.data.repository.itinerary.JourneyLeg
import com.pelotcl.app.generic.data.repository.itinerary.JourneyResult
import com.pelotcl.app.generic.data.repository.offline.MapStyleRepository
import com.pelotcl.app.generic.data.network.MapStyleData
import com.pelotcl.app.generic.service.TransportServiceProvider
import com.pelotcl.app.generic.ui.components.favorites.AddFavoriteDialog
import com.pelotcl.app.generic.ui.components.MapLibreView
import com.pelotcl.app.generic.ui.components.StationBottomSheet
import com.pelotcl.app.generic.ui.components.StationInfo
import com.pelotcl.app.generic.ui.components.search.StationSearchResult
import com.pelotcl.app.generic.ui.components.search.TransportSearchBar
import com.pelotcl.app.generic.ui.components.search.TransportSearchContent
import com.pelotcl.app.generic.ui.theme.PrimaryColor
import com.pelotcl.app.generic.ui.theme.AccentColor
import com.pelotcl.app.generic.ui.theme.SecondaryColor
import com.pelotcl.app.generic.ui.theme.Stone800
import com.pelotcl.app.generic.ui.theme.Yellow500
import com.pelotcl.app.generic.ui.viewmodel.TransportLinesUiState
import com.pelotcl.app.generic.ui.viewmodel.TransportStopsUiState
import com.pelotcl.app.generic.ui.viewmodel.TransportViewModel
import com.pelotcl.app.utils.transport.BusIconHelper
import com.pelotcl.app.utils.transport.LineColorHelper
import com.pelotcl.app.utils.LocationHelper.startLocationUpdates
import com.pelotcl.app.utils.LocationHelper.stopLocationUpdates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import java.util.Calendar
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.text.iterator

private const val PRIORITY_STOPS_MIN_ZOOM = 12.5f
private const val TRAM_STOPS_MIN_ZOOM = 14.0f
private const val SECONDARY_STOPS_MIN_ZOOM = 17.0f
private const val SELECTED_STOP_MIN_ZOOM = 9.0f
private const val LIVE_MODE_ZOOM_LEVEL =
    12.0f // Zoom level for live tracking mode (below PRIORITY_STOPS_MIN_ZOOM to hide stop icons)

private fun currentTimeInSeconds(): Int {
    val calendar = Calendar.getInstance()
    return calendar.get(Calendar.HOUR_OF_DAY) * 3600 +
            calendar.get(Calendar.MINUTE) * 60 +
            calendar.get(Calendar.SECOND)
}

private fun formatRemainingTime(
    departureTimeSeconds: Int,
    arrivalTimeSeconds: Int,
    nowSeconds: Int
): String {
    val secondsInDay = 24 * 3600
    val fullTripSeconds = if (arrivalTimeSeconds >= departureTimeSeconds) {
        arrivalTimeSeconds - departureTimeSeconds
    } else {
        arrivalTimeSeconds + secondsInDay - departureTimeSeconds
    }

    val elapsedSinceDeparture = if (nowSeconds >= departureTimeSeconds) {
        nowSeconds - departureTimeSeconds
    } else {
        nowSeconds + secondsInDay - departureTimeSeconds
    }

    val remainingSeconds = if (elapsedSinceDeparture in 0..fullTripSeconds) {
        fullTripSeconds - elapsedSinceDeparture
    } else {
        fullTripSeconds
    }

    val remainingMinutes = (remainingSeconds / 60).coerceAtLeast(0)
    return if (remainingMinutes < 60) {
        "$remainingMinutes min"
    } else {
        "${remainingMinutes / 60}h${(remainingMinutes % 60).toString().padStart(2, '0')}"
    }
}

private fun normalizeTimeAroundReference(timeSeconds: Int, referenceSeconds: Int): Int {
    val day = 24 * 3600
    var normalized = timeSeconds
    while (normalized < referenceSeconds - day / 2) normalized += day
    while (normalized > referenceSeconds + day / 2) normalized -= day
    return normalized
}

private fun getCurrentAndNextNavigationLeg(
    journey: JourneyResult,
    nowSeconds: Int
): Pair<JourneyLeg?, JourneyLeg?> {
    val nonWalkingLegs = journey.legs.filterNot { it.isWalking }
    if (nonWalkingLegs.isEmpty()) return null to null

    val reference = journey.departureTime
    val now = normalizeTimeAroundReference(nowSeconds, reference)
    val normalizedLegs = nonWalkingLegs.map { leg ->
        val dep = normalizeTimeAroundReference(leg.departureTime, reference)
        val arr = normalizeTimeAroundReference(leg.arrivalTime, reference)
        dep to arr
    }

    var currentIndex = normalizedLegs.indexOfFirst { (dep, arr) -> now in dep..arr }
    if (currentIndex == -1) {
        currentIndex = normalizedLegs.indexOfFirst { (dep, _) -> now < dep }
    }
    if (currentIndex == -1) {
        currentIndex = nonWalkingLegs.lastIndex
    }

    val currentLeg = nonWalkingLegs.getOrNull(currentIndex)
    val nextLeg = nonWalkingLegs.drop(currentIndex + 1).firstOrNull()
    return currentLeg to nextLeg
}

private fun formatDurationUntil(
    nowNormalizedSeconds: Int,
    targetNormalizedSeconds: Int
): String {
    val remainingSeconds = (targetNormalizedSeconds - nowNormalizedSeconds).coerceAtLeast(0)
    if (remainingSeconds < 60) return "moins d'1 min"

    val remainingMinutes = remainingSeconds / 60
    return if (remainingMinutes < 60) {
        "$remainingMinutes min"
    } else {
        "${remainingMinutes / 60}h${(remainingMinutes % 60).toString().padStart(2, '0')}"
    }
}

private data class LegStopPosition(
    val index: Int,
    val lat: Double,
    val lon: Double
)

private fun computeRemainingStopsOnLeg(
    leg: JourneyLeg,
    userLocation: LatLng?
): Int {
    val stops = ArrayList<LegStopPosition>(leg.intermediateStops.size + 2)
    stops += LegStopPosition(index = 0, lat = leg.fromLat, lon = leg.fromLon)
    leg.intermediateStops.forEachIndexed { stopIndex, stop ->
        stops += LegStopPosition(index = stopIndex + 1, lat = stop.lat, lon = stop.lon)
    }
    val terminusIndex = stops.size
    stops += LegStopPosition(index = terminusIndex, lat = leg.toLat, lon = leg.toLon)

    val nearestStopIndex = userLocation?.let { location ->
        stops
            .filter { isValidJourneyCoordinate(it.lat, it.lon) }
            .minByOrNull { stop ->
                squaredDistance(
                    lat1 = location.latitude,
                    lon1 = location.longitude,
                    lat2 = stop.lat,
                    lon2 = stop.lon
                )
            }?.index
    } ?: 0

    return (terminusIndex - nearestStopIndex).coerceAtLeast(0)
}

private fun findUpcomingNonWalkingLeg(
    journey: JourneyResult,
    currentLeg: JourneyLeg,
    offsetFromCurrent: Int
): JourneyLeg? {
    val nonWalkingLegs = journey.legs.filterNot { it.isWalking }
    val currentIndex = nonWalkingLegs.indexOfFirst { leg ->
        leg.fromStopId == currentLeg.fromStopId &&
                leg.toStopId == currentLeg.toStopId &&
                leg.departureTime == currentLeg.departureTime &&
                leg.arrivalTime == currentLeg.arrivalTime &&
                leg.routeName == currentLeg.routeName
    }
    if (currentIndex == -1) return null
    return nonWalkingLegs.getOrNull(currentIndex + offsetFromCurrent)
}

private fun computeRemainingJourneySeconds(
    journey: JourneyResult,
    nowSeconds: Int
): Int {
    val reference = journey.departureTime
    val nowNormalized = normalizeTimeAroundReference(nowSeconds, reference)
    val arrivalNormalized = normalizeTimeAroundReference(journey.arrivalTime, reference)
    return (arrivalNormalized - nowNormalized).coerceAtLeast(0)
}

private fun isNearestJourneyStopTerminus(
    journey: JourneyResult,
    userLocation: LatLng?
): Boolean {
    if (userLocation == null) return false

    val stops = mutableListOf<LatLng>()
    journey.legs.filterNot { it.isWalking }.forEach { leg ->
        if (isValidJourneyCoordinate(leg.fromLat, leg.fromLon)) {
            stops.add(LatLng(leg.fromLat, leg.fromLon))
        }
        leg.intermediateStops.forEach { stop ->
            if (isValidJourneyCoordinate(stop.lat, stop.lon)) {
                stops.add(LatLng(stop.lat, stop.lon))
            }
        }
        if (isValidJourneyCoordinate(leg.toLat, leg.toLon)) {
            stops.add(LatLng(leg.toLat, leg.toLon))
        }
    }
    if (stops.isEmpty()) return false

    val nearestIndex = stops.indices.minByOrNull { index ->
        squaredDistance(
            lat1 = userLocation.latitude,
            lon1 = userLocation.longitude,
            lat2 = stops[index].latitude,
            lon2 = stops[index].longitude
        )
    } ?: return false

    return nearestIndex == stops.lastIndex
}

private fun isValidJourneyCoordinate(lat: Double, lon: Double): Boolean {
    return lat in -90.0..90.0 && lon in -180.0..180.0 && (lat != 0.0 || lon != 0.0)
}

private fun buildNavigationPathPoints(journey: JourneyResult): List<LatLng> {
    val points = mutableListOf<LatLng>()
    journey.legs.filterNot { it.isWalking }.forEach { leg ->
        if (isValidJourneyCoordinate(leg.fromLat, leg.fromLon)) {
            points.add(LatLng(leg.fromLat, leg.fromLon))
        }
        leg.intermediateStops.forEach { stop ->
            if (isValidJourneyCoordinate(stop.lat, stop.lon)) {
                points.add(LatLng(stop.lat, stop.lon))
            }
        }
        if (isValidJourneyCoordinate(leg.toLat, leg.toLon)) {
            points.add(LatLng(leg.toLat, leg.toLon))
        }
    }
    return points
}

private fun findNavigationAxisSegment(
    userLocation: LatLng,
    pathPoints: List<LatLng>
): Pair<LatLng, LatLng>? {
    if (pathPoints.size < 2) return null

    val nearestIndex = pathPoints.indices.minByOrNull { index ->
        squaredDistance(
            lat1 = userLocation.latitude,
            lon1 = userLocation.longitude,
            lat2 = pathPoints[index].latitude,
            lon2 = pathPoints[index].longitude
        )
    } ?: return null

    val startIndex = if (nearestIndex >= pathPoints.lastIndex) {
        (pathPoints.lastIndex - 1).coerceAtLeast(0)
    } else {
        nearestIndex
    }
    val endIndex = (startIndex + 1).coerceAtMost(pathPoints.lastIndex)
    if (startIndex == endIndex) return null
    return pathPoints[startIndex] to pathPoints[endIndex]
}

private fun computeBearingDegrees(from: LatLng, to: LatLng): Double {
    val fromLat = Math.toRadians(from.latitude)
    val fromLon = Math.toRadians(from.longitude)
    val toLat = Math.toRadians(to.latitude)
    val toLon = Math.toRadians(to.longitude)
    val dLon = toLon - fromLon

    val y = sin(dLon) * cos(toLat)
    val x = cos(fromLat) * sin(toLat) - sin(fromLat) * cos(toLat) * cos(dLon)
    val bearing = Math.toDegrees(atan2(y, x))
    return (bearing + 360.0) % 360.0
}

private fun canonicalLineName(lineName: String): String {
    return when (val upperName = lineName.trim().uppercase()) {
        "NAVI1" -> "NAV1"
        else -> upperName
    }
}

private fun normalizeLineNameForUi(lineName: String): String {
    return if (canonicalLineName(lineName) == "NAV1") "NAVI1" else lineName
}

private fun areEquivalentLineNames(first: String, second: String): Boolean {
    return canonicalLineName(first) == canonicalLineName(second)
}

private fun isNavigoneLine(lineName: String): Boolean {
    val upperName = lineName.trim().uppercase()
    return upperName.startsWith("NAVI") || canonicalLineName(upperName) == "NAV1"
}

private fun isMetroTramOrFunicular(lineName: String): Boolean {
    val upperName = lineName.uppercase()
    return when {
        upperName in setOf("A", "B", "C", "D") -> true
        upperName in setOf("F1", "F2") -> true
        isNavigoneLine(upperName) -> true
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
        isNavigoneLine(upperName) -> false // Navigone
        upperName == "RX" -> false
        upperName.startsWith("T") -> true // tram et trambus
        else -> true // bus
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

// Cache Paint objects by color to avoid repeated allocations during live tracking
private val vehiclePaintCache = HashMap<Int, Paint>(8)

// Track which map layer slots are currently in use (typically 4-10 out of 51 possible)
// Used by filter functions to avoid iterating all (-25..25) slots
private var currentMapSlots: Set<Int> = emptySet()

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

    val circlePaint = vehiclePaintCache.getOrPut(color) {
        Paint().apply {
            this.color = color
            isAntiAlias = true
            style = Paint.Style.FILL
        }
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, circlePaint)

    fun drawCenteredDrawable(drawable: Drawable, maxSize: Int) {
        val intrinsicWidth = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else maxSize
        val intrinsicHeight =
            if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else maxSize
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
    ITINERARY,
    NAVIGATION
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
private fun mapStyleLabel(style: MapStyleData): String {
    return when (style.key) {
        "positron" -> "Clair"
        "dark_matter" -> "Sombre"
        "bright" -> "OSM"
        "liberty" -> "3D"
        "satellite" -> "Satellite"
        else -> style.displayName
    }
}

@Composable
private fun MapStylePreviewTile(
    style: MapStyleData,
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    val imageRes = when (style.key) {
        "positron" -> R.drawable.visu_positron
        "dark_matter" -> R.drawable.visu_dark_matter
        "bright" -> R.drawable.visu_osm_bright
        "liberty" -> R.drawable.visu_liberty
        "satellite" -> R.drawable.visu_satellite
        else -> R.drawable.visu_positron
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
        val halfHeight = height / 2
        val halfWidth = width / 2

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
    selectedMapStyle: MapStyleData,
    onDismiss: () -> Unit,
    onStyleSelected: (MapStyleData) -> Unit
) {
    val mapStyleConfig = TransportServiceProvider.getMapStyleConfig()
    val standardStyles = remember { mapStyleConfig.getStandardMapStyles() }
    val satelliteStyle = remember { mapStyleConfig.getSatelliteMapStyle() }
    val allStyles = remember { standardStyles + satelliteStyle }
    val firstRowStyles = remember { allStyles.take(4) }
    val secondRowStyles = remember { allStyles.drop(4) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SecondaryColor,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Thème",
                color = PrimaryColor
            )
            Spacer(modifier = Modifier.size(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                firstRowStyles.forEach { style ->
                    val enabled = !isOffline || style.key in downloadedMapStyles
                    val isSelected = style.key == selectedMapStyle.key

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
                            color = if (enabled) PrimaryColor else Color(0xFF9CA3AF)
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
                        val isSelected = style.key == selectedMapStyle.key

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
                                color = if (enabled) PrimaryColor else Color(0xFF9CA3AF)
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
    initialUserLocation: LatLng? = null,
    isVisible: Boolean = true,
    onMapStyleChanged: (MapStyleData) -> Unit = {},
    isSearchExpanded: Boolean = false,
    onItineraryModeChanged: (Boolean) -> Unit = {},
    onNavigationModeChanged: (Boolean) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState(initial = TransportLinesUiState.Loading)
    val stopsUiState by viewModel.stopsUiState.collectAsState(initial = TransportStopsUiState.Loading)
    val favoriteStops by viewModel.favoriteStops.collectAsState(initial = emptySet())
    val vehiclePositions by viewModel.vehiclePositions.collectAsState(initial = emptyList())
    val isLiveTrackingEnabled by viewModel.isLiveTrackingEnabled.collectAsState(initial = false)
    val isOffline by viewModel.isOffline.collectAsState(initial = false)
    val isGlobalLiveEnabled by viewModel.isGlobalLiveEnabled.collectAsState(initial = false)
    val globalVehiclePositions by viewModel.globalVehiclePositions.collectAsState(initial = emptyList())
    val headsigns by viewModel.headsigns.collectAsState(initial = emptyMap())
    val availableDirections by viewModel.availableDirections.collectAsState(initial = emptyList())
    val allSchedules by viewModel.allSchedules.collectAsState(initial = emptyList())
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
    val mapStyleRepository = remember { MapStyleRepository(context, TransportServiceProvider.getMapStyleConfig()) }
    val offlineDataInfo by viewModel.offlineDataInfo.collectAsState(initial = com.pelotcl.app.generic.data.offline.OfflineDataInfo())
    var mapStyleUrl by remember { mutableStateOf(mapStyleRepository.getSelectedStyle().styleUrl) }
    var selectedMapStyle by remember {
        mutableStateOf(
            mapStyleRepository.getEffectiveStyle(
                isOffline,
                offlineDataInfo.downloadedMapStyles
            )
        )
    }
    var isMapStyleMenuExpanded by rememberSaveable { mutableStateOf(false) }
    val isDarkMatterStyle = selectedMapStyle.key == "dark_matter"
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

    var sheetContentState by remember { mutableStateOf<SheetContentState?>(null) }

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
    var itineraryNearbyDepartureStops by remember { mutableStateOf<List<String>>(emptyList()) }
    var itineraryJourneys by remember { mutableStateOf<List<JourneyResult>>(emptyList()) }
    var selectedItineraryJourney by remember { mutableStateOf<JourneyResult?>(null) }
    var itineraryResultsVersion by remember { mutableIntStateOf(0) }
    var wasInNavigationMode by remember { mutableStateOf(false) }

    var allSchedulesInfo by remember { mutableStateOf<AllSchedulesInfo?>(null) }

    // Preserve selected direction when navigating to/from schedule details
    var selectedDirection by remember { mutableIntStateOf(0) }
    // One-shot flag to keep an explicit direction chosen from station departures.
    var preserveSelectedDirectionOnce by remember { mutableStateOf(false) }

    var temporaryLoadedBusLines by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showAddFavoriteDialog by remember { mutableStateOf(false) }
    var addFavoriteInitialStopName by remember { mutableStateOf<String?>(null) }

    // Save zoom level before live tracking to restore it when disabled
    var zoomBeforeLiveTracking by remember { mutableStateOf<Double?>(null) }

    val selectedLineNameFromViewModel by viewModel.selectedLineName.collectAsState(initial = null)

    // Track previous sheetContentState to detect transitions
    var previousSheetContentState by remember { mutableStateOf<SheetContentState?>(null) }
    val isSheetExpandedOrExpanding =
        scaffoldSheetState.bottomSheetState.currentValue == SheetValue.Expanded ||
                scaffoldSheetState.bottomSheetState.targetValue == SheetValue.Expanded
    val density = LocalDensity.current
    val navConfiguration = LocalConfiguration.current
    val navHorizontalPaddingPx = with(density) { 24.dp.roundToPx() }
    val navTopPaddingPx = with(density) { (navConfiguration.screenHeightDp.dp * 0.42f).roundToPx() }
    val navBottomPaddingPx = with(density) { (navConfiguration.screenHeightDp.dp * 0.12f).roundToPx() }

    LaunchedEffect(sheetContentState, selectedStation, selectedItineraryJourney) {
        onSheetStateChanged(sheetContentState != null)
        onItineraryModeChanged(
            sheetContentState == SheetContentState.ITINERARY ||
                    sheetContentState == SheetContentState.NAVIGATION
        )
        onNavigationModeChanged(sheetContentState == SheetContentState.NAVIGATION)

        if (sheetContentState == SheetContentState.NAVIGATION) {
            requestedSheetValueForNextContent = null
            scope.launch {
                scaffoldSheetState.bottomSheetState.hide()
            }
            previousSheetContentState = sheetContentState
            return@LaunchedEffect
        }

        val requestedValue = requestedSheetValueForNextContent
        if (requestedValue != null &&
            sheetContentState != null &&
            sheetContentState != previousSheetContentState
        ) {
            scope.launch {
                when (requestedValue) {
                    SheetValue.Expanded -> scaffoldSheetState.bottomSheetState.expand()
                    SheetValue.PartiallyExpanded -> scaffoldSheetState.bottomSheetState.partialExpand()
                    SheetValue.Hidden -> scaffoldSheetState.bottomSheetState.hide()
                }
            }
            return@LaunchedEffect
        }

        // Expand the sheet when selectedItineraryJourney becomes null
        if (selectedItineraryJourney == null && sheetContentState == SheetContentState.ITINERARY) {
            scope.launch {
                scaffoldSheetState.bottomSheetState.expand()
            }
        }

        if (sheetContentState == SheetContentState.STATION &&
            selectedStation != null &&
            previousSheetContentState != SheetContentState.STATION
        ) {
            scope.launch {
                if (previousSheetContentState == SheetContentState.LINE_DETAILS &&
                    isSheetExpandedOrExpanding
                ) {
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
                    selectedLine?.currentStationName?.isNotBlank() == true)
        ) {
            scope.launch {
                if (previousSheetContentState == SheetContentState.STATION &&
                    isSheetExpandedOrExpanding
                ) {
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
            selectedLine?.currentStationName?.isBlank() == true
        ) {
            scope.launch {
                scaffoldSheetState.bottomSheetState.partialExpand()
            }
        }

        if (sheetContentState == SheetContentState.ITINERARY &&
            previousSheetContentState != SheetContentState.ITINERARY
        ) {
            scope.launch {
                // Itinerary opens expanded by default.
                scaffoldSheetState.bottomSheetState.expand()
            }
        }

        // Keep transition history in sync for the next state change.
        previousSheetContentState = sheetContentState
    }

    var itinerarySearchTarget by remember { mutableStateOf<ItineraryFieldTarget?>(null) }
    var itinerarySearchFocusNonce by remember { mutableIntStateOf(0) }

    // Initialize itinerary defaults when opening inline itinerary mode:
    // - arrival = selected stop used to launch itinerary
    // - departure = nearest stop to current user location
    LaunchedEffect(sheetContentState, itineraryInitialStopName) {
        if (sheetContentState != SheetContentState.ITINERARY) return@LaunchedEffect
        // Let bottom-sheet opening animation start before running heavier itinerary initialization.
        kotlinx.coroutines.yield()

        val locationAtOpen = userLocation
        val stopsAtOpen = (stopsUiState as? TransportStopsUiState.Success)?.stops

        if (itineraryArrivalStop == null) {
            val arrivalName = itineraryInitialStopName?.takeIf { it.isNotBlank() }
            if (arrivalName != null) {
                val ids = viewModel.raptorRepository.resolveStopIdsByName(arrivalName)
                if (ids.isNotEmpty()) {
                    itineraryArrivalStop = SelectedStop(name = arrivalName, stopIds = ids)
                }
            }
        }

        if (itineraryDepartureStop == null) {
            if (locationAtOpen != null) {
                val nearestStops = viewModel.raptorRepository.findNearestStops(
                    latitude = locationAtOpen.latitude,
                    longitude = locationAtOpen.longitude,
                    limit = 5
                )
                val nearestStopNames = nearestStops.map { it.name }.distinct()
                itineraryNearbyDepartureStops = nearestStopNames

                val nearestStopName = nearestStopNames.firstOrNull()
                    ?: stopsAtOpen?.let { findNearestStopName(locationAtOpen, it) }
                if (!nearestStopName.isNullOrBlank()) {
                    val ids = viewModel.raptorRepository.resolveStopIdsByName(nearestStopName)
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
            scaffoldSheetState.bottomSheetState.currentValue != SheetValue.Hidden
        ) {
            scaffoldSheetState.bottomSheetState.hide()
        }
    }

    var previousSheetValue by remember { mutableStateOf<SheetValue?>(null) }
    LaunchedEffect(scaffoldSheetState.bottomSheetState.currentValue) {
        val current = scaffoldSheetState.bottomSheetState.currentValue
        val previous = previousSheetValue

        if (current != previous) {
            val justBecameHidden =
                previous != null && previous != SheetValue.Hidden && current == SheetValue.Hidden

            if (justBecameHidden && sheetContentState != SheetContentState.NAVIGATION) {
                sheetContentState = null
                selectedStation = null
                selectedLine = null
            }

            previousSheetValue = current
        }
    }

    // Additional effect to handle sheet dismissal by swipe or other means
    LaunchedEffect(scaffoldSheetState.bottomSheetState.isVisible) {
        if (
            !scaffoldSheetState.bottomSheetState.isVisible &&
            sheetContentState != null &&
            sheetContentState != SheetContentState.NAVIGATION
        ) {
            sheetContentState = null
            selectedStation = null
            selectedLine = null
        }
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
                    propsObj.addProperty("ligne", lineFeature.properties.lineName)
                    propsObj.addProperty("nom_trace", lineFeature.properties.traceName)
                    propsObj.addProperty("couleur", LineColorHelper.getColorForLine(lineFeature))
                    // Determine line width property based on type
                    val upperName = lineFeature.properties.lineName.uppercase()
                    val width = when {
                        lineFeature.properties.transportType == "BAT" || isNavigoneLine(upperName) -> 2f
                        lineFeature.properties.transportType == "TRA" || lineFeature.properties.transportType == "TRAM" || upperName.startsWith(
                            "TB"
                        ) -> 2f

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
                val oldLayerId = "layer-${feature.properties.lineName}-${feature.properties.traceCode}"
                val oldSourceId = "line-${feature.properties.lineName}-${feature.properties.traceCode}"
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

            val targetStop = searchSelectedStop.stopId?.let { selectedId ->
                allStops.find { it.properties.id == selectedId }
            } ?: allStops.find {
                it.properties.nom.equals(searchSelectedStop.stopName, ignoreCase = true)
            }

            if (targetStop != null) {
                val lines = BusIconHelper.getAllLinesForStop(targetStop)
                val stationInfo = StationInfo(
                    nom = targetStop.properties.nom,
                    lignes = lines,
                    desserte = targetStop.properties.desserte,
                    stopIds = listOf(targetStop.properties.id)
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
        if (!itinerarySelectedStopName.isNullOrBlank()) {
            itineraryDepartureStop = null
            itineraryDepartureQuery = ""
            itineraryNearbyDepartureStops = emptyList()
            itineraryInitialStopName = itinerarySelectedStopName
            itineraryArrivalQuery = itinerarySelectedStopName
            sheetContentState = SheetContentState.ITINERARY
            itineraryArrivalStop =
                SelectedStop(name = itinerarySelectedStopName, stopIds = emptyList())
            val ids = viewModel.raptorRepository.resolveStopIdsByName(itinerarySelectedStopName)
            if (itineraryInitialStopName == itinerarySelectedStopName) {
                itineraryArrivalStop =
                    SelectedStop(name = itinerarySelectedStopName, stopIds = ids)
            }
            onItinerarySelectionHandled()
        }
    }

    // Handle selection from stop options (target) - mimic map click behavior
    LaunchedEffect(optionsSelectedStop, stopsUiState, mapInstance) {
        if (optionsSelectedStop != null && mapInstance != null && stopsUiState is TransportStopsUiState.Success) {
            val allStops = (stopsUiState as TransportStopsUiState.Success).stops

            val targetStop = optionsSelectedStop.stopId?.let { selectedId ->
                allStops.find { it.properties.id == selectedId }
            } ?: allStops.find {
                it.properties.nom.equals(optionsSelectedStop.stopName, ignoreCase = true)
            }

            if (targetStop != null) {
                val lines = BusIconHelper.getAllLinesForStop(targetStop)
                val stationInfo = StationInfo(
                    nom = targetStop.properties.nom,
                    lignes = lines,
                    desserte = targetStop.properties.desserte,
                    stopIds = listOf(targetStop.properties.id)
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
        if (!preserveSelectedDirectionOnce) {
            selectedDirection = 0
        }
    }

    LaunchedEffect(sheetContentState) {
        if (sheetContentState != SheetContentState.LINE_DETAILS && sheetContentState != SheetContentState.ALL_SCHEDULES && temporaryLoadedBusLines.isNotEmpty()) {
            temporaryLoadedBusLines.forEach { busLine ->
                viewModel.removeLineFromLoaded(busLine)
            }
            temporaryLoadedBusLines = emptySet()
        }

        if (sheetContentState == SheetContentState.NAVIGATION) {
            scaffoldSheetState.bottomSheetState.hide()
        }

        if (
            sheetContentState != SheetContentState.ITINERARY &&
            sheetContentState != SheetContentState.NAVIGATION
        ) {
            itineraryJourneys = emptyList()
            selectedItineraryJourney = null
        }
    }

    LaunchedEffect(selectedItineraryJourney) {
        if (selectedItineraryJourney != null) {
            itinerarySearchTarget = null
        }
    }

    LaunchedEffect(
        mapInstance,
        mapStyleVersion,
        sheetContentState,
        itineraryJourneys,
        selectedItineraryJourney,
        isMapStyleMenuExpanded
    ) {
        if (isMapStyleMenuExpanded) return@LaunchedEffect
        val map = mapInstance ?: return@LaunchedEffect

        if (
            sheetContentState != SheetContentState.ITINERARY &&
            sheetContentState != SheetContentState.NAVIGATION
        ) {
            map.getStyle { style ->
                clearItineraryLayers(style)
            }
            return@LaunchedEffect
        }

        hideMapLines(map)
        val journeysToDraw = when (sheetContentState) {
            SheetContentState.NAVIGATION -> {
                selectedItineraryJourney?.let { listOf(it) } ?: emptyList()
            }
            else -> itineraryJourneys
        }
        drawItinerariesOnMap(
            map = map,
            journeys = journeysToDraw,
            selectedJourney = selectedItineraryJourney,
            viewModel = viewModel
        )
    }

    LaunchedEffect(
        mapInstance,
        sheetContentState,
        itineraryResultsVersion,
        isMapStyleMenuExpanded
    ) {
        if (isMapStyleMenuExpanded) return@LaunchedEffect
        if (
            sheetContentState != SheetContentState.ITINERARY &&
            sheetContentState != SheetContentState.NAVIGATION
        ) return@LaunchedEffect
        val map = mapInstance ?: return@LaunchedEffect
        val journeysToZoom = when (sheetContentState) {
            SheetContentState.NAVIGATION -> {
                selectedItineraryJourney?.let { listOf(it) } ?: emptyList()
            }
            else -> itineraryJourneys
        }
        if (journeysToZoom.isEmpty()) return@LaunchedEffect

        zoomToItineraries(map, journeysToZoom)
    }

    LaunchedEffect(mapInstance, sheetContentState, userLocation, selectedItineraryJourney) {
        val isInNavigationMode = sheetContentState == SheetContentState.NAVIGATION
        val map = mapInstance

        if (map == null) {
            wasInNavigationMode = isInNavigationMode
            return@LaunchedEffect
        }

        if (isInNavigationMode) {
            val currentUserLocation = userLocation
            val target = currentUserLocation ?: map.cameraPosition.target
            val journey = selectedItineraryJourney
            val pathPoints = journey?.let { buildNavigationPathPoints(it) }.orEmpty()
            val axisSegment = if (currentUserLocation != null) {
                findNavigationAxisSegment(currentUserLocation, pathPoints)
            } else {
                null
            }
            val bearing = if (axisSegment != null) {
                computeBearingDegrees(axisSegment.first, axisSegment.second)
            } else {
                map.cameraPosition.bearing
            }

            map.setPadding(
                navHorizontalPaddingPx,
                navTopPaddingPx,
                navHorizontalPaddingPx,
                navBottomPaddingPx
            )

            val navigationCamera = CameraPosition.Builder(map.cameraPosition)
                .target(target)
                .zoom(maxOf(map.cameraPosition.zoom, 17.5))
                .tilt(60.0)
                .bearing(bearing)
                .build()

            map.animateCamera(
                CameraUpdateFactory.newCameraPosition(navigationCamera),
                1000
            )
        } else if (!isInNavigationMode && wasInNavigationMode) {
            map.setPadding(0, 0, 0, 0)
            val resetCamera = CameraPosition.Builder(map.cameraPosition)
                .tilt(0.0)
                .bearing(0.0)
                .build()

            // Force immediate reset to true north + 2D when exiting navigation.
            map.moveCamera(CameraUpdateFactory.newCameraPosition(resetCamera))
        }

        wasInNavigationMode = isInNavigationMode
    }

    // Keep LIVE mode active while switching between global and per-line context.
    LaunchedEffect(
        selectedLine?.lineName,
        sheetContentState,
        isLiveTrackingEnabled,
        isGlobalLiveEnabled,
        isOffline
    ) {
        if (isOffline) return@LaunchedEffect

        val isLiveModeEnabled = isLiveTrackingEnabled || isGlobalLiveEnabled
        if (!isLiveModeEnabled) return@LaunchedEffect

        val isLineContext =
            sheetContentState == SheetContentState.LINE_DETAILS || sheetContentState == SheetContentState.ALL_SCHEDULES
        val selectedTrackableLine =
            selectedLine?.lineName?.takeIf { isLineContext && isLiveTrackableLine(it) }
        val selectedNotTrackableLine =
            selectedLine?.lineName?.takeIf { isLineContext && !isLiveTrackableLine(it) }

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
    LaunchedEffect(
        vehiclePositions,
        mapInstance,
        selectedLine,
        mapStyleVersion,
        isMapStyleMenuExpanded
    ) {
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
            val iconName = "vehicle-marker-line-${markerType.name.lowercase()}-${
                Integer.toHexString(markerColor)
            }"
            ensureVehicleMarkerImage(
                mapStyle = style,
                context = context,
                iconName = iconName,
                color = markerColor,
                markerType = markerType,
                size = 72
            )

            // Add symbol layer with bus marker
            val symbolLayer =
                SymbolLayer("vehicle-positions-layer", "vehicle-positions-source").apply {
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
                        val name = "global-vehicle-marker-${markerType.name.lowercase()}-${
                            Integer.toHexString(lineColor)
                        }"
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

            val symbolLayer = SymbolLayer(
                "global-vehicle-positions-layer",
                "global-vehicle-positions-source"
            ).apply {
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
                    val hasSelectedInState =
                        lines.any { areEquivalentLineNames(it.properties.lineName, selectedName) }

                    if (!hasSelectedInState && isMetroTramOrFunicular(selectedName)) {
                        viewModel.reloadStrongLines()
                    }

                    filterMapLines(map, lines, currentSelectedLine.lineName)

                    val selectedStopName =
                        currentSelectedLine.currentStationName.takeIf { it.isNotBlank() }
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
                } else if (
                    currentSheetState == SheetContentState.ITINERARY ||
                    currentSheetState == SheetContentState.NAVIGATION
                ) {
                    hideMapLines(map)
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
    val itinerarySearchReserved = remember(sheetContentState, selectedItineraryJourney) {
        if (sheetContentState == SheetContentState.ITINERARY && selectedItineraryJourney == null) {
            itinerarySearchOverlayHeight
        } else {
            0.dp
        }
    }
    val itinerarySheetMaxHeight =
        (configuration.screenHeightDp.dp - itinerarySearchReserved - bottomPadding - itinerarySheetSafetyOffset)
            .coerceAtLeast(280.dp)

    // Handle back button press - close sheets/selections before exiting app
    BackHandler(enabled = sheetContentState != null || selectedLine != null || selectedStation != null || itinerarySearchTarget != null) {
        when {
            itinerarySearchTarget != null -> {
                // If search overlay is active, dismiss it first
                itinerarySearchTarget = null
            }
            sheetContentState == SheetContentState.ALL_SCHEDULES -> {
                requestedSheetValueForNextContent = if (isSheetExpandedOrExpanding) {
                    SheetValue.Expanded
                } else {
                    SheetValue.PartiallyExpanded
                }
                allSchedulesInfo = null
                sheetContentState = SheetContentState.LINE_DETAILS
            }

            sheetContentState == SheetContentState.ITINERARY -> {
                sheetContentState = null
                itineraryInitialStopName = null
                itineraryDepartureStop = null
                itineraryArrivalStop = null
                itineraryDepartureQuery = ""
                itineraryArrivalQuery = ""
            }
            sheetContentState == SheetContentState.NAVIGATION -> {
                requestedSheetValueForNextContent = SheetValue.Expanded
                sheetContentState = SheetContentState.ITINERARY
            }
            // If viewing line details, go back to station (if came from station) or close
            sheetContentState == SheetContentState.LINE_DETAILS -> {
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
            sheetContentState == SheetContentState.STATION -> {
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

    val stationCollapsedPeekHeight = bottomPadding + 300.dp
    val itineraryCollapsedPeekHeight = bottomPadding + 100.dp
    val peekHeight = when (sheetContentState) {
        SheetContentState.LINE_DETAILS -> stationCollapsedPeekHeight
        SheetContentState.ALL_SCHEDULES -> stationCollapsedPeekHeight
        SheetContentState.STATION -> stationCollapsedPeekHeight
        SheetContentState.ITINERARY -> itineraryCollapsedPeekHeight
        SheetContentState.NAVIGATION -> 0.dp
        else -> 0.dp
    }
    val unifiedSheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

    BottomSheetScaffold(
        scaffoldState = scaffoldSheetState,
        sheetPeekHeight = peekHeight,
        sheetShape = unifiedSheetShape,
        modifier = modifier,
        sheetContainerColor = SecondaryColor,
        sheetContent = {
            if (sheetContentState == SheetContentState.NAVIGATION) {
                Spacer(modifier = Modifier.height(0.dp))
            } else {
                Column(
                    modifier = Modifier
                        .padding(bottom = bottomPadding)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (sheetContentState) {
                        SheetContentState.LINE_DETAILS -> {
                            if (selectedLine != null) {
                                LineDetailsSheetContent(
                                    lineInfo = selectedLine!!,
                                    viewModel = viewModel,
                                    selectedDirection = selectedDirection,
                                    onDirectionChange = { newDirection ->
                                        selectedDirection = newDirection
                                    },
                                    onBackToStation = {
                                        selectedLine?.let { lineInfo ->
                                            val lineName = lineInfo.lineName
                                            if (!isMetroTramOrFunicular(lineName)) {
                                                viewModel.removeLineFromLoaded(lineName)
                                            }
                                        }

                                        if (selectedStation != null) {
                                            requestedSheetValueForNextContent =
                                                if (isSheetExpandedOrExpanding) {
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
                                                    temporaryLoadedBusLines =
                                                        temporaryLoadedBusLines + lineName
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

                                        // Preserve current direction when navigating to another stop
                                        // from the line details stops list.
                                        preserveSelectedDirectionOnce = true

                                        // Keep station state aligned with the last stop selected from line details,
                                        // so Back returns to this stop instead of the initial one.
                                        val matchingStop =
                                            (stopsUiState as? TransportStopsUiState.Success)
                                                ?.stops
                                                ?.find {
                                                    it.properties.nom.equals(
                                                        stopName,
                                                        ignoreCase = true
                                                    )
                                                }
                                        selectedStation = if (matchingStop != null) {
                                            StationInfo(
                                                nom = matchingStop.properties.nom,
                                                lignes = BusIconHelper.getAllLinesForStop(matchingStop),
                                                desserte = matchingStop.properties.desserte,
                                                stopIds = listOf(matchingStop.properties.id)
                                            )
                                        } else {
                                            StationInfo(
                                                nom = stopName,
                                                lignes = selectedStation?.lignes ?: emptyList(),
                                                desserte = selectedStation?.desserte ?: "",
                                                stopIds = selectedStation?.stopIds ?: emptyList()
                                            )
                                        }

                                        selectedLine = LineInfo(
                                            lineName = selectedLine!!.lineName,
                                            currentStationName = selectedStation?.nom ?: stopName
                                        )
                                        scope.launch {
                                            scaffoldSheetState.bottomSheetState.partialExpand()
                                        }
                                    },
                                    onShowAllSchedules = { lineName, directionName, schedules ->
                                        requestedSheetValueForNextContent =
                                            if (isSheetExpandedOrExpanding) {
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
                                        requestedSheetValueForNextContent = SheetValue.Expanded
                                        itineraryDepartureStop = null
                                        itineraryDepartureQuery = ""
                                        itineraryNearbyDepartureStops = emptyList()
                                        itineraryInitialStopName = stopName
                                        itineraryArrivalQuery = stopName
                                        itineraryArrivalStop = SelectedStop(
                                            name = stopName,
                                            stopIds = emptyList()
                                        )
                                        sheetContentState = SheetContentState.ITINERARY
                                        scope.launch {
                                            val ids =
                                                viewModel.raptorRepository.resolveStopIdsByName(stopName)
                                            if (itineraryInitialStopName == stopName) {
                                                itineraryArrivalStop = SelectedStop(
                                                    name = stopName,
                                                    stopIds = ids)
                                            }
                                        }
                                    },
                                    onHeaderClick = {
                                        scope.launch {
                                            scaffoldSheetState.bottomSheetState.expand()
                                        }
                                    },
                                    favoriteStops = favoriteStops,
                                    onToggleFavoriteStop = { viewModel.toggleFavoriteStop(it) },
                                    onHeaderLineCountChanged = { _ -> }
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

                                        preserveSelectedDirectionOnce = true
                                        selectedDirection = directionId

                                        selectedLine = LineInfo(
                                            lineName = lineName,
                                            currentStationName = selectedStation?.nom ?: ""
                                        )

                                        if (!isMetroTramOrFunicular(lineName)) {
                                            scope.launch {
                                                viewModel.addLineToLoaded(lineName)
                                                if (isTemporaryBus(lineName)) {
                                                    temporaryLoadedBusLines =
                                                        temporaryLoadedBusLines + lineName
                                                }
                                                delay(100)
                                                sheetContentState = SheetContentState.LINE_DETAILS
                                            }
                                        } else {
                                            sheetContentState = SheetContentState.LINE_DETAILS
                                        }
                                    },
                                    isFavoriteStop = favoriteStops.any {
                                        it.equals(
                                            selectedStation!!.nom,
                                            ignoreCase = true
                                        )
                                    },
                                    onToggleFavoriteStop = {
                                        viewModel.toggleFavoriteStop(
                                            selectedStation!!.nom
                                        )
                                    },
                                    onAddFavoriteClick = { stopName ->
                                        addFavoriteInitialStopName = stopName
                                        showAddFavoriteDialog = true
                                        requestedSheetValueForNextContent = null
                                        selectedLine = null
                                        selectedStation = null
                                        sheetContentState = null
                                        scope.launch {
                                            scaffoldSheetState.bottomSheetState.hide()
                                        }
                                    },
                                    onItineraryClick = { stopName ->
                                        requestedSheetValueForNextContent = SheetValue.Expanded
                                        itineraryDepartureStop = null
                                        itineraryDepartureQuery = ""
                                        itineraryNearbyDepartureStops = emptyList()
                                        itineraryInitialStopName = stopName
                                        itineraryArrivalQuery = stopName
                                        itineraryArrivalStop = SelectedStop(
                                            name = stopName,
                                            stopIds = emptyList()
                                        )
                                        sheetContentState = SheetContentState.ITINERARY
                                        scope.launch {
                                            val ids =
                                                viewModel.raptorRepository.resolveStopIdsByName(stopName)
                                            if (itineraryInitialStopName == stopName) {
                                                itineraryArrivalStop = SelectedStop(
                                                    name = stopName,
                                                    stopIds = ids)
                                            }
                                        }
                                    }
                                )
                            }
                        }

                        SheetContentState.ALL_SCHEDULES -> {
                            if (allSchedulesInfo != null) {
                                val schedulesForCurrentDirection =
                                    allSchedules.ifEmpty { allSchedulesInfo!!.schedules }
                                val resolvedAllSchedulesInfo = allSchedulesInfo!!.copy(
                                    directionName = headsigns[selectedDirection]
                                        ?: allSchedulesInfo!!.directionName,
                                    schedules = schedulesForCurrentDirection
                                )
                                val allSchedulesDirections =
                                    allSchedulesInfo!!.availableDirections.ifEmpty {
                                        availableDirections
                                    }
                                val allSchedulesHeadsigns = allSchedulesInfo!!.headsigns.ifEmpty {
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
                                        selectedLine?.currentStationName?.takeIf { it.isNotBlank() }
                                            ?.let { stopName ->
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
                                        requestedSheetValueForNextContent =
                                            if (isSheetExpandedOrExpanding) {
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
                                onJourneysChanged = { journeys ->
                                    itineraryJourneys = journeys
                                    itineraryResultsVersion++
                                },
                                onSelectedJourneyChanged = { journey ->
                                    selectedItineraryJourney = journey
                                },
                                onStartNavigation = { journey ->
                                    selectedItineraryJourney = journey
                                    requestedSheetValueForNextContent = null
                                    sheetContentState = SheetContentState.NAVIGATION
                                    scope.launch {
                                        scaffoldSheetState.bottomSheetState.hide()
                                    }
                                },
                                onClose = {
                                    scope.launch {
                                        scaffoldSheetState.bottomSheetState.hide()
                                    }
                                    itineraryInitialStopName = null
                                    itineraryDepartureStop = null
                                    itineraryArrivalStop = null
                                    itineraryDepartureQuery = ""
                                    itineraryArrivalQuery = ""
                                    itineraryNearbyDepartureStops = emptyList()
                                    sheetContentState = null
                                },
                                onRequestExpandSheet = {
                                    requestedSheetValueForNextContent = SheetValue.Expanded
                                }
                            )
                        }

                        SheetContentState.NAVIGATION -> {}
                        null -> {}
                    }
                }
            }
        }
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            val isNavigationMode = sheetContentState == SheetContentState.NAVIGATION
            val navigationNowSeconds by produceState(
                initialValue = currentTimeInSeconds(),
                key1 = isNavigationMode,
                key2 = selectedItineraryJourney
            ) {
                value = currentTimeInSeconds()
                while (isNavigationMode && selectedItineraryJourney != null) {
                    delay(30_000)
                    value = currentTimeInSeconds()
                }
            }

            LaunchedEffect(
                isNavigationMode,
                selectedItineraryJourney,
                navigationNowSeconds,
                userLocation
            ) {
                if (!isNavigationMode) return@LaunchedEffect
                val journey = selectedItineraryJourney ?: return@LaunchedEffect
                val remainingSeconds = computeRemainingJourneySeconds(journey, navigationNowSeconds)
                val atTerminus = isNearestJourneyStopTerminus(journey, userLocation)
                if (remainingSeconds < 60 && atTerminus) {
                    requestedSheetValueForNextContent = SheetValue.Expanded
                    sheetContentState = SheetContentState.ITINERARY
                }
            }

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
                centerOnUserLocation = shouldCenterOnUser,
                isInteractive = true
            )

            if (
                !isNavigationMode &&
                (uiState is TransportLinesUiState.Loading || stopsUiState is TransportStopsUiState.Loading)
            ) {
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
                visible = !isNavigationMode && userLocation != null && !isCenteredOnUser,
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
                    containerColor = PrimaryColor,
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
                            color = SecondaryColor,
                            radius = size.minDimension / 2.5f,
                            style = Stroke(width = 7f)
                        )
                    }
                }
            }

            if (isNavigationMode) {
                val currentJourney = selectedItineraryJourney
                val (currentLeg, nextLeg) = currentJourney?.let {
                    getCurrentAndNextNavigationLeg(it, navigationNowSeconds)
                } ?: (null to null)
                val shouldChangeLineInMainCard =
                    if (currentJourney != null && currentLeg != null && nextLeg != null) {
                        val reference = currentJourney.departureTime
                        val nowNormalized =
                            normalizeTimeAroundReference(navigationNowSeconds, reference)
                        val legDepartureNormalized =
                            normalizeTimeAroundReference(currentLeg.departureTime, reference)
                        nowNormalized >= legDepartureNormalized
                    } else {
                        false
                    }
                val upcomingLeg =
                    if (currentJourney != null && currentLeg != null) {
                        val offset = if (shouldChangeLineInMainCard) 2 else 1
                        findUpcomingNonWalkingLeg(
                            journey = currentJourney,
                            currentLeg = currentLeg,
                            offsetFromCurrent = offset
                        )
                    } else {
                        null
                    }
                val topMainShape = if (upcomingLeg != null) {
                    RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 0.dp, bottomEnd = 20.dp)
                } else {
                    RoundedCornerShape(20.dp)
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, top = 32.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(132.dp)
                            .clip(topMainShape)
                            .background(PrimaryColor)
                    ) {
                        if (currentJourney != null && currentLeg != null) {
                            val reference = currentJourney.departureTime
                            val nowNormalized =
                                normalizeTimeAroundReference(navigationNowSeconds, reference)
                            val legDepartureNormalized =
                                normalizeTimeAroundReference(currentLeg.departureTime, reference)
                            val isWaitingForVehicle = nowNormalized < legDepartureNormalized
                            val hasCorrespondence = nextLeg != null
                            val shouldChangeLine = !isWaitingForVehicle && hasCorrespondence
                            val displayedLeg =
                                if (shouldChangeLine && hasCorrespondence) nextLeg else currentLeg
                            val routeName = displayedLeg.routeName ?: ""
                            val iconRes = BusIconHelper.getResourceIdForLine(context, routeName)
                            val fallbackColor =
                                Color(LineColorHelper.getColorForLineString(routeName))
                            val directionValue =
                                displayedLeg.direction?.takeIf { it.isNotBlank() } ?: "?"
                            val directionText = "Direction $directionValue"
                            val actionText = if (isWaitingForVehicle) {
                                val remainingBeforeDeparture = formatDurationUntil(
                                    nowNormalizedSeconds = nowNormalized,
                                    targetNormalizedSeconds = legDepartureNormalized
                                )
                                "Dans $remainingBeforeDeparture, monter à ${currentLeg.fromStopName}"
                            } else {
                                val remainingStops = computeRemainingStopsOnLeg(
                                    leg = currentLeg,
                                    userLocation = userLocation
                                )
                                val targetStopName = currentLeg.toStopName.ifBlank { "l'arrêt suivant" }
                                val actionVerb =
                                    if (shouldChangeLine) {
                                        "changer de ligne à $targetStopName"
                                    } else {
                                        "descendre à $targetStopName"
                                    }
                                if (remainingStops <= 0) {
                                    "Au prochain arrêt, $actionVerb"
                                } else {
                                    val stopWord = if (remainingStops == 1) "arrêt" else "arrêts"
                                    "Dans $remainingStops $stopWord, $actionVerb"
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (iconRes != 0) {
                                    Image(
                                        painter = painterResource(id = iconRes),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .padding(horizontal = 4.dp)
                                            .size(44.dp)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .padding(horizontal = 4.dp)
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(fallbackColor),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = routeName.ifBlank { "?" }.take(3),
                                            color = SecondaryColor,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Column(
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = directionText,
                                        color = Color(0xFF9CA3AF),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        text = actionText,
                                        color = SecondaryColor,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                    if (upcomingLeg != null) {
                        val nextRouteName = upcomingLeg.routeName ?: ""
                        val nextIconRes = BusIconHelper.getResourceIdForLine(context, nextRouteName)
                        val nextFallbackColor =
                            Color(LineColorHelper.getColorForLineString(nextRouteName))
                        Box(
                            modifier = Modifier
                                .wrapContentWidth()
                                .wrapContentHeight()
                                .clip(RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp))
                                .background(Stone800)
                        ) {
                            Row(
                                modifier = Modifier
                                    .wrapContentWidth()
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Start
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "A suivre",
                                        fontSize = 16.sp,
                                        color = SecondaryColor,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    if (nextIconRes != 0) {
                                        Image(
                                            painter = painterResource(id = nextIconRes),
                                            contentDescription = null,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .clip(CircleShape)
                                                .background(nextFallbackColor),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = nextRouteName.ifBlank { "?" }.take(2),
                                                color = SecondaryColor,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .offset(y = bottomPadding)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(108.dp)
                            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                            .background(PrimaryColor)
                            .padding(bottom = 12.dp)
                    ) {
                        selectedItineraryJourney?.let { journey ->
                            Column(
                                modifier = Modifier.align(Alignment.Center),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = formatRemainingTime(
                                        departureTimeSeconds = journey.departureTime,
                                        arrivalTimeSeconds = journey.arrivalTime,
                                        nowSeconds = navigationNowSeconds
                                    ),
                                    color = SecondaryColor,
                                    style = MaterialTheme.typography.headlineMedium
                                )
                                Text(
                                    text = journey.formatArrivalTime(),
                                    color = Color(0xFF9CA3AF),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Normal
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Retour",
                            tint = SecondaryColor,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 20.dp)
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Stone800)
                                .clickable {
                                    requestedSheetValueForNextContent = SheetValue.Expanded
                                    sheetContentState = SheetContentState.ITINERARY
                                }
                                .padding(8.dp)
                        )
                        Icon(
                            painter = painterResource(id = R.drawable.add_triangle_24px),
                            contentDescription = null,
                            tint = Yellow500,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 20.dp)
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Stone800)
                                .padding(10.dp)
                        )
                    }
                }
            }

            // Unified LIVE button (global when no selected bus line, line-specific otherwise)
            val isLineContext =
                sheetContentState == SheetContentState.LINE_DETAILS || sheetContentState == SheetContentState.ALL_SCHEDULES
            // When a sheet is open, place controls where favorites row usually sits.
            val controlsTopPadding = if (sheetContentState != null) 100.dp else 146.dp
            val selectedTrackableLineName =
                selectedLine?.lineName?.takeIf { isLineContext && isLiveTrackableLine(it) }
            val hasSelectedNotTrackableLine =
                selectedLine?.lineName?.let { isLineContext && !isLiveTrackableLine(it) } == true
            val showLiveButton = !isOffline && !hasSelectedNotTrackableLine

            if (sheetContentState != SheetContentState.ITINERARY && !isNavigationMode) {
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
                        border = if (isDarkMatterStyle && !isSearchExpanded) BorderStroke(
                            1.dp,
                            Color.Gray
                        ) else null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryColor
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
                            tint = SecondaryColor,
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
                            else -> PrimaryColor // Black when inactive
                        }
                        val showLiveBorder =
                            isDarkMatterStyle && buttonColor == PrimaryColor && !isSearchExpanded
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
                                    drawCircle(color = SecondaryColor)
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "LIVE",
                                    fontWeight = FontWeight.Bold,
                                    color = SecondaryColor
                                )
                            }
                        }
                    }
                }
            }

            if (sheetContentState == SheetContentState.ITINERARY && selectedItineraryJourney == null) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(top = 8.dp, start = 10.dp, end = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            ItinerarySearchBarField(
                                selectedStop = itineraryDepartureStop,
                                onClick = {
                                    itinerarySearchTarget = ItineraryFieldTarget.DEPARTURE
                                    // If a stop is selected, always reopen with its full name.
                                    // This prevents reverting to a previous partial query after choosing a result.
                                    itineraryDepartureQuery = itineraryDepartureStop?.name
                                        ?: itineraryDepartureQuery.ifBlank { "" }
                                    itinerarySearchFocusNonce++
                                },
                                icon = Icons.Default.MyLocation,
                                placeholder = "Arret de depart"
                            )

                            ItinerarySearchBarField(
                                modifier = Modifier.offset(y = (-18).dp),
                                selectedStop = itineraryArrivalStop,
                                onClick = {
                                    itinerarySearchTarget = ItineraryFieldTarget.ARRIVAL
                                    // If a stop is selected, always reopen with its full name.
                                    // This prevents reverting to a previous partial query after choosing a result.
                                    itineraryArrivalQuery = itineraryArrivalStop?.name
                                        ?: itineraryArrivalQuery.ifBlank { "" }
                                    itinerarySearchFocusNonce++
                                },
                                icon = Icons.Default.Search,
                                placeholder = "Arret d'arrivee"
                            )
                        }

                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .offset(y = 10.dp)
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(SecondaryColor)
                                .clickable {
                                    val previousDeparture = itineraryDepartureStop
                                    itineraryDepartureStop = itineraryArrivalStop
                                    itineraryArrivalStop = previousDeparture
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapVert,
                                contentDescription = "Inverser",
                                tint = PrimaryColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

        }
    }

    if (sheetContentState == SheetContentState.ITINERARY && itinerarySearchTarget != null) {
        val isDepartureSearch = itinerarySearchTarget == ItineraryFieldTarget.DEPARTURE
        val overlayQuery = if (isDepartureSearch) itineraryDepartureQuery else itineraryArrivalQuery

        TransportSearchBar(
            viewModel = viewModel,
            modifier = Modifier.fillMaxSize(),
            content = TransportSearchContent.STOPS_ONLY,
            showHistory = false,
            startExpanded = true,
            showDarkOutline = false,
            searchPlaceholder = if (isDepartureSearch) {
                "Rechercher un depart"
            } else {
                "Rechercher une arrivee"
            },
            query = overlayQuery,
            onQueryChange = { newValue ->
                if (isDepartureSearch) {
                    itineraryDepartureQuery = newValue
                } else {
                    itineraryArrivalQuery = newValue
                }
            },
            focusNonce = itinerarySearchFocusNonce,
            onExpandedChange = { expanded ->
                if (!expanded) {
                    // When search bar is collapsed, reset the search target to dismiss the overlay
                    itinerarySearchTarget = null
                }
            },
            onStopPrimary = { result ->
                scope.launch {
                    val stopIds = viewModel.raptorRepository.resolveStopIdsByName(result.stopName)
                    val selectedStop = SelectedStop(
                        name = result.stopName,
                        stopIds = stopIds
                    )
                    if (isDepartureSearch) {
                        itineraryDepartureStop = selectedStop
                        itineraryDepartureQuery = ""
                    } else {
                        itineraryArrivalStop = selectedStop
                        itineraryArrivalQuery = ""
                    }
                    // Reset search target after selection
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
            containerColor = SecondaryColor,
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
                            val isLoaded = currentLines.any {
                                areEquivalentLineNames(it.properties.lineName, lineName)
                            }

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

    if (showAddFavoriteDialog) {
        AddFavoriteDialog(
            onDismiss = {
                // ...
            },
            onFavoriteCreated = { name, iconName, stopName ->
                viewModel.addUserFavorite(name, iconName, stopName)
            },
            viewModel = viewModel,
            initialStopName = addFavoriteInitialStopName
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItinerarySearchBarField(
    modifier: Modifier = Modifier,
    selectedStop: SelectedStop?,
    onClick: () -> Unit,
    icon: ImageVector,
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
                        color = SecondaryColor
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = AccentColor
                    )
                },
                colors = TextFieldDefaults.colors(
                    focusedTextColor = SecondaryColor,
                    unfocusedTextColor = SecondaryColor,
                    cursorColor = SecondaryColor,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    focusedContainerColor = PrimaryColor,
                    unfocusedContainerColor = PrimaryColor,
                    focusedPlaceholderColor = SecondaryColor.copy(alpha = 0.6f),
                    unfocusedPlaceholderColor = SecondaryColor.copy(alpha = 0.6f)
                )
            )
        },
        expanded = false,
        onExpandedChange = { if (it) onClick() },
        colors = SearchBarDefaults.colors(
            containerColor = PrimaryColor,
            dividerColor = Color.Transparent
        )
    ) {}
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
            // Consider all stops, not just those with desserte
            // This prevents bus stops from disappearing when Raptor assets are missing
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
    onAddFavoriteClick: (String) -> Unit = {},
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
        onAddFavoriteClick = onAddFavoriteClick,
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
    val selectedAliases = when (canonicalLineName(selectedLineName)) {
        "NAV1" -> listOf("NAV1", "NAVI1")
        else -> listOf(selectedLineName.trim().uppercase())
    }

    map.getStyle { style ->
        val layerId = "all-lines-layer"
        val existingLayer = style.getLayer(layerId)

        if (existingLayer != null) {
            val filterExpressions = selectedAliases.map { alias ->
                Expression.eq(Expression.get("ligne"), alias)
            }.toTypedArray()
            val lineFilter = if (filterExpressions.size == 1) {
                filterExpressions.first()
            } else {
                Expression.any(*filterExpressions)
            }
            (existingLayer as? LineLayer)?.setFilter(
                lineFilter
            )
        }

        // Also hide/show individual line layers (for lignes fortes)
        allLines.forEach { feature ->
            val ligne = feature.properties.lineName
            val codeTrace = feature.properties.traceCode

            val individualLayerId = "layer-${ligne}-${codeTrace}"
            style.getLayer(individualLayerId)?.let { layer ->
                val shouldBeVisible = areEquivalentLineNames(ligne, selectedLineName)
                layer.setProperties(
                    PropertyFactory.visibility(if (shouldBeVisible) "visible" else "none")
                )
            }
        }
    }
    val visibleCandidates =
        allLines.count { areEquivalentLineNames(it.properties.lineName, selectedLineName) }
    return visibleCandidates
}

private fun zoomToLine(
    map: MapLibreMap,
    allLines: List<Feature>,
    selectedLineName: String
) {
    val lineFeatures = allLines.filter {
        areEquivalentLineNames(it.properties.lineName, selectedLineName)
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
        CameraUpdateFactory.newLatLngBounds(
            bounds,
            paddingLeft,
            paddingTop,
            paddingRight,
            paddingBottom
        ),
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

    val coordinates = stop.geometry.coordinates
    if (coordinates.size < 2) return
    val lat = coordinates[1]
    val lon = coordinates[0]
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

    val linePropertyName = "has_line_${canonicalLineName(selectedLineName)}"

    // Filter layers only for slots that exist (instead of all -25..25)
    currentMapSlots.forEach { idx ->
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
        val linePropertyName = "has_line_${canonicalLineName(selectedLineName)}"

        // Filter layers only for slots that exist
        currentMapSlots.forEach { idx ->
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
        .find { areEquivalentLineNames(it.properties.lineName, selectedLineName) }
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
            val hasLine = lines.any { areEquivalentLineNames(it, selectedLineName) }
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
                    val coordinates = stop.geometry.coordinates
                    if (coordinates.size < 2) return@forEach
                    coordinatesArray.add(coordinates[0])
                    coordinatesArray.add(coordinates[1])
                    add("coordinates", coordinatesArray)
                }
                add("geometry", pointGeometry)

                val properties = JsonObject().apply {
                    addProperty("nom", stop.properties.nom)
                    addProperty("desserte", stop.properties.desserte)
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

private fun hideMapLines(
    map: MapLibreMap
) {
    map.getStyle { style ->
        style.getLayer("all-lines-layer")?.setProperties(PropertyFactory.visibility("none"))

        style.layers
            .map { it.id }
            .filter { it.startsWith("layer-") }
            .forEach { layerId ->
                style.getLayer(layerId)?.setProperties(PropertyFactory.visibility("none"))
            }

        style.getLayer("line-stops-circles")?.let { style.removeLayer(it) }
        style.getSource("line-stops-circles-source")?.let { style.removeSource(it) }
    }
}

private fun clearItineraryLayers(style: Style) {
    val layerIds = style.layers.map { it.id }.filter { it.startsWith("inline-itinerary-") }
    layerIds.forEach { layerId ->
        style.getLayer(layerId)?.let { style.removeLayer(it) }
        val sourceId = layerId.replace("-layer-", "-source-")
        style.getSource(sourceId)?.let { style.removeSource(it) }
    }
}

private fun drawItinerariesOnMap(
    map: MapLibreMap,
    journeys: List<JourneyResult>,
    selectedJourney: JourneyResult?,
    viewModel: TransportViewModel
) {
    map.getStyle { style ->
        clearItineraryLayers(style)
        if (journeys.isEmpty()) return@getStyle

        val journeysToDraw = selectedJourney?.let { listOf(it) } ?: journeys

        journeysToDraw.forEachIndexed { journeyIndex, journey ->
            journey.legs.forEachIndexed { legIndex, leg ->
                val lineColor = if (leg.isWalking) {
                    "#6B7280"
                } else {
                    val colorInt = LineColorHelper.getColorForLineString(leg.routeName ?: "")
                    String.format(Locale.ROOT, "#%06X", 0xFFFFFF and colorInt)
                }

                var drewSection = false

                if (!leg.isWalking) {
                    val lineName = leg.routeName ?: ""
                    val lines = try {
                        kotlinx.coroutines.runBlocking {
                            viewModel.transportRepository.getLineByName(lineName).getOrElse { emptyList() }
                        }
                    } catch (e: Exception) {
                        emptyList<Feature>()
                    }

                    if (lines.isNotEmpty()) {
                        val sectionedLines = viewModel.sectionLinesBetweenStops(
                            lines,
                            leg.fromStopId,
                            leg.toStopId,
                            leg
                        )
                        if (sectionedLines.isNotEmpty()) {
                            val sectionedLine = sectionedLines.first()
                            val sectionGeometry = sectionedLine.geometry
                            if (sectionGeometry is com.pelotcl.app.generic.data.model.Geometry) {
                                val coordinates = sectionGeometry.coordinates
                                if (coordinates.isNotEmpty()) {
                                    val firstLine = coordinates.firstOrNull()
                                    if (!firstLine.isNullOrEmpty() && firstLine.size > 1) {
                                        val coordinatesArray = JsonArray()
                                        firstLine.forEach { coord ->
                                            val coordArray = JsonArray()
                                            coordArray.add(coord[0])
                                            coordArray.add(coord[1])
                                            coordinatesArray.add(coordArray)
                                        }
                                        val lineGeoJson = JsonObject().apply {
                                            addProperty("type", "Feature")
                                            val geometry = JsonObject().apply {
                                                addProperty("type", "LineString")
                                                add("coordinates", coordinatesArray)
                                            }
                                            add("geometry", geometry)
                                        }
                                        val sourceId = "inline-itinerary-leg-source-$journeyIndex-$legIndex"
                                        val layerId = "inline-itinerary-leg-layer-$journeyIndex-$legIndex"
                                        style.addSource(GeoJsonSource(sourceId, lineGeoJson.toString()))
                                        val lineLayer = LineLayer(layerId, sourceId).apply {
                                            setProperties(
                                                PropertyFactory.lineColor(lineColor),
                                                PropertyFactory.lineWidth(5f),
                                                PropertyFactory.lineOpacity(1.0f),
                                                PropertyFactory.lineCap("round"),
                                                PropertyFactory.lineJoin("round")
                                            )
                                        }
                                        style.addLayer(lineLayer)
                                        drewSection = true
                                    }
                                }
                            }
                        }
                    }
                }

                if (!drewSection) {
                    val coordinatesArray = JsonArray()
                    val fromCoord = JsonArray()
                    fromCoord.add(leg.fromLon)
                    fromCoord.add(leg.fromLat)
                    coordinatesArray.add(fromCoord)

                    leg.intermediateStops.forEach { stop ->
                        val coord = JsonArray()
                        coord.add(stop.lon)
                        coord.add(stop.lat)
                        coordinatesArray.add(coord)
                    }

                    val toCoord = JsonArray()
                    toCoord.add(leg.toLon)
                    toCoord.add(leg.toLat)
                    coordinatesArray.add(toCoord)

                    val lineGeoJson = JsonObject().apply {
                        addProperty("type", "Feature")
                        val geometry = JsonObject().apply {
                            addProperty("type", "LineString")
                            add("coordinates", coordinatesArray)
                        }
                        add("geometry", geometry)
                    }

                    val sourceId = "inline-itinerary-leg-source-$journeyIndex-$legIndex"
                    val layerId = "inline-itinerary-leg-layer-$journeyIndex-$legIndex"

                    style.addSource(GeoJsonSource(sourceId, lineGeoJson.toString()))
                    val lineLayer = LineLayer(layerId, sourceId).apply {
                        setProperties(
                            PropertyFactory.lineColor(lineColor),
                            PropertyFactory.lineWidth(if (leg.isWalking) 3f else 5f),
                            PropertyFactory.lineOpacity(1.0f),
                            PropertyFactory.lineCap("round"),
                            PropertyFactory.lineJoin("round")
                        )
                        if (leg.isWalking) {
                            setProperties(PropertyFactory.lineDasharray(arrayOf(2f, 2f)))
                        }
                    }
                    style.addLayer(lineLayer)
                }
            }
        }
    }
}

private fun zoomToItineraries(
    map: MapLibreMap,
    journeys: List<JourneyResult>
) {
    if (journeys.isEmpty()) return

    val boundsBuilder = LatLngBounds.Builder()
    var hasCoordinates = false

    journeys.forEach { journey ->
        journey.legs.forEach { leg ->
            boundsBuilder.include(LatLng(leg.fromLat, leg.fromLon))
            boundsBuilder.include(LatLng(leg.toLat, leg.toLon))
            hasCoordinates = true

            leg.intermediateStops.forEach { stop ->
                boundsBuilder.include(LatLng(stop.lat, stop.lon))
                hasCoordinates = true
            }
        }
    }

    if (!hasCoordinates) return

    try {
        val bounds = boundsBuilder.build()
        map.animateCamera(
            CameraUpdateFactory.newLatLngBounds(
                bounds,
                70,
                120,
                70,
                520
            ),
            900
        )
    } catch (_: Exception) {
        // Ignore invalid bounds edge cases.
    }
}

private fun showAllMapLines(
    map: MapLibreMap,
    allLines: List<Feature>
) {
    map.getStyle { style ->
        clearItineraryLayers(style)

        (style.getLayer("all-lines-layer") as? LineLayer)?.let { allLinesLayer ->
            allLinesLayer.setProperties(PropertyFactory.visibility("visible"))
            allLinesLayer.setFilter(Expression.literal(true))
        }

        allLines.forEach { feature ->
            val ligne = feature.properties.lineName
            val codeTrace = feature.properties.traceCode

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

    // Reset filters to show all stops — only iterate slots that exist
    currentMapSlots.forEach { idx ->
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
        val ligne = feature.properties.lineName
        val codeTrace = feature.properties.traceCode

        val sourceId = "line-${ligne}-${codeTrace}"
        val layerId = "layer-${ligne}-${codeTrace}"

        style.getLayer(layerId)?.let { style.removeLayer(it) }
        style.getSource(sourceId)?.let { style.removeSource(it) }

        val lineGeoJson = createGeoJsonFromFeature(feature)

        val lineSource = GeoJsonSource(sourceId, lineGeoJson)
        style.addSource(lineSource)

        val lineColor = LineColorHelper.getColorForLine(feature)

        val upperLineName = ligne.uppercase()
        val familleTransport = feature.properties.transportType
        val lineWidth = when {
            familleTransport == "BAT" || isNavigoneLine(upperLineName) -> 2f
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
    val (stopsGeoJson, requiredIcons, usedSlots) = // Full cache hit — GeoJSON, icons, AND usedSlots are all cached
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

            Triple(stopsGeoJson, requiredIcons, usedSlots)
        }

    map.getStyle { style ->
        val sourceId = "transport-stops"
        val priorityLayerPrefix = "transport-stops-layer-priority"
        val tramLayerPrefix = "transport-stops-layer-tram"
        val secondaryLayerPrefix = "transport-stops-layer-secondary"


        // Only remove layers for slots that were actually created (instead of all -25..25)
        currentMapSlots.forEach { idx ->
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
            val allCached = viewModel?.hasAllIcons(requiredIcons.toList()) == true

            val bitmaps: Map<String, Bitmap> = if (allCached) {
                // All icons are cached - retrieve them directly without snapshot copy
                requiredIcons.mapNotNull { iconName ->
                    viewModel.getIconBitmap(iconName)?.let { iconName to it }
                }.toMap()
            } else {
                // Load missing bitmaps and cache them individually
                requiredIcons.mapNotNull { iconName ->
                    // Check cache first for this specific icon
                    viewModel?.getIconBitmap(iconName)?.let { return@mapNotNull iconName to it }

                    try {
                        val resourceId =
                            BusIconHelper.getResourceIdForDrawableName(context, iconName)
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

                // Save usedSlots for filter functions to avoid iterating all -25..25
                currentMapSlots = usedSlots.toSet()

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
                        val cameraUpdate =
                            CameraUpdateFactory.newLatLngZoom(point, map.cameraPosition.zoom + 2)
                        map.animateCamera(cameraUpdate)
                        return@OnMapClickListener true
                    }

                    // In global LIVE mode, clicking a vehicle opens its line details.
                    val globalVehicleFeatures =
                        map.queryRenderedFeatures(screenPoint, "global-vehicle-positions-layer")
                    if (globalVehicleFeatures.isNotEmpty()) {
                        val feature = globalVehicleFeatures.first()
                        val props = feature.properties()
                        if (props != null) {
                            try {
                                val lineName =
                                    if (props.has("lineName")) props.get("lineName").asString else ""
                                if (lineName.isNotEmpty()) {
                                    onLineClick(normalizeLineNameForUi(lineName))
                                    return@OnMapClickListener true
                                }
                            } catch (_: Exception) {
                                // Ignore parse errors
                            }
                        }
                    }

                    // Check individual stops first (higher priority than lines)
                    val interactableLayers = usedSlots.flatMap { idx ->
                        listOf(
                            "$priorityLayerPrefix-$idx",
                            "$tramLayerPrefix-$idx",
                            "$secondaryLayerPrefix-$idx"
                        )
                    }.toTypedArray()

                    if (interactableLayers.isNotEmpty()) {
                        val stopFeatures = map.queryRenderedFeatures(screenPoint, *interactableLayers)
                        if (stopFeatures.isNotEmpty()) {
                            val feature = stopFeatures.first()
                            val props = feature.properties()
                            if (props != null) {
                                try {
                                    val stopName =
                                        if (props.has("nom")) props.get("nom").asString else ""
                                    val stopId =
                                        if (props.has("stop_id")) props.get("stop_id").asInt else null
                                    val lignesJson =
                                        if (props.has("lignes")) props.get("lignes").asString else "[]"

                                    val lignes = try {
                                        val jsonArray = JsonParser.parseString(lignesJson).asJsonArray
                                        jsonArray.map { it.asString }
                                    } catch (_: Exception) {
                                        emptyList()
                                    }

                                    if (stopName.isNotBlank()) {
                                        val stationInfo = StationInfo(
                                            nom = stopName,
                                            lignes = lignes,
                                            stopIds = stopId?.let { listOf(it) } ?: emptyList()
                                        )
                                        onStationClick(stationInfo)
                                        return@OnMapClickListener true
                                    }
                                } catch (_: Exception) {
                                    // Ignore parse errors
                                }
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

                    val lineFeatures =
                        map.queryRenderedFeatures(lineHitbox, *allLineLayerIds.toTypedArray())

                    if (lineFeatures.isNotEmpty()) {
                        val feature = lineFeatures.first()
                        val props = feature.properties()
                        if (props != null) {
                            try {
                                val lineName =
                                    if (props.has("ligne")) props.get("ligne").asString else ""
                                if (lineName.isNotEmpty()) {
                                    onLineClick(normalizeLineNameForUi(lineName))
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
            addProperty("ligne", feature.properties.lineName)
            addProperty("nom_trace", feature.properties.traceName)
            addProperty("couleur", feature.properties.color ?: "")
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

            // Calculate average position (centroid) for all stops with same name
            val validCoordinates = stopsGroup
                .mapNotNull { stop ->
                    val coordinates = stop.geometry.coordinates
                    if (coordinates.size < 2) null else coordinates[0] to coordinates[1]
                }
            val avgLon = validCoordinates.map { it.first }.average()
            val avgLat = validCoordinates.map { it.second }.average()
            if (avgLon.isNaN() || avgLat.isNaN()) {
                return@map firstStop
            }
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

        val coordinates = stop.geometry.coordinates
        if (coordinates.size < 2) continue
        val lon = coordinates[0]
        val lat = coordinates[1]
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
            sb.append("\"stop_id\":").append(stop.properties.id).append(",")
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
