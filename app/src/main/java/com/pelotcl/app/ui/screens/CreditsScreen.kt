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
                text = "Les données de transport utilisées dans l’application proviennent " +
                        "exclusivement du site data.grandlyon.com et sont soumises à la Licence " +
                        "Mobilités.\n\n" +
                        "Les tracés géographiques des lignes de bus (incluant Chrono, Pleine " +
                        "Lune, Bus relais, Gare Express, Navette, Soyeuse, Zone Industrielle et " +
                        "Junior Direct), tramway, Rhônexpress, Trambus, métro, funiculaire et " +
                        "Navigone sont téléchargés directement depuis l’API publique délivrée " +
                        "par le SYTRAL sur le site data.grandlyon.com.\n\n" +
                        "Les positions géographiques ainsi que les noms des arrêts, les contenus " +
                        "des lignes et les horaires proviennent tous du fichier GTFS (General " +
                        "Transit Feed Specification) distribué par le SYTRAL sur le site " +
                        "data.grandlyon.com. Ces données sont manuellement mises à jour par les " +
                        "développeurs et subissent un prétraitement avant d’être utilisées. " +
                        "Aucune donnée n’est modifiée, uniquement leur organisation est " +
                        "transformée afin d’être traitée plus rapidement par l’application.\n\n" +
                        "Les pictogrammes des lignes proviennent également du site " +
                        "data.grandlyon.com et sont fournies par le SYTRAL au format SVG.\n\n" +
                        "Les alertes trafic et les positions de véhicules en temps réel sont " +
                        "fournies par le SYTRAL sur le site data.grandlyon.com via une API " +
                        "fermée requérant une authentification. En vertu de la Licence Mobilités, " +
                        "Dotshell met à disposition du public un miroir de ces données à " +
                        "l’adresse api.dotshell.eu/pelo/v1/. Le miroir fait des requêtes " +
                        "périodiquement à l’API du SYTRAL, enregistre en mémoire le résultat et " +
                        "redistribue au client une copie, accompagnée du timestamp de la dernière " +
                        "mise à jour. Les données sont purement copiées et redistribuées à " +
                        "l’exception des alertes trafic pour lesquelles les bus scolaires Junior " +
                        "Direct ont été fusionnés aux lignes classiques et les doublons ont été " +
                        "supprimés.",
                color = Color.White,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Column(modifier = Modifier.padding(bottom = 16.dp)) {
                ClickableLink(
                    label = "data.grandlyon.com",
                    url = "https://data.grandlyon.com",
                    uriHandler = uriHandler
                )
                Spacer(modifier = Modifier.height(4.dp))
                ClickableLink(
                    label = "api.dotshell.eu/pelo/v1/",
                    url = "https://api.dotshell.eu/pelo/v1/",
                    uriHandler = uriHandler
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Cartographie",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp, top = 8.dp)
            )

            Text(
                text = "Les données de cartographie sont fournies par MapLibre et " +
                        "OpenStreetMaps.\n\n" +
                        "Les fonds de carte (dénommés Positron, Dark Matter, OSM Bright et " +
                        "Liberty) sont fournis par OpenMapTiles. L’application utilise par " +
                        "défaut le thème Positron (Light) de Map Tiler.\n\n" +
                        "Les tuiles de la vue satellite proviennent de la ESRI World Imagery.",
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
                Spacer(modifier = Modifier.height(4.dp))
                ClickableLink(
                    label = "esri.com",
                    url = "https://www.esri.com",
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
                text = "L’application est développée de manière indépendante par Dotshell sous " +
                        "licence GPL-3.0. Pelo n’est pas affilié aux TCL ou au SYTRAL.\n\n" +
                        "Le code source est disponible sur la page GitHub de Dotshell aux dépôts " +
                        "suivants : pelo-android pour le code source de l’application Android, " +
                        "pelo-ios pour celui de l’application iOS/iPadOS, raptor-gtfs-pipeline, " +
                        "raptor-kt et raptor-sw pour le système d’itinéraire RAPTOR et enfin " +
                        "TCL-API-mirror pour le miroir des données en temps réel.\n\n" +
                        "Toute contribution est ouverte à la communauté.\n\n" +
                        "Pour plus d’information, rendez-vous sur dotshell.eu.",
                color = Color.White,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Column(modifier = Modifier.padding(bottom = 16.dp)) {
                ClickableLink(
                    label = "github.com/dotshell-org",
                    url = "https://github.com/dotshell-org",
                    uriHandler = uriHandler
                )
                Spacer(modifier = Modifier.height(4.dp))
                ClickableLink(
                    label = "dotshell.eu",
                    url = "https://www.dotshell.eu",
                    uriHandler = uriHandler
                )
            }

            Spacer(modifier = Modifier.height(80.dp))
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
