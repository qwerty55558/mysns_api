package com.mysns.main.graphql.model

data class CreateSplitInput(
    val totalAmount: Int,
    val memo: String? = null,
    /** 개설자 본인 비율(%). null이면 균등 분배 대상에 포함. */
    val creatorPercent: Int? = null,
    /** 태그된 정산 대상(개설자 제외). */
    val participants: List<SplitParticipantInput> = emptyList(),
)

data class SplitParticipantInput(
    val userId: String,
    /** null이면 균등 분배 대상. */
    val percent: Int? = null,
)
