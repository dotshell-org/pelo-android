package com.pelotcl.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.pelotcl.app.R
import com.pelotcl.app.data.model.TrafficAlert
import com.pelotcl.app.data.repository.TrafficAlertPush
import com.pelotcl.app.utils.DotshellRequestLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.math.min

class TrafficAlertsWebSocketService : Service() {

    companion object {
        private const val TAG = "TrafficAlertsWSService"
        private const val CHANNEL_ID = "traffic_alerts_channel"
        private const val NOTIFICATION_ID = 1001
        private const val TRAFFIC_ALERTS_WS_URL = "wss://api.dotshell.eu/pelo/v1/traffic/alerts/ws"
        private const val MIN_RECONNECT_BACKOFF_MS = 1_000L
        private const val MAX_RECONNECT_BACKOFF_MS = 60_000L

        fun getStartIntent(context: android.content.Context, lines: List<String>): Intent {
            return Intent(context, TrafficAlertsWebSocketService::class.java).apply {
                putStringArrayListExtra("lines", ArrayList(lines))
            }
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val gson = Gson()
    private lateinit var wsClient: OkHttpClient
    private var webSocket: WebSocket? = null
    private var connectionJob: Job? = null
    private var desiredLines: List<String> = emptyList()
    private var isServiceStarted = false
    private var sessionCounter = 0
    private val prefs by lazy { getSharedPreferences("traffic_alerts_ws_service", MODE_PRIVATE) }
    private var restartRequested = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")

        wsClient = OkHttpClient.Builder()
            .addInterceptor(DotshellRequestLogger.interceptor("ws"))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called (startId=$startId, flags=$flags, intentNull=${intent == null})")

        val lines = intent?.getStringArrayListExtra("lines")
            ?: loadSubscriptionFromPrefs()
            ?: emptyList()
        if (!isServiceStarted) {
            isServiceStarted = true
            startForeground()
        }

        updateSubscription(lines)
        restartRequested = false
        return START_STICKY
    }

    private fun startForeground() {
        createNotificationChannel()

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Surveillance des alertes trafic")
            .setContentText("Reception des alertes en temps reel")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        Log.d(TAG, "Service started in foreground")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Alertes Trafic",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Service de surveillance des alertes trafic en temps reel"
                setShowBadge(false)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun updateSubscription(lines: List<String>) {
        val normalizedLines = lines
            .map { it.trim().uppercase() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(50)

        if (normalizedLines.isEmpty()) {
            Log.w(TAG, "No lines provided, stopping service")
            stopSelf()
            return
        }

        if (normalizedLines == desiredLines) {
            Log.d(TAG, "Subscription unchanged (${normalizedLines.size} lines), keeping connection")
            return
        }

        desiredLines = normalizedLines
        saveSubscriptionToPrefs(normalizedLines)
        Log.d(TAG, "Updating subscription (${normalizedLines.size} lines): ${normalizedLines.joinToString(",")}")
        restartConnectionLoop(normalizedLines)
    }

    private fun restartConnectionLoop(lines: List<String>) {
        connectionJob?.cancel()
        webSocket?.cancel()
        val job = Job()
        connectionJob = job
        serviceScope.launch(job) {
            runConnectionLoop(lines, job)
        }
    }

    private suspend fun runConnectionLoop(lines: List<String>, job: Job) {
        var backoffMs = MIN_RECONNECT_BACKOFF_MS
        while (job.isActive) {
            val sessionId = ++sessionCounter
            Log.d(TAG, "Connecting WS session=$sessionId to $TRAFFIC_ALERTS_WS_URL")
            val result = connectOnce(lines, sessionId)
            if (!job.isActive) break
            backoffMs = if (result.wasConnected) {
                MIN_RECONNECT_BACKOFF_MS
            } else {
                min(backoffMs * 2, MAX_RECONNECT_BACKOFF_MS)
            }
            Log.w(TAG, "WS session=$sessionId ended (${result.reason}), reconnect in ${backoffMs}ms")
            delay(backoffMs)
        }
    }

    private data class CloseResult(val reason: String, val wasConnected: Boolean)

    private suspend fun connectOnce(lines: List<String>, sessionId: Int): CloseResult {
        val connected = AtomicBoolean(false)
        return suspendCancellableCoroutine { cont ->
            val completed = AtomicBoolean(false)
            val request = Request.Builder()
                .url(TRAFFIC_ALERTS_WS_URL)
                .build()

            val listener = object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    connected.set(true)
                    Log.d(TAG, "WS session=$sessionId connected")
                    val subscribePayload = JsonObject().apply {
                        addProperty("type", "subscribe")
                        add("lines", gson.toJsonTree(lines))
                    }
                    val payloadStr = subscribePayload.toString()
                    Log.d(TAG, "WS session=$sessionId subscribe: $payloadStr")
                    webSocket.send(payloadStr)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleWebSocketMessage(text)
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "WS session=$sessionId closing: code=$code reason=$reason")
                    webSocket.close(code, reason)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "WS session=$sessionId closed: code=$code reason=$reason")
                    if (completed.compareAndSet(false, true)) {
                        cont.resume(CloseResult("closed $code $reason", connected.get()))
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.w(TAG, "WS session=$sessionId failure: ${t.message}")
                    if (completed.compareAndSet(false, true)) {
                        cont.resume(CloseResult("failure ${t.message}", connected.get()))
                    }
                }
            }

            val ws = wsClient.newWebSocket(request, listener)
            webSocket = ws
            cont.invokeOnCancellation { ws.cancel() }
        }
    }

