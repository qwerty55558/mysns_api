package com.mysns.main.graphql.model

data class CreatePostInput(
    val content: String,
    val imageUrls: List<String>? = null,
    val tag: String? = null,
)
