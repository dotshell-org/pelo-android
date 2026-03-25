package com.pelotcl.app.generic.ui.components.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pelotcl.app.generic.ui.components.search.StationSearchResult
import com.pelotcl.app.generic.ui.components.search.TransportSearchBar
import com.pelotcl.app.generic.ui.components.search.TransportSearchContent
import com.pelotcl.app.generic.ui.theme.Gray700
import com.pelotcl.app.generic.ui.theme.PrimaryColor
import com.pelotcl.app.generic.ui.theme.AccentColor
import com.pelotcl.app.generic.ui.theme.SecondaryColor
import com.pelotcl.app.generic.ui.viewmodel.TransportViewModel

/**
 * Dialog for creating a new favorite from predefined presets.
 * @param onDismiss Callback when dialog is dismissed
 * @param onFavoriteCreated Callback when a new favorite is created
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddFavoriteDialog(
    onDismiss: () -> Unit,
    onFavoriteCreated: (String, String, String) -> Unit,
    viewModel: TransportViewModel,
    initialStopName: String? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val presets = remember {
        listOf(
            FavoritePreset("Maison", "home"),
            FavoritePreset("Travail", "work"),
            FavoritePreset("Ecole", "school"),
            FavoritePreset("Courses", "shopping"),
            FavoritePreset("Gare routière", "bus"),
            FavoritePreset("Gare ferroviaire", "train"),
            FavoritePreset("Autre", "star")
        )
    }

    var selectedPreset by remember { mutableStateOf(presets.firstOrNull()) }
    var customOtherTitle by remember { mutableStateOf("") }
    var selectedStop by remember(initialStopName) {
        mutableStateOf(initialStopName?.let {
            StationSearchResult(
                stopName = it,
                lines = emptyList()
            )
        })
    }
    var stopSearchOverlayQuery by remember { mutableStateOf("") }
    var showStopSearchFullscreen by remember { mutableStateOf(false) }

    LaunchedEffect(showStopSearchFullscreen) {
        if (showStopSearchFullscreen) {
            stopSearchOverlayQuery = ""
        }
    }

    val isOtherSelected = selectedPreset?.name == "Autre"
    val finalFavoriteTitle =
        if (isOtherSelected) customOtherTitle.trim() else (selectedPreset?.name ?: "")

    LaunchedEffect(Unit) {
        sheetState.expand()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SecondaryColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Nouveau favori",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryColor
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Fermer",
                        tint = Gray700
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Preset selection
            Text(
                text = "Type de favori",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = PrimaryColor
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presets.forEach { preset ->
                    IconSelectionButton(
                        iconName = preset.iconName,
                        label = preset.name,
                        isSelected = preset == selectedPreset,
                        onClick = {
                            selectedPreset = preset
                            if (preset.name != "Autre") {
                                customOtherTitle = ""
                            }
                        }
                    )
                }
            }

            if (isOtherSelected) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Titre du favori",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = PrimaryColor
                )
                Spacer(modifier = Modifier.height(8.dp))
                BasicTextField(
                    value = customOtherTitle,
                    onValueChange = { customOtherTitle = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color(0xFFF5F5F5))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = PrimaryColor),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    decorationBox = { innerTextField ->
                        if (customOtherTitle.isBlank()) {
                            Text(
                                text = "Ex: Salle de sport",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray
                            )
                        }
                        innerTextField()
                    }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Stop selection
            Text(
                text = "Arrêt associé",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = PrimaryColor
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(PrimaryColor)
                    .clickable { showStopSearchFullscreen = true }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = SecondaryColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = selectedStop?.stopName ?: "Rechercher un arrêt",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SecondaryColor
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Create button
            Button(
                onClick = {
                    val preset = selectedPreset
                    val stop = selectedStop
                    if (preset != null && stop != null && finalFavoriteTitle.isNotBlank()) {
                        onFavoriteCreated(finalFavoriteTitle, preset.iconName, stop.stopName)
                        onDismiss()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryColor,
                    contentColor = SecondaryColor,
                    disabledContainerColor = Color.Gray,
                    disabledContentColor = SecondaryColor
                ),
                shape = RoundedCornerShape(28.dp),
                enabled = selectedPreset != null && selectedStop != null && finalFavoriteTitle.isNotBlank()
            ) {
                Text(
                    text = "Créer le favori",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showStopSearchFullscreen) {
        Dialog(
            onDismissRequest = { showStopSearchFullscreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PrimaryColor)
            ) {
                TransportSearchBar(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize(),
                    content = TransportSearchContent.STOPS_ONLY,
                    showHistory = false,
                    startExpanded = true,
                    showDarkOutline = false,
                    searchPlaceholder = "Rechercher un arrêt",
                    query = stopSearchOverlayQuery,
                    onQueryChange = { q ->
                        stopSearchOverlayQuery = q
                    },
                    onExpandedChange = { expanded ->
                        if (!expanded) showStopSearchFullscreen = false
                    },
                    onStopPrimary = { result ->
                        selectedStop = result
                        stopSearchOverlayQuery = ""
                        showStopSearchFullscreen = false
                    }
                )
            }
        }
    }
}

/**
 * Icon selection button
 */
@Composable
private fun IconSelectionButton(
    iconName: String,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val icon = favoriteIcon(iconName)

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .background(if (isSelected) Color(0x1A000000) else Color.Transparent)
            .border(1.dp, if (isSelected) PrimaryColor else Color.Gray, RoundedCornerShape(24.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(PrimaryColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = iconName,
                    tint = SecondaryColor,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = PrimaryColor,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
            if (isSelected) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(AccentColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = SecondaryColor,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}


private data class FavoritePreset(
    val name: String,
    val iconName: String
)