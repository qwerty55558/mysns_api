package com.mysns.main.graphql.model

import java.time.OffsetDateTime

data class Comment(
    val id: Long,
    val content: String,
    val authorId: Long,
    val postId: Long,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime? = null,
)
