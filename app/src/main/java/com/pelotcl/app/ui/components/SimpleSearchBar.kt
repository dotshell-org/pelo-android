package com.pelotcl.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pelotcl.app.ui.theme.Red500
import com.pelotcl.app.utils.BusIconHelper

data class StationSearchResult(
    val stopName: String,
    val lines: List<String>,
    val isPmr: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleSearchBar(
    searchResults: List<StationSearchResult>,
    onSearch: (StationSearchResult) -> Unit,
    onQueryChange: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }

    Box(
        (if (expanded) Modifier.fillMaxSize().background(Color.Black) else modifier)
            .semantics { isTraversalGroup = true }
            .padding(0.dp)
    ) {
        SearchBar(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .semantics { traversalIndex = 0f }
                .padding(horizontal = if (expanded) 0.dp else 10.dp),
            inputField = {
                SearchBarDefaults.InputField(
                    query = query,
                    onQueryChange = { q ->
                        query = q
                        onQueryChange(q)
                    },
                    onSearch = {
                        searchResults.firstOrNull()?.let {
                            onSearch(it)
                        }
                        expanded = false
                        query = ""
                    },
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    placeholder = { Text("Search for a stop...", color = Color.White) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = Red500,
                            modifier = Modifier
                                .padding(start = if (expanded) 32.dp else 0.dp,
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
            onExpandedChange = { expanded = it },
            colors = SearchBarDefaults.colors(
                containerColor = Color.Black,
                dividerColor = Color.Transparent
            )
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState())
            ) {
                searchResults.forEach { result ->
                    ListItem(
                        headlineContent = {
                            Column {
                                Text(
                                    result.stopName,
                                    color = Color.Black,
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
                                        // Limit icons to prevent UI overflow
                                        result.lines.take(6).forEach { lineName ->
                                            SearchConnectionBadge(lineName = lineName)
                                        }
                                        if (result.lines.size > 6) {
                                            Text("...", fontSize = 10.sp, color = Color.Gray)
                                        }
                                    }
                                }
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.White),
                        modifier = Modifier
                            .clickable {
                                query = ""
                                expanded = false
                                onSearch(result)
                            }
                            .fillMaxWidth()
                    )
                }
                if (searchResults.isEmpty() && query.isNotEmpty()) {
                    ListItem(
                        headlineContent = {
                            Text(
                                "No results",
                                color = Color.Black.copy(alpha = 0.6f)
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchConnectionBadge(lineName: String) {
    val context = LocalContext.current
    val drawableName = BusIconHelper.getDrawableNameForLineName(lineName)
    val resourceId = context.resources.getIdentifier(drawableName, "drawable", context.packageName)

    if (resourceId != 0) {
        Image(
            painter = painterResource(id = resourceId),
            contentDescription = null,
            modifier = Modifier.size(24.dp)
        )
    } else {
        val backgroundColor = when (lineName) {
            "A", "B", "C", "D" -> Color(0xFFEC4899)
            else -> Color.Gray
        }
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(backgroundColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = lineName,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontSize = 8.sp
            )
        }
    }
}