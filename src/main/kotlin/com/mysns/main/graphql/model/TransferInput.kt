package com.mysns.main.graphql.model

data class TransferInput(
    val recipientId: String,
    val amount: Int,
    val memo: String? = null,
)
