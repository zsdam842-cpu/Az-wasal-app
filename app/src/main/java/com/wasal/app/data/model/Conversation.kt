package com.wasal.app.data.model

data class Conversation(
    val id: String,
    val title: String,
    val lastMessage: String = "",
    val time: String = "",
    val unreadCount: Int = 0
)
