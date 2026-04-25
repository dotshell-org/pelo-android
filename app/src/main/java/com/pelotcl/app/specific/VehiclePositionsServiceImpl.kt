package com.pelotcl.app.specific

import com.google.gson.JsonObject
import com.pelotcl.app.generic.data.GsonProvider
import com.pelotcl.app.generic.data.models.realtime.vehiclepositions.SiriData
import com.pelotcl.app.generic.data.models.realtime.vehiclepositions.SimpleVehiclePosition
import com.pelotcl.app.generic.data.models.realtime.vehiclepositions.VehicleActivity
import com.pelotcl.app.generic.data.models.realtime.vehiclepositions.VehiclePositionsResponse
import com.pelotcl.app.generic.data.network.VehiclePositionsService
import com.pelotcl.app.generic.utils.network.DotshellRequestLogger
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit

/**
 * Lyon-specific implementation of VehiclePositionsService
 * Handles real-time vehicle positions for the Lyon transport network
 */
class VehiclePositionsServiceImpl : VehiclePositionsService {
    
    override fun getVehiclePositionsStreamUrl(): String {
        return "https://api.dotshell.eu/pelo/v1/vehicle-monitoring/positions/stream"
    }
    
    private val gson = GsonProvider.instance
    private val streamClient = OkHttpClient.Builder()
        .addInterceptor(DotshellRequestLogger.interceptor("sse"))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
    
    override fun streamAllVehiclePositions(): Flow<Result<List<SimpleVehiclePosition>>> {
        return createVehiclePositionsFlow()
    }
    
    override fun streamVehiclePositionsByLine(lineName: String): Flow<Result<List<SimpleVehiclePosition>>> {
        return streamAllVehiclePositions().map { result ->
            result.map { positions ->
                positions.filter { position ->
                    position.lineName.equals(lineName, ignoreCase = true)
                }
            }
        }
    }
    
    override fun streamStrongLinesVehiclePositions(): Flow<Result<List<SimpleVehiclePosition>>> {
        // Lyon strong lines: Metro (A, B, C, D), Tram (T1, T2, T3, T4, T5, T6), Rhonexpress
        val strongLines = setOf("A", "B", "C", "D", "T1", "T2", "T3", "T4", "T5", "T6", "Rhonexpress")
        
        return streamAllVehiclePositions().map { result ->
            result.map { positions ->
                positions.filter { position ->
                    strongLines.contains(position.lineName)
                }
            }
        }
    }
    
    private fun createVehiclePositionsFlow(): Flow<Result<List<SimpleVehiclePosition>>> {
        return callbackFlow {
            val request = Request.Builder()
                .url(getVehiclePositionsStreamUrl())
                .header("Accept", "text/event-stream")
                .build()

            val listener = object : EventSourceListener() {
                override fun onOpen(eventSource: EventSource, response: Response) {
                    android.util.Log.i(
                        "DotshellRequest",
                        "[SSE] Connection established to ${getVehiclePositionsStreamUrl()}"
                    )
                }

                override fun onEvent(
                    eventSource: EventSource,
                    id: String?,
                    type: String?,
                    data: String
                ) {
                    if (type == "heartbeat") {
                        android.util.Log.i("DotshellRequest", "[SSE] Heartbeat received")
                        return
                    }

                    android.util.Log.i(
                        "DotshellRequest",
                        "[SSE] Event received: type=$type, id=$id"
                    )
                    runCatching { parsePositionsEventData(data) }
                        .onSuccess {
                            android.util.Log.i(
                                "DotshellRequest",
                                "[SSE] Successfully parsed ${it.size} vehicle positions"
                            )
                            trySend(Result.success(it))
                        }
                        .onFailure {
                            android.util.Log.e(
                                "DotshellRequest",
                                "[SSE] Failed to parse event data",
                                it
                            )
                            trySend(Result.failure(it))
                        }
                }

                override fun onClosed(eventSource: EventSource) {
                    android.util.Log.i("DotshellRequest", "[SSE] Connection closed")
                    close()
                }

                override fun onFailure(
                    eventSource: EventSource,
                    t: Throwable?,
                    response: Response?
                ) {
                    android.util.Log.i(
                        "DotshellRequest",
                        "[SSE] Connection closed, will reconnect automatically"
                    )
                    close(t ?: Exception("Vehicle positions SSE connection closed"))
                }
            }

            val eventSource = EventSources.createFactory(streamClient).newEventSource(request, listener)
            awaitClose { eventSource.cancel() }
        }.retryWhen { cause, attempt ->
            if (attempt >= 10) {
                return@retryWhen false
            }

            val backoff = (1_000L * (1L shl attempt.coerceAtMost(5).toInt())).coerceAtMost(30_000L)
            delay(backoff)
            true
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
        val positions = activities.mapNotNull { activity ->
            val journey = activity.monitoredVehicleJourney ?: return@mapNotNull null
            val location = journey.vehicleLocation ?: return@mapNotNull null
            val lat = location.latitude ?: return@mapNotNull null
            val lon = location.longitude ?: return@mapNotNull null
            val lineRef = journey.lineRef?.value ?: return@mapNotNull null
            val vehicleId = activity.vehicleMonitoringRef?.value ?: return@mapNotNull null

            val lineName = extractLineNameFromRef(lineRef)

            SimpleVehiclePosition(
                vehicleId = vehicleId,
                lineName = lineName,
                latitude = lat,
                longitude = lon,
                bearing = journey.bearing,
                destinationName = journey.destinationName?.firstOrNull()?.value,
                direction = journey.directionRef?.value
            )
        }

        return positions
    }
    
    /**
     * Extracts the line name from a LineRef value
     * Example: "ActIV:Line::67:ORG" -> "67"
     * Example: "ActIV:Line::C3:ORG" -> "C3"
     * Example: "ActIV:Line::T1:ORG" -> "T1"
     */
    private fun extractLineNameFromRef(lineRef: String): String {
        // Format: "ActIV:Line::LINE_NAME:ORG"
        val parts = lineRef.split("::")
        if (parts.size >= 2) {
            val afterDoubleDots = parts[1]
            val colonIndex = afterDoubleDots.indexOf(":")
            if (colonIndex > 0) {
                return afterDoubleDots.take(colonIndex)
            }
            return afterDoubleDots
        }
        return lineRef
    }
}
