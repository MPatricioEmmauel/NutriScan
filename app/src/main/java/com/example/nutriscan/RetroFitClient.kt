package com.example.nutriscan

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response

object RetroFitClient {

    private const val BASE_URL = "https://world.openfoodfacts.net/"
    private const val USER_AGENT= "NutriScanApp - Android - Version 1.0"

    private val client = OkHttpClient.Builder()
        .addInterceptor{ chain ->
            val request = chain.request().newBuilder()
                .addHeader("User-Agent", USER_AGENT)
                .build()
            chain.proceed(request)
        }
        .build()

    val instance: API by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        retrofit.create(API::class.java)
    }

}