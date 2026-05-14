package com.mysns.main.graphql.model

data class AddCommentInput(
    val postId: String,
    val content: String,
)
