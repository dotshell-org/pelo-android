package com.pelotcl.app.data.gtfs

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import java.io.FileOutputStream
import java.io.IOException

enum class LineType {
    METRO, FUNICULAR, NAVIGONE, TRAM, BUS, CHRONO, OTHER
}

class SchedulesRepository(private val context: Context) {

    private val dbHelper = SchedulesDatabaseHelper(context)

    private fun getLineType(lineName: String): LineType {
        return when {
            lineName.uppercase() in setOf("A", "B", "C", "D") -> LineType.METRO
            lineName.uppercase().startsWith("F") -> LineType.FUNICULAR
            lineName.uppercase().startsWith("NAV") -> LineType.NAVIGONE
            lineName.uppercase().startsWith("T") && !lineName.uppercase().startsWith("TB") -> LineType.TRAM
            lineName.uppercase().startsWith("C") && lineName.substring(1).toIntOrNull() != null -> LineType.CHRONO
            else -> LineType.BUS // Default to bus for all other lines
        }
    }

    fun getHeadsigns(routeName: String): Map<Int, String> {
        val result = mutableMapOf<Int, String>()
        try {
            val db = dbHelper.readableDatabase
            val cursor = db.rawQuery(
                "SELECT direction_id, trip_headsign FROM directions WHERE route_name = ?",
                arrayOf(routeName)
            )
            while (cursor.moveToNext()) {
                val directionId = cursor.getInt(0)
                val headsign = cursor.getString(1)
                result[directionId] = headsign
            }
            cursor.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }

    fun getSchedules(lineName: String, stopName: String, directionId: Int, isHoliday: Boolean): List<String> {
        Log.d("NavigoneDebug", "getSchedules: line='$lineName', stop='$stopName', direction=$directionId, isHoliday=$isHoliday")
        val result = mutableListOf<String>()
        try {
            val db = dbHelper.readableDatabase
            
            val lineType = getLineType(lineName)
            val effectiveIsHoliday = if (lineType == LineType.METRO || lineType == LineType.FUNICULAR) {
                false // Metros and Funiculars always use normal weekday schedules
            } else {
                isHoliday // Other lines use the determined holiday status
            }

            // Determine which day column to check in calendar table
            // New logic:
            //  - Metro & Funicular: always use weekday schedules -> force "monday"
            //  - Others: use the real day-of-week column (monday..sunday)
            val calendar = java.util.Calendar.getInstance()
            val dayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK)
            val actualDayColumn = when (dayOfWeek) {
                java.util.Calendar.MONDAY -> "monday"
                java.util.Calendar.TUESDAY -> "tuesday"
                java.util.Calendar.WEDNESDAY -> "wednesday"
                java.util.Calendar.THURSDAY -> "thursday"
                java.util.Calendar.FRIDAY -> "friday"
                java.util.Calendar.SATURDAY -> "saturday"
                else -> "sunday"
            }
            val dayColumn = if (lineType == LineType.METRO || lineType == LineType.FUNICULAR) "monday" else actualDayColumn

            // Distinction vacances/pas vacances pour TOUTES les lignes sauf métro et funiculaire, et uniquement en semaine (Lun–Ven).
            // Les jeux de services TCL utilisent souvent des suffixes "AM" (année scolaire) et "AV" (vacances)
            // dans les service_id, mais les préfixes varient selon les lignes. On filtre donc sur '%AM%' vs '%AV%'.
            // Samedi/Dimanche: on ne filtre pas (les services dédiés week-end s'appliquent via calendar).
            // On prévoit un fallback: si le filtre AM/AV renvoie 0 résultat, on relance sans filtre
            // afin de supporter les lignes qui n'utilisent pas ces suffixes.
            val isWeekday = dayColumn in setOf("monday", "tuesday", "wednesday", "thursday", "friday")
            var appliedAmAvFilter = false
            var serviceIdFilter = ""
            if (lineType != LineType.METRO && lineType != LineType.FUNICULAR && isWeekday) {
                appliedAmAvFilter = true
                serviceIdFilter = if (effectiveIsHoliday) {
                    "AND s.service_id LIKE '%AV%'"
                } else {
                    "AND s.service_id LIKE '%AM%'"
                }
            }
            Log.d("NavigoneDebug", "Querying with dayColumn: '$dayColumn', isWeekday=$isWeekday, effectiveIsHoliday: $effectiveIsHoliday, lineType: $lineType, serviceIdFilter: '$serviceIdFilter'")

            // Try exact match first (strict comparison requested)
            var cursor = db.rawQuery(
                """
                SELECT DISTINCT substr(s.arrival_time, 1, 5) AS arrival_time 
                FROM schedules s
                JOIN calendar c ON s.service_id = c.service_id
                WHERE s.route_name = ? 
                AND s.direction_id = ?
                AND c.$dayColumn = 1
                $serviceIdFilter
                AND s.station_name = ?
                ORDER BY s.arrival_time
                """,
                arrayOf(lineName, directionId.toString(), stopName)
            )
            while (cursor.moveToNext()) {
                result.add(cursor.getString(0))
            }
            cursor.close()
            Log.d("NavigoneDebug", "Found ${result.size} schedules after exact match query.")
            
            // Strict mode: no LIKE fallback
            if (result.isEmpty()) {
                Log.w("NavigoneDebug", "Strict exact match returned 0 schedules for line='$lineName', stop='$stopName', direction=$directionId, day=$dayColumn, serviceFilter='${serviceIdFilter.isNotBlank()}'. Will try without AM/AV if applicable.")
            }

                // Strict mode: no tokenized fallback here

            // Fallback: si on a appliqué le filtre AM/AV et qu'on n'a rien trouvé, relancer sans filtre (toujours en égalité stricte)
            if (result.isEmpty() && appliedAmAvFilter) {
                Log.d("NavigoneDebug", "No results with AM/AV filter, retrying without service_id filter as fallback.")
                serviceIdFilter = ""
                // Re-try exact match
                cursor = db.rawQuery(
                    """
                    SELECT DISTINCT substr(s.arrival_time, 1, 5) AS arrival_time 
                    FROM schedules s
                    JOIN calendar c ON s.service_id = c.service_id
                    WHERE s.route_name = ? 
                    AND s.direction_id = ?
                    AND c.$dayColumn = 1
                    $serviceIdFilter
                    AND s.station_name = ?
                    ORDER BY s.arrival_time
                    """,
                    arrayOf(lineName, directionId.toString(), stopName)
                )
                while (cursor.moveToNext()) {
                    result.add(cursor.getString(0))
                }
                cursor.close()

                if (result.isEmpty()) {
                    Log.w("NavigoneDebug", "Strict exact match still 0 without AM/AV filter for line='$lineName', stop='$stopName', direction=$directionId, day=$dayColumn. This indicates a stop name mismatch.\nConseil: vérifier que le libellé passé à getSchedules() correspond exactement à 'station_name' dans la base.")
                }
                Log.d("NavigoneDebug", "Found ${result.size} schedules after fallback without AM/AV filter.")
            }
            
        } catch (e: Exception) {
            Log.e("NavigoneDebug", "Error in getSchedules: ${e.message}", e)
            e.printStackTrace()
        }
        Log.d("NavigoneDebug", "Returning ${result.distinct().size} unique schedules.")
        return result.distinct().sorted()
    }

