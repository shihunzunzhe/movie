package com.earthvideo.app.data.api

import android.os.Build
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    /**
     * Emulator: 10.0.2.2 maps to host localhost.
     * For real device debugging on LAN, set HOST_IP to your computer's LAN IP (e.g., 192.168.1.36).
     */
    private val isEmulator = Build.FINGERPRINT.contains("generic") ||
            Build.PRODUCT.contains("sdk") ||
            Build.MODEL.contains("Android SDK") ||
            Build.MANUFACTURER.contains("Genymotion") ||
            Build.BRAND.contains("google") && Build.MODEL.contains("sdk")

    private const val HOST_IP = "192.168.1.36"
    private const val PORT = 8808

    private val BASE_URL by lazy {
        if (isEmulator) "http://10.0.2.2:$PORT/"
        else "http://$HOST_IP:$PORT/"
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val httpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(httpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val apiService: ApiService = retrofit.create(ApiService::class.java)

    fun getBaseUrl(): String = BASE_URL
}
