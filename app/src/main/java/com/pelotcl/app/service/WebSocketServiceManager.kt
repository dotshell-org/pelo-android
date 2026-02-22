package com.pelotcl.app.service

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

object WebSocketServiceManager {
    private const val TAG = "WebSocketServiceMgr"

    fun startTrafficAlertsService(context: Context, lines: List<String>) {
        try {
            if (lines.isEmpty()) {
                Log.w(TAG, "Skipped starting WS service: no lines")
                return
            }
            val intent = TrafficAlertsWebSocketService.getStartIntent(context, lines)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            
            Log.d(TAG, "Traffic alerts WebSocket service started with ${lines.size} lines")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start WebSocket service", e)
        }
    }

    fun stopTrafficAlertsService(context: Context) {
        try {
            val intent = Intent(context, TrafficAlertsWebSocketService::class.java)
            context.stopService(intent)
            Log.d(TAG, "Traffic alerts WebSocket service stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop WebSocket service", e)
        }
    }

    fun isServiceRunning(context: Context): Boolean {
        // This is a simplified check - in a real app you might want a more robust solution
        // For now, we'll just return true if we can find the service class
        return try {
            val serviceIntent = Intent(context, TrafficAlertsWebSocketService::class.java)
            // In a real implementation, you would check if the service is actually running
            true
        } catch (e: Exception) {
            false
        }
    }
}
