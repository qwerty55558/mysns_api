package com.mysns.main.graphql.model

data class CreateCommentInput(
    val postId: String,
    val content: String,
)
