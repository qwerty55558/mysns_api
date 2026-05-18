package com.mysns.main.graphql.model

data class CreatePostInput(
    val content: String,
    val imageUrls: List<String>? = null,
    val tag: String? = null,
    val item: String? = null,
    val amount: Int? = null,
    val place: PlaceInput? = null,
)
