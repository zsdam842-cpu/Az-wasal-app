package com.wasal.app.data.model

data class MessageReaction(
    val userCode: String,
    val emoji: String
)

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
    val localUri: String? = null,
    // الرد على رسالة: معرّف الرسالة المقتبسة ونصّها المختصر
    val replyToId: String? = null,
    val replyToType: String = "text",
    val replyToSenderId: String = "",
    val replyToPreview: String = "",
    val reactions: List<MessageReaction> = emptyList()
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
