package com.pelotcl.app.generic.ui.components.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.pelotcl.app.generic.data.network.MapStyleData
import com.pelotcl.app.generic.data.repository.offline.MapStyleCompat
import com.pelotcl.app.generic.data.repository.offline.SearchHistoryItem
import com.pelotcl.app.generic.data.repository.offline.SearchHistoryRepository
import com.pelotcl.app.generic.data.repository.offline.SearchType
import com.pelotcl.app.generic.ui.viewmodel.TransportViewModel
import kotlinx.coroutines.delay

@Composable
fun TransportSearchBar(
    viewModel: TransportViewModel,
    modifier: Modifier = Modifier,
    currentMapStyle: MapStyleData = MapStyleCompat.POSITRON,
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
    showDirections: Boolean = true,
) {
    val context = LocalContext.current
    val searchHistoryRepository = remember(showHistory) {
        if (showHistory) SearchHistoryRepository(context) else null
    }

    var uncontrolledQuery by remember { mutableStateOf("") }
    val isParentControlled = query != null && onQueryChange != null
    val effectiveQuery = if (isParentControlled) query else uncontrolledQuery
    val setEffectiveQuery: (String) -> Unit = { q ->
        if (isParentControlled) {
            onQueryChange.invoke(q)
        } else {
            uncontrolledQuery = q
        }
    }

    val resolvedStopMinLen = minQueryLengthForResults ?: 2
    val resolvedLineMinLen = 1
    val resolvedUiMinLen = if (content == TransportSearchContent.STOPS_ONLY) {
        resolvedStopMinLen
    } else {
        resolvedLineMinLen
    }
    val resolvedDebounce = debounceMs
        ?: if (content == TransportSearchContent.STOPS_ONLY && !showHistory) 250L else 300L

    val resolvedShowDarkOutline = showDarkOutline
        ?: (currentMapStyle.key == "dark_matter" && !startExpanded)

    var stationSearchResults by remember { mutableStateOf<List<StationSearchResult>>(emptyList()) }
    var lineSearchResults by remember { mutableStateOf<List<LineSearchResult>>(emptyList()) }
    var searchHistory by remember { mutableStateOf<List<SearchHistoryItem>>(emptyList()) }

    fun reloadHistory() {
        searchHistory = searchHistoryRepository?.getSearchHistory() ?: emptyList()
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

    LaunchedEffect(
        effectiveQuery,
        content,
        resolvedStopMinLen,
        resolvedLineMinLen,
        resolvedDebounce
    ) {
        val current = effectiveQuery.trim()
        if (current.isEmpty()) {
            stationSearchResults = emptyList()
            lineSearchResults = emptyList()
            return@LaunchedEffect
        }
        delay(resolvedDebounce)
        if (current != effectiveQuery.trim()) return@LaunchedEffect

        stationSearchResults = if (
            content != TransportSearchContent.LINES_ONLY &&
            current.length >= resolvedStopMinLen
        ) {
            viewModel.searchStops(current)
        } else {
            emptyList()
        }
        lineSearchResults = if (
            content != TransportSearchContent.STOPS_ONLY &&
            current.length >= resolvedLineMinLen
        ) {
            viewModel.searchLines(current)
        } else {
            emptyList()
        }
    }

    fun clearQuery() {
        setEffectiveQuery("")
        stationSearchResults = emptyList()
        lineSearchResults = emptyList()
    }

    SimpleSearchBar(
        modifier = modifier,
        searchResults = stationSearchResults,
        lineSearchResults = lineSearchResults,
        searchHistory = if (showHistory) searchHistory else emptyList(),
        onQueryChange = { q -> setEffectiveQuery(q) },
        externalQuery = effectiveQuery,
        externalOnQueryChange = setEffectiveQuery,
        onSearch = { stop ->
            android.util.Log.i("TransportSearchBar", "onSearch called for stop: ${stop.stopName}")
            if (showHistory) addStopToHistory(stop)
            onStopPrimary(stop)
            clearQuery()
        },
        onLineSearch = { line ->
            android.util.Log.i("TransportSearchBar", "onLineSearch called for line: ${line.lineName}")
            if (showHistory) addLineToHistory(line)
            onLineSelected(line)
            clearQuery()
        },
        onHistoryItemClick = { historyItem ->
            android.util.Log.i("TransportSearchBar", "onHistoryItemClick: ${historyItem.query}")
            if (historyItem.type == SearchType.LINE) {
                onLineSelected(LineSearchResult(historyItem.query))
            } else {
                onStopPrimary(
                    StationSearchResult(
                        stopName = historyItem.query,
                        lines = historyItem.lines
                    )
                )
            }
            clearQuery()
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
        minQueryLengthForResults = resolvedUiMinLen,
        showDirections = showDirections
    )
}
