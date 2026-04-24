package com.pelotcl.app.generic.data.network

import android.content.Context
import com.pelotcl.app.generic.utils.DotshellRequestLogger
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
 * Now uses TransportConfig to be city-agnostic
 */
object RetrofitInstance {

    private const val CACHE_SIZE =
        50L * 1024 * 1024 // 50 MB for better caching of large WFS GeoJSON payloads
    private const val CACHE_MAX_AGE_MINUTES = 30 // Cache validity for online requests
    private const val CACHE_MAX_STALE_DAYS = 7 // Use stale cache for up to 7 days when offline

    @Volatile
    private var okHttpClient: OkHttpClient? = null

    @Volatile
    private var retrofit: Retrofit? = null

    /**
     * Initialize the RetrofitInstance with application context for HTTP caching.
     * Should be called once at app startup with the appropriate TransportConfig.
     */
    fun initialize(context: Context, config: TransportConfig) {
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
                .addInterceptor(DotshellRequestLogger.interceptor("http"))
                .addInterceptor(cacheInterceptor)
                .addNetworkInterceptor(networkInterceptor)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()

            retrofit = Retrofit.Builder()
                .baseUrl(config.baseUrl)
                .client(okHttpClient!!)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            // Note: We can't create the specific API instance here since we don't know
            // the concrete implementation. This should be handled by DI.
        }
    }

    /**
     * Get a Retrofit instance configured with the given TransportConfig.
     * This allows creating specific API implementations.
     */
    fun getRetrofit(config: TransportConfig): Retrofit {
        if (retrofit != null) return retrofit!!

        synchronized(this) {
            if (retrofit == null) {
                retrofit = Retrofit.Builder()
                    .baseUrl(config.baseUrl)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
            }
            return retrofit!!
        }
    }

    /**
     * Get the shared OkHttpClient with cache and connection pool.
     * Useful for creating Retrofit instances with different base URLs
     * while sharing the HTTP cache and connection pool.
     * Returns null if not yet initialized - callers should fallback gracefully.
     */
    fun getSharedClient(): OkHttpClient? = okHttpClient

}