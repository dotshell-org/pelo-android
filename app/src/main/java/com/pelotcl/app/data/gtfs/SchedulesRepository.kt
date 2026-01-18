package com.pelotcl.app.data.gtfs

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import android.util.LruCache
import com.pelotcl.app.ui.components.StationSearchResult
import com.pelotcl.app.data.repository.TransportRepository
import java.io.FileOutputStream
import java.io.IOException

enum class LineType {
    METRO, FUNICULAR, NAVIGONE, TRAM, BUS, CHRONO
}

class SchedulesRepository(context: Context) {

    private val appContext: Context = context.applicationContext
    private val dbHelper = SchedulesDatabaseHelper(appContext)

    companion object {
        // LRU Cache for schedules: key = "lineName|stopName|directionId|isHoliday"
        // Max 100 entries (typical usage pattern is viewing ~10-20 stops per session)
        private val schedulesCache = LruCache<String, List<String>>(100)

        // LRU Cache for headsigns: key = routeName
        private val headsignsCache = LruCache<String, Map<Int, String>>(50)

        // LRU Cache for search results: key = query
        private val searchCache = LruCache<String, List<StationSearchResult>>(30)

        /**
         * Clear all caches (call when data is updated)
         */
        fun clearCaches() {
            schedulesCache.evictAll()
            headsignsCache.evictAll()
            searchCache.evictAll()
        }
    }

    suspend fun searchStopsByName(query: String): List<StationSearchResult> {
        // Check cache first
        val cacheKey = query.lowercase().trim()
        searchCache.get(cacheKey)?.let { return it }

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
            // Fallback: search within cached API stops if SQLite fails (e.g. no such table)
            return try {
                val repo = TransportRepository(appContext)
                val stopsResult = repo.getAllStops()
                val lower = query.lowercase()
                stopsResult.getOrNull()?.features
                    ?.asSequence()
                    ?.filter { it.properties.nom.contains(query, ignoreCase = true) }
                    ?.map { stop ->
                        val desserteRaw = stop.properties.desserte
                        val lines = if (!desserteRaw.isNullOrBlank()) desserteRaw.split(',').map { it.trim() } else emptyList()
                        StationSearchResult(stop.properties.nom, lines, stop.properties.pmr)
                    }
                    ?.sortedWith(compareBy<StationSearchResult>({ !it.stopName.lowercase().startsWith(lower) }, { it.stopName }))
                    ?.take(50)
                    ?.toList()
                    ?: emptyList()
            } catch (t: Throwable) {
                Log.e("SchedulesRepository", "Fallback search failed: ${t.message}")
                emptyList()
            }
        }

        // Cache the results
        if (results.isNotEmpty()) {
            searchCache.put(cacheKey, results)
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
        // Check cache first
        headsignsCache.get(routeName)?.let { return it }

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

            // Cache the result
            if (result.isNotEmpty()) {
                headsignsCache.put(routeName, result)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }

    fun getSchedules(lineName: String, stopName: String, directionId: Int, isHoliday: Boolean): List<String> {
        // Build cache key
        val cacheKey = "$lineName|$stopName|$directionId|$isHoliday"
        schedulesCache.get(cacheKey)?.let { return it }

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

        val finalResult = result.distinct().sorted()

        // Cache the result (even empty results to avoid repeated queries)
        schedulesCache.put(cacheKey, finalResult)

        return finalResult
    }

    private class SchedulesDatabaseHelper(private val context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

        companion object {
            private const val DB_NAME = "schedules.db"
            private const val DB_VERSION = 4
        }

        override fun onCreate(db: SQLiteDatabase) {
            // Nothing to do here, because we copy the database from assets
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // Force recreation by throwing an exception, which is caught in getReadableDatabase
            throw RuntimeException("Database upgrade required from $oldVersion to $newVersion")
        }

        override fun getReadableDatabase(): SQLiteDatabase {
            if (!checkDatabase()) {
                copyDatabase()
            }
            return try {
                super.getReadableDatabase()
            } catch (_: Exception) {
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

                // Set version to match expected version to avoid immediate upgrade loop
                try {
                    val db = SQLiteDatabase.openDatabase(outFile.path, null, SQLiteDatabase.OPEN_READWRITE)
                    db.version = DB_VERSION
                    db.close()
                } catch (e: Exception) {
                    Log.e("SchedulesRepository", "Error setting database version: ${e.message}")
                }
            } catch (e: IOException) {
                e.printStackTrace()
                Log.e("SchedulesRepository", "Error copying database: ${e.message}")
            }
        }
    }
}