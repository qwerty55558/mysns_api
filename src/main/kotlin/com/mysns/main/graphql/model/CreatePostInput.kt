package com.mysns.main.graphql.model

import com.mysns.main.graphql.data.PostType
import com.mysns.main.graphql.data.ThemePreset

data class CreatePostInput(
    val content: String,
    val imageUrls: List<String>? = null,
    val tag: String? = null,
    val item: String? = null,
    val amount: Int? = null,
    val place: PlaceInput? = null,
    val theme: ThemePreset? = null,
    val type: PostType? = null,
    val crowdfunding: CrowdfundingInput? = null,
)
