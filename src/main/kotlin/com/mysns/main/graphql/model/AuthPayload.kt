package com.mysns.main.graphql.model

import java.time.OffsetDateTime

data class AuthPayload(
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiresAt: OffsetDateTime,
    val user: User,
)
