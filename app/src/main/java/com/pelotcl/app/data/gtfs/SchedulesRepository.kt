package com.pelotcl.app.data.gtfs

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.pelotcl.app.ui.components.StationSearchResult
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

enum class LineType {
    METRO, FUNICULAR, NAVIGONE, TRAM, BUS, CHRONO, OTHER
}

class SchedulesRepository(private val context: Context) {

    private val dbHelper = SchedulesDatabaseHelper(context)

    fun searchStopsByName(query: String): List<StationSearchResult> {
        val results = mutableListOf<StationSearchResult>()
        try {
            val db = dbHelper.readableDatabase

            // On vérifie d'abord que la table existe (gestion des mises à jour foireuses)
            // Si la table n'existe pas, cela lèvera une exception qui sera catchée
            val cursor = db.rawQuery(
                """
                SELECT nom, desserte, pmr 
                FROM arrets 
                WHERE nom LIKE ? 
                ORDER BY 
                  CASE WHEN nom LIKE ? THEN 1 ELSE 2 END, 
                  nom
                LIMIT 50
                """,
                arrayOf("%$query%", "$query%")
            )

            while (cursor.moveToNext()) {
                val name = cursor.getString(0)
                val desserteRaw = cursor.getString(1)
                val isPmr = cursor.getInt(2) == 1

                val lines = if (!desserteRaw.isNullOrBlank()) {
                    desserteRaw.split(",").map { it.trim() }
                } else {
                    emptyList()
                }

                results.add(StationSearchResult(name, lines, isPmr))
            }
            cursor.close()

        } catch (e: Exception) {
            Log.e("SchedulesRepository", "Error searching stops: ${e.message}")
            // Si l'erreur est "no such table", c'est que la DB est vieille.
            // On pourrait forcer un upgrade ici, mais le changement de version ci-dessous devrait suffire.
        }
        return results
    }

    private fun getLineType(lineName: String): LineType {
        return when {
            lineName.uppercase() in setOf("A", "B", "C", "D") -> LineType.METRO
            lineName.uppercase().startsWith("F") -> LineType.FUNICULAR
            lineName.uppercase().startsWith("NAV") -> LineType.NAVIGONE
            lineName.uppercase().startsWith("T") && !lineName.uppercase().startsWith("TB") -> LineType.TRAM
            lineName.uppercase().startsWith("C") && lineName.substring(1).toIntOrNull() != null -> LineType.CHRONO
            else -> LineType.BUS
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
        // ... (votre code getSchedules existant, inchangé pour la lisibilité) ...
        // Je remets le bloc complet pour éviter les erreurs de copier/coller
        Log.d("NavigoneDebug", "getSchedules: line='$lineName', stop='$stopName', direction=$directionId, isHoliday=$isHoliday")
        val result = mutableListOf<String>()
        try {
            val db = dbHelper.readableDatabase

            val lineType = getLineType(lineName)
            val effectiveIsHoliday = if (lineType == LineType.METRO || lineType == LineType.FUNICULAR) {
                false
            } else {
                isHoliday
            }

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

            val isWeekday = dayColumn in setOf("monday", "tuesday", "wednesday", "thursday", "friday")
            var appliedAmAvFilter = false
            var serviceIdFilter = ""

            if (lineType != LineType.METRO && lineType != LineType.FUNICULAR && lineType != LineType.NAVIGONE && isWeekday) {
                appliedAmAvFilter = true
                serviceIdFilter = if (effectiveIsHoliday) {
                    "AND s.service_id LIKE '%AV%'"
                } else {
                    "AND s.service_id LIKE '%AM%'"
                }
            }

            var cursor = db.rawQuery(
                """
                SELECT DISTINCT substr(s.arrival_time, 1, 5) AS arrival_time 
                FROM schedules s
                JOIN calendar c ON s.service_id = c.service_id
                WHERE s.route_name = ? 
                AND s.direction_id = ?
                AND c.$dayColumn = 1
                $serviceIdFilter
                AND s.station_name = ? COLLATE NOCASE
                ORDER BY s.arrival_time
                """,
                arrayOf(lineName, directionId.toString(), stopName)
            )
            while (cursor.moveToNext()) {
                result.add(cursor.getString(0))
            }
            cursor.close()

            if (result.isEmpty() && appliedAmAvFilter) {
                serviceIdFilter = ""
                cursor = db.rawQuery(
                    """
                    SELECT DISTINCT substr(s.arrival_time, 1, 5) AS arrival_time 
                    FROM schedules s
                    JOIN calendar c ON s.service_id = c.service_id
                    WHERE s.route_name = ? 
                    AND s.direction_id = ?
                    AND c.$dayColumn = 1
                    $serviceIdFilter
                    AND s.station_name = ? COLLATE NOCASE
                    ORDER BY s.arrival_time
                    """,
                    arrayOf(lineName, directionId.toString(), stopName)
                )
                while (cursor.moveToNext()) {
                    result.add(cursor.getString(0))
                }
                cursor.close()
            }

        } catch (e: Exception) {
            Log.e("NavigoneDebug", "Error in getSchedules: ${e.message}", e)
        }
        return result.distinct().sorted()
    }

    private class SchedulesDatabaseHelper(private val context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

        companion object {
            private const val DB_NAME = "schedules.db"
            // J'ai passé la version à 2 pour forcer la mise à jour
            private const val DB_VERSION = 2
        }

        override fun onCreate(db: SQLiteDatabase) {
            // Rien à faire ici, car on copie la base depuis les assets
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // Si la version change (ex: passage de 1 à 2), on supprime l'ancienne base
            // pour forcer la recopie au prochain accès
            Log.d("SchedulesRepository", "Upgrading DB from $oldVersion to $newVersion: deleting old file.")
            context.deleteDatabase(DB_NAME)
            // Note: On ne peut pas appeler copyDatabase() ici car la DB est ouverte par SQLiteOpenHelper
            // La copie se fera automatiquement au prochain appel de getReadableDatabase()
            // car checkDatabase() retournera false.
        }

        override fun getReadableDatabase(): SQLiteDatabase {
            if (!checkDatabase()) {
                copyDatabase()
            }
            return try {
                super.getReadableDatabase()
            } catch (e: Exception) {
                // Fallback critique : si corruption ou problème de version, on force la recréation
                context.deleteDatabase(DB_NAME)
                copyDatabase()
                super.getReadableDatabase()
            }
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
            Log.d("SchedulesRepository", "Copying database from assets...")
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
                Log.d("SchedulesRepository", "Database copied successfully.")
            } catch (e: IOException) {
                e.printStackTrace()
                Log.e("SchedulesRepository", "Error copying database: ${e.message}")
            }
        }
    }
}