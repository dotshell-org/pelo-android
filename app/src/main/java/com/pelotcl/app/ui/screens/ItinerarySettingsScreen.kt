package com.pelotcl.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Euro
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.pelotcl.app.data.repository.ItineraryPreferencesRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItinerarySettingsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val itineraryPrefsRepo = remember { ItineraryPreferencesRepository(context) }

    var jdLinesEnabled by remember { mutableStateOf(itineraryPrefsRepo.isJdLinesEnabled()) }
    var rxLineEnabled by remember { mutableStateOf(itineraryPrefsRepo.isRxLineEnabled()) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Itinéraire",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour",
                            tint = Color.White,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black
                )
            )
        },
        containerColor = Color.Black
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            ItineraryLineCategorySection(
                lines = listOf(
                    ItineraryLineOption(
                        title = "Junior Direct (JD)",
                        subtitle = "Lignes scolaires",
                        isSelected = jdLinesEnabled,
                        onClick = {
                            val enabled = !jdLinesEnabled
                            jdLinesEnabled = enabled
                            itineraryPrefsRepo.setJdLinesEnabled(enabled)
                        }
                    ),
                    ItineraryLineOption(
                        title = "RhoneExpress (RX)",
                        subtitle = "Navette aéroport",
                        isSelected = rxLineEnabled,
                        onClick = {
                            val enabled = !rxLineEnabled
                            rxLineEnabled = enabled
                            itineraryPrefsRepo.setRxLineEnabled(enabled)
                        }
                    )
                )
            )
        }
    }
}

private data class ItineraryLineOption(
    val title: String,
    val subtitle: String,
    val isSelected: Boolean,
    val onClick: () -> Unit
)

@Composable
private fun ItineraryLineCategorySection(
    lines: List<ItineraryLineOption>
) {
    Column {
        // Category header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Euro,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Lignes à tarification spéciale",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Line options card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1C1C1E)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column {
                lines.forEachIndexed { index, line ->
                    ItineraryLineItem(
                        title = line.title,
                        subtitle = line.subtitle,
                        isSelected = line.isSelected,
                        onClick = line.onClick
                    )

                    // Add divider between items (but not after the last one)
                    if (index < lines.size - 1) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 56.dp)
                                .height(0.5.dp)
                                .background(Color(0xFF3A3A3C))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ItineraryLineItem(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Selection indicator
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(
                    width = if (isSelected) 0.dp else 2.dp,
                    color = if (isSelected) Color.Transparent else Color(0xFF8E8E93),
                    shape = RoundedCornerShape(8.dp)
                )
                .background(
                    if (isSelected) Color(0xFFE60000) else Color.Transparent,
                    shape = RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Sélectionné",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Style name
        Column {
            Text(
                text = title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
            )
            Text(
                text = subtitle,
                color = Color(0xFFAAAAAA),
                fontSize = 13.sp
            )
        }
    }
}
