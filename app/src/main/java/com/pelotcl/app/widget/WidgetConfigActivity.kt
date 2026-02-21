package com.pelotcl.app.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import com.pelotcl.app.data.gtfs.SchedulesRepository
import com.pelotcl.app.data.repository.FavoritesRepository
import com.pelotcl.app.ui.theme.PeloTheme
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Refresh

class WidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Default result = CANCELED so if the user backs out, widget isn't added
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
            PeloTheme {
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

private data class LineDirection(
    val lineName: String,
    val directionId: Int,
    val headsign: String
)

/** Unique lines grouped with their available directions */
private data class LineWithDirections(
    val lineName: String,
    val directions: List<LineDirection>
)

// directionId = -1 means "both directions"
private const val DIRECTION_BOTH = -1

/** Holds the line/direction choice before picking refresh interval */
private data class PendingConfig(
    val stopName: String,
    val lineName: String?,
    val directionId: Int,
    val desserte: String
)

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
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
        pendingConfig != null -> "Fréquence de mise à jour"
        selectedLine != null -> "Choisir la direction"
        selectedStop != null -> "Choisir le mode"
        else -> "Choisir un arrêt"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        when {
                            pendingConfig != null -> {
                                // Go back to direction or line step
                                pendingConfig = null
                            }
                            selectedLine != null -> selectedLine = null
                            selectedStop != null -> selectedStop = null
                            else -> onCancel()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            if (selectedStop == null) {
                // Step 1: Choose a favorite stop
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
                    // Step 4: Choose refresh interval
                    RefreshIntervalStep(
                        onIntervalSelected = { intervalMinutes ->
                            val cfg = pendingConfig!!
                            onConfigComplete(cfg.stopName, cfg.lineName, cfg.directionId, cfg.desserte, intervalMinutes)
                        }
                    )
                } else if (selectedLine == null) {
                    // Step 2: Choose all lines or a specific line
                    LineSelectionStep(
                        desserte = desserte,
                        schedulesRepository = schedulesRepository,
                        onAllLinesSelected = {
                            pendingConfig = PendingConfig(selectedStop!!, null, 0, desserte)
                        },
                        onLineSelected = { line -> selectedLine = line }
                    )
                } else {
                    // Step 3: Choose direction for the selected line
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
}

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
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = Color(0xFFFFC107)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Aucun arrêt favori",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Ajoutez des arrêts favoris dans l'app\nen cliquant sur l'étoile d'un arrêt.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn {
            items(favoriteStops) { stopName ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onStopSelected(stopName) }
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Place,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stopName,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                HorizontalDivider()
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun LineSelectionStep(
    desserte: String,
    schedulesRepository: SchedulesRepository,
    onAllLinesSelected: () -> Unit,
    onLineSelected: (LineWithDirections) -> Unit
) {
    // Parse desserte to find which lines serve this stop, then enrich with all GTFS directions
    val linesWithDirections = remember(desserte) {
        // Extract unique line names from desserte
        val lineNames = desserte.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { entry ->
                val parts = entry.split(":")
                if (parts.size == 2) parts[0].trim() else null
            }
            .distinct()

        // For each line, get ALL directions from GTFS headsigns (not just desserte entries)
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

    LazyColumn {
        // Option: All lines
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAllLinesSelected() },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Toutes les lignes",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Prochains départs de toutes les lignes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Ou une ligne spécifique :",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // Options: Unique lines
        items(linesWithDirections) { line ->
            val subtitle = line.directions.joinToString(" · ") { it.headsign }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onLineSelected(line) }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = line.lineName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(48.dp)
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun DirectionSelectionStep(
    line: LineWithDirections,
    onDirectionSelected: (Int) -> Unit
) {
    Column {
        Text(
            text = "Ligne ${line.lineName}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Option: Both directions (only if there are 2 directions)
        if (line.directions.size > 1) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onDirectionSelected(DIRECTION_BOTH) },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Les deux directions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = line.directions.joinToString(" et ") { it.headsign },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        maxLines = 2
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Ou une seule direction :",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // Individual directions
        line.directions.forEach { dir ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onDirectionSelected(dir.directionId) }
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dir.headsign,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            HorizontalDivider()
        }
    }
}

private data class RefreshOption(
    val label: String,
    val minutes: Int
)

@Composable
private fun RefreshIntervalStep(
    onIntervalSelected: (Int) -> Unit
) {
    val options = listOf(
        RefreshOption("1 minute", 1),
        RefreshOption("5 minutes", 5),
        RefreshOption("15 minutes", 15),
        RefreshOption("30 minutes", 30)
    )

    Column {
        Text(
            text = "À quelle fréquence le widget doit-il se mettre à jour ?",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        options.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onIntervalSelected(option.minutes) }
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Toutes les ${option.label}",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    if (option.minutes <= 5) {
                        Text(
                            text = if (option.minutes == 1) "Consomme plus de batterie" else "Peut consommer plus de batterie",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            HorizontalDivider()
        }
    }
}
