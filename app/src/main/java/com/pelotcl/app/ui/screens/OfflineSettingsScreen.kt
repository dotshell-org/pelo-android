package com.pelotcl.app.ui.screens

import android.content.Context
import android.text.format.Formatter
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.sin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pelotcl.app.data.offline.OfflineDataInfo
import com.pelotcl.app.data.offline.OfflineDownloadState
import com.pelotcl.app.data.offline.OfflineRepository
import com.pelotcl.app.data.repository.MapStyle
import com.pelotcl.app.data.repository.MapStyleCategory
import com.pelotcl.app.ui.viewmodel.TransportViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun OfflineSettingsScreen(
    viewModel: TransportViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val offlineDataInfo by viewModel.offlineDataInfo.collectAsState()
    val downloadState by viewModel.offlineDataManager.downloadState.collectAsState()
    val isOffline by viewModel.isOffline.collectAsState()
    val offlineRepository = remember { OfflineRepository.getInstance(context) }
    var selectedMapStyles by remember { mutableStateOf(offlineRepository.getSelectedMapStyles()) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 60.dp, bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Spacer(modifier = Modifier.height(60.dp))
            Icon(
                imageVector = if (offlineDataInfo.isAvailable) Icons.Filled.CheckCircle else Icons.Filled.CloudOff,
                contentDescription = null,
                tint = if (offlineDataInfo.isAvailable) Color(0xFF4CAF50) else Color.Gray,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Mode hors ligne",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (offlineDataInfo.isAvailable)
                    "Les données hors ligne sont disponibles"
                else
                    "Téléchargez les données pour utiliser l'appli sans connexion",
                color = Color.Gray,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(50.dp))

            // Status card
            if (offlineDataInfo.isAvailable) {
                OfflineStatusCard(offlineDataInfo, context)
                Spacer(modifier = Modifier.height(30.dp))
            }

            CategoryHeader(Icons.Default.Map, "Fonds de carte")

            MapStyleSelectionCard(
                selectedStyles = selectedMapStyles,
                downloadedStyles = offlineDataInfo.downloadedMapStyles,
                isDownloading = downloadState is OfflineDownloadState.Downloading,
                isEnabled = !isOffline,
                onStyleToggled = { styleKey, checked ->
                    val newSet = if (checked) selectedMapStyles + styleKey
                    else if (selectedMapStyles.size > 1) selectedMapStyles - styleKey
                    else selectedMapStyles
                    selectedMapStyles = newSet
                    offlineRepository.setSelectedMapStyles(newSet)
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Download Section
            when (val state = downloadState) {
                is OfflineDownloadState.Downloading -> DownloadProgressCard(state)
                else -> {
                    Button(
                        enabled = !isOffline,
                        onClick = { viewModel.startOfflineDownload() },
                        modifier = Modifier.fillMaxWidth().alpha(if (isOffline) 0.5f else 1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE60000),
                            disabledContainerColor = Color(0xFF3A3A3C)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.CloudDownload, null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (offlineDataInfo.isAvailable) "Mettre à jour" else "Télécharger les données",
                            fontSize = 16.sp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    if (state is OfflineDownloadState.Error) {
                        Text(state.message, color = Color(0xFFEF4444), fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            CategoryHeader(Icons.Default.Info, "Fonctionnalités hors ligne")
            OfflineInfoCard()

            Spacer(modifier = Modifier.height(100.dp))
        }

        IconButton(
            onClick = onBackClick,
            modifier = Modifier.statusBarsPadding().padding(start = 4.dp, top = 8.dp).align(Alignment.TopStart)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour", tint = Color.White)
        }
    }
}

@Composable
private fun CategoryHeader(icon: ImageVector, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 4.dp)
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadProgressCard(state: OfflineDownloadState.Downloading) {
    val animatedProgress by animateFloatAsState(
        targetValue = state.progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 600),
        label = "progress"
    )

    val progressColor = lerp(Color(0xFFEF4444), Color(0xFF4CAF50), animatedProgress)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Spacer(modifier = Modifier.height(7.dp))

            Text(state.stepDescription,
                color = Color.White,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            WavyLinearProgressIndicator(
                progress = animatedProgress,
                modifier = Modifier.fillMaxWidth(),
                indicatorColor = progressColor,
                trackColor = Color(0xFF3A3A3C)
            )

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun MapStyleSelectionCard(
    selectedStyles: Set<String>,
    downloadedStyles: Set<String>,
    isDownloading: Boolean,
    isEnabled: Boolean,
    onStyleToggled: (String, Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            val styles = MapStyle.getByCategory(MapStyleCategory.STANDARD)
            styles.forEachIndexed { index, style ->
                val isSelected = style.key in selectedStyles
                val isDownloaded = style.key in downloadedStyles
                val isInteractive = isEnabled && !isDownloading

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = isInteractive) { onStyleToggled(style.key, !isSelected) }
                        .padding(vertical = 16.dp, horizontal = 12.dp)
                        .alpha(if (isInteractive) 1f else 0.5f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Case carrée custom
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .border(2.dp, if (isSelected) Color.Transparent else Color(0xFF8E8E93), RoundedCornerShape(6.dp))
                            .background(if (isSelected) Color(0xFFE60000) else Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }

                    Spacer(modifier = Modifier.width(16.dp))
                    Text(style.displayName, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))

                    if (isDownloaded) {
                        Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
                    }
                }
                if (index < styles.size - 1) {
                    Box(modifier = Modifier.fillMaxWidth().padding(start = 56.dp).height(0.5.dp).background(Color(0xFF3A3A3C)))
                }
            }
        }
    }
}

@Composable
private fun OfflineStatusCard(info: OfflineDataInfo, context: Context) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            StatusRow("Dernière mise à jour", formatTimestamp(info.lastDownloadTimestamp))
            Spacer(modifier = Modifier.height(8.dp))
            StatusRow("Espace utilisé", Formatter.formatFileSize(context, info.totalSizeBytes))
            Spacer(modifier = Modifier.height(8.dp))
            StatusRow("Lignes de bus", if (info.busLinesCount > 0) "${info.busLinesCount} lignes" else "Aucune")
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.Gray, fontSize = 14.sp)
        Text(value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun OfflineInfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            listOf("Carte", "Lignes et arrêts", "Horaires", "Recherche", "Calcul d'itinéraire").forEach {
                FeatureRow(it, true)
            }
            FeatureRow("Suivi temps réel", false)
        }
    }
}

@Composable
private fun FeatureRow(feature: String, available: Boolean) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = if (available) "✓" else "✕",
            color = if (available) Color(0xFF4CAF50) else Color(0xFFEF4444),
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(feature, color = if (available) Color.White else Color.Gray, fontSize = 14.sp)
    }
}

private fun formatTimestamp(timestamp: Long): String {
    if (timestamp == 0L) return "Jamais"
    return SimpleDateFormat("d MMM yyyy 'à' HH:mm", Locale.FRANCE).format(Date(timestamp))
}

@Composable
private fun WavyLinearProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    indicatorColor: Color,
    trackColor: Color,
    barHeight: androidx.compose.ui.unit.Dp = 7.dp,
    waveAmplitude: androidx.compose.ui.unit.Dp = 3.dp,
    wavelength: androidx.compose.ui.unit.Dp = 40.dp,
    waveSpeedMillis: Int = 1000,
    gapWidth: androidx.compose.ui.unit.Dp = 5.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = waveSpeedMillis, easing = LinearEasing)
        ),
        label = "wavePhase"
    )

    Canvas(modifier = modifier.height(barHeight + waveAmplitude * 2)) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val halfHeight = barHeight.toPx() / 2f

        val progressWidth = (width * progress.coerceIn(0f, 1f))
        val gapPx = gapWidth.toPx()

        val rigidStart = progressWidth + gapPx

        if (rigidStart < width) {
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(rigidStart, centerY - halfHeight),
                size = Size(width - rigidStart, barHeight.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(halfHeight, halfHeight)
            )
        }

        if (progressWidth > 0f) {
            val ampPx = waveAmplitude.toPx()
            val lenPx = wavelength.toPx()
            val phasePx = phase * lenPx

            val path = Path()

            var x = 0f
            path.moveTo(0f, centerY - halfHeight + sin((phasePx) / lenPx * 2 * PI).toFloat() * ampPx)

            val step = 5f
            while (x <= progressWidth) {
                val y = centerY - halfHeight + sin((x + phasePx) / lenPx * 2 * PI).toFloat() * ampPx
                path.lineTo(x, y)
                x += step
            }

            path.lineTo(progressWidth, centerY + halfHeight + sin((progressWidth + phasePx) / lenPx * 2 * PI).toFloat() * ampPx)

            x = progressWidth
            while (x >= 0f) {
                val y = centerY + halfHeight + sin((x + phasePx) / lenPx * 2 * PI).toFloat() * ampPx
                path.lineTo(x, y)
                x -= step
            }

            path.close()
            drawPath(path, indicatorColor)
        }
    }
}