
package com.wasal.app.data.network

object ServerConfig {

    // خادم الرسائل والحسابات وجهات الاتصال والمحادثات
    const val CORE_SERVER_URL =
        "https://wasal-media-server-production.up.railway.app"

    // خادم الوسائط والمكالمات والصور والفيديو والصوت
    const val MEDIA_CALLS_SERVER_URL =
        "https://wasal-server-production.up.railway.app"

    // مسارات Socket.IO
    const val CORE_SOCKET_PATH = "/socket.io"
    const val MEDIA_SOCKET_PATH = "/socket.io"

    // واجهات خادم الرسائل
    const val AUTH_ENDPOINT = "/api/auth"
    const val USERS_ENDPOINT = "/api/users"
    const val CONVERSATIONS_ENDPOINT = "/api/conversations"
    const val MESSAGES_ENDPOINT = "/api/messages"

    // واجهات خادم الوسائط والمكالمات
    const val MEDIA_ENDPOINT = "/api/media"
    const val CALLS_ENDPOINT = "/api/calls"

    // خادم STUN للمكالمات
    const val STUN_SERVER =
        "stun:stun.l.google.com:19302"
}