    private class SchedulesDatabaseHelper(private val context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
        
        companion object {
            private const val DB_NAME = "schedules.db"
            private const val DB_VERSION = 1
        }

        override fun onCreate(db: SQLiteDatabase) {
            // Database created via copy, no schema creation needed here
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // In a production app, we would handle DB updates here by re-copying the file
            // For now, we rely on the fact that if the file exists, we use it.
        }
        
        override fun getReadableDatabase(): SQLiteDatabase {
             if (!checkDatabase()) {
                 copyDatabase()
             }
             return super.getReadableDatabase()
        }

        override fun getWritableDatabase(): SQLiteDatabase {
             if (!checkDatabase()) {
                 copyDatabase()
             }
             return super.getWritableDatabase()
        }
        
        private fun checkDatabase(): Boolean {
            val dbFile = context.getDatabasePath(DB_NAME)
            return dbFile.exists()
        }
        
        private fun copyDatabase() {
            try {
                val inputStream = context.assets.open("databases/$DB_NAME")
                val outFile = context.getDatabasePath(DB_NAME)
                
                if (outFile.parentFile?.exists() == false) {
                    outFile.parentFile?.mkdirs()
                }

                val outputStream = FileOutputStream(outFile)
                inputStream.copyTo(outputStream)
                outputStream.flush()
                outputStream.close()
                inputStream.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }
}
