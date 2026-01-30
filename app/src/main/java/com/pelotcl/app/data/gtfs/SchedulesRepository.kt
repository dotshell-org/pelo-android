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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class LineType {
    METRO, FUNICULAR, NAVIGONE, TRAM, BUS, CHRONO
}

class SchedulesRepository(context: Context) {

    private val appContext: Context = context.applicationContext
    private val dbHelper = SchedulesDatabaseHelper(appContext)

    // Mutex for thread-safe access to allStopsCache
    private val allStopsMutex = Mutex()
    // In-memory cache for all stops (loaded from DB)
    private var allStopsCache: List<Triple<String, String, Boolean>>? = null
    // Cache of normalized stop names for fast search (indexed same as allStopsCache)
    private var normalizedNamesCache: List<String>? = null

    companion object {
        // LRU Cache for schedules: key = "lineName|stopName|directionId|isHoliday"
        // Max 100 entries (typical usage pattern is viewing ~10-20 stops per session)
        private val schedulesCache = LruCache<String, List<String>>(100)

        // LRU Cache for headsigns: key = routeName
        private val headsignsCache = LruCache<String, Map<Int, String>>(50)

        // LRU Cache for search results: key = normalized query (3+ chars)
        // Increased size for better search responsiveness
        private val searchCache = LruCache<String, List<StationSearchResult>>(50)

        // LRU Cache for stop sequences: key = "routeName|directionId"
        private val stopSequencesCache = LruCache<String, List<Pair<String, Int>>>(100)

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
            stopSequencesCache.evictAll()
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
            // Utiliser la recherche en mémoire pour une gestion fiable des accents
            // (SQLite LIKE ne gère pas bien les accents sur Android)
            val allStops = getAllStops()
            val normalizedNames = normalizedNamesCache ?: return emptyList()

            // Filtrer les candidats avec fuzzy matching optimisé (utilise le cache des noms normalisés)
            val filteredWithIndex = allStops.indices.filter { index ->
                SearchUtils.fuzzyContainsNormalized(normalizedNames[index], normalizedQuery)
            }.map { index -> allStops[index] to normalizedNames[index] }

            // Trier: priorité aux arrêts qui commencent par la query (avec noms pré-normalisés)
            val sorted = filteredWithIndex.sortedWith(
                compareBy(
                    { !SearchUtils.fuzzyStartsWithNormalized(it.second, normalizedQuery) },
                    { it.first.first }
                )
            ).take(50).map { it.first }

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

    /**
     * Helper to load all stops into memory once for efficient filtering.
     * Thread-safe using Mutex. Also builds the normalized names cache.
     */
    private suspend fun getAllStops(): List<Triple<String, String, Boolean>> {
        allStopsCache?.let { return it }

        return allStopsMutex.withLock {
            allStopsCache?.let { return it }

            val stops = mutableListOf<Triple<String, String, Boolean>>()
            val normalizedNames = mutableListOf<String>()
            try {
                val db = dbHelper.readableDatabase
                // Load critical columns for search: name, lines served (desserte), isPMR
                val cursor = db.rawQuery("SELECT nom, desserte, pmr FROM arrets", null)

                while (cursor.moveToNext()) {
                    val name = cursor.getString(0)
                    val desserteRaw = cursor.getString(1) ?: ""
                    val isPmr = cursor.getInt(2) == 1
                    stops.add(Triple(name, desserteRaw, isPmr))
                    normalizedNames.add(SearchUtils.normalizeForSearch(name))
                }
                cursor.close()
            } catch (e: Exception) {
                Log.e("SchedulesRepository", "Error loading all stops: ${e.message}")
            }

            // Cache the immutable lists
            val immutableStops = stops.toList()
            allStopsCache = immutableStops
            normalizedNamesCache = normalizedNames.toList()
            immutableStops
        }
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

    /**
     * Retrieves the canonical stop sequence for a line and direction from GTFS data.
     * @param routeName The route/line name (e.g., "86", "A", "T1")
     * @param directionId The direction ID (0 or 1)
     * @return List of pairs (station_name, stop_sequence) ordered by sequence, or empty if not found
     */
    fun getStopSequences(routeName: String, directionId: Int): List<Pair<String, Int>> {
        val cacheKey = "$routeName|$directionId"
        stopSequencesCache.get(cacheKey)?.let { return it }

        val result = mutableListOf<Pair<String, Int>>()
        try {
            val db = dbHelper.readableDatabase
            val cursor = db.rawQuery(
                """
                SELECT station_name, stop_sequence 
                FROM stop_sequences 
                WHERE route_name = ? AND direction_id = ?
                ORDER BY stop_sequence ASC
                """.trimIndent(),
                arrayOf(routeName, directionId.toString())
            )
            while (cursor.moveToNext()) {
                val stationName = cursor.getString(0)
                val stopSequence = cursor.getInt(1)
                result.add(Pair(stationName, stopSequence))
            }
            cursor.close()

            if (result.isNotEmpty()) {
                stopSequencesCache.put(cacheKey, result)
            }
        } catch (e: Exception) {
            Log.e("SchedulesRepository", "Error getting stop sequences for $routeName dir $directionId", e)
        }
        return result
    }

    /**
     * Retrieves all available stop sequences for a line (both directions).
     * @param routeName The route/line name
     * @return Map of directionId to list of (station_name, stop_sequence)
     */
    fun getAllStopSequences(routeName: String): Map<Int, List<Pair<String, Int>>> {
        val result = mutableMapOf<Int, List<Pair<String, Int>>>()
        // Try both directions
        for (directionId in listOf(0, 1)) {
            val sequences = getStopSequences(routeName, directionId)
            if (sequences.isNotEmpty()) {
                result[directionId] = sequences
            }
        }
        return result
    }

    fun getSchedules(lineName: String, stopName: String, directionId: Int, isSchoolHoliday: Boolean, isPublicHoliday: Boolean): List<String> {
        // Build cache key
        val cacheKey = "$lineName|$stopName|$directionId|$isSchoolHoliday|$isPublicHoliday"
        schedulesCache.get(cacheKey)?.let {
            return it
        }

        val result = mutableListOf<String>()
        try {
            val db = dbHelper.readableDatabase

            val lineType = getLineType(lineName)

            // School holidays only affect bus schedules (AM vs AV)
            val effectiveIsSchoolHoliday = if (lineType == LineType.METRO || lineType == LineType.FUNICULAR || lineType == LineType.TRAM) {
                false
            } else {
                isSchoolHoliday
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

            // CRITICAL: Public holidays always use Sunday schedules, regardless of actual day
            val dayColumn = if (isPublicHoliday) {
                "sunday"
            } else {
                actualDayColumn
            }

            // Format today's date as YYYYMMDD for GTFS calendar date comparison
            val todayFormatted = String.format(
                java.util.Locale.US,
                "%04d%02d%02d",
                calendar.get(java.util.Calendar.YEAR),
                calendar.get(java.util.Calendar.MONTH) + 1,
                calendar.get(java.util.Calendar.DAY_OF_MONTH)
            )

            val isWeekday = actualDayColumn in setOf("monday", "tuesday", "wednesday", "thursday", "friday")
            var appliedAmAvFilter = false
            var serviceIdFilter = ""

            // For bus lines on weekdays: use AM (normal) or AV (school holiday) schedules
            // Note: This is separate from public holidays which override the dayColumn to Sunday
            if (lineType != LineType.METRO && lineType != LineType.FUNICULAR && lineType != LineType.NAVIGONE && lineType != LineType.TRAM && isWeekday && !isPublicHoliday) {
                appliedAmAvFilter = true
                serviceIdFilter = if (effectiveIsSchoolHoliday) {
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

            // For strong lines (metro/tram/funicular), select only ONE service_id to avoid
            // mixing schedules from overlapping service periods (e.g., school vs vacation)
            // We select the service with the MOST schedules (main service for the day)
            var strongLineServiceFilter = ""
            if (lineType == LineType.METRO || lineType == LineType.FUNICULAR || lineType == LineType.TRAM) {
                val serviceIdCursor = db.rawQuery(
                    """
                    SELECT s.service_id, COUNT(*) as cnt
                    FROM schedules s
                    JOIN calendar c ON s.service_id = c.service_id
                    WHERE s.route_name = ? 
                    AND s.direction_id = ?
                    AND c.$dayColumn = 1
                    AND c.start_date <= ?
                    AND c.end_date >= ?
                    AND s.station_name = ? COLLATE NOCASE
                    GROUP BY s.service_id
                    ORDER BY cnt DESC
                    LIMIT 1
                    """,
                    arrayOf(lineName, directionId.toString(), todayFormatted, todayFormatted, stopName)
                )
                if (serviceIdCursor.moveToFirst()) {
                    val selectedServiceId = serviceIdCursor.getString(0)
                    strongLineServiceFilter = "AND s.service_id = '$selectedServiceId'"
                }
                serviceIdCursor.close()
            }

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
                $strongLineServiceFilter
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
                // For strong lines, also try to get a single service_id without date filter
                if (strongLineServiceFilter.isEmpty() && (lineType == LineType.METRO || lineType == LineType.FUNICULAR || lineType == LineType.TRAM)) {
                    val serviceIdCursor = db.rawQuery(
                        """
                        SELECT s.service_id, COUNT(*) as cnt
                        FROM schedules s
                        JOIN calendar c ON s.service_id = c.service_id
                        WHERE s.route_name = ? 
                        AND s.direction_id = ?
                        AND c.$dayColumn = 1
                        AND s.station_name = ? COLLATE NOCASE
                        GROUP BY s.service_id
                        ORDER BY cnt DESC
                        LIMIT 1
                        """,
                        arrayOf(lineName, directionId.toString(), stopName)
                    )
                    if (serviceIdCursor.moveToFirst()) {
                        val selectedServiceId = serviceIdCursor.getString(0)
                        strongLineServiceFilter = "AND s.service_id = '$selectedServiceId'"
                    }
                    serviceIdCursor.close()
                }

                cursor = db.rawQuery(
                    """
                    SELECT DISTINCT substr(s.arrival_time, 1, 5) AS arrival_time 
                    FROM schedules s
                    JOIN calendar c ON s.service_id = c.service_id
                    WHERE s.route_name = ? 
                    AND s.direction_id = ?
                    AND c.$dayColumn = 1
                    $serviceIdFilter
                    $strongLineServiceFilter
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
                    $strongLineServiceFilter
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