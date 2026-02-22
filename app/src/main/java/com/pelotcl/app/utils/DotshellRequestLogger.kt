package com.pelotcl.app.utils

import android.util.Log
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

object DotshellRequestLogger {
    private const val TAG = "DotshellRequest"
    private const val HOST = "api.dotshell.eu"

    fun isDotshell(url: HttpUrl): Boolean {
        return url.host.equals(HOST, ignoreCase = true)
    }

    fun logRequest(request: Request, channel: String) {
        if (!isDotshell(request.url)) return
        Log.d(TAG, "[$channel] ${request.method} ${request.url}")
    }

    fun logResponse(response: Response, channel: String) {
        if (!isDotshell(response.request.url)) return
        Log.d(TAG, "[$channel] Response: ${response.code} ${response.message} from ${response.request.url}")
    }

    fun interceptor(channel: String): Interceptor {
        return Interceptor { chain ->
            val request = chain.request()
            logRequest(request, channel)
            val response = chain.proceed(request)
            logResponse(response, channel)
            response
        }
    }
}
