package com.pelotcl.app.utils

import android.annotation.SuppressLint
import android.content.Context
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.json.JSONObject
import java.time.Month

data class Holiday(
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate?
)

class HolidayDetector(private val context: Context) {

    private val holidays: List<Holiday>

    init {
        val jsonString = loadJsonFromAssets(context, "holidays.json")
        holidays = parseHolidays(jsonString)
    }

    private fun loadJsonFromAssets(context: Context, fileName: String): String {
        return context.assets.open(fileName).bufferedReader().use { it.readText() }
    }

    @SuppressLint("NewApi")
    private fun parseHolidays(jsonString: String): List<Holiday> {
        val jsonObject = JSONObject(jsonString)
        val holidaysArray = jsonObject.getJSONArray("holidays")
        val parsedHolidays = mutableListOf<Holiday>()
        val formatter = DateTimeFormatter.ISO_LOCAL_DATE

        for (i in 0 until holidaysArray.length()) {
            val holidayObject = holidaysArray.getJSONObject(i)
            val name = holidayObject.getString("name")
            val startDate = LocalDate.parse(holidayObject.getString("start_date_inclusive"), formatter)
            val endDate = if (holidayObject.isNull("end_date_inclusive")) {
                null
            } else {
                LocalDate.parse(holidayObject.getString("end_date_inclusive"), formatter)
            }

            parsedHolidays.add(Holiday(name, startDate, endDate))
        }
        return parsedHolidays
    }

    /**
     * Check if a given date is a school holiday (vacation period)
     */
    @SuppressLint("NewApi")
    fun isSchoolHoliday(date: LocalDate): Boolean {
        return holidays.any { holiday ->
            !date.isBefore(holiday.startDate) && (holiday.endDate == null || !date.isAfter(holiday.endDate))
        }
    }

    /**
     * Check if a given date is a French public holiday
     * French public holidays include:
     * - Fixed holidays: January 1, May 1, May 8, July 14, August 15, November 1, November 11, December 25
     * - Moveable holidays: Easter Monday, Ascension Day, Whit Monday (based on Easter date)
     */
    @SuppressLint("NewApi")
    fun isFrenchPublicHoliday(date: LocalDate): Boolean {
        val year = date.year

        // Fixed holidays
        val fixedHolidays = listOf(
            LocalDate.of(year, Month.JANUARY, 1),        // New Year's Day
            LocalDate.of(year, Month.MAY, 1),            // Labour Day
            LocalDate.of(year, Month.MAY, 8),            // Victory in Europe Day
            LocalDate.of(year, Month.JULY, 14),          // Bastille Day
            LocalDate.of(year, Month.AUGUST, 15),        // Assumption of Mary
            LocalDate.of(year, Month.NOVEMBER, 1),       // All Saints' Day
            LocalDate.of(year, Month.NOVEMBER, 11),      // Armistice Day
            LocalDate.of(year, Month.DECEMBER, 25)       // Christmas Day
        )

        if (fixedHolidays.contains(date)) {
            return true
        }

        // Moveable holidays (based on Easter)
        val easterDate = calculateEasterDate(year)
        val easterMonday = easterDate.plusDays(1)
        val ascensionDay = easterDate.plusDays(39)   // 39 days after Easter
        val whitMonday = easterDate.plusDays(50)      // 50 days after Easter (Pentecost Monday)

        return date == easterMonday || date == ascensionDay || date == whitMonday
    }

    /**
     * Check if a given date should use holiday/Sunday schedules
     * Returns true if the date is either a school holiday OR a French public holiday
     */
    @SuppressLint("NewApi")
    fun isHoliday(date: LocalDate): Boolean {
        return isSchoolHoliday(date) || isFrenchPublicHoliday(date)
    }

    /**
     * Calculate Easter date for a given year using the Computus algorithm (Meeus/Jones/Butcher)
     * This is the most accurate algorithm for Gregorian calendar dates
     */
    @SuppressLint("NewApi")
    private fun calculateEasterDate(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = ((h + l - 7 * m + 114) % 31) + 1

        return LocalDate.of(year, month, day)
    }
}