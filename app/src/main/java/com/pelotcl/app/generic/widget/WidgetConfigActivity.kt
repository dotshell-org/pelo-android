package com.pelotcl.app.generic.widget

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
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import com.pelotcl.app.generic.data.repository.offline.SchedulesRepository
import com.pelotcl.app.generic.ui.components.search.TransportSearchBar
import com.pelotcl.app.generic.ui.components.search.TransportSearchContent
import com.pelotcl.app.generic.ui.theme.PrimaryColor
import com.pelotcl.app.generic.ui.theme.SecondaryColor
import com.pelotcl.app.generic.ui.viewmodel.TransportViewModel
import com.pelotcl.app.utils.transport.BusIconHelper
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

// -- App-consistent dark colors --
private val DarkBackground = PrimaryColor
private val DarkCard = Color(0xFF1C1C1E)
private val DarkCardPressed = Color(0xFF2C2C2E)
private val DarkDivider = Color(0xFF3A3A3C)
private val TextPrimary = SecondaryColor
private val TextSecondary = Color(0xFF8E8E93)
private val AccentRed = Color(0xFFE60000)
private val AccentYellow = Color(0xFFFFC107)

class WidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var widgetStyle = WidgetStyle.ALL_LINES_MINUTES
    private lateinit var viewModel: TransportViewModel

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
        widgetStyle = resolveWidgetStyle(applicationContext, appWidgetId)

        // Create ViewModel with context
        viewModel = TransportViewModel(applicationContext)

        val schedulesRepository = SchedulesRepository.getInstance(applicationContext)

        setContent {
            WidgetConfigScreen(
                widgetStyle = widgetStyle,
                schedulesRepository = schedulesRepository,
                viewModel = viewModel,
                onConfigComplete = { stopName, lineName, directionId, desserte ->
                    saveWidgetConfig(stopName, lineName, directionId, desserte)
                },
                onCancel = { finish() }
            )
        }
    }

    private fun saveWidgetConfig(
        stopName: String,
        lineName: String?,
        directionId: Int,
        desserte: String
    ) {
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
                prefs[PeloWidget.PREF_WIDGET_STYLE] = widgetStyle.id
                prefs[PeloWidget.PREF_REFRESH_INTERVAL] = widgetStyle.refreshIntervalMinutes
            }

            PeloWidget().update(context, glanceId)
            WidgetRefreshScheduler.schedule(
                context,
                appWidgetId,
                widgetStyle.refreshIntervalMinutes
            )

            val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(RESULT_OK, resultValue)
            finish()
        }
    }
}

// -- Search functionality --

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun SearchStopStep(
    viewModel: TransportViewModel,
    onStopSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Use the TransportSearchBar component
        TransportSearchBar(
            viewModel = viewModel,
            modifier = Modifier.padding(8.dp),
            content = TransportSearchContent.STOPS_ONLY,
            showHistory = false,
            startExpanded = true,
            searchPlaceholder = "Rechercher un arrêt",
            onStopPrimary = { stopResult ->
                onStopSelected(stopResult.stopName)
            },
            onStopSecondary = { stopResult ->
                onStopSelected(stopResult.stopName)
            }
        )
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
                        tryAwaitRelease()
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

// -- Top bar with search --

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun WidgetConfigTopBar(
    title: String,
    viewModel: TransportViewModel,
    onStopSelected: (String) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        // Search bar at the top
        TransportSearchBar(
            viewModel = viewModel,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            content = TransportSearchContent.STOPS_ONLY,
            showHistory = false,
            startExpanded = false,
            searchPlaceholder = "Rechercher un arrêt...",
            onStopPrimary = { stopResult ->
                onStopSelected(stopResult.stopName)
            },
            onStopSecondary = { stopResult ->
                onStopSelected(stopResult.stopName)
            }
        )

        // Title below search bar
        if (title.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// -- Main screen --

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun WidgetConfigScreen(
    widgetStyle: WidgetStyle,
    schedulesRepository: SchedulesRepository,
    viewModel: TransportViewModel,
    onConfigComplete: (stopName: String, lineName: String?, directionId: Int, desserte: String) -> Unit,
    onCancel: () -> Unit
) {
    var selectedStop by remember { mutableStateOf<String?>(null) }
    var pendingConfig by remember { mutableStateOf<PendingConfig?>(null) }

    val title = when {
        pendingConfig != null -> "Mise à jour"
        selectedStop != null && !widgetStyle.requiresSpecificLine -> "Arrêt sélectionné"
        selectedStop != null -> "Choisir une ligne"
        else -> ""
    }

    val onBack: () -> Unit = {
        when {
            pendingConfig != null -> pendingConfig = null
            selectedStop != null -> selectedStop = null
            else -> onCancel()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        WidgetConfigTopBar(
            title = title,
            viewModel = viewModel,
            onStopSelected = { stopName ->
                selectedStop = stopName
            },
            onBack = onBack
        )

        if (selectedStop == null) {
            // Show welcome message when no stop is selected
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Place,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = AccentRed
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Recherchez un arrêt",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Utilisez la barre de recherche ci-dessus pour trouver un arrêt",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            val desserte = remember(selectedStop) {
                schedulesRepository.getDesserteForStop(selectedStop!!) ?: ""
            }

            if (pendingConfig != null) {
                LaunchedEffect(pendingConfig) {
                    pendingConfig?.let { cfg ->
                        onConfigComplete(cfg.stopName, cfg.lineName, cfg.directionId, cfg.desserte)
                    }
                }
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Configuration du widget...",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                }
            } else {
                if (widgetStyle.requiresSpecificLine) {
                    // For line-specific widgets, show line selection
                    LineSelectionStep(
                        desserte = desserte,
                        schedulesRepository = schedulesRepository,
                        onLineSelected = { lineWithDirections ->
                            pendingConfig = PendingConfig(
                                selectedStop!!,
                                lineWithDirections.lineName,
                                DIRECTION_BOTH, // Show both directions for the selected line
                                desserte
                            )
                        }
                    )
                } else {
                    // For all-lines widgets, complete configuration immediately
                    LaunchedEffect(selectedStop) {
                        pendingConfig = PendingConfig(selectedStop!!, null, 0, desserte)
                    }
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun LineSelectionStep(
    desserte: String,
    schedulesRepository: SchedulesRepository,
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
            val headsigns = schedulesRepository.getHeadsigns(line)

            if (headsigns.isEmpty()) return@mapNotNull null

            val dirs = headsigns.map { (dirId, headsign) ->
                LineDirection(line, dirId, headsign)
            }.sortedBy { it.directionId }

            LineWithDirections(line, dirs)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(12.dp))
        ) {
            linesWithDirections.forEachIndexed { index, line ->
                DarkMenuRow(onClick = { onLineSelected(line) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LineIcon(
                            lineName = line.lineName,
                            modifier = Modifier.size(width = 40.dp, height = 24.dp)
                        )
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

@Composable
fun LineIcon(
    lineName: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val drawableId = remember(lineName) {
        BusIconHelper.getResourceIdForLine(context, lineName)
    }

    if (drawableId != 0) {
        Image(
            painter = painterResource(id = drawableId),
            contentDescription = "Ligne $lineName",
            modifier = modifier
        )
    } else {
        Text(
            text = lineName,
            color = TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = modifier
        )
    }
}