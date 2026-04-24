package com.pelotcl.app.generic.ui.components.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pelotcl.app.generic.data.models.stops.Favorite
import com.pelotcl.app.generic.ui.theme.PrimaryColor
import com.pelotcl.app.generic.ui.theme.SecondaryColor

/**
 * Individual favorite item in the favorites bar
 */
@Composable
fun FavoriteItem(
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
            .then(
                if (isDarkMode) Modifier.border(
                    1.dp,
                    Color(0xFF9CA3AF),
                    RoundedCornerShape(20.dp)
                ) else Modifier
            )
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
