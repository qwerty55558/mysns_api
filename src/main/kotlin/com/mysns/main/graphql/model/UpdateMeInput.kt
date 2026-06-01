package com.mysns.main.graphql.model

data class UpdateMeInput(
    val displayName: String? = null,
    val bio: String? = null,
    val privateAccount: Boolean? = null,
)
