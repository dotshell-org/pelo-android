package com.pelotcl.app.data.api

import com.pelotcl.app.utils.DotshellRequestLogger
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Singleton object to create and provide the Retrofit instance for the vehicle positions API
 */
object VehiclePositionsRetrofitInstance {
    
    private const val BASE_URL = "https://api.dotshell.eu/"

    @Volatile
    private var okHttpClient: OkHttpClient? = null

    @Volatile
    private var retrofit: Retrofit? = null

    @Volatile
    private var apiInstance: VehiclePositionsApi? = null

    /**
     * Initialize the Retrofit instance for vehicle positions.
     * No caching since we want real-time data.
     */
    private fun initialize() {
        if (okHttpClient != null) return

        synchronized(this) {
            if (okHttpClient != null) return

            okHttpClient = OkHttpClient.Builder()
                .addInterceptor(DotshellRequestLogger.interceptor("http"))
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build()

            retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(okHttpClient!!)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            apiInstance = retrofit!!.create(VehiclePositionsApi::class.java)
        }
    }

    /**
     * Get the API instance.
     */
    val api: VehiclePositionsApi
        get() {
            apiInstance?.let { return it }
            initialize()
            return apiInstance!!
        }
}
