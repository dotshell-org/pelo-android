package com.pelotcl.app.data.repository

import com.pelotcl.app.data.api.VehiclePositionsRetrofitInstance
import com.pelotcl.app.data.model.SiriData
import com.pelotcl.app.data.model.SimpleVehiclePosition
import com.pelotcl.app.data.model.VehicleActivity
import com.pelotcl.app.data.model.VehiclePositionsResponse
import com.pelotcl.app.utils.DotshellRequestLogger
import com.pelotcl.app.utils.withRetry
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit

/**
 * Repository for fetching real-time vehicle positions from the SIRI-lite API
 */
class VehiclePositionsRepository {

    companion object {
        private const val VEHICLE_POSITIONS_STREAM_URL = "https://api.dotshell.eu/pelo/v1/vehicle-monitoring/positions/stream"
        private const val MAX_STREAM_BACKOFF_MS = 30_000L
    }

    private val api = VehiclePositionsRetrofitInstance.api
    private val gson = Gson()
    private val streamClient = OkHttpClient.Builder()
        .addInterceptor(DotshellRequestLogger.interceptor("sse"))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Fetches all vehicle positions from the SIRI-lite API
     *
     * @return List of all vehicle positions across the entire network
     */
    suspend fun getAllVehiclePositions(): Result<List<SimpleVehiclePosition>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = withRetry(maxRetries = 2, initialDelayMs = 500) {
                    api.getVehiclePositions()
                }

                if (!response.success) {
                    return@withContext Result.failure(Exception("API returned success=false"))
                }

                val positions = extractPositionsFromSiriData(response.data)

                Result.success(positions)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Streams all vehicle positions from the mirror API using SSE.
     * Automatically retries with exponential backoff on disconnect.
     */
    fun streamAllVehiclePositions(): Flow<Result<List<SimpleVehiclePosition>>> {
        return callbackFlow {
            val request = Request.Builder()
                .url(VEHICLE_POSITIONS_STREAM_URL)
                .header("Accept", "text/event-stream")
                .build()

            val listener = object : EventSourceListener() {
                override fun onOpen(eventSource: EventSource, response: Response) {
                    android.util.Log.d("VehiclePositionsRepo", "SSE connected to api.dotshell.eu")
                    android.util.Log.d("DotshellRequest", "[SSE] Connection established to $VEHICLE_POSITIONS_STREAM_URL")
                }

                override fun onEvent(
                    eventSource: EventSource,
                    id: String?,
                    type: String?,
                    data: String
                ) {
                    if (type == "heartbeat") {
                        android.util.Log.d("DotshellRequest", "[SSE] Heartbeat received")
                        return
                    }

                    android.util.Log.d("DotshellRequest", "[SSE] Event received: type=$type, id=$id")
                    runCatching { parsePositionsEventData(data) }
                        .onSuccess { 
                            android.util.Log.d("DotshellRequest", "[SSE] Successfully parsed ${it.size} vehicle positions")
                            trySend(Result.success(it))
                        }
                        .onFailure { 
                            android.util.Log.e("DotshellRequest", "[SSE] Failed to parse event data", it)
                            trySend(Result.failure(it))
                        }
                }

                override fun onClosed(eventSource: EventSource) {
                    android.util.Log.d("DotshellRequest", "[SSE] Connection closed")
                    close()
                }

                override fun onFailure(
                    eventSource: EventSource,
                    t: Throwable?,
                    response: Response?
                ) {
                    android.util.Log.d("DotshellRequest", "[SSE] Connection closed, will reconnect automatically")
                    close(t ?: Exception("Vehicle positions SSE connection closed"))
                }
            }

            val eventSource = EventSources.createFactory(streamClient).newEventSource(request, listener)
            awaitClose { eventSource.cancel() }
        }.retryWhen { cause, attempt ->
            val backoff = (1_000L * (1L shl attempt.coerceAtMost(5).toInt())).coerceAtMost(MAX_STREAM_BACKOFF_MS)
            android.util.Log.w(
                "VehiclePositionsRepo",
                "SSE disconnected (${cause.message}), reconnecting in ${backoff}ms"
            )
            delay(backoff)
            true
        }
    }

    /**
     * Fetches all vehicle positions and filters them by the given line name
     *
     * @param lineName The line name to filter by (e.g., "67", "C3", "T1")
     * @return List of vehicle positions for the specified line
     */
    suspend fun getVehiclePositionsForLine(lineName: String): Result<List<SimpleVehiclePosition>> {
        return getAllVehiclePositions().map { positions ->
            positions.filter { it.lineName.equals(lineName, ignoreCase = true) }
        }
    }

    private fun parsePositionsEventData(data: String): List<SimpleVehiclePosition> {
        val eventJson = gson.fromJson(data, JsonObject::class.java)
        val payloadElement = eventJson.get("payload") ?: eventJson

        runCatching {
            gson.fromJson(payloadElement, VehiclePositionsResponse::class.java)
        }.getOrNull()?.let { parsed ->
            if (parsed.success) {
                val fromResponse = extractPositionsFromSiriData(parsed.data)
                if (fromResponse.isNotEmpty()) return fromResponse
            }
        }

        runCatching {
            gson.fromJson(payloadElement, SiriData::class.java)
        }.getOrNull()?.let { siriData ->
            val fromSiri = extractPositionsFromSiriData(siriData)
            if (fromSiri.isNotEmpty()) return fromSiri
        }

        if (payloadElement.isJsonObject && payloadElement.asJsonObject.has("data")) {
            runCatching {
                gson.fromJson(payloadElement.asJsonObject.get("data"), SiriData::class.java)
            }.getOrNull()?.let { siriData ->
                return extractPositionsFromSiriData(siriData)
            }
        }

        return emptyList()
    }

    private fun extractPositionsFromSiriData(data: SiriData?): List<SimpleVehiclePosition> {
        val activities = data?.siri?.serviceDelivery?.vehicleMonitoringDelivery
            ?.flatMap { it.vehicleActivity ?: emptyList() }
            ?: emptyList()
        return mapActivitiesToPositions(activities)
    }

    private fun mapActivitiesToPositions(activities: List<VehicleActivity>): List<SimpleVehiclePosition> {
        return activities.mapNotNull { activity ->
            val journey = activity.monitoredVehicleJourney ?: return@mapNotNull null
            val location = journey.vehicleLocation ?: return@mapNotNull null
            val lat = location.latitude ?: return@mapNotNull null
            val lon = location.longitude ?: return@mapNotNull null
            val lineRef = journey.lineRef?.value ?: return@mapNotNull null
            val vehicleId = activity.vehicleMonitoringRef?.value ?: return@mapNotNull null

            SimpleVehiclePosition(
                vehicleId = vehicleId,
                lineName = extractLineNameFromRef(lineRef),
                latitude = lat,
                longitude = lon,
                bearing = journey.bearing,
                destinationName = journey.destinationName?.firstOrNull()?.value,
                direction = journey.directionRef?.value
            )
        }
    }

    /**
     * Extracts the line name from a LineRef value
     * Example: "ActIV:Line::67:SYTRAL" -> "67"
     * Example: "ActIV:Line::C3:SYTRAL" -> "C3"
     * Example: "ActIV:Line::T1:SYTRAL" -> "T1"
     */
    private fun extractLineNameFromRef(lineRef: String): String {
        // Format: "ActIV:Line::LINE_NAME:SYTRAL"
        val parts = lineRef.split("::")
        if (parts.size >= 2) {
            val afterDoubleDots = parts[1]
            val colonIndex = afterDoubleDots.indexOf(":")
            if (colonIndex > 0) {
                return afterDoubleDots.substring(0, colonIndex)
            }
            return afterDoubleDots
        }
        return lineRef
    }
}
