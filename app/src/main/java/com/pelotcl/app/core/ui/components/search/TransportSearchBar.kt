package com.pelotcl.app.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.pelotcl.app.core.data.repository.offline.MapStyle
import com.pelotcl.app.core.data.repository.offline.SearchHistoryItem
import com.pelotcl.app.core.data.repository.offline.SearchHistoryRepository
import com.pelotcl.app.core.data.repository.offline.SearchType
import com.pelotcl.app.core.ui.viewmodel.TransportViewModel
import kotlinx.coroutines.delay

@Composable
fun TransportSearchBar(
    viewModel: TransportViewModel,
    modifier: Modifier = Modifier,
    currentMapStyle: MapStyle = MapStyle.POSITRON,
    content: TransportSearchContent = TransportSearchContent.STOPS_AND_LINES,
    showHistory: Boolean = true,
    startExpanded: Boolean = false,
    showDarkOutline: Boolean? = null,
    searchPlaceholder: String = "Rechercher",
    query: String? = null,
    onQueryChange: ((String) -> Unit)? = null,
    minQueryLengthForResults: Int? = null,
    debounceMs: Long? = null,
    focusNonce: Int = 0,
    onExpandedChange: (Boolean) -> Unit = {},
    onStopPrimary: (StationSearchResult) -> Unit,
    onStopSecondary: (StationSearchResult) -> Unit = {},
    onLineSelected: (LineSearchResult) -> Unit = {},
) {
    val context = LocalContext.current
    val searchHistoryRepository = remember(showHistory) {
        if (showHistory) SearchHistoryRepository(context) else null
    }

    var uncontrolledQuery by remember { mutableStateOf("") }
    val isParentControlled = query != null && onQueryChange != null
    val effectiveQuery = if (isParentControlled) query else uncontrolledQuery
    val setEffectiveQuery: (String) -> Unit = { q ->
        if (isParentControlled) onQueryChange(q) else uncontrolledQuery = q
    }

    val resolvedMinLen = minQueryLengthForResults
        ?: if (content == TransportSearchContent.STOPS_ONLY && !showHistory) 2 else 1
    val resolvedDebounce = debounceMs
        ?: if (content == TransportSearchContent.STOPS_ONLY && !showHistory) 250L else 300L

    val resolvedShowDarkOutline = showDarkOutline
        ?: (currentMapStyle == MapStyle.DARK_MATTER && !startExpanded)

    var stationSearchResults by remember { mutableStateOf<List<StationSearchResult>>(emptyList()) }
    var lineSearchResults by remember { mutableStateOf<List<LineSearchResult>>(emptyList()) }
    var searchHistory by remember { mutableStateOf<List<SearchHistoryItem>>(emptyList()) }

    fun reloadHistory() {
        if (searchHistoryRepository != null) {
            searchHistory = searchHistoryRepository.getSearchHistory()
        } else {
            searchHistory = emptyList()
        }
    }

    fun addStopToHistory(stop: StationSearchResult) {
        searchHistoryRepository?.addToHistory(
            SearchHistoryItem(
                query = stop.stopName,
                type = SearchType.STOP,
                lines = stop.lines
            )
        )
        reloadHistory()
    }

    fun addLineToHistory(line: LineSearchResult) {
        searchHistoryRepository?.addToHistory(
            SearchHistoryItem(
                query = line.lineName,
                type = SearchType.LINE
            )
        )
        reloadHistory()
    }

    LaunchedEffect(showHistory) {
        if (showHistory) reloadHistory()
        else searchHistory = emptyList()
    }

    LaunchedEffect(effectiveQuery, content, resolvedMinLen, resolvedDebounce) {
        val current = effectiveQuery.trim()
        if (current.isEmpty()) {
            stationSearchResults = emptyList()
            lineSearchResults = emptyList()
            return@LaunchedEffect
        }
        if (current.length < resolvedMinLen) {
            stationSearchResults = emptyList()
            lineSearchResults = emptyList()
            return@LaunchedEffect
        }
        delay(resolvedDebounce)
        if (current != effectiveQuery.trim()) return@LaunchedEffect

        if (content != TransportSearchContent.LINES_ONLY) {
            stationSearchResults = viewModel.searchStops(current)
        } else {
            stationSearchResults = emptyList()
        }
        if (content != TransportSearchContent.STOPS_ONLY) {
            lineSearchResults = viewModel.searchLines(current)
        } else {
            lineSearchResults = emptyList()
        }
    }

    fun clearQuery() {
        setEffectiveQuery("")
    }

    SimpleSearchBar(
        modifier = modifier,
        searchResults = stationSearchResults,
        lineSearchResults = lineSearchResults,
        searchHistory = if (showHistory) searchHistory else emptyList(),
        onQueryChange = {},
        externalQuery = effectiveQuery,
        externalOnQueryChange = setEffectiveQuery,
        onSearch = { stop ->
            if (showHistory) addStopToHistory(stop)
            onStopPrimary(stop)
            clearQuery()
        },
        onLineSearch = { line ->
            if (showHistory) addLineToHistory(line)
            onLineSelected(line)
            clearQuery()
        },
        onHistoryItemClick = { historyItem ->
            if (historyItem.type == SearchType.LINE) {
                viewModel.selectLine(historyItem.query)
            } else {
                onStopPrimary(
                    StationSearchResult(
                        stopName = historyItem.query,
                        lines = historyItem.lines
                    )
                )
            }
        },
        onHistoryItemRemove = { historyItem ->
            searchHistoryRepository?.removeFromHistory(historyItem.query, historyItem.type)
            reloadHistory()
        },
        showDarkOutline = resolvedShowDarkOutline,
        onExpandedChange = onExpandedChange,
        onStopOptionsClick = { stop ->
            if (showHistory) addStopToHistory(stop)
            onStopSecondary(stop)
        },
        onHistoryItemOptionsClick = { historyItem ->
            if (historyItem.type == SearchType.STOP) {
                onStopSecondary(
                    StationSearchResult(
                        stopName = historyItem.query,
                        lines = historyItem.lines
                    )
                )
            }
        },
        content = content,
        showHistory = showHistory,
        startExpanded = startExpanded,
        searchPlaceholder = searchPlaceholder,
        focusNonce = focusNonce,
        minQueryLengthForResults = resolvedMinLen
    )
}
