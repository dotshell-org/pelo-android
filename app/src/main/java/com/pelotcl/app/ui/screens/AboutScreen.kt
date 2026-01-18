package com.pelotcl.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowRight
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Optional

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBackClick: () -> Unit,
    onLegalClick: () -> Unit = {},
    onCreditsClick: () -> Unit = {},
    onContactClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "À propos",
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
            // App version
            AboutMenuItem(
                title = "Version de l'application",
                subtitle = "0.0.0",
                onClick = {}
            )

            HorizontalDivider()

            // Legal mentions / Terms of use
            AboutMenuItem(
                title = "Mentions légales / CGU",
                onClick = onLegalClick
            )

            HorizontalDivider()

            // Credits
            AboutMenuItem(
                title = "Crédits",
                onClick = onCreditsClick
            )

            HorizontalDivider()

            // Contact / Signal a bug
            AboutMenuItem(
                title = "Nous contacter / Signaler un bug",
                onClick = onContactClick
            )
        }
    }
}

@Composable
private fun AboutMenuItem(
    title: String,
    subtitle: String = "",
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardModifier = modifier
        .fillMaxWidth()
        .padding(vertical = 8.dp)
        .clickable(onClick = onClick)

    Card(
        modifier = cardModifier,
        colors = CardDefaults.cardColors(
            containerColor = Color.Black
        )
    ) {
        Row {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp)
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (subtitle != "") {
                    Text(
                        text = subtitle,
                        color = Color.Gray,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            if (subtitle == "") {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = "Next Arrow Icon",
                    tint = Color.White,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}
