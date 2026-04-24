package com.pelotcl.app.generic.data.cache.journey

/**
 * Wrapper for cached journey with timestamp
 */
data class CachedJourney(
    val journeys: List<SerializableJourneyResult>,
    val timestamp: Long
)