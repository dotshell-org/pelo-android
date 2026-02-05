package com.pelotcl.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onAboutClick: () -> Unit,
    onMapStyleClick: () -> Unit,
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
        // Main content - centered vertically
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo Pelo avec animation de rotation continue
            val rotation by animateFloatAsState(
                targetValue = if (isEasterEggActive) 3600f else 0f, // 10 tours complets
                animationSpec = tween(10000), // 10 secondes
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
                        if (clickCount == 3) {
                            isEasterEggActive = true
                            clickCount = 0
                            // Vibration haptic feedback
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    }
            )

            // Bouton Fond de carte
            Button(
                onClick = onMapStyleClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2C2C2C)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Fond de carte",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            // Espacement entre les boutons
            Spacer(
                modifier = Modifier.height(16.dp)
            )

            // Bouton À propos
            Button(
                onClick = onAboutClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2C2C2C)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "À propos",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }

        // Back button overlaid at top-left, below status bar
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
