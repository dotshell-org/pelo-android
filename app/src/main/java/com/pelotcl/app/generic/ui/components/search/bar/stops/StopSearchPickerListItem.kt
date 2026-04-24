package com.pelotcl.app.generic.ui.components.search.bar.stops

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pelotcl.app.generic.data.models.search.StationSearchResult
import com.pelotcl.app.generic.ui.theme.PrimaryColor
import com.pelotcl.app.generic.ui.theme.SecondaryColor

@Composable
fun StopSearchPickerListItem(
    result: StationSearchResult,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                Spacer(modifier = Modifier.size(6.dp))
                Text(
                    result.stopName,
                    color = SecondaryColor,
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
                            SearchConnectionBadge(lineName = lineName, sizeDp = 24)
                        }
                    }
                }
                Spacer(modifier = Modifier.size(4.dp))
            }
        },
        colors = ListItemDefaults.colors(containerColor = PrimaryColor),
        modifier = Modifier
            .clickable(onClick = onClick)
            .fillMaxWidth()
    )
}
