package com.pelotcl.app.generic.ui.components.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pelotcl.app.generic.data.models.Favorite
import com.pelotcl.app.generic.ui.theme.PrimaryColor
import com.pelotcl.app.generic.ui.theme.SecondaryColor

/**
 * Horizontal scrollable bar showing user-created favorites
 * @param favorites List of user favorites to display
 * @param onFavoriteClick Callback when a favorite is clicked
 * @param modifier Modifier for the component
 * @param isDarkMode Whether dark map theme is enabled (should use map theme, not system theme)
 */
@Composable
fun FavoritesBar(
    favorites: List<Favorite>,
    onAddFavoriteClick: () -> Unit,
    onFavoriteClick: (Favorite) -> Unit,
    onRemoveFavoriteClick: (Favorite) -> Unit,
    modifier: Modifier = Modifier,
    isDarkMode: Boolean = false
) {
    var favoriteToDelete by remember { mutableStateOf<Favorite?>(null) }
    val chipTextStyle = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = SecondaryColor
    )

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val textMeasurer = rememberTextMeasurer()
        val buttonBaseContentWidth = 15.dp + 16.dp + 16.dp + 4.dp // start + end + icon + spacer
        val outerHorizontalPadding = 16.dp + 12.dp
        val interItemSpacing = 8.dp

        fun estimateButtonWidth(text: String): Dp {
            val textWidthPx = textMeasurer.measure(
                text = AnnotatedString(text),
                style = chipTextStyle
            ).size.width.toFloat()
            val textWidthDp = with(density) { textWidthPx.toDp() }
            return buttonBaseContentWidth + textWidthDp
        }

        val createButtonWidth =
            remember(density, textMeasurer) { estimateButtonWidth("Creer un favori") }
        val favoritesTotalWidth = remember(favorites, density, textMeasurer) {
            favorites.fold(0.dp) { acc, favorite ->
                acc + estimateButtonWidth(favorite.name)
            }
        }
        val totalSpacing = (favorites.size * interItemSpacing.value).dp
        val estimatedTotalContentWidth = createButtonWidth + favoritesTotalWidth + totalSpacing
        val availableWidth = maxWidth - outerHorizontalPadding
        val fitsWithoutScroll = estimatedTotalContentWidth <= availableWidth

        if (fitsWithoutScroll) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 8.dp, end = 12.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                favorites.forEach { favorite ->
                    FavoriteItem(
                        favorite = favorite,
                        onClick = { onFavoriteClick(favorite) },
                        onLongClick = { favoriteToDelete = favorite },
                        textStyle = chipTextStyle,
                        isDarkMode = isDarkMode
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                AddFavoriteItem(
                    onClick = onAddFavoriteClick,
                    textStyle = chipTextStyle,
                    isDarkMode = isDarkMode
                )
            }
        } else {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 8.dp,
                    end = 12.dp,
                    bottom = 8.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(favorites, key = { it.id }) { favorite ->
                    FavoriteItem(
                        favorite = favorite,
                        onClick = { onFavoriteClick(favorite) },
                        onLongClick = { favoriteToDelete = favorite },
                        textStyle = chipTextStyle,
                        isDarkMode = isDarkMode
                    )
                }
                item {
                    AddFavoriteItem(
                        onClick = onAddFavoriteClick,
                        textStyle = chipTextStyle,
                        isDarkMode = isDarkMode
                    )
                }
            }
        }

        favoriteToDelete?.let { favorite ->
            AlertDialog(
                onDismissRequest = { favoriteToDelete = null },
                title = { Text("Supprimer le favori") },
                text = { Text("Voulez-vous supprimer \"${favorite.name}\" ?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onRemoveFavoriteClick(favorite)
                            favoriteToDelete = null
                        }
                    ) {
                        Text("Supprimer")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { favoriteToDelete = null }) {
                        Text("Annuler")
                    }
                }
            )
        }
    }
}

/**
 * Individual favorite item in the favorites bar
 */
@Composable
private fun FavoriteItem(
    favorite: Favorite,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    textStyle: TextStyle,
    isDarkMode: Boolean = false
) {
    val icon = favoriteIcon(favorite.iconName)

    Row(
        modifier = Modifier
            .shadow(4.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(PrimaryColor)
            .then(if (isDarkMode) Modifier.border(1.dp, Color(0xFF9CA3AF), RoundedCornerShape(20.dp)) else Modifier)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(start = 15.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = favorite.name,
            tint = SecondaryColor,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = favorite.name,
            style = textStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun AddFavoriteItem(
    onClick: () -> Unit,
    textStyle: TextStyle,
    isDarkMode: Boolean = false
) {
    Row(
        modifier = Modifier
            .shadow(4.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(PrimaryColor)
            .then(if (isDarkMode) Modifier.border(1.dp, Color(0xFF9CA3AF), RoundedCornerShape(20.dp)) else Modifier)
            .combinedClickable(onClick = onClick)
            .padding(start = 15.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Ajouter un favori",
            tint = SecondaryColor,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "Créer un favori",
            style = textStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
