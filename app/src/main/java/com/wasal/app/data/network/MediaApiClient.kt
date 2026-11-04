package com.wasal.app.data.network

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class MediaApiClient(
    val client: OkHttpClient = OkHttpClient()
) {
    /**
     * عميل الرفع: مهلة أطول من الافتراضية (10 ثوانٍ) لأن رفع صورة أو
     * فيديو على شبكة ضعيفة يحتاج وقتًا، وكانت المهلة القصيرة تُظهر
     * "فشل رفع الملف" مع أن الملف كان يُرسل فعليًا.
     */
    val uploadClient: OkHttpClient by lazy {
        client.newBuilder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(3, TimeUnit.MINUTES)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    val baseUrl: String
        get() = ServerConfig.MEDIA_CALLS_SERVER_URL
}
