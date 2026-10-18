package com.nothing.expensetracker.update.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    // The repository's default branch is "master"; the old "main" path 404'd, so the updater
    // could never fetch version.json and no update was ever offered.
    private const val BASE_URL = "https://raw.githubusercontent.com/Nithin0029/Essentialkeyexpense/master/"

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(logging)
        .build()

    val instance: UpdateApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
            .create(UpdateApi::class.java)
    }
}
