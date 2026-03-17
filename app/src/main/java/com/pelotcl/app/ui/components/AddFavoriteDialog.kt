package com.pelotcl.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pelotcl.app.R
import com.pelotcl.app.data.model.Favorite
import com.pelotcl.app.ui.theme.Gray700
import com.pelotcl.app.ui.theme.Red500

/**
 * Dialog for creating a new favorite
 * @param onDismiss Callback when dialog is dismissed
 * @param onFavoriteCreated Callback when a new favorite is created
 * @param availableStops List of available stops to choose from
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddFavoriteDialog(
    onDismiss: () -> Unit,
    onFavoriteCreated: (String, String, String) -> Unit,
    availableStops: List<String>
) {
    val sheetState = rememberModalBottomSheetState()
    var favoriteName by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf("home") }
    var selectedStop by remember { mutableStateOf<String?>(null) }
    var showStopSelection by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .verticalScroll(rememberScrollState())
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
                    color = Color.Black
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

            // Name field
            Text(
                text = "Nom du favori",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Color.Black
            )
            Spacer(modifier = Modifier.height(8.dp))
            BasicTextField(
                value = favoriteName,
                onValueChange = { favoriteName = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF5F5F5))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.Black),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (favoriteName.isNotBlank() && selectedStop != null) {
                            onFavoriteCreated(
                                favoriteName,
                                selectedIcon,
                                selectedStop!!
                            )
                            onDismiss()
                        }
                    }
                ),
                decorationBox = { innerTextField ->
                    if (favoriteName.isEmpty()) {
                        Text(
                            text = "Ex: Maison, Travail, École",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray
                        )
                    }
                    innerTextField()
                }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Icon selection
            Text(
                text = "Icône",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Color.Black
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Favorite.DEFAULT_ICONS.forEach { iconName ->
                    IconSelectionButton(
                        iconName = iconName,
                        isSelected = iconName == selectedIcon,
                        onClick = { selectedIcon = iconName }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Stop selection
            Text(
                text = "Arrêt associé",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Color.Black
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showStopSelection = true }
                    .background(Color(0xFFF5F5F5))
                    .padding(16.dp)
            ) {
                if (selectedStop != null) {
                    Text(
                        text = selectedStop!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Black
                    )
                } else {
                    Text(
                        text = "Sélectionner un arrêt",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Create button
            Button(
                onClick = {
                    if (favoriteName.isNotBlank() && selectedStop != null) {
                        onFavoriteCreated(
                            favoriteName,
                            selectedIcon,
                            selectedStop!!
                        )
                        onDismiss()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Black,
                    contentColor = Color.White,
                    disabledContainerColor = Color.Gray,
                    disabledContentColor = Color.White
                ),
                enabled = favoriteName.isNotBlank() && selectedStop != null
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

    // Stop selection dialog
    if (showStopSelection) {
        StopSelectionDialog(
            availableStops = availableStops,
            onStopSelected = { stop ->
                selectedStop = stop
                showStopSelection = false
            },
            onDismiss = { showStopSelection = false }
        )
    }
}

/**
 * Icon selection button
 */
@Composable
private fun IconSelectionButton(
    iconName: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val iconResId = when (iconName.lowercase()) {
        "home" -> R.drawable.ic_home
        "work" -> R.drawable.ic_work
        "school" -> R.drawable.ic_school
        "shopping" -> R.drawable.ic_shopping
        "star" -> R.drawable.ic_star
        "heart" -> R.drawable.ic_heart
        "bus" -> R.drawable.ic_bus
        "train" -> R.drawable.ic_train
        "location" -> R.drawable.ic_location
        "flag" -> R.drawable.ic_flag
        else -> R.drawable.ic_star
    }

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .background(if (isSelected) Color(0x1A000000) else Color.Transparent)
            .border(1.dp, if (isSelected) Color.Black else Color.Gray, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = iconResId),
                contentDescription = iconName,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Red500),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

/**
 * Dialog for selecting a stop
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StopSelectionDialog(
    availableStops: List<String>,
    onStopSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    var searchQuery by remember { mutableStateOf("") }

    val filteredStops = if (searchQuery.isBlank()) {
        availableStops
    } else {
        availableStops.filter { stop ->
            stop.contains(searchQuery, ignoreCase = true)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White
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
                    text = "Sélectionner un arrêt",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Fermer",
                        tint = Gray700
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Search field
            BasicTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF5F5F5))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.Black),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(
                    onSearch = {}
                ),
                decorationBox = { innerTextField ->
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "Rechercher un arrêt",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray
                        )
                    }
                    innerTextField()
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Stop list
            if (filteredStops.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Aucun arrêt trouvé",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    filteredStops.forEach { stop ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onStopSelected(stop) }
                                .padding(vertical = 8.dp)
                        ) {
                            Text(
                                text = stop,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Black,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}