package com.pelotcl.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Crédits",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour",
                            tint = Color.White
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
        val uriHandler = LocalUriHandler.current
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Données de transport",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = "Les données de transport utilisées dans l'application proviennent " +
                        "exclusivement du site de la métropole de Lyon [data.grandlyon.com]. Les " +
                        "tracés géographiques des lignes de bus (incluant Chrono, Pleine Lune, " +
                        "Bus relais, Gare Express, Navette, Soyeuse, Zone industrielle et Junior " +
                        "Direct), tramway, Rhône Express, trambus, métropolitain, funiculaire et" +
                        "Navigone sont téléchargés en direct depuis l'API publique de la " +
                        "métropole de Lyon. Il en va de même pour les positions géographiques des " +
                        "arrêts du réseau qui sont téléchargées en direct depuis l'API. Quant " +
                        "aux noms des arrêts et des lignes, ainsi qu'aux horaires, l'application " +
                        "utilise comme source le fichier GTFS fourni par la métropole de Lyon sur" +
                        "son site. Les icônes des lignes proviennent également de ce même site.",
                color = Color.White,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            ClickableLink(
                label = "data.grandlyon.com",
                url = "https://data.grandlyon.com",
                uriHandler = uriHandler
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Cartographie",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp, top = 8.dp)
            )

            Text(
                text = "Les données de cartographie sont fournies par MapLibre [maplibre.org] et " +
                        "OpenStreetMaps [openstreetmap.org]. Le fond de carte est fourni par " +
                        "OpenMapTiles [openmaptiles.org]. L'application utilise le thème \"Light\" " +
                        "de Map Tiler [maptiler.com].",
                color = Color.White,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Column(modifier = Modifier.padding(bottom = 16.dp)) {
                ClickableLink(
                    label = "maplibre.org",
                    url = "https://maplibre.org",
                    uriHandler = uriHandler
                )
                Spacer(modifier = Modifier.height(4.dp))
                ClickableLink(
                    label = "openstreetmap.org",
                    url = "https://www.openstreetmap.org",
                    uriHandler = uriHandler
                )
                Spacer(modifier = Modifier.height(4.dp))
                ClickableLink(
                    label = "openmaptiles.org",
                    url = "https://openmaptiles.org",
                    uriHandler = uriHandler
                )
                Spacer(modifier = Modifier.height(4.dp))
                ClickableLink(
                    label = "maptiler.com",
                    url = "https://www.maptiler.com",
                    uriHandler = uriHandler
                )
            }

            Text(
                text = "Développement",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp, top = 8.dp)
            )

            Text(
                text = "L'application est développée de manière indépendante par Dotshell [dotshell.eu] " +
                        "sous license GPL-3.0. Pelo n'est pas affilié aux TCL ou à la SYTRAL. Les " +
                        "données utilisées sont totalement publiques et accessibles à tous. Il est " +
                        "possible à n'importe qui d'aider au développement de l'application sur " +
                        "GitHub [github.com/dotshell-org/pelo-android].",
                color = Color.White,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            ClickableLink(
                label = "dotshell.eu",
                url = "https://www.dotshell.eu",
                uriHandler = uriHandler
            )
            ClickableLink(
                label = "github.com/dotshell-org/pelo-android",
                url = "https://github.com/dotshell-org/pelo-android",
                uriHandler = uriHandler
            )

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ClickableLink(
    label: String,
    url: String,
    uriHandler: androidx.compose.ui.platform.UriHandler,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.clickable { uriHandler.openUri(url) }
    ) {
        Text(
            text = label,
            color = Color(0xFF3B82F6),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = Icons.Filled.OpenInNew,
            contentDescription = "Ouvrir",
            tint = Color(0xFF3B82F6),
            modifier = Modifier
                .padding(top = 2.dp)
                .width(14.dp)
                .height(14.dp)
        )
    }
}
