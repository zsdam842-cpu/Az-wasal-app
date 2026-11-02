package com.wasal.app.data.model

data class Message(
    val id: String,
    val content: String,
    val isMine: Boolean,
    val time: String = "",
    val type: String = "text",
    val status: MessageStatus = MessageStatus.SENT,
    val senderCode: String = "",
    val deletedForEveryone: Boolean = false,
    // معاينة محلية (صورة/فيديو) تُعرض لحظة الإرسال قبل رفع الملف
    val localUri: String? = null
)

val Message.isLocalPending: Boolean
    get() = id.startsWith("local-")

enum class MessageStatus {
    SENDING,
    SENT,
    DELIVERED,
    READ,
    FAILED
}
