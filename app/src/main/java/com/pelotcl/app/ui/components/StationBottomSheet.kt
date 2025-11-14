package com.pelotcl.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Accessible
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import com.pelotcl.app.ui.theme.Gray200
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationBottomSheet(
    stationInfo: StationInfo?,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onLineClick: (String) -> Unit = {}
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
                
                // Liste scrollable des lignes
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    stationInfo.lignes.forEachIndexed { index, ligne ->
                        LineListItem(
                            lineName = ligne,
                            onClick = { onLineClick(ligne) }
                        )
                        
                        // Divider entre les lignes, sauf après la dernière
                        if (index < stationInfo.lignes.size - 1) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = Gray200
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Item de liste pour une ligne de transport
 */
@Composable
private fun LineListItem(
    lineName: String,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val drawableName = getDrawableNameForLine(lineName)
    val resourceId = context.resources.getIdentifier(drawableName, "drawable", context.packageName)
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icône de la ligne à gauche
        if (resourceId != 0) {
            Image(
                painter = painterResource(id = resourceId),
                contentDescription = "Ligne $lineName",
                modifier = Modifier.size(60.dp)
            )
        } else {
            // Fallback si l'icône n'existe pas
            Text(
                text = lineName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Gray700
            )
        }
        
        // Chevron à droite
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Voir la ligne $lineName",
            tint = Gray700,
            modifier = Modifier.size(24.dp)
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
