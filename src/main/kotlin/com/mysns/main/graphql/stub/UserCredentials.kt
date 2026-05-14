package com.mysns.main.graphql.stub

data class UserCredentials(
    val userId: Long,
    val username: String,
    val passwordHash: String,
)
