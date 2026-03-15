package com.pelotcl.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pelotcl.app.R
import com.pelotcl.app.data.repository.SearchHistoryItem
import com.pelotcl.app.data.repository.SearchType
import com.pelotcl.app.ui.theme.Red500
import com.pelotcl.app.ui.theme.Stone900
import com.pelotcl.app.utils.BusIconHelper

@Immutable
data class StationSearchResult(
    val stopName: String,
    val lines: List<String>,
    val isPmr: Boolean = false
)

@Immutable
data class LineSearchResult(
    val lineName: String,
    val category: String = ""
)

private sealed class UnifiedSearchResult {
    abstract val sortKey: String

    data class Line(val result: LineSearchResult) : UnifiedSearchResult() {
        override val sortKey: String = result.lineName.uppercase()
    }

    data class Stop(val result: StationSearchResult) : UnifiedSearchResult() {
        override val sortKey: String = result.stopName.uppercase()
    }
}

private fun isStrongLine(line: String): Boolean {
    val upperLine = line.uppercase()
    return when {
        upperLine in setOf("A", "B", "C", "D") -> true
        upperLine in setOf("F1", "F2") -> true
        upperLine.startsWith("NAV") -> true
        upperLine.startsWith("T") -> true
        upperLine == "RX" -> true
        else -> false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleSearchBar(
    modifier: Modifier = Modifier,
    searchResults: List<StationSearchResult>,
    lineSearchResults: List<LineSearchResult> = emptyList(),
    searchHistory: List<SearchHistoryItem> = emptyList(),
    favoriteStops: List<SearchHistoryItem> = emptyList(),
    onSearch: (StationSearchResult) -> Unit,
    onLineSearch: (LineSearchResult) -> Unit = {},
    onHistoryItemClick: (SearchHistoryItem) -> Unit = {},
    onHistoryItemRemove: (SearchHistoryItem) -> Unit = {},
    onHistoryItemOptionsClick: (SearchHistoryItem) -> Unit = {},
    onQueryChange: (String) -> Unit = {},
    showDarkOutline: Boolean = false,
    onExpandedChange: (Boolean) -> Unit = {},
    onStopOptionsClick: (StationSearchResult) -> Unit = {}
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }

    val density = LocalDensity.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val imeHeight = WindowInsets.ime.getBottom(density)

    var previousImeHeight by remember { mutableIntStateOf(0) }
    var keyboardHiddenByScroll by remember { mutableStateOf(false) }

    LaunchedEffect(imeHeight, searchHistory.isEmpty()) {
        if (previousImeHeight > 0 && imeHeight == 0 && query.isEmpty() && expanded && !keyboardHiddenByScroll && searchHistory.isEmpty()) {
            expanded = false
        }
        if (imeHeight > 0) {
            keyboardHiddenByScroll = false
        }
    }

    fun setExpandedState(next: Boolean) {
        expanded = next
        onExpandedChange(next)
    }

    Box(
        modifier = (if (expanded) Modifier.fillMaxSize().background(Color.Black) else modifier)
            .semantics { isTraversalGroup = true }
            .padding(0.dp)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                if (expanded) {
                    setExpandedState(false)
                    keyboardController?.hide()
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
                    modifier = if (showDarkOutline && !expanded) {
                        Modifier
                            .clip(RoundedCornerShape(28.dp))
                            .border(1.dp, Color.Gray, RoundedCornerShape(28.dp))
                    } else {
                        Modifier
                    },
                    query = query,
                    onQueryChange = { q ->
                        query = q
                        onQueryChange(q)
                    },
                    onSearch = {
                        searchResults.firstOrNull()?.let { onSearch(it) }
                        setExpandedState(false)
                        query = ""
                    },
                    expanded = expanded,
                    onExpandedChange = { shouldExpand ->
                        if (shouldExpand || (searchHistory.isEmpty() && !keyboardHiddenByScroll)) {
                            setExpandedState(shouldExpand)
                        }
                    },
                    placeholder = { Text("Rechercher", color = Color.White) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = Red500,
                            modifier = Modifier.padding(
                                start = if (expanded) 32.dp else 0.dp,
                                end = if (expanded) 12.dp else 0.dp
                            )
                        )
                    },
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
                if (shouldExpand || (searchHistory.isEmpty() && !keyboardHiddenByScroll)) {
                    setExpandedState(shouldExpand)
                }
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
                            keyboardHiddenByScroll = true
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
                                keyboardHiddenByScroll = true
                            }
                        }
                    }
                    .verticalScroll(scrollState)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {}
            ) {
                if (query.isEmpty()) {
                    if (favoriteStops.isNotEmpty()) {
                        SectionHeader(icon = Icons.Default.Star, text = "Arrêts favoris")
                        favoriteStops.forEach { historyItem ->
                            HistoryListItem(
                                historyItem = historyItem,
                                showRemove = false,
                                onClick = {
                                    onHistoryItemClick(historyItem)
                                    query = ""
                                    setExpandedState(false)
                                },
                                onOptionsClick = { onHistoryItemOptionsClick(historyItem) },
                                onRemoveClick = {}
                            )
                        }
                    }

                    if (searchHistory.isNotEmpty()) {
                        SectionHeader(icon = Icons.Default.History, text = "Recherches récentes")
                        searchHistory.forEach { historyItem ->
                            HistoryListItem(
                                historyItem = historyItem,
                                showRemove = true,
                                onClick = {
                                    onHistoryItemClick(historyItem)
                                    query = ""
                                    setExpandedState(false)
                                },
                                onOptionsClick = { onHistoryItemOptionsClick(historyItem) },
                                onRemoveClick = { onHistoryItemRemove(historyItem) }
                            )
                        }
                    }
                }

                val combinedResults = remember(lineSearchResults, searchResults) {
                    val lines = lineSearchResults.map { UnifiedSearchResult.Line(it) }
                    val stops = searchResults.map { UnifiedSearchResult.Stop(it) }
                    (lines + stops).sortedBy { it.sortKey }
                }

                combinedResults.forEach { unifiedResult ->
                    when (unifiedResult) {
                        is UnifiedSearchResult.Line -> {
                            LineSearchResultItem(
                                lineResult = unifiedResult.result,
                                onClick = {
                                    query = ""
                                    setExpandedState(false)
                                    onLineSearch(unifiedResult.result)
                                }
                            )
                        }
                        is UnifiedSearchResult.Stop -> {
                            StopSearchResultItem(
                                result = unifiedResult.result,
                                onClick = {
                                    query = ""
                                    setExpandedState(false)
                                    onSearch(unifiedResult.result)
                                },
                                onOptionsClick = {
                                    query = ""
                                    onQueryChange("")
                                    setExpandedState(false)
                                    onStopOptionsClick(unifiedResult.result)
                                }
                            )
                        }
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
                            setExpandedState(false)
                            keyboardController?.hide()
                        }
                )

                if (searchResults.isEmpty() && lineSearchResults.isEmpty() && query.isNotEmpty()) {
                    ListItem(
                        headlineContent = {
                            Text(
                                "Aucun résultat",
                                color = Color.White.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Black),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = text,
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun SearchConnectionBadge(lineName: String) {
    val context = LocalContext.current
    val resourceId = BusIconHelper.getResourceIdForLine(context, lineName)
    
    if (resourceId != 0) {
        Image(
            painter = painterResource(id = resourceId),
            contentDescription = stringResource(R.string.line_icon, lineName),
            modifier = Modifier.size(30.dp)
        )
    }
}

@Composable
private fun StopSearchResultItem(
    result: StationSearchResult,
    onClick: () -> Unit,
    onOptionsClick: () -> Unit
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
                        result.lines.forEach { lineName ->
                            if (isStrongLine(lineName)) {
                                SearchConnectionBadge(lineName = lineName)
                            }
                        }
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy((-8).dp)
                    ) {
                        result.lines.forEach { lineName ->
                            if (!isStrongLine(lineName)) {
                                SearchConnectionBadge(lineName = lineName)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.size(4.dp))
            }
        },
        trailingContent = {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Stone900)
                    .clickable(onClick = onClick)
            ) {
                Icon(
                    imageVector = Icons.Default.Directions,
                    contentDescription = "Itinéraire",
                    tint = Color.White,
                    modifier = Modifier
                        .size(17.dp)
                        .align(Alignment.Center)
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Black),
        modifier = Modifier
            .clickable(onClick = onOptionsClick)
            .fillMaxWidth()
    )
}

@Composable
private fun HistoryListItem(
    historyItem: SearchHistoryItem,
    showRemove: Boolean,
    onClick: () -> Unit,
    onOptionsClick: () -> Unit,
    onRemoveClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy((-6).dp)
                ) {
                    Text(
                        historyItem.query,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Medium
                    )
                    if (historyItem.type == SearchType.LINE) {
                        Text(
                            "Ligne",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp
                        )
                    } else if (historyItem.lines.isNotEmpty()) {
                        Spacer(modifier = Modifier.size(10.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            historyItem.lines.forEach { lineName ->
                                if (isStrongLine(lineName)) {
                                    SearchConnectionBadge(lineName = lineName)
                                }
                            }
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy((-8).dp)
                        ) {
                            historyItem.lines.forEach { lineName ->
                                if (!isStrongLine(lineName)) {
                                    SearchConnectionBadge(lineName = lineName)
                                }
                            }
                        }
                    }
                }
                if (historyItem.type == SearchType.STOP) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Stone900)
                            .clickable(onClick = onClick)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Directions,
                            contentDescription = "Itinéraire",
                            tint = Color.White,
                            modifier = Modifier
                                .size(17.dp)
                                .align(Alignment.Center)
                        )
                    }
                }
                if (showRemove) {
                    IconButton(onClick = onRemoveClick) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Supprimer",
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Black),
        modifier = Modifier
            .clickable(onClick = onOptionsClick)
            .fillMaxWidth()
    )
}

@Composable
private fun LineSearchResultItem(
    lineResult: LineSearchResult,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val resourceId = BusIconHelper.getResourceIdForLine(context, lineResult.lineName)
    val modeBusId = BusIconHelper.getResourceIdForDrawableName(context, "mode_bus")

    ListItem(
        headlineContent = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (resourceId != 0) {
                    Image(
                        painter = painterResource(id = resourceId),
                        contentDescription = stringResource(R.string.line_icon, lineResult.lineName),
                        modifier = Modifier.size(40.dp)
                    )
                } else if (modeBusId != 0) {
                    Image(
                        painter = painterResource(id = modeBusId),
                        contentDescription = stringResource(R.string.bus_mode_icon),
                        modifier = Modifier.size(30.dp)
                    )
                }
                Spacer(modifier = Modifier.size(8.dp))
                Column(modifier = Modifier.padding(start = 10.dp)) {
                    Text(
                        "${lineResult.category} ${lineResult.lineName}",
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Black),
        modifier = Modifier
            .clickable(onClick = onClick)
            .fillMaxWidth()
    )
}

@Preview(showBackground = true)
@Composable
fun PreviewLineSearchResultItem() {
    val mockResult = LineSearchResult(
        lineName = "F1",
        category = "Funiculaire"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        LineSearchResultItem(
            lineResult = mockResult,
            onClick = { /* Action de clic ici */ }
        )
    }
}
