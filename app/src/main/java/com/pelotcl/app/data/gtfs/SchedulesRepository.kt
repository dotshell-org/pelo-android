package com.pelotcl.app.data.gtfs

import android.content.Context

class SchedulesRepository(private val context: Context) {

    fun getHeadsigns(routeName: String): Map<Int, String> {
        // TODO: Implement GTFS headsign parsing
        return emptyMap()
    }

    fun getSchedules(lineName: String, stopName: String, directionId: Int, isHoliday: Boolean): List<String> {
        // TODO: Implement GTFS schedule parsing
        return emptyList()
    }
}
