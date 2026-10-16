package com.wasal.app.data.model

data class Message(
    val id: String,
    val content: String,
    val isMine: Boolean,
    val time: String = "",
    val type: String = "text",
    val status: MessageStatus = MessageStatus.SENT,
    val senderCode: String = "",
    val deletedForEveryone: Boolean = false
)

enum class MessageStatus {
    SENDING,
    SENT,
    DELIVERED,
    READ,
    FAILED
}
