package com.pelotcl.app.generic.ui.screens.plan.itinerary

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.pelotcl.app.generic.data.models.itinerary.SelectedStop
import com.pelotcl.app.generic.ui.theme.AccentColor
import com.pelotcl.app.generic.ui.theme.PrimaryColor
import com.pelotcl.app.generic.ui.theme.SecondaryColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItinerarySearchBarField(
    modifier: Modifier = Modifier,
    selectedStop: SelectedStop?,
    onClick: () -> Unit,
    icon: ImageVector,
    placeholder: String
) {
    val displayedValue = selectedStop?.name ?: ""

    SearchBar(
        modifier = modifier
            .fillMaxWidth(),
        inputField = {
            SearchBarDefaults.InputField(
                query = displayedValue,
                onQueryChange = { onClick() },
                onSearch = { onClick() },
                expanded = false,
                onExpandedChange = { if (it) onClick() },
                placeholder = {
                    Text(
                        text = placeholder,
                        color = SecondaryColor
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
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
        },
        expanded = false,
        onExpandedChange = { if (it) onClick() },
        colors = SearchBarDefaults.colors(
            containerColor = PrimaryColor,
            dividerColor = Color.Transparent
        )
    ) {}
}
