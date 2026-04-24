package com.pelotcl.app.generic.ui.components.search.bar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pelotcl.app.generic.data.repository.offline.search.SearchHistoryItem
import com.pelotcl.app.generic.data.models.search.LineSearchResult
import com.pelotcl.app.generic.data.models.search.StationSearchResult
import com.pelotcl.app.generic.data.models.search.TransportSearchContent
import com.pelotcl.app.generic.data.models.search.UnifiedSearchResult
import com.pelotcl.app.generic.ui.components.search.bar.lines.LineSearchResultItem
import com.pelotcl.app.generic.ui.components.search.bar.stops.StopSearchPickerListItem
import com.pelotcl.app.generic.ui.components.search.bar.stops.StopSearchResultItem
import com.pelotcl.app.generic.ui.theme.PrimaryColor
import com.pelotcl.app.generic.ui.theme.AccentColor
import com.pelotcl.app.generic.ui.theme.SecondaryColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleSearchBar(
    modifier: Modifier = Modifier,
    searchResults: List<StationSearchResult>,
    lineSearchResults: List<LineSearchResult> = emptyList(),
    searchHistory: List<SearchHistoryItem> = emptyList(),
    onSearch: (StationSearchResult) -> Unit,
    onLineSearch: (LineSearchResult) -> Unit = {},
    onHistoryItemClick: (SearchHistoryItem) -> Unit = {},
    onHistoryItemRemove: (SearchHistoryItem) -> Unit = {},
    onHistoryItemOptionsClick: (SearchHistoryItem) -> Unit = {},
    onQueryChange: (String) -> Unit = {},
    showDarkOutline: Boolean = false,
    onExpandedChange: (Boolean) -> Unit = {},
    onStopOptionsClick: (StationSearchResult) -> Unit = {},
    content: TransportSearchContent = TransportSearchContent.STOPS_AND_LINES,
    showHistory: Boolean = true,
    startExpanded: Boolean = false,
    searchPlaceholder: String = "Rechercher",
    externalQuery: String? = null,
    externalOnQueryChange: ((String) -> Unit)? = null,
    focusNonce: Int = 0,
    minQueryLengthForResults: Int = 1,
    showDirections: Boolean = true
) {
    val isControlled = externalQuery != null && externalOnQueryChange != null
    var internalQuery by rememberSaveable { mutableStateOf("") }
    val queryText = if (isControlled) externalQuery else internalQuery

    fun setQueryText(q: String) {
        if (isControlled) {
            externalOnQueryChange(q)
        } else {
            internalQuery = q
            onQueryChange(q)
        }
    }

    var expanded by remember(startExpanded) { mutableStateOf(startExpanded) }
    val focusRequester = remember { FocusRequester() }
    var expandedTextFieldValue by remember {
        mutableStateOf(TextFieldValue(queryText, TextRange(queryText.length)))
    }

    val density = LocalDensity.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val imeHeight = WindowInsets.ime.getBottom(density)

    var previousImeHeight by remember { mutableIntStateOf(0) }
    var keyboardHiddenByScroll by remember { mutableStateOf(false) }

    val historyEmptyOrDisabled = !showHistory || searchHistory.isEmpty()

    LaunchedEffect(imeHeight, historyEmptyOrDisabled) {
        if (previousImeHeight > 0 && imeHeight == 0 && queryText.isEmpty() && expanded && !keyboardHiddenByScroll && historyEmptyOrDisabled) {
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

    val combinedResults = remember(lineSearchResults, searchResults, content) {
        buildList {
            if (content != TransportSearchContent.STOPS_ONLY) {
                addAll(lineSearchResults.map { UnifiedSearchResult.Line(it) })
            }
            if (content != TransportSearchContent.LINES_ONLY) {
                addAll(searchResults.map { UnifiedSearchResult.Stop(it) })
            }
        }.sortedBy { it.sortKey }
    }

    val pickOnlyStopRows = content == TransportSearchContent.STOPS_ONLY && !showHistory
    val trimmedQuery = queryText.trim()
    val showNoResults = trimmedQuery.length >= minQueryLengthForResults && trimmedQuery.length > 1 &&
            when (content) {
                TransportSearchContent.STOPS_ONLY -> searchResults.isEmpty()
                TransportSearchContent.LINES_ONLY -> lineSearchResults.isEmpty()
                TransportSearchContent.STOPS_AND_LINES -> searchResults.isEmpty() && lineSearchResults.isEmpty()
            }

    fun submitFirstResult() {
        when (val first = combinedResults.firstOrNull()) {
            is UnifiedSearchResult.Stop -> {
                setExpandedState(false)
                setQueryText("")
                onSearch(first.result)
            }

            is UnifiedSearchResult.Line -> {
                setExpandedState(false)
                setQueryText("")
                onLineSearch(first.result)
            }

            null -> Unit
        }
    }

    LaunchedEffect(focusNonce) {
        if (focusNonce > 0 || startExpanded) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    LaunchedEffect(expanded) {
        if (expanded) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    LaunchedEffect(expanded, queryText) {
        if (expanded) {
            if (expandedTextFieldValue.text != queryText) {
                expandedTextFieldValue = TextFieldValue(queryText, TextRange(queryText.length))
            } else {
                expandedTextFieldValue = expandedTextFieldValue.copy(
                    selection = TextRange(expandedTextFieldValue.text.length)
                )
            }
        }
    }

    Box(
        modifier = (if (expanded) Modifier
            .fillMaxSize()
            .background(PrimaryColor) else modifier)
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
                if (expanded) {
                    TextField(
                        modifier = Modifier.focusRequester(focusRequester),
                        value = expandedTextFieldValue,
                        onValueChange = { newValue ->
                            expandedTextFieldValue = newValue.copy(
                                selection = TextRange(newValue.text.length)
                            )
                            setQueryText(newValue.text)
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { submitFirstResult() }),
                        placeholder = { Text(searchPlaceholder, color = SecondaryColor) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = AccentColor,
                                modifier = Modifier.padding(start = 32.dp, end = 12.dp)
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = SecondaryColor,
                            unfocusedTextColor = SecondaryColor,
                            cursorColor = SecondaryColor,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            focusedContainerColor = PrimaryColor,
                            unfocusedContainerColor = PrimaryColor,
                            focusedPlaceholderColor = SecondaryColor.copy(alpha = 0.6f),
                            unfocusedPlaceholderColor = SecondaryColor.copy(alpha = 0.6f)
                        )
                    )
                } else {
                    SearchBarDefaults.InputField(
                        modifier = if (showDarkOutline) {
                            Modifier
                                .clip(RoundedCornerShape(28.dp))
                                .border(1.dp, Color.Gray, RoundedCornerShape(28.dp))
                        } else {
                            Modifier
                        },
                        query = queryText,
                        onQueryChange = { q -> setQueryText(q) },
                        onSearch = { submitFirstResult() },
                        expanded = false,
                        onExpandedChange = { shouldExpand ->
                            if (shouldExpand || (historyEmptyOrDisabled && !keyboardHiddenByScroll)) {
                                setExpandedState(shouldExpand)
                            }
                        },
                        placeholder = { Text(searchPlaceholder, color = SecondaryColor) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = AccentColor
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = SecondaryColor,
                            unfocusedTextColor = SecondaryColor,
                            cursorColor = SecondaryColor,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            focusedContainerColor = PrimaryColor,
                            unfocusedContainerColor = PrimaryColor,
                            focusedPlaceholderColor = SecondaryColor.copy(alpha = 0.6f),
                            unfocusedPlaceholderColor = SecondaryColor.copy(alpha = 0.6f)
                        )
                    )
                }
            },
            expanded = expanded,
            onExpandedChange = { shouldExpand ->
                if (shouldExpand || (historyEmptyOrDisabled && !keyboardHiddenByScroll)) {
                    setExpandedState(shouldExpand)
                }
            },
            colors = SearchBarDefaults.colors(
                containerColor = PrimaryColor,
                dividerColor = Color.Transparent
            )
        ) {
            val lazyListState = rememberLazyListState()

            LaunchedEffect(lazyListState) {
                snapshotFlow { lazyListState.isScrollInProgress }
                    .collect { isScrolling ->
                        if (isScrolling) {
                            keyboardHiddenByScroll = true
                            keyboardController?.hide()
                        }
                    }
            }

            LazyColumn(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 28.dp)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitFirstDown(requireUnconsumed = false)
                                keyboardHiddenByScroll = true
                            }
                        }
                    }
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {},
                state = lazyListState
            ) {
                if (queryText.isEmpty() && showHistory && searchHistory.isNotEmpty()) {
                    item(key = "history_header") {
                        SectionHeader(icon = Icons.Default.History, text = "Recherches récentes")
                    }
                    items(searchHistory, key = { "history_${it.query}_${it.type}" }) { historyItem ->
                        HistoryListItem(
                            historyItem = historyItem,
                            showRemove = true,
                            onClick = {
                                onHistoryItemClick(historyItem)
                                setQueryText("")
                                setExpandedState(false)
                            },
                            onOptionsClick = {
                                setQueryText("")
                                setExpandedState(false)
                                keyboardController?.hide()
                                onHistoryItemOptionsClick(historyItem)
                            },
                            onRemoveClick = { onHistoryItemRemove(historyItem) }
                        )
                    }
                }

                items(combinedResults, key = { it.itemKey }) { unifiedResult ->
                    when (unifiedResult) {
                        is UnifiedSearchResult.Line -> {
                            LineSearchResultItem(
                                lineResult = unifiedResult.result,
                                onClick = {
                                    setQueryText("")
                                    setExpandedState(false)
                                    onLineSearch(unifiedResult.result)
                                }
                            )
                        }

                        is UnifiedSearchResult.Stop -> {
                            if (pickOnlyStopRows || !showDirections) {
                                StopSearchPickerListItem(
                                    result = unifiedResult.result,
                                    onClick = {
                                        setQueryText("")
                                        setExpandedState(false)
                                        onSearch(unifiedResult.result)
                                    }
                                )
                            } else {
                                StopSearchResultItem(
                                    result = unifiedResult.result,
                                    onClick = {
                                        setQueryText("")
                                        setExpandedState(false)
                                        onSearch(unifiedResult.result)
                                    },
                                    onOptionsClick = {
                                        setQueryText("")
                                        setExpandedState(false)
                                        onStopOptionsClick(unifiedResult.result)
                                    }
                                )
                            }
                        }
                    }
                }

                item(key = "bottom_spacer") {
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
                }

                if (showNoResults) {
                    item(key = "no_results") {
                        ListItem(
                            headlineContent = {
                                Text(
                                    "Aucun résultat",
                                    color = SecondaryColor.copy(alpha = 0.6f),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = PrimaryColor),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