    private fun handleWebSocketMessage(message: String) {
        try {
            val push = parseTrafficAlertMessage(message)
            if (push != null) {
                Log.d(TAG, "Received alert for line ${push.line}: ${push.alert.title}")
                showTrafficAlertNotification(push)
            } else {
                Log.d(TAG, "WS message ignored: $message")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse WebSocket message", e)
        }
    }

    private fun parseTrafficAlertMessage(message: String): TrafficAlertPush? {
        val root = gson.fromJson(message, JsonObject::class.java)
        val type = root.get("type")?.asString ?: return null
        if (type != "alert") return null

        val line = root.get("line")?.asString ?: return null
        val timestamp = root.get("timestamp")?.asString
        val alertJson = root.get("alert") ?: return null
        val alert = gson.fromJson(alertJson, TrafficAlert::class.java)

        return TrafficAlertPush(line, timestamp, alert)
    }

    private fun showTrafficAlertNotification(push: TrafficAlertPush) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = (System.currentTimeMillis() % 10000).toInt()

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Alerte Trafic - Ligne ${push.line}")
            .setContentText(push.alert.title)
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()

        notificationManager.notify(notificationId, notification)
        Log.d(TAG, "Showing notification for alert: ${push.alert.title}")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed (restartRequested=$restartRequested, lines=${desiredLines.size})")

        isServiceStarted = false
        connectionJob?.cancel()
        webSocket?.cancel()
        serviceScope.cancel()
        if (restartRequested) {
            scheduleRestart("onDestroy")
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.w(TAG, "Task removed while service running")
        restartRequested = true
        scheduleRestart("onTaskRemoved")
    }

    private fun saveSubscriptionToPrefs(lines: List<String>) {
        prefs.edit().putStringSet("lines", lines.toSet()).apply()
    }

    private fun loadSubscriptionFromPrefs(): List<String>? {
        val set = prefs.getStringSet("lines", null) ?: return null
        return set.toList()
    }

    private fun scheduleRestart(source: String) {
        val intent = Intent(this, TrafficAlertsWebSocketService::class.java)
        val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            PendingIntent.getService(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        val triggerAt = System.currentTimeMillis() + 5_000L
        Log.w(TAG, "Scheduling service restart in 5s from $source")
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
    }
}
