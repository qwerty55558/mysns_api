package com.mysns.main.graphql.data

data class UserCredentials(
    val userId: Long,
    val username: String,
    val passwordHash: String,
)
