package com.pelotcl.app.data.gtfs

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import android.util.LruCache
import com.pelotcl.app.ui.components.StationSearchResult
import com.pelotcl.app.data.repository.TransportRepository
import com.pelotcl.app.utils.SearchUtils
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

        // LRU Cache for search results: key = normalized query (3+ chars)
        // Increased size for better search responsiveness
        private val searchCache = LruCache<String, List<StationSearchResult>>(50)

        // Track if database has been warmed up
        @Volatile
        private var isDatabaseWarmedUp = false

        /**
         * Clear all caches (call when data is updated)
         */
        @Suppress("unused") // Public API for cache invalidation when GTFS data is updated
        fun clearCaches() {
            schedulesCache.evictAll()
            headsignsCache.evictAll()
            searchCache.evictAll()
        }
    }

    /**
     * Warm up the SQLite database by triggering a simple query.
     * This initializes the database connection, applies optimizations,
     * and loads the page cache. Call on IO dispatcher at startup.
     */
    fun warmupDatabase() {
        if (isDatabaseWarmedUp) return
        try {
            val db = dbHelper.readableDatabase
            // Simple query to warm up the cache and trigger PRAGMA settings
            db.rawQuery("SELECT 1 FROM arrets LIMIT 1", null).use { cursor ->
                cursor.moveToFirst()
            }
            // Also warm up the schedules table (most queried)
            db.rawQuery("SELECT 1 FROM schedules LIMIT 1", null).use { cursor ->
                cursor.moveToFirst()
            }
            isDatabaseWarmedUp = true
        } catch (e: Exception) {
            Log.w("SchedulesRepository", "Database warmup failed: ${e.message}")
        }
    }

    suspend fun searchStopsByName(query: String): List<StationSearchResult> {
        // Don't cache very short queries (too many results, not useful)
        val normalizedQuery = SearchUtils.normalizeForSearch(query)
        val cacheKey = normalizedQuery
        if (cacheKey.length >= 2) {
            searchCache.get(cacheKey)?.let { return it }
        }

        val results = mutableListOf<StationSearchResult>()
        try {
            val db = dbHelper.readableDatabase

            // Optimisation: utiliser SQL pour pré-filtrer largement
            // On prend tous les arrêts qui contiennent au moins le premier mot
            // puis le fuzzy matching en mémoire fait le travail précis (avec accents, tirets, etc.)
            val words = query.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
            
            val cursor = if (words.isEmpty()) {
                db.rawQuery("SELECT nom, desserte, pmr FROM arrets LIMIT 50", null)
            } else {
                // Pré-filtre large: juste le premier mot pour réduire la liste
                // Le fuzzy matching vérifiera tous les mots après
                db.rawQuery(
                    "SELECT nom, desserte, pmr FROM arrets WHERE LOWER(nom) LIKE ? LIMIT 500",
                    arrayOf("%${words[0].lowercase()}%")
                )
            }

            val candidateStops = mutableListOf<Triple<String, String, Boolean>>()
            while (cursor.moveToNext()) {
                val name = cursor.getString(0)
                val desserteRaw = cursor.getString(1) ?: ""
                val isPmr = cursor.getInt(2) == 1
                candidateStops.add(Triple(name, desserteRaw, isPmr))
            }
            cursor.close()

            // Filtrer les candidats avec fuzzy matching pour une précision maximale
            val filteredStops = candidateStops.filter { (name, _, _) ->
                SearchUtils.fuzzyContains(name, query)
            }

            // Trier: priorité aux arrêts qui commencent par la query
            val sorted = filteredStops.sortedWith(
                compareBy(
                    { !SearchUtils.fuzzyStartsWith(it.first, query) },
                    { it.first }
                )
            ).take(50)

            // Convertir en StationSearchResult et fusionner les arrêts du même nom
            val stopsByName = mutableMapOf<String, MutableList<String>>()
            val pmrByName = mutableMapOf<String, Boolean>()
            
            sorted.forEach { (name, desserteRaw, isPmr) ->
                val lines = if (desserteRaw.isNotBlank()) {
                    // Parse pour enlever les suffixes :A et :R et extraire uniquement les noms de lignes
                    desserteRaw.split(",")
                        .mapNotNull { part ->
                            val trimmed = part.trim()
                            if (trimmed.isEmpty()) null else trimmed.substringBefore(":").trim()
                        }
                        .filter { it.isNotEmpty() }
                } else {
                    emptyList()
                }
                
                // Fusionner les lignes pour le même arrêt
                stopsByName.getOrPut(name) { mutableListOf() }.addAll(lines)
                // Un arrêt est PMR s'il l'est dans au moins une de ses entrées
                pmrByName[name] = pmrByName.getOrDefault(name, false) || isPmr
            }
            
            // Créer les résultats avec lignes fusionnées et dédupliquées
            stopsByName.forEach { (name, lines) ->
                results.add(StationSearchResult(name, lines.distinct(), pmrByName[name] ?: false))
            }

        } catch (e: Exception) {
            Log.e("SchedulesRepository", "Error searching stops: ${e.message}")
            // Fallback: search within cached API stops if SQLite fails (e.g. no such table)
            return try {
                val repo = TransportRepository(appContext)
                val stopsResult = repo.getAllStops()
                
                // Grouper les arrêts par nom et fusionner leurs lignes
                val stopsByName = mutableMapOf<String, MutableList<String>>()
                val pmrByName = mutableMapOf<String, Boolean>()
                
                stopsResult.getOrNull()?.features
                    ?.asSequence()
                    ?.filter { SearchUtils.fuzzyContains(it.properties.nom, query) }
                    ?.forEach { stop ->
                        val desserteRaw = stop.properties.desserte
                        val lines = desserteRaw?.takeIf { !it.isBlank() }
                            ?.split(',')
                            ?.mapNotNull { part ->
                                val trimmed = part.trim()
                                if (trimmed.isEmpty()) null else trimmed.substringBefore(":").trim()
                            }
                            ?.filter { it.isNotEmpty() }
                            ?: emptyList()
                        
                        val name = stop.properties.nom
                        stopsByName.getOrPut(name) { mutableListOf() }.addAll(lines)
                        pmrByName[name] = pmrByName.getOrDefault(name, false) || stop.properties.pmr
                    }
                
                // Créer les résultats avec lignes fusionnées
                stopsByName.map { (name, lines) ->
                    StationSearchResult(name, lines.distinct(), pmrByName[name] ?: false)
                }
                    .sortedWith(compareBy<StationSearchResult>({ !SearchUtils.fuzzyStartsWith(it.stopName, query) }, { it.stopName }))
                    .take(50)
                    .toList()
                    ?: emptyList()
            } catch (t: Throwable) {
                Log.e("SchedulesRepository", "Fallback search failed: ${t.message}")
                emptyList()
            }
        }

        // Cache the results (only for queries with 2+ chars)
        if (results.isNotEmpty() && cacheKey.length >= 2) {
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
        schedulesCache.get(cacheKey)?.let {
            return it
        }

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

            // Format today's date as YYYYMMDD for GTFS calendar date comparison
            val todayFormatted = String.format(
                java.util.Locale.US,
                "%04d%02d%02d",
                calendar.get(java.util.Calendar.YEAR),
                calendar.get(java.util.Calendar.MONTH) + 1,
                calendar.get(java.util.Calendar.DAY_OF_MONTH)
            )

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

            // Debug: Check if stop exists in DB
            val stopCheckCursor = db.rawQuery(
                "SELECT COUNT(*) FROM schedules WHERE station_name = ? COLLATE NOCASE",
                arrayOf(stopName)
            )
            stopCheckCursor.close()

            // Debug: Check if line+stop combination exists
            val lineStopCheckCursor = db.rawQuery(
                "SELECT COUNT(*) FROM schedules WHERE route_name = ? AND station_name = ? COLLATE NOCASE",
                arrayOf(lineName, stopName)
            )
            lineStopCheckCursor.close()

            // Debug: Check calendar entries for this line
            val calendarCheckCursor = db.rawQuery(
                """
                SELECT DISTINCT c.service_id, c.start_date, c.end_date, c.$dayColumn 
                FROM schedules s
                JOIN calendar c ON s.service_id = c.service_id
                WHERE s.route_name = ? AND s.station_name = ? COLLATE NOCASE
                LIMIT 5
                """,
                arrayOf(lineName, stopName)
            )
            calendarCheckCursor.close()

            // First attempt: with date validation (strict GTFS compliance)
            var cursor = db.rawQuery(
                """
                SELECT DISTINCT substr(s.arrival_time, 1, 5) AS arrival_time 
                FROM schedules s
                JOIN calendar c ON s.service_id = c.service_id
                WHERE s.route_name = ? 
                AND s.direction_id = ?
                AND c.$dayColumn = 1
                AND c.start_date <= ?
                AND c.end_date >= ?
                $serviceIdFilter
                AND s.station_name = ? COLLATE NOCASE
                ORDER BY s.arrival_time
                """,
                arrayOf(lineName, directionId.toString(), todayFormatted, todayFormatted, stopName)
            )
            while (cursor.moveToNext()) {
                result.add(cursor.getString(0))
            }
            cursor.close()

            // Fallback 1: If no results with date validation, try without date filter
            // This handles expired GTFS data gracefully
            if (result.isEmpty()) {
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

            // Fallback 2: If still no results and AM/AV filter was applied, try without it
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
            Log.e("SchedulesDebug", "EXCEPTION in getSchedules: ${e.message}", e)
        }

        val finalResult = result.distinct().sorted()
        if (finalResult.isEmpty()) {
            Log.w("SchedulesDebug", "NO SCHEDULES FOUND for line='$lineName' stop='$stopName' dir=$directionId")
        }

        // Cache the result (even empty results to avoid repeated queries)
        schedulesCache.put(cacheKey, finalResult)

        return finalResult
    }

    private class SchedulesDatabaseHelper(private val context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

        companion object {
            private const val DB_NAME = "schedules.db"
            private const val DB_VERSION = 6  // Incremented to force re-copy from assets
        }

        override fun onCreate(db: SQLiteDatabase) {
            // Nothing to do here, because we copy the database from assets
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // Close this DB and delete it, the next getReadableDatabase call will re-copy
            context.deleteDatabase(DB_NAME)
        }

        override fun getReadableDatabase(): SQLiteDatabase {
            val dbFile = context.getDatabasePath(DB_NAME)

            // Check if database needs to be copied or updated
            var needsCopy = !dbFile.exists()

            if (!needsCopy && dbFile.exists()) {
                // Check if version is outdated OR if tables are missing
                try {
                    val existingDb = SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY)
                    val existingVersion = existingDb.version

                    // Check if schedules table exists
                    val cursor = existingDb.rawQuery(
                        "SELECT name FROM sqlite_master WHERE type='table' AND name='schedules'",
                        null
                    )
                    val hasSchedulesTable = cursor.count > 0
                    cursor.close()
                    existingDb.close()

                    if (existingVersion < DB_VERSION || !hasSchedulesTable) {
                        context.deleteDatabase(DB_NAME)
                        needsCopy = true
                    }
                } catch (e: Exception) {
                    Log.e("SchedulesDebug", "Error opening database, will re-copy: ${e.message}")
                    context.deleteDatabase(DB_NAME)
                    needsCopy = true
                }
            }

            if (needsCopy) {
                copyDatabase()
            }

            val db = try {
                super.getReadableDatabase()
            } catch (e: Exception) {
                Log.e("SchedulesDebug", "Error opening database, will re-copy: ${e.message}")
                context.deleteDatabase(DB_NAME)
                copyDatabase()
                super.getReadableDatabase()
            }

            // Final verification: check if tables exist after opening
            try {
                val cursor = db.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='schedules'",
                    null
                )
                val hasTable = cursor.count > 0
                cursor.close()
                if (!hasTable) {
                    Log.e("SchedulesDebug", "CRITICAL: schedules table still missing after copy! Forcing re-copy...")
                    db.close()
                    context.deleteDatabase(DB_NAME)
                    copyDatabase()
                    return super.getReadableDatabase()
                }
            } catch (e: Exception) {
                Log.e("SchedulesDebug", "Error verifying tables: ${e.message}")
            }

            // Optimize database for read-heavy workload
            optimizeDatabase(db)
            // Ensure indexes exist for fast queries
            ensureIndexes(db)
            return db
        }

        override fun getWritableDatabase(): SQLiteDatabase {
            // Use same logic as getReadableDatabase for consistency
            val dbFile = context.getDatabasePath(DB_NAME)

            var needsCopy = !dbFile.exists()

            if (!needsCopy && dbFile.exists()) {
                try {
                    val existingDb = SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY)
                    val existingVersion = existingDb.version
                    existingDb.close()
                    if (existingVersion < DB_VERSION) {
                        context.deleteDatabase(DB_NAME)
                        needsCopy = true
                    }
                } catch (e: Exception) {
                    context.deleteDatabase(DB_NAME)
                    needsCopy = true
                }
            }

            if (needsCopy) {
                copyDatabase()
            }

            val db = super.getWritableDatabase()
            optimizeDatabase(db)
            ensureIndexes(db)
            return db
        }

        /**
         * Apply SQLite performance optimizations (WAL mode, cache size, etc.)
         * These settings provide 30-50% faster reads for our read-heavy workload.
         */
        private fun optimizeDatabase(db: SQLiteDatabase) {
            try {
                // Enable WAL mode for better concurrent read performance
                db.execSQL("PRAGMA journal_mode=WAL")
                // Reduce sync frequency (safe for read-heavy apps)
                db.execSQL("PRAGMA synchronous=NORMAL")
                // Increase page cache to 8MB (negative = KB)
                db.execSQL("PRAGMA cache_size=-8000")
                // Memory-map up to 64MB for faster reads
                db.execSQL("PRAGMA mmap_size=67108864")
                // Optimize temp storage
                db.execSQL("PRAGMA temp_store=MEMORY")
            } catch (e: Exception) {
                Log.w("SchedulesRepository", "Could not apply SQLite optimizations: ${e.message}")
            }
        }

        /**
         * Create composite indexes if they don't exist for faster schedule queries.
         * These indexes dramatically speed up getSchedules() lookups.
         */
        private fun ensureIndexes(db: SQLiteDatabase) {
            try {
                // Composite index for schedule lookups (route_name, direction_id, station_name)
                db.execSQL("""
                    CREATE INDEX IF NOT EXISTS idx_schedules_lookup 
                    ON schedules(route_name, direction_id, station_name)
                """)

                // Index for station name searches
                db.execSQL("""
                    CREATE INDEX IF NOT EXISTS idx_arrets_nom 
                    ON arrets(nom)
                """)

                // Index for calendar service_id lookups
                db.execSQL("""
                    CREATE INDEX IF NOT EXISTS idx_calendar_service 
                    ON calendar(service_id)
                """)

                // Index for directions lookups
                db.execSQL("""
                    CREATE INDEX IF NOT EXISTS idx_directions_route 
                    ON directions(route_name)
                """)
            } catch (e: Exception) {
                Log.w("SchedulesRepository", "Could not create indexes: ${e.message}")
            }
        }

        private fun copyDatabase() {
            Log.e("SchedulesDebug", "copyDatabase: Copying schedules.db from assets...")
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
                    Log.e("SchedulesDebug", "Error setting database version: ${e.message}")
                }
            } catch (e: IOException) {
                e.printStackTrace()
                Log.e("SchedulesDebug", "Error copying database: ${e.message}")
            }
        }
    }
}