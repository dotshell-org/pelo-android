package com.pelotcl.app.ui.screens

import android.text.format.Formatter
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pelotcl.app.data.offline.OfflineDataInfo
import com.pelotcl.app.data.offline.OfflineDownloadState
import com.pelotcl.app.ui.viewmodel.TransportViewModel
import kotlinx.coroutines.launch
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
    val scope = rememberCoroutineScope()
    val offlineDataInfo by viewModel.offlineDataInfo.collectAsState()
    val downloadState by viewModel.offlineDataManager.downloadState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }

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
                    "Les donn\u00e9es hors ligne sont disponibles"
                else
                    "T\u00e9l\u00e9chargez les donn\u00e9es pour utiliser l'appli sans connexion",
                color = Color.Gray,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Status card
            if (offlineDataInfo.isAvailable) {
                OfflineStatusCard(offlineDataInfo, context)
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Download / Update button
            when (val state = downloadState) {
                is OfflineDownloadState.Idle, is OfflineDownloadState.Complete, is OfflineDownloadState.Error -> {
                    Button(
                        onClick = {
                            scope.launch {
                                viewModel.offlineDataManager.downloadAllOfflineData()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF3B82F6)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (offlineDataInfo.isAvailable) "Mettre \u00e0 jour" else "T\u00e9l\u00e9charger les donn\u00e9es",
                            fontSize = 16.sp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    if (state is OfflineDownloadState.Error) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.message,
                            color = Color(0xFFEF4444),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                is OfflineDownloadState.Downloading -> {
                    DownloadProgressCard(state)
                }
            }

            // Delete button
            if (offlineDataInfo.isAvailable && downloadState !is OfflineDownloadState.Downloading) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFFEF4444)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Supprimer les donn\u00e9es hors ligne",
                        fontSize = 16.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Info card
            OfflineInfoCard()
        }

        // Back button
        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 4.dp, top = 8.dp)
                .align(Alignment.TopStart)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Retour",
                tint = Color.White
            )
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Supprimer les donn\u00e9es hors ligne ?") },
            text = {
                Text("Les donn\u00e9es t\u00e9l\u00e9charg\u00e9es seront supprim\u00e9es. Vous devrez les t\u00e9l\u00e9charger \u00e0 nouveau pour utiliser le mode hors ligne.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        scope.launch {
                            viewModel.offlineDataManager.deleteAllOfflineData()
                        }
                    }
                ) {
                    Text("Supprimer", color = Color(0xFFEF4444))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }
}

@Composable
private fun OfflineStatusCard(info: OfflineDataInfo, context: android.content.Context) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            StatusRow(
                label = "Derni\u00e8re mise \u00e0 jour",
                value = formatTimestamp(info.lastDownloadTimestamp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            StatusRow(
                label = "Espace utilis\u00e9",
                value = Formatter.formatFileSize(context, info.totalSizeBytes)
            )
            Spacer(modifier = Modifier.height(8.dp))
            StatusRow(
                label = "Lignes de bus",
                value = if (info.busLinesCount > 0) "${info.busLinesCount} lignes" else "Non t\u00e9l\u00e9charg\u00e9es"
            )
            Spacer(modifier = Modifier.height(8.dp))
            StatusRow(
                label = "Tuiles de carte",
                value = if (info.mapTilesDownloaded) "T\u00e9l\u00e9charg\u00e9es" else "Non t\u00e9l\u00e9charg\u00e9es"
            )
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = Color.Gray, fontSize = 14.sp)
        Text(text = value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DownloadProgressCard(state: OfflineDownloadState.Downloading) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color(0xFF3B82F6),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = state.stepDescription,
                    color = Color.White,
                    fontSize = 14.sp
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF3B82F6),
                trackColor = Color(0xFF3A3A3C),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${(state.progress * 100).toInt()}%",
                color = Color.Gray,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun OfflineInfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = Color(0xFF3B82F6),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Fonctionnalit\u00e9s hors ligne",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            FeatureRow("Carte", true)
            FeatureRow("Lignes et arr\u00eats", true)
            FeatureRow("Horaires", true)
            FeatureRow("Recherche", true)
            FeatureRow("Calcul d'itin\u00e9raire", true)
            FeatureRow("Alertes trafic (derni\u00e8res connues)", true)
            FeatureRow("Suivi v\u00e9hicules en temps r\u00e9el", false)
        }
    }
}

@Composable
private fun FeatureRow(feature: String, available: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (available) "\u2713" else "\u2717",
            color = if (available) Color(0xFF4CAF50) else Color(0xFFEF4444),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = feature,
            color = if (available) Color.White else Color.Gray,
            fontSize = 14.sp
        )
    }
}

private fun formatTimestamp(timestamp: Long): String {
    if (timestamp == 0L) return "Jamais"
    val sdf = SimpleDateFormat("d MMM yyyy '\u00e0' HH:mm", Locale.FRANCE)
    return sdf.format(Date(timestamp))
}
