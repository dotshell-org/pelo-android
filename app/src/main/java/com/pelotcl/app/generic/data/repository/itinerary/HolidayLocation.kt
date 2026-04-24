package com.pelotcl.app.generic.data.repository.itinerary

import kotlinx.serialization.Serializable

@Serializable
internal data class HolidayLocation(
    val department: String,
    val academy: String,
    val zone: String
)