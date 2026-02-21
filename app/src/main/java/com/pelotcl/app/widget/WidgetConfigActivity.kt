package com.pelotcl.app.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import com.pelotcl.app.data.gtfs.SchedulesRepository
import com.pelotcl.app.data.repository.FavoritesRepository
import com.pelotcl.app.utils.LineColorHelper
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

// -- App-consistent dark colors --
private val DarkBackground = Color.Black
private val DarkCard = Color(0xFF1C1C1E)
private val DarkCardPressed = Color(0xFF2C2C2E)
private val DarkDivider = Color(0xFF3A3A3C)
private val TextPrimary = Color.White
private val TextSecondary = Color(0xFF8E8E93)
private val AccentRed = Color(0xFFE60000)
private val AccentYellow = Color(0xFFFFC107)

class WidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.Transparent.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(Color.Transparent.toArgb())
        )
        super.onCreate(savedInstanceState)

        setResult(RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val favoritesRepository = FavoritesRepository(applicationContext)
        val schedulesRepository = SchedulesRepository.getInstance(applicationContext)
        val favoriteStops = favoritesRepository.getFavoriteStops().toList().sorted()

        setContent {
            WidgetConfigScreen(
                favoriteStops = favoriteStops,
                favoritesRepository = favoritesRepository,
                schedulesRepository = schedulesRepository,
                onConfigComplete = { stopName, lineName, directionId, desserte, refreshInterval ->
                    saveWidgetConfig(stopName, lineName, directionId, desserte, refreshInterval)
                },
                onCancel = { finish() }
            )
        }
    }

    private fun saveWidgetConfig(stopName: String, lineName: String?, directionId: Int, desserte: String, refreshIntervalMinutes: Int) {
        val context = applicationContext
        MainScope().launch {
            val glanceId = GlanceAppWidgetManager(context)
                .getGlanceIdBy(appWidgetId)

            updateAppWidgetState(context, glanceId) { prefs ->
                prefs[PeloWidget.PREF_STOP_NAME] = stopName
                if (lineName != null) {
                    prefs[PeloWidget.PREF_LINE_NAME] = lineName
                } else {
                    prefs.remove(PeloWidget.PREF_LINE_NAME)
                }
                prefs[PeloWidget.PREF_DIRECTION_ID] = directionId
                prefs[PeloWidget.PREF_DESSERTE] = desserte
                prefs[PeloWidget.PREF_REFRESH_INTERVAL] = refreshIntervalMinutes
            }

            PeloWidget().update(context, glanceId)
            WidgetRefreshScheduler.schedule(context, appWidgetId, refreshIntervalMinutes)

            val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(RESULT_OK, resultValue)
            finish()
        }
    }
}

// -- Data classes --

private data class LineDirection(
    val lineName: String,
    val directionId: Int,
    val headsign: String
)

private data class LineWithDirections(
    val lineName: String,
    val directions: List<LineDirection>
)

private const val DIRECTION_BOTH = -1

private data class PendingConfig(
    val stopName: String,
    val lineName: String?,
    val directionId: Int,
    val desserte: String
)

private data class RefreshOption(
    val label: String,
    val minutes: Int,
    val subtitle: String? = null
)

// -- Reusable dark-themed row with press animation --

@Composable
private fun DarkMenuRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showChevron: Boolean = true,
    content: @Composable () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val bgColor by animateColorAsState(
        targetValue = if (isPressed) DarkCardPressed else DarkCard,
        animationSpec = tween(120),
        label = "rowPress"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(bgColor)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = { onClick() }
                )
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f)) {
            content()
        }
        if (showChevron) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

// -- Top bar --

@Composable
private fun DarkTopBar(
    title: String,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .clickable { onBack() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Retour",
                tint = TextPrimary,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// -- Main screen --

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun WidgetConfigScreen(
    favoriteStops: List<String>,
    favoritesRepository: FavoritesRepository,
    schedulesRepository: SchedulesRepository,
    onConfigComplete: (stopName: String, lineName: String?, directionId: Int, desserte: String, refreshIntervalMinutes: Int) -> Unit,
    onCancel: () -> Unit
) {
    var selectedStop by remember { mutableStateOf<String?>(null) }
    var selectedLine by remember { mutableStateOf<LineWithDirections?>(null) }
    var pendingConfig by remember { mutableStateOf<PendingConfig?>(null) }

    val title = when {
        pendingConfig != null -> "Mise à jour"
        selectedLine != null -> "Direction"
        selectedStop != null -> "Lignes"
        else -> "Widget"
    }

    val onBack: () -> Unit = {
        when {
            pendingConfig != null -> pendingConfig = null
            selectedLine != null -> selectedLine = null
            selectedStop != null -> selectedStop = null
            else -> onCancel()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        DarkTopBar(title = title, onBack = onBack)

        if (selectedStop == null) {
            StopSelectionStep(
                favoriteStops = favoriteStops,
                onStopSelected = { selectedStop = it }
            )
        } else {
            val desserte = remember(selectedStop) {
                favoritesRepository.getDesserteForStop(selectedStop!!)
                    ?: schedulesRepository.getDesserteForStop(selectedStop!!)
                    ?: ""
            }

            if (pendingConfig != null) {
                RefreshIntervalStep(
                    onIntervalSelected = { intervalMinutes ->
                        val cfg = pendingConfig!!
                        onConfigComplete(cfg.stopName, cfg.lineName, cfg.directionId, cfg.desserte, intervalMinutes)
                    }
                )
            } else if (selectedLine == null) {
                LineSelectionStep(
                    desserte = desserte,
                    schedulesRepository = schedulesRepository,
                    onAllLinesSelected = {
                        pendingConfig = PendingConfig(selectedStop!!, null, 0, desserte)
                    },
                    onLineSelected = { line -> selectedLine = line }
                )
            } else {
                DirectionSelectionStep(
                    line = selectedLine!!,
                    onDirectionSelected = { directionId ->
                        pendingConfig = PendingConfig(selectedStop!!, selectedLine!!.lineName, directionId, desserte)
                    }
                )
            }
        }
    }
}

// -- Step 1: Stop selection --

@Composable
private fun StopSelectionStep(
    favoriteStops: List<String>,
    onStopSelected: (String) -> Unit
) {
    if (favoriteStops.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 32.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = AccentYellow
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Aucun arrêt favori",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Ajoutez des arrêts favoris dans l'app\nen cliquant sur l'étoile d'un arrêt.",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        Column(modifier = Modifier.padding(top = 8.dp)) {
            Text(
                text = "Choisir un arrêt favori",
                color = TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )

            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                favoriteStops.forEachIndexed { index, stopName ->
                    DarkMenuRow(onClick = { onStopSelected(stopName) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Place,
                                contentDescription = null,
                                tint = AccentRed,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = stopName,
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    if (index < favoriteStops.lastIndex) {
                        HorizontalDivider(
                            color = DarkDivider,
                            modifier = Modifier.padding(start = 48.dp)
                        )
                    }
                }
            }
        }
    }
}

// -- Step 2: Line selection --

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun LineSelectionStep(
    desserte: String,
    schedulesRepository: SchedulesRepository,
    onAllLinesSelected: () -> Unit,
    onLineSelected: (LineWithDirections) -> Unit
) {
    val linesWithDirections = remember(desserte) {
        val lineNames = desserte.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { entry ->
                val parts = entry.split(":")
                if (parts.size == 2) parts[0].trim() else null
            }
            .distinct()

        lineNames.mapNotNull { line ->
            val displayLine = if (line.equals("NAVI1", ignoreCase = true)) "NAV1" else line
            val gtfsLine = if (line.equals("NAV1", ignoreCase = true)) "NAVI1" else line
            val headsigns = schedulesRepository.getHeadsigns(gtfsLine)

            if (headsigns.isEmpty()) return@mapNotNull null

            val dirs = headsigns.map { (dirId, headsign) ->
                LineDirection(displayLine, dirId, headsign)
            }.sortedBy { it.directionId }

            LineWithDirections(displayLine, dirs)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp)
    ) {
        // "All lines" highlight card
        Text(
            text = "Mode d'affichage",
            color = TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )

        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(12.dp))
        ) {
            DarkMenuRow(onClick = onAllLinesSelected) {
                Column {
                    Text(
                        text = "Toutes les lignes",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Prochains départs de toutes les lignes",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Specific lines
        Text(
            text = "Ou une ligne spécifique",
            color = TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )

        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(12.dp))
        ) {
            linesWithDirections.forEachIndexed { index, line ->
                DarkMenuRow(onClick = { onLineSelected(line) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Line badge with TCL color
                        val lineColor = LineColorHelper.getColorForLineString(line.lineName)
                        Box(
                            modifier = Modifier
                                .size(width = 40.dp, height = 24.dp)
                                .background(
                                    Color(lineColor),
                                    RoundedCornerShape(6.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = line.lineName,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = line.directions.joinToString(" · ") { it.headsign },
                            color = TextSecondary,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                if (index < linesWithDirections.lastIndex) {
                    HorizontalDivider(
                        color = DarkDivider,
                        modifier = Modifier.padding(start = 68.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// -- Step 3: Direction selection --

@Composable
private fun DirectionSelectionStep(
    line: LineWithDirections,
    onDirectionSelected: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp)
    ) {
        // Line badge header
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val lineColor = LineColorHelper.getColorForLineString(line.lineName)
            Box(
                modifier = Modifier
                    .size(width = 40.dp, height = 24.dp)
                    .background(Color(lineColor), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = line.lineName,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Ligne ${line.lineName}",
                color = TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // Both directions option
        if (line.directions.size > 1) {
            Text(
                text = "Toutes les directions",
                color = TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )

            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                DarkMenuRow(onClick = { onDirectionSelected(DIRECTION_BOTH) }) {
                    Column {
                        Text(
                            text = "Les deux directions",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = line.directions.joinToString(" et ") { it.headsign },
                            color = TextSecondary,
                            fontSize = 13.sp,
                            maxLines = 2
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Ou une seule direction",
                color = TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
        } else {
            Text(
                text = "Direction",
                color = TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
        }

        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(12.dp))
        ) {
            line.directions.forEachIndexed { index, dir ->
                DarkMenuRow(onClick = { onDirectionSelected(dir.directionId) }) {
                    Text(
                        text = dir.headsign,
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (index < line.directions.lastIndex) {
                    HorizontalDivider(
                        color = DarkDivider,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// -- Step 4: Refresh interval --

@Composable
private fun RefreshIntervalStep(
    onIntervalSelected: (Int) -> Unit
) {
    val options = listOf(
        RefreshOption("1 minute", 1, "Consomme plus de batterie"),
        RefreshOption("5 minutes", 5, "Peut consommer plus de batterie"),
        RefreshOption("15 minutes", 15),
        RefreshOption("30 minutes", 30)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp)
    ) {
        Text(
            text = "Fréquence de mise à jour",
            color = TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )

        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(12.dp))
        ) {
            options.forEachIndexed { index, option ->
                DarkMenuRow(
                    onClick = { onIntervalSelected(option.minutes) },
                    showChevron = false
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Timer,
                            contentDescription = null,
                            tint = AccentRed,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Toutes les ${option.label}",
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                            if (option.subtitle != null) {
                                Text(
                                    text = option.subtitle,
                                    color = TextSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
                if (index < options.lastIndex) {
                    HorizontalDivider(
                        color = DarkDivider,
                        modifier = Modifier.padding(start = 48.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
