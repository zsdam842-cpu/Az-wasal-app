package com.wasal.app.data.model

data class Contact(
    val id: String,
    val name: String,
    val wasalCode: String = "",
    val avatarUrl: String? = null,
    val bio: String = ""
)
