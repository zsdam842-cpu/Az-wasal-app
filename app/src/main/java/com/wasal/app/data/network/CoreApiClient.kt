package com.wasal.app.data.network

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

object CoreApiClient {

    private val jsonMediaType =
        "application/json; charset=utf-8".toMediaType()

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .build()

    val baseUrl: String
        get() = ServerConfig.CORE_SERVER_URL

    /**
     * إنشاء طلب GET إلى خادم الرسائل.
     */
    fun get(
        path: String,
        accessToken: String? = null
    ): Request {
        return requestBuilder(path, accessToken)
            .get()
            .build()
    }

    /**
     * إنشاء طلب POST بصيغة JSON.
     */
    fun post(
        path: String,
        jsonBody: String,
        accessToken: String? = null
    ): Request {
        val body = jsonBody.toRequestBody(jsonMediaType)

        return requestBuilder(path, accessToken)
            .post(body)
            .build()
    }

    /**
     * إنشاء طلب PUT بصيغة JSON.
     */
    fun put(
        path: String,
        jsonBody: String,
        accessToken: String? = null
    ): Request {
        val body = jsonBody.toRequestBody(jsonMediaType)

        return requestBuilder(path, accessToken)
            .put(body)
            .build()
    }

    /**
     * إنشاء طلب DELETE.
     */
    fun delete(
        path: String,
        accessToken: String? = null
    ): Request {
        return requestBuilder(path, accessToken)
            .delete()
            .build()
    }

    /**
     * تنفيذ طلب HTTP وإرجاع الاستجابة.
     * يجب استدعاء هذه الدالة من خلفية التطبيق،
     * وليس مباشرة من واجهة المستخدم.
     */
    @Throws(IOException::class)
    fun execute(request: Request): Response {
        return client.newCall(request).execute()
    }

    private fun requestBuilder(
        path: String,
        accessToken: String?
    ): Request.Builder {
        val normalizedPath = when {
            path.startsWith("/") -> path
            else -> "/$path"
        }

        val builder = Request.Builder()
            .url(baseUrl + normalizedPath)
            .header("Accept", "application/json")

        if (!accessToken.isNullOrBlank()) {
            builder.header(
                "Authorization",
                "Bearer $accessToken"
            )
        }

        return builder
    }
}
