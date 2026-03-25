package com.pelotcl.app.generic.data.model

/**
 * Represents a user-created favorite with a name, icon, and associated stop
 */
data class Favorite(
    val id: String, // Unique identifier
    val name: String, // User-defined name for the favorite
    val iconName: String, // Name of the icon resource
    val stopName: String, // Name of the associated stop
)