package com.pelotcl.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onItineraryClick: () -> Unit,
    onAboutClick: () -> Unit,
    onMapStyleClick: () -> Unit,
    onOfflineClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var clickCount by remember { mutableIntStateOf(0) }
    var isEasterEggActive by remember { mutableStateOf(false) }
    val hapticFeedback = LocalHapticFeedback.current
    
    // Reset click count after 2 seconds
    LaunchedEffect(clickCount) {
        if (clickCount > 0) {
            delay(2000)
            if (clickCount < 3) clickCount = 0
        }
    }
    
    // Auto-disable easter egg after 10 seconds
    LaunchedEffect(isEasterEggActive) {
        if (isEasterEggActive) {
            delay(10000)
            isEasterEggActive = false
        }
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp)
                .padding(top = 40.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val rotation by animateFloatAsState(
                targetValue = if (isEasterEggActive) 3600f else 0f,
                animationSpec = tween(10000),
                label = "logo_rotation"
            )

            Image(
                painter = painterResource(id = com.pelotcl.app.R.drawable.ic_launcher_foreground),
                contentDescription = "Logo Pelo",
                modifier = Modifier
                    .size(200.dp)
                    .padding(bottom = 48.dp)
                    .rotate(rotation)
                    .clickable {
                        clickCount++
                        if (clickCount >= 3) {
                            clickCount = 0
                            isEasterEggActive = true
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    }
            )

            SettingsMenuRow(
                title = "Itinéraire",
                onClick = onItineraryClick
            )
            HorizontalDivider(color = Color(0xFF3A3A3C))
            SettingsMenuRow(
                title = "Fond de carte",
                onClick = onMapStyleClick
            )
            HorizontalDivider(color = Color(0xFF3A3A3C))
            SettingsMenuRow(
                title = "Mode hors ligne",
                onClick = onOfflineClick
            )
            HorizontalDivider(color = Color(0xFF3A3A3C))
            SettingsMenuRow(
                title = "À propos",
                onClick = onAboutClick
            )

            Spacer(modifier = Modifier.height(24.dp))
        }

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
}

@Composable
private fun SettingsMenuRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = "Next Arrow Icon",
                tint = Color.White
            )
        }
    }
}
