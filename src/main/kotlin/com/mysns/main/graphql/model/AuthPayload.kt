package com.mysns.main.graphql.model

data class AuthPayload(
    val accessToken: String,
    val user: User,
)
