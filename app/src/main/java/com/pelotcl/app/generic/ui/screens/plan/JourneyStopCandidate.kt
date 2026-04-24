package com.pelotcl.app.generic.ui.screens.plan

data class JourneyStopCandidate(
    val legIndex: Int,
    val isLegEnd: Boolean,
    val lat: Double,
    val lon: Double
)