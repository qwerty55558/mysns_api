package com.mysns.main.graphql.model

import java.time.OffsetDateTime

data class CrowdfundingInput(
    val goalAmount: Int,
    val deadline: OffsetDateTime,
)
