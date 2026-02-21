package com.pelotcl.app.widget

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import com.pelotcl.app.data.gtfs.SchedulesRepository
import com.pelotcl.app.utils.HolidayDetector
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

data class UpcomingDeparture(
    val lineName: String,
    val directionName: String,
    val time: String,
    val minutesUntil: Long
)

object ScheduleWidgetHelper {

    @RequiresApi(Build.VERSION_CODES.O)
    fun getUpcomingDepartures(
        context: Context,
        stopName: String,
        lineName: String,
        directionId: Int,
        count: Int = 3
    ): List<UpcomingDeparture> {
        val repo = SchedulesRepository.getInstance(context)
        val holidayDetector = HolidayDetector(context)

        val today = LocalDate.now()
        val isSchoolHoliday = holidayDetector.isSchoolHoliday(today)
        val isPublicHoliday = holidayDetector.isFrenchPublicHoliday(today)

        val gtfsLineName = if (lineName.equals("NAV1", ignoreCase = true)) "NAVI1" else lineName

        val headsigns = repo.getHeadsigns(gtfsLineName)
        val directionName = headsigns[directionId] ?: ""

        val allSchedules = repo.getSchedules(
            gtfsLineName, stopName, directionId, isSchoolHoliday, isPublicHoliday
        )

        val now = LocalTime.now()
        return allSchedules
            .mapNotNull { time -> minutesUntil(time, now)?.let { UpcomingDeparture(lineName, directionName, time, it) } }
            .take(count)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun getAllUpcomingDepartures(
        context: Context,
        stopName: String,
        desserteString: String,
        count: Int = 5
    ): List<UpcomingDeparture> {
        val repo = SchedulesRepository.getInstance(context)
        val holidayDetector = HolidayDetector(context)

        val today = LocalDate.now()
        val isSchoolHoliday = holidayDetector.isSchoolHoliday(today)
        val isPublicHoliday = holidayDetector.isFrenchPublicHoliday(today)
        val now = LocalTime.now()

        // Parse desserte string: "86:A,86:R,C3:A,C3:R" -> list of (line, direction_letter)
        val lineDirections = desserteString.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { entry ->
                val parts = entry.split(":")
                if (parts.size == 2) Pair(parts[0].trim(), parts[1].trim()) else null
            }

        val allDepartures = mutableListOf<UpcomingDeparture>()

        // Group by line to avoid duplicate API calls
        val lineGroups = lineDirections.groupBy { it.first }

        for ((line, directions) in lineGroups) {
            val gtfsLineName = if (line.equals("NAV1", ignoreCase = true)) "NAVI1" else line
            val displayLineName = if (gtfsLineName == "NAVI1") "NAV1" else line
            val headsigns = repo.getHeadsigns(gtfsLineName)

            for ((_, dirLetter) in directions) {
                // Map direction letter to direction_id: A=0, R=1
                val directionId = when (dirLetter.uppercase()) {
                    "A" -> 0
                    "R" -> 1
                    else -> continue
                }

                val directionName = headsigns[directionId] ?: ""
                val schedules = repo.getSchedules(
                    gtfsLineName, stopName, directionId, isSchoolHoliday, isPublicHoliday
                )

                schedules.mapNotNull { time ->
                    minutesUntil(time, now)?.let {
                        UpcomingDeparture(displayLineName, directionName, time, it)
                    }
                }.let { allDepartures.addAll(it) }
            }
        }

        return allDepartures
            .sortedBy { it.minutesUntil }
            .take(count)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun minutesUntil(scheduleTime: String, now: LocalTime): Long? {
        try {
            val cleanTime = if (scheduleTime.count { it == ':' } == 2) {
                scheduleTime.substringBeforeLast(":")
            } else {
                scheduleTime
            }
            val parts = cleanTime.split(":")
            if (parts.size < 2) return null
            val hour = parts[0].toInt()
            val minute = parts[1].toInt()
            if (hour > 23) return null
            val schedule = LocalTime.of(hour, minute)
            val diff = ChronoUnit.MINUTES.between(now, schedule)
            return if (diff < 0) null else diff
        } catch (_: Exception) {
            return null
        }
    }
}
