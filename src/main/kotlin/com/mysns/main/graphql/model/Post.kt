package com.mysns.main.graphql.model

import java.time.OffsetDateTime

data class Post(
    val id: Long,
    val content: String,
    val authorId: Long,
    val createdAt: OffsetDateTime,
)
