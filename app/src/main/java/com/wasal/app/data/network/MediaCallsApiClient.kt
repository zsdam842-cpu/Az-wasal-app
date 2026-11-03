package com.wasal.app.data.network

import okhttp3.Request

/**
 * عميل خادم الوسائط والمكالمات.
 *
 * يستخدم نفس إعداد OkHttp في التطبيق (مهلات وسياسة إعادة المحاولة)،
 * ويبني المسارات على MEDIA_CALLS_SERVER_URL لا على خادم الحسابات.
 */
object MediaCallsApiClient {

    val client get() = CoreApiClient.client

    val baseUrl: String
        get() = ServerConfig.MEDIA_CALLS_SERVER_URL.trimEnd('/')

    fun get(
        path: String,
        accessToken: String? = null
    ): Request = builder(path, accessToken)
        .get()
        .build()

    fun post(
        path: String,
        accessToken: String? = null
    ): Request = builder(path, accessToken)
        .post(okhttp3.RequestBody.create(null, ByteArray(0)))
        .build()

    private fun builder(
        path: String,
        accessToken: String?
    ): Request.Builder {
        val normalized = if (path.startsWith("/")) path else "/$path"

        val builder = Request.Builder()
            .url(baseUrl + normalized)
            .header("Accept", "application/json")

        if (!accessToken.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $accessToken")
        }

        return builder
    }
}
