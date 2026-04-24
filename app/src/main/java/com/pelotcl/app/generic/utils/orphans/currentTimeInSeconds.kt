package com.pelotcl.app.generic.utils.orphans

import java.util.Calendar

fun currentTimeInSeconds(): Int {
    val calendar = Calendar.getInstance()
    return calendar.get(Calendar.HOUR_OF_DAY) * 3600 +
            calendar.get(Calendar.MINUTE) * 60 +
            calendar.get(Calendar.SECOND)
}
