package com.pelotcl.app.transport.lyontcl.ui.screens.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
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
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pelotcl.app.core.ui.screens.about.AboutScreenContract

/**
 * Implémentation Lyon TCL des écrans "À propos"
 */
class LyonTclAboutScreen : AboutScreenContract {
    
    override val screenTitle: String = "À propos"
    
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun AboutScreenContent(
        onBackClick: () -> Unit,
        onCreditsClick: () -> Unit,
        onLegalClick: () -> Unit,
        onContactClick: () -> Unit
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(text = screenTitle, color = Color.White, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
                )
            },
            containerColor = Color.Black
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
            ) {
                AboutMenuItem("Crédits", onCreditsClick)
                AboutMenuItem("Mentions légales / CGU", onLegalClick)
                AboutMenuItem("Contact", onContactClick)
            }
        }
    }
    
    @Composable
    private fun AboutMenuItem(text: String, onClick: () -> Unit) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(16.dp)
                .fillMaxSize(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text(text = text, color = Color.White, fontSize = 18.sp)
            Spacer(modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.OpenInNew, "Ouvrir", tint = Color.White)
        }
    }
    
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun CreditsScreenContent(
        onBackClick: () -> Unit,
        onDataSourceClick: () -> Unit,
        onApiSourceClick: () -> Unit
    ) {
        val sections = getCreditSections()
        
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(text = "Crédits", color = Color.White, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
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
                sections.forEach { section ->
                    Text(
                        text = section.title,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    Text(
                        text = section.content,
                        color = Color.White,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    section.links.forEach { link ->
                        ClickableLink(link.label, link.url, uriHandler)
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
    
    @Composable
    private fun ClickableLink(label: String, url: String, uriHandler: UriHandler) {
        Row(
            modifier = Modifier
                .clickable { uriHandler.openUri(url) }
                .padding(vertical = 4.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text(text = label, color = Color(0xFF4285F4), fontSize = 14.sp)
            Spacer(modifier = Modifier.width(4.dp))
            Icon(Icons.AutoMirrored.Filled.OpenInNew, "Ouvrir", tint = Color(0xFF4285F4), modifier = Modifier.size(16.dp))
        }
    }
    
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun LegalScreenContent(onBackClick: () -> Unit) {
        val sections = getLegalSections()
        
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(text = "Mentions légales / CGU", color = Color.White, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
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
                        text = section.content,
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
    
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun ContactScreenContent(onBackClick: () -> Unit) {
        val contactInfo = getContactInfo()
        
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(text = "Contact", color = Color.White, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
                )
            },
            containerColor = Color.Black
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
            ) {
                contactInfo.email?.let { email ->
                    ContactItem("Email", email, "mailto:$email")
                }
                
                contactInfo.website?.let { website ->
                    ContactItem("Site web", website, website)
                }
                
                if (contactInfo.socialMedia.isNotEmpty()) {
                    Text(
                        text = "Réseaux sociaux",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
                    )
                    
                    contactInfo.socialMedia.forEach { social ->
                        ContactItem(social.platform, "@${social.username}", social.url)
                    }
                }
            }
        }
    }
    
    @Composable
    private fun ContactItem(label: String, value: String, url: String) {
        val uriHandler = LocalUriHandler.current
        
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Text(text = label, color = Color.White, fontSize = 14.sp)
            Row(
                modifier = Modifier
                    .clickable { uriHandler.openUri(url) }
                    .padding(vertical = 4.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(text = value, color = Color(0xFF4285F4), fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.OpenInNew, "Ouvrir", tint = Color(0xFF4285F4))
            }
        }
    }
    
    override fun getCreditSections(): List<AboutScreenContract.CreditSection> {
        return listOf(
            AboutScreenContract.CreditSection(
                title = "Données de transport",
                content = "Les données de transport utilisées dans l’application proviennent " +
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
                links = listOf(
                    AboutScreenContract.CreditLink("data.grandlyon.com", "https://data.grandlyon.com"),
                    AboutScreenContract.CreditLink("api.dotshell.eu/pelo/v1/", "https://api.dotshell.eu/pelo/v1/")
                )
            ),
            AboutScreenContract.CreditSection(
                title = "Cartographie",
                content = "Les données de cartographie sont fournies par MapLibre et " +
                        "OpenStreetMaps.\n\n" +
                        "Les fonds de carte (dénommés Positron, Dark Matter, OSM Bright et " +
                        "Liberty) sont fournis par OpenMapTiles. L’application utilise par " +
                        "défaut le thème Positron (Light) de Map Tiler.\n\n" +
                        "Les tuiles de la vue satellite proviennent de la ESRI World Imagery.",
                links = listOf(
                    AboutScreenContract.CreditLink("MapLibre", "https://maplibre.org"),
                    AboutScreenContract.CreditLink("OpenStreetMap", "https://www.openstreetmap.org"),
                    AboutScreenContract.CreditLink("OpenMapTiles", "https://openmaptiles.org")
                )
            ),
            AboutScreenContract.CreditSection(
                title = "Technologies",
                content = "Cette application est développée avec les technologies suivantes :\n" +
                        "• Kotlin et Jetpack Compose pour l'interface utilisateur\n" +
                        "• Hilt pour l'injection de dépendances\n" +
                        "• Retrofit pour les appels réseau\n" +
                        "• MapLibre pour la cartographie\n" +
                        "• Room pour la base de données locale\n" +
                        "• Coroutines pour la programmation asynchrone",
                links = emptyList()
            ),
            AboutScreenContract.CreditSection(
                title = "Licence",
                content = "Le code source de l’Application est distribué sous licence GPL‑3.0. " +
                        "Pelo n’est pas affilié aux TCL ou au SYTRAL.\n\n" +
                        "L’application utilise TCL-API-mirror pour le miroir des données en temps réel.",
                links = listOf(
                    AboutScreenContract.CreditLink("Licence GPL-3.0", "https://www.gnu.org/licenses/gpl-3.0.html"),
                    AboutScreenContract.CreditLink("TCL-API-mirror", "https://github.com/dotshell/tcl-api-mirror")
                )
            )
        )
    }
    
    override fun getLegalSections(): List<AboutScreenContract.LegalSection> {
        return listOf(
            AboutScreenContract.LegalSection(
                title = "Éditeur",
                content = "L’Application Pelo est éditée par Dotshell. Dotshell n’est pas affiliée " +
                        "aux TCL ni au SYTRAL. Pour toute question, se référer aux moyens de contact " +
                        "indiqués dans l’Application."
            ),
            AboutScreenContract.LegalSection(
                title = "Objet",
                content = "Les présentes CGU définissent les conditions d’accès et d’utilisation de " +
                        "l’Application Pelo (ci‑après « l’Application »). L’utilisation de " +
                        "l’Application vaut acceptation de ces conditions."
            ),
            AboutScreenContract.LegalSection(
                title = "Description de l'application",
                content = "L’Application aide l’utilisateur à se déplacer dans le réseau TCL (Lyon et " +
                        "agglomération) via des outils de recherche et de planification. Les sources " +
                        "tierces sont listées dans la page « Crédits »."
            ),
            AboutScreenContract.LegalSection(
                title = "Permissions",
                content = "L’Application peut solliciter les permissions suivantes selon les besoins " +
                        "techniques :\n" +
                        "• Localisation : Pour afficher les arrêts proches (traitée localement).\n" +
                        "• Réseau/Internet : Pour récupérer les horaires en temps réel."
            ),
            AboutScreenContract.LegalSection(
                title = "Traitement des données et confidentialité",
                content = "Pelo est conçue selon le principe de la protection des données dès la " +
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
            AboutScreenContract.LegalSection(
                title = "Responsabilité",
                content = "Les informations (horaires, perturbations) sont fournies à titre indicatif. " +
                        "Dotshell décline toute responsabilité en cas d’inexactitude des données " +
                        "provenant de tiers ou d’indisponibilité du réseau de transport."
            ),
            AboutScreenContract.LegalSection(
                title = "Propriété intellectuelle",
                content = "Le code source de l’Application est distribué sous licence GPL‑3.0. " +
                        "Les marques et logos des réseaux de transport demeurent la propriété " +
                        "de leurs titulaires respectifs."
            ),
            AboutScreenContract.LegalSection(
                title = "Mises à jour",
                content = "Dotshell se réserve le droit de modifier ces CGU. La version applicable est " +
                        "celle disponible dans l'Application au moment de son utilisation."
            )
        )
    }
    
    override fun getContactInfo(): AboutScreenContract.ContactInfo {
        return AboutScreenContract.ContactInfo(
            email = "contact@pelo.app",
            website = "https://pelo.app",
            socialMedia = listOf(
                AboutScreenContract.SocialMediaLink(
                    platform = "Twitter",
                    url = "https://twitter.com/pelo_app",
                    username = "pelo_app"
                ),
                AboutScreenContract.SocialMediaLink(
                    platform = "GitHub",
                    url = "https://github.com/dotshell/pelo",
                    username = "dotshell"
                )
            )
        )
    }
}