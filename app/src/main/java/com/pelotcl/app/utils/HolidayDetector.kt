package com.pelotcl.app.utils

import android.annotation.SuppressLint
import android.content.Context
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

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

    @SuppressLint("NewApi")
    fun isSchoolHoliday(date: LocalDate): Boolean {
        return holidays.any { holiday ->
            !date.isBefore(holiday.startDate) && (holiday.endDate == null || !date.isAfter(holiday.endDate))
        }
    }
}
