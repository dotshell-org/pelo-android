package com.pelotcl.app.specific.utils

import java.time.LocalDate

data class Holiday(
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate?
)