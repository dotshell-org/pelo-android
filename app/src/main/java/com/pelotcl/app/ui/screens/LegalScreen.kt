package com.pelotcl.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sections = listOf(
        LegalSection(
            title = "Éditeur",
            body = "L’Application Pelo est éditée par Dotshell. Dotshell n’est pas affiliée " +
                "aux TCL ni au SYTRAL. Pour toute question, se référer aux moyens de contact " +
                "indiqués dans l’Application."
        ),
        LegalSection(
            title = "Objet",
            body = "Les présentes CGU définissent les conditions d’accès et d’utilisation de " +
                "l’Application Pelo (ci‑après « l’Application »). L’utilisation de " +
                "l’Application vaut acceptation de ces conditions."
        ),
        LegalSection(
            title = "Description de l’application",
            body = "L’Application aide l’utilisateur à se déplacer dans le réseau TCL (Lyon et " +
                "agglomération) via des outils de recherche et de planification. Les sources " +
                "tierces sont listées dans la page « Crédits »."
        ),
        LegalSection(
            title = "Permissions",
            body = "L’Application peut solliciter les permissions suivantes selon les besoins " +
                "techniques :\n" +
                "• Localisation : Pour afficher les arrêts proches (traitée localement).\n" +
                "• Réseau/Internet : Pour récupérer les horaires en temps réel."
        ),
        LegalSection(
            title = "Traitement des données et confidentialité",
            body = "Pelo est conçue selon le principe de la protection des données dès la " +
                "conception.\n\n" +
                "5.1. Stockage Local Exclusif\n" +
                "L'intégralité de vos données d'usage (historique de recherche, paramètres " +
                "de l'application, préférences d'affichage) est stockée exclusivement en " +
                "local sur votre appareil. Dotshell ne dispose d'aucun accès à ces " +
                "informations.\n\n" +
                "5.2. Cas particulier des Favoris (Connexion Socket)\n" +
                "Pour permettre la mise à jour en temps réel de vos lignes et arrêts favoris, " +
                "une connexion technique temporaire (socket) est établie à l'ouverture de " +
                "l'application et rompue dès sa fermeture.\n" +
                "• Cet échange est strictement anonyme.\n" +
                "• Aucune donnée permettant de vous identifier n'est transmise ou enregistrée " +
                "sur un serveur externe.\n" +
                "• Ce flux sert uniquement à la synchronisation technique des données de " +
                "transport durant votre session de navigation.\n\n" +
                "5.3. Absence de revente\n" +
                "Dotshell ne collecte, ne vend, ni ne commercialise aucune donnée personnelle. " +
                "Les requêtes réseau effectuées servent uniquement à interroger les API de " +
                "transport nécessaires au service (voir page « Crédits »)."
        ),
        LegalSection(
            title = "Responsabilité",
            body = "Les informations (horaires, perturbations) sont fournies à titre indicatif. " +
                "Dotshell décline toute responsabilité en cas d’inexactitude des données " +
                "provenant de tiers ou d’indisponibilité du réseau de transport."
        ),
        LegalSection(
            title = "Propriété intellectuelle",
            body = "Le code source de l’Application est distribué sous licence GPL‑3.0. " +
                "Les marques et logos des réseaux de transport demeurent la propriété " +
                "de leurs titulaires respectifs."
        ),
        LegalSection(
            title = "Mises à jour",
            body = "Dotshell se réserve le droit de modifier ces CGU. La version applicable est " +
                "celle disponible dans l'Application au moment de son utilisation."
        )
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Mentions légales / CGU",
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            sections.forEach { section ->
                Text(
                    text = section.title,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Text(
                    text = section.body,
                    color = Color.White,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

private data class LegalSection(
    val title: String,
    val body: String
)
