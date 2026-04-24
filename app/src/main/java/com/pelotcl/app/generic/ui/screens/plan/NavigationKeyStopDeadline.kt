package com.pelotcl.app.generic.ui.screens.plan

data class NavigationKeyStopDeadline(
    val stopId: String,
    val stopName: String,
    val lat: Double,
    val lon: Double,
    val deadlineSeconds: Int,
    val type: NavigationKeyStopType
)