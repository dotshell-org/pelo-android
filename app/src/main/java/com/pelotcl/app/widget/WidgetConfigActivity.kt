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
                    schedulesRepository = schedulesRepository,
                    onConfigComplete = { stopName, lineName, directionId ->
                        saveWidgetConfig(stopName, lineName, directionId)
                    },
                    onCancel = { finish() }
                )
            }
        }
    }

    private fun saveWidgetConfig(stopName: String, lineName: String?, directionId: Int) {
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
            }

            PeloWidget().update(context, glanceId)

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

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WidgetConfigScreen(
    favoriteStops: List<String>,
    schedulesRepository: SchedulesRepository,
    onConfigComplete: (stopName: String, lineName: String?, directionId: Int) -> Unit,
    onCancel: () -> Unit
) {
    var selectedStop by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (selectedStop == null) "Choisir un arrêt" else "Choisir le mode",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedStop != null) {
                            selectedStop = null
                        } else {
                            onCancel()
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
                // Step 2: Choose mode (all lines or specific line+direction)
                ModeSelectionStep(
                    stopName = selectedStop!!,
                    schedulesRepository = schedulesRepository,
                    onModeSelected = { lineName, directionId ->
                        onConfigComplete(selectedStop!!, lineName, directionId)
                    }
                )
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
private fun ModeSelectionStep(
    stopName: String,
    schedulesRepository: SchedulesRepository,
    onModeSelected: (lineName: String?, directionId: Int) -> Unit
) {
    val desserte = remember(stopName) {
        schedulesRepository.getDesserteForStop(stopName) ?: ""
    }

    // Parse available lines and directions from desserte
    val lineDirections = remember(desserte) {
        desserte.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { entry ->
                val parts = entry.split(":")
                if (parts.size == 2) {
                    val line = parts[0].trim()
                    val dirLetter = parts[1].trim()
                    val dirId = when (dirLetter.uppercase()) {
                        "A" -> 0
                        "R" -> 1
                        else -> return@mapNotNull null
                    }
                    val displayLine = if (line.equals("NAVI1", ignoreCase = true)) "NAV1" else line
                    val gtfsLine = if (line.equals("NAV1", ignoreCase = true)) "NAVI1" else line
                    val headsigns = schedulesRepository.getHeadsigns(gtfsLine)
                    val headsign = headsigns[dirId] ?: "Direction $dirLetter"
                    LineDirection(displayLine, dirId, headsign)
                } else null
            }
    }

    LazyColumn {
        // Option: All lines
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onModeSelected(null, 0) },
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

        // Options: Specific line + direction
        items(lineDirections) { ld ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onModeSelected(ld.lineName, ld.directionId) }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = ld.lineName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(48.dp)
                    )
                    Text(
                        text = ld.headsign,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            HorizontalDivider()
        }
    }
}
