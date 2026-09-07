package com.smartmechanic.ai.data.remote

import com.smartmechanic.ai.BuildConfig
import com.smartmechanic.ai.config.AIConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    val geminiApiService: GeminiApiService by lazy {
        // Interceptor برای لاگ ایمن (بدون افشای API Key) — فقط در Debug فعال است

        val redactingLogger = HttpLoggingInterceptor { message ->
            if (BuildConfig.DEBUG) {
                android.util.Log.d("GeminiHttp", message.replace(Regex("key=[^&\\s]+"), "key=***"))
            }
        }.apply { level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE }

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(AIConfig.REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(AIConfig.REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(AIConfig.REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor(redactingLogger)
            .build()

        Retrofit.Builder()
            .baseUrl(AIConfig.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GeminiApiService::class.java)
    }
}
