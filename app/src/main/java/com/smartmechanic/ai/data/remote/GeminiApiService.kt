package com.smartmechanic.ai.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.POST

interface GeminiApiService {

    /**
     * فراخوانی generateContent برای مدل مشخص‌شده.
     * نام مدل به‌صورت پارامتر Path است تا بتوان بین مدل سبک/سنگین سوییچ کرد
     * بدون تغییر در امضای این متد (نیازمندی بخش ۲ سند طراحی).
     */
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): Response<GeminiResponse>
}
