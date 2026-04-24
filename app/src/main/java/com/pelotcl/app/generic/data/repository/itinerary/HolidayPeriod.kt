package com.pelotcl.app.generic.data.repository.itinerary

import java.time.LocalDate

/**
 * Internal representation of a holiday period
 */
internal data class HolidayPeriod(
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate
)