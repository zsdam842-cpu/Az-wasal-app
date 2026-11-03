package com.wasal.app.data.network

object ServerConfig {

    // خادم الحسابات والرسائل وجهات الاتصال والمحادثات
    const val CORE_SERVER_URL =
        "https://az-wasal-server-production.up.railway.app"

    // خادم الوسائط والمكالمات والصور والفيديو والصوت
    //
    // تنبيه مهم: العنوان القديم wasal-media-server-production.up.railway.app
    // لا وجود له على Railway ويعيد 404 «Application not found»، وكان سبب
    // فشل كل الصور والصوت والمكالمات. الاسم الصحيح يحمل بادئة az-.
    const val MEDIA_CALLS_SERVER_URL =
        "https://az-wasal-media-server-production.up.railway.app"

    // مسارات Socket.IO
    const val CORE_SOCKET_PATH = "/socket.io"
    const val MEDIA_SOCKET_PATH = "/socket.io"

    // واجهات خادم الحسابات والرسائل
    const val AUTH_ENDPOINT = "/api/auth"
    const val USERS_ENDPOINT = "/api/users"
    const val CONVERSATIONS_ENDPOINT = "/api/conversations"
    const val MESSAGES_ENDPOINT = "/api/messages"

    // واجهات خادم الوسائط والمكالمات
    const val MEDIA_ENDPOINT = "/api/media"
    const val CALLS_ENDPOINT = "/api/calls"
    const val ICE_SERVERS_ENDPOINT = "/api/calls/ice-servers"

    // خادم STUN الاحتياطي (يُستخدم فقط إذا تعذّر جلب القائمة من الخادم)
    const val STUN_SERVER =
        "stun:stun.l.google.com:19302"

    /** عنوان WebSocket لخادم المكالمات. */
    val mediaSocketUrl: String
        get() = MEDIA_CALLS_SERVER_URL
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://")
            .trimEnd('/')

    /** عنوان WebSocket لخادم الرسائل. */
    val coreSocketUrl: String
        get() = CORE_SERVER_URL
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://")
            .trimEnd('/')
}
