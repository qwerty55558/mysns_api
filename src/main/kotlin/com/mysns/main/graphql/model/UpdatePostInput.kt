package com.mysns.main.graphql.model

data class UpdatePostInput(
    val content: String? = null,
    val tag: String? = null,
    val item: String? = null,
    val amount: Int? = null,
    val place: PlaceInput? = null,
    val imageUrls: List<String>? = null,
)
