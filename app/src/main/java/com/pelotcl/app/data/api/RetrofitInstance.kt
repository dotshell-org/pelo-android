package com.pelotcl.app.data.api

import android.content.Context
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Singleton object to create and provide the Retrofit instance with HTTP caching
 */
object RetrofitInstance {
    
    private const val BASE_URL = "https://data.grandlyon.com/"
    private const val CACHE_SIZE = 50L * 1024 * 1024 // 50 MB for better caching of large WFS GeoJSON payloads
    private const val CACHE_MAX_AGE_MINUTES = 30 // Cache validity for online requests
    private const val CACHE_MAX_STALE_DAYS = 7 // Use stale cache for up to 7 days when offline

    @Volatile
    private var okHttpClient: OkHttpClient? = null

    @Volatile
    private var retrofit: Retrofit? = null

    @Volatile
    private var apiInstance: GrandLyonApi? = null

    /**
     * Initialize the RetrofitInstance with application context for HTTP caching.
     * Should be called once at app startup (e.g., in Application.onCreate or MainActivity).
     */
    fun initialize(context: Context) {
        if (okHttpClient != null) return

        synchronized(this) {
            if (okHttpClient != null) return

            val cacheDir = File(context.cacheDir, "http_cache")
            val cache = Cache(cacheDir, CACHE_SIZE)

            // Interceptor to add cache headers to requests
            val cacheInterceptor = Interceptor { chain ->
                var request = chain.request()

                // Add cache control to the request
                request = request.newBuilder()
                    .cacheControl(
                        CacheControl.Builder()
                            .maxAge(CACHE_MAX_AGE_MINUTES, TimeUnit.MINUTES)
                            .maxStale(CACHE_MAX_STALE_DAYS, TimeUnit.DAYS)
                            .build()
                    )
                    .build()

                chain.proceed(request)
            }

            // Network interceptor to modify response headers for caching
            val networkInterceptor = Interceptor { chain ->
                val response = chain.proceed(chain.request())

                // Ensure response is cacheable by adding appropriate headers
                response.newBuilder()
                    .removeHeader("Pragma")
                    .removeHeader("Cache-Control")
                    .header("Cache-Control", "public, max-age=${CACHE_MAX_AGE_MINUTES * 60}")
                    .build()
            }

            okHttpClient = OkHttpClient.Builder()
                .cache(cache)
                .addInterceptor(cacheInterceptor)
                .addNetworkInterceptor(networkInterceptor)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()

            retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(okHttpClient!!)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            apiInstance = retrofit!!.create(GrandLyonApi::class.java)
        }
    }

    /**
     * Get the API instance. Falls back to non-cached client if not initialized.
     */
    val api: GrandLyonApi
        get() {
            apiInstance?.let { return it }

            // Fallback: create without cache if not initialized
            synchronized(this) {
                if (apiInstance == null) {
                    val fallbackRetrofit = Retrofit.Builder()
                        .baseUrl(BASE_URL)
                        .addConverterFactory(GsonConverterFactory.create())
                        .build()
                    apiInstance = fallbackRetrofit.create(GrandLyonApi::class.java)
                }
                return apiInstance!!
            }
        }

    /**
     * Clear the HTTP cache
     */
    fun clearCache() {
        try {
            okHttpClient?.cache?.evictAll()
        } catch (e: Exception) {
            android.util.Log.e("RetrofitInstance", "Error clearing HTTP cache", e)
        }
    }
}
