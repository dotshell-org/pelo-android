package com.pelotcl.app.data.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Objet singleton pour créer et fournir l'instance Retrofit
 */
object RetrofitInstance {
    
    private const val BASE_URL = "https://data.grandlyon.com/"
    
    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    
    val api: GrandLyonApi by lazy {
        retrofit.create(GrandLyonApi::class.java)
    }
}
