package com.wasal.app.data.model

data class Conversation(
    val id: String,
    val title: String,
    val lastMessage: String = "",
    val time: String = "",
    val unreadCount: Int = 0,
    val avatarUrl: String? = null,
    val bio: String = "",
    /**
     * معرّف الطرف الآخر كما يعرفه الخادم (users.id).
     * يستخدمه بدء المكالمة: خادم المكالمات يوجّه الأحداث إلى
     * user:<id>، فالمعرّف الخاطئ يعني مكالمة لا تصل أبدًا.
     */
    val peerId: String = "",
    /** رمز وصال للطرف الآخر. */
    val otherCode: String = ""
)
