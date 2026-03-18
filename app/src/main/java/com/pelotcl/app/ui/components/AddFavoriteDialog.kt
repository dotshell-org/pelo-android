package com.pelotcl.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pelotcl.app.ui.theme.Gray700
import com.pelotcl.app.ui.theme.Red500
import com.pelotcl.app.ui.viewmodel.TransportViewModel
import com.pelotcl.app.utils.BusIconHelper
import kotlinx.coroutines.delay

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
    viewModel: TransportViewModel
) {
    val sheetState = rememberModalBottomSheetState()
    val context = LocalContext.current

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

    var selectedPreset by remember { mutableStateOf<FavoritePreset?>(presets.firstOrNull()) }
    var customOtherTitle by remember { mutableStateOf("") }
    var selectedStop by remember { mutableStateOf<StationSearchResult?>(null) }
    var stopQuery by remember { mutableStateOf("") }
    var stopResults by remember { mutableStateOf<List<StationSearchResult>>(emptyList()) }
    var showStopSearchFullscreen by remember { mutableStateOf(false) }

    val isOtherSelected = selectedPreset?.name == "Autre"
    val finalFavoriteTitle = if (isOtherSelected) customOtherTitle.trim() else (selectedPreset?.name ?: "")

    LaunchedEffect(stopQuery) {
        val query = stopQuery.trim()
        if (query.length < 2) {
            stopResults = emptyList()
            return@LaunchedEffect
        }

        delay(250)
        stopResults = viewModel.searchStops(query)
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

            // Preset selection
            Text(
                text = "Type de favori",
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
                    color = Color.Black
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
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.Black),
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
                color = Color.Black
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.Black)
                    .clickable { showStopSearchFullscreen = true }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = selectedStop?.stopName ?: "Rechercher un arrêt",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
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
                    containerColor = Color.Black,
                    contentColor = Color.White,
                    disabledContainerColor = Color.Gray,
                    disabledContentColor = Color.White
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
        StopSearchFullscreenOverlay(
            query = stopQuery,
            searchResults = stopResults,
            onQueryChange = { newQuery ->
                stopQuery = newQuery
                if (selectedStop?.stopName != newQuery) {
                    selectedStop = null
                }
            },
            onDismiss = { showStopSearchFullscreen = false },
            onResultSelected = { result ->
                selectedStop = result
                stopQuery = ""
                stopResults = emptyList()
                showStopSearchFullscreen = false
            }
        )
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
            .border(1.dp, if (isSelected) Color.Black else Color.Gray, RoundedCornerShape(24.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(24.dp)
                    .clip(CircleShape)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = iconName,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Black,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
            if (isSelected) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
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
}


private data class FavoritePreset(
    val name: String,
    val iconName: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StopSearchFullscreenOverlay(
    query: String,
    searchResults: List<StationSearchResult>,
    onQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onResultSelected: (StationSearchResult) -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    var expanded by rememberSaveable { mutableStateOf(true) }
    var queryField by remember {
        mutableStateOf(
            TextFieldValue(
                text = query,
                selection = TextRange(query.length)
            )
        )
    }

    LaunchedEffect(query) {
        queryField = TextFieldValue(
            text = query,
            selection = TextRange(query.length)
        )
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .semantics { isTraversalGroup = true }
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    if (expanded) {
                        expanded = false
                        keyboardController?.hide()
                        onDismiss()
                    }
                }
        ) {
            SearchBar(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .semantics { traversalIndex = 0f }
                    .padding(horizontal = if (expanded) 0.dp else 10.dp),
                inputField = {
                    SearchBarDefaults.InputField(
                        query = queryField.text,
                        onQueryChange = { updated ->
                            queryField = TextFieldValue(updated, TextRange(updated.length))
                            onQueryChange(updated)
                        },
                        onSearch = {
                            searchResults.firstOrNull()?.let(onResultSelected)
                        },
                        expanded = expanded,
                        onExpandedChange = { shouldExpand ->
                            expanded = shouldExpand
                            if (!shouldExpand) onDismiss()
                        },
                        placeholder = { Text("Rechercher", color = Color.White) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = Color.White,
                                modifier = Modifier.padding(
                                    start = if (expanded) 32.dp else 0.dp,
                                    end = if (expanded) 12.dp else 0.dp
                                )
                            )
                        },
                        modifier = Modifier
                            .focusRequester(focusRequester)
                            .fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color.White,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            focusedContainerColor = Color.Black,
                            unfocusedContainerColor = Color.Black,
                            focusedPlaceholderColor = Color.White.copy(alpha = 0.6f),
                            unfocusedPlaceholderColor = Color.White.copy(alpha = 0.6f)
                        )
                    )
                },
                expanded = expanded,
                onExpandedChange = { shouldExpand ->
                    expanded = shouldExpand
                    if (!shouldExpand) onDismiss()
                },
                colors = SearchBarDefaults.colors(
                    containerColor = Color.Black,
                    dividerColor = Color.Transparent
                )
            ) {
                val scrollState = rememberScrollState()

                LaunchedEffect(scrollState) {
                    snapshotFlow { scrollState.isScrollInProgress }
                        .collect { isScrolling ->
                            if (isScrolling) {
                                keyboardController?.hide()
                            }
                        }
                }

                Column(
                    Modifier
                        .padding(top = 12.dp, bottom = 28.dp)
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitFirstDown(requireUnconsumed = false)
                                }
                            }
                        }
                        .verticalScroll(scrollState)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {}
                ) {
                    if (queryField.text.length >= 2) {
                        searchResults.forEach { result ->
                            FavoriteStopSearchResultItem(
                                result = result,
                                onClick = { onResultSelected(result) }
                            )
                        }
                    }

                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) {
                                expanded = false
                                keyboardController?.hide()
                                onDismiss()
                            }
                    )

                    if (searchResults.isEmpty() && queryField.text.isNotEmpty()) {
                        Text(
                            text = "Aucun résultat",
                            color = Color.White.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteSearchConnectionBadge(lineName: String) {
    val context = LocalContext.current
    val resourceId = BusIconHelper.getResourceIdForLine(context, lineName)

    if (resourceId != 0) {
        Image(
            painter = painterResource(id = resourceId),
            contentDescription = lineName,
            modifier = Modifier.size(30.dp)
        )
    }
}

@Composable
private fun FavoriteStopSearchResultItem(
    result: StationSearchResult,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                Spacer(modifier = Modifier.size(6.dp))
                Text(
                    result.stopName,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold
                )
                if (result.lines.isNotEmpty()) {
                    Spacer(modifier = Modifier.size(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        result.lines.take(4).forEach { lineName ->
                            FavoriteSearchConnectionBadge(lineName = lineName)
                        }
                    }
                }
                Spacer(modifier = Modifier.size(4.dp))
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Black),
        modifier = Modifier
            .clickable(onClick = onClick)
            .fillMaxWidth()
    )
}