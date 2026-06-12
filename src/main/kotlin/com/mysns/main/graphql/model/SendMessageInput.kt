package com.mysns.main.graphql.model

data class SendMessageInput(
    val recipientId: String,
    val text: String? = null,
    val sharedPostId: String? = null,
)
