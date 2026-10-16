package com.wasal.app.data.network

import okhttp3.OkHttpClient

class MediaApiClient(
    val client: OkHttpClient = OkHttpClient()
) {
    val baseUrl: String
        get() = ServerConfig.MEDIA_CALLS_SERVER_URL
}
