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

    // directionId = -1 means both directions
    private const val DIRECTION_BOTH = -1

    @RequiresApi(Build.VERSION_CODES.O)
    private data class ScheduleContext(
        val repo: SchedulesRepository,
        val now: LocalTime,
        val isSchoolHoliday: Boolean,
        val isPublicHoliday: Boolean
    )

    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildScheduleContext(context: Context): ScheduleContext {
        val repo = SchedulesRepository.getInstance(context)
        val holidayDetector = HolidayDetector(context)
        val today = LocalDate.now()
        return ScheduleContext(
            repo = repo,
            now = LocalTime.now(),
            isSchoolHoliday = holidayDetector.isSchoolHoliday(today),
            isPublicHoliday = holidayDetector.isFrenchPublicHoliday(today)
        )
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun getUpcomingDepartures(
        context: Context,
        stopName: String,
        lineName: String,
        directionId: Int,
        count: Int = 3
    ): List<UpcomingDeparture> {
        val ctx = buildScheduleContext(context)

        val gtfsLineName = if (lineName.equals("NAV1", ignoreCase = true)) "NAVI1" else lineName
        val headsigns = ctx.repo.getHeadsigns(gtfsLineName)

        val directionsToQuery = if (directionId == DIRECTION_BOTH) {
            headsigns.keys.toList().ifEmpty { listOf(0, 1) }
        } else {
            listOf(directionId)
        }

        val allDepartures = mutableListOf<UpcomingDeparture>()
        for (dir in directionsToQuery) {
            val directionName = headsigns[dir] ?: ""
            val schedules = ctx.repo.getSchedules(
                gtfsLineName, stopName, dir, ctx.isSchoolHoliday, ctx.isPublicHoliday
            )
            schedules.mapNotNull { time ->
                minutesUntil(time, ctx.now)?.let {
                    UpcomingDeparture(lineName, directionName, time, it)
                }
            }.let { allDepartures.addAll(it) }
        }

        return allDepartures
            .sortedBy { it.minutesUntil }
            .take(count)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun getAllUpcomingDepartures(
        context: Context,
        stopName: String,
        desserteString: String,
        count: Int = 5
    ): List<UpcomingDeparture> {
        val ctx = buildScheduleContext(context)

        val lineDirections = desserteString.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { entry ->
                val parts = entry.split(":")
                if (parts.size == 2) Pair(parts[0].trim(), parts[1].trim()) else null
            }

        val allDepartures = mutableListOf<UpcomingDeparture>()
        val lineGroups = lineDirections.groupBy { it.first }

        for ((line, directions) in lineGroups) {
            val gtfsLineName = if (line.equals("NAV1", ignoreCase = true)) "NAVI1" else line
            val displayLineName = if (gtfsLineName == "NAVI1") "NAV1" else line
            val headsigns = ctx.repo.getHeadsigns(gtfsLineName)

            for ((_, dirLetter) in directions) {
                val directionId = when (dirLetter.uppercase()) {
                    "A" -> 0
                    "R" -> 1
                    else -> continue
                }

                val directionName = headsigns[directionId] ?: ""
                val schedules = ctx.repo.getSchedules(
                    gtfsLineName, stopName, directionId, ctx.isSchoolHoliday, ctx.isPublicHoliday
                )

                schedules.mapNotNull { time ->
                    minutesUntil(time, ctx.now)?.let {
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
