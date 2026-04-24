package com.pelotcl.app.generic.ui.screens.plan

data class AllSchedulesInfo(
    val lineName: String,
    val directionName: String,
    val schedules: List<String>,
    val availableDirections: List<Int> = emptyList(),
    val headsigns: Map<Int, String> = emptyMap()
)