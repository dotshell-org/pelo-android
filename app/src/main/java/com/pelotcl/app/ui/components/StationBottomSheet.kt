package com.pelotcl.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessible
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pelotcl.app.ui.theme.Gray700

/**
 * Données d'une station pour l'affichage dans le bottom sheet
 */
data class StationInfo(
    val nom: String,
    val lignes: List<String>, // Liste des noms de lignes (ex: ["A", "D", "F1"])
    val isPmr: Boolean = false,
    val desserte: String = "" // Chaîne de desserte complète pour référence
)

/**
 * Bottom sheet affichant les informations d'une station
 * (nom de la station et toutes les lignes qui la desservent)
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StationBottomSheet(
    stationInfo: StationInfo?,
    sheetState: SheetState,
    onDismiss: () -> Unit
) {
    if (stationInfo != null) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
            ) {
                // Nom de la station
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stationInfo.nom,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        modifier = Modifier.weight(1f)
                    )
                    
                    // Icône PMR si la station est accessible
                    if (stationInfo.isPmr) {
                        Icon(
                            imageVector = Icons.Default.Accessible,
                            contentDescription = "Station accessible PMR",
                            tint = Color(0xFF2563EB),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Section des lignes
                Text(
                    text = "Lignes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Gray700,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                // Affichage des icônes de lignes
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    stationInfo.lignes.forEach { ligne ->
                        LineIcon(lineName = ligne)
                    }
                }
            }
        }
    }
}

/**
 * Affiche l'icône d'une ligne de transport
 */
@Composable
private fun LineIcon(lineName: String) {
    val context = LocalContext.current
    val drawableName = getDrawableNameForLine(lineName)
    val resourceId = context.resources.getIdentifier(drawableName, "drawable", context.packageName)
    
    if (resourceId != 0) {
        Image(
            painter = painterResource(id = resourceId),
            contentDescription = "Ligne $lineName",
            modifier = Modifier.size(48.dp)
        )
    } else {
        // Fallback si l'icône n'existe pas
        Text(
            text = lineName,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = Gray700
        )
    }
}

/**
 * Convertit un nom de ligne en nom de drawable
 * (même logique que BusIconHelper mais dupliquée ici pour éviter les dépendances circulaires)
 */
private fun getDrawableNameForLine(lineName: String): String {
    if (lineName.isBlank()) {
        return ""
    }
    
    val isNumericOnly = lineName.all { it.isDigit() }
    
    return if (isNumericOnly) {
        "_$lineName"
    } else {
        lineName.lowercase()
    }
}
