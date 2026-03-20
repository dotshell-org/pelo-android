package com.pelotcl.app.data.model

import androidx.compose.ui.graphics.Color

/**
 * Represents a user-created favorite with a name, icon, and associated stop
 */
data class Favorite(
    val id: String, // Unique identifier
    val name: String, // User-defined name for the favorite
    val iconName: String, // Name of the icon resource
    val iconColor: String, // Color of the icon in hex format
    val stopName: String, // Name of the associated stop
    val stopId: String? = null // Optional stop ID
) {
    companion object {
        // Default icons that users can choose from
        val DEFAULT_ICONS = listOf(
            "home",
            "work",
            "school",
            "shopping",
            "star",
            "heart",
            "bus",
            "train",
            "location",
            "flag"
        )

        // Default colors
        val DEFAULT_COLORS = listOf(
            "#FF5722", "#E91E63", "#9C27B0", "#673AB7", "#3F51B5",
            "#2196F3", "#03A9F4", "#00BCD4", "#009688", "#4CAF50",
            "#8BC34A", "#CDDC39", "#FFC107", "#FF9800", "#795548"
        )
    }

    fun getIconColorAsColor(): Color {
        return try {
            Color(android.graphics.Color.parseColor(iconColor))
        } catch (e: IllegalArgumentException) {
            Color(0xFFFF5722) // Default to deep orange if parsing fails
        }
    }
}