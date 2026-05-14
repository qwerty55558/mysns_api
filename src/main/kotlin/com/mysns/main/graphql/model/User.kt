package com.mysns.main.graphql.model

import java.time.OffsetDateTime

data class User(
    val id: Long,
    val username: String,
    val displayName: String,
    val bio: String?,
    val createdAt: OffsetDateTime,
    val avatarUrl: String? = null,
)
