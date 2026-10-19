package com.wasal.app.data.network

import okhttp3.OkHttpClient
import okhttp3.Request

class MediaApiClient(
    val client: OkHttpClient = OkHttpClient()
) {
    val baseUrl: String
        get() = ServerConfig.MEDIA_CALLS_SERVER_URL

    fun get(
        path: String,
        accessToken: String? = null
    ): Request {
        val normalizedPath = if (path.startsWith("/")) path else "/$path"
        val builder = Request.Builder()
            .url(baseUrl + normalizedPath)
            .header("Accept", "application/json")

        if (!accessToken.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $accessToken")
        }

        return builder.get().build()
    }
}
