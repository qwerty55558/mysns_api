package com.mysns.main.graphql.model

import com.mysns.main.graphql.data.SubscriptionPlan
import com.mysns.main.graphql.data.ThemePreset

data class SubscribeInput(
    val plan: SubscriptionPlan,
    val theme: ThemePreset,
    val autoRenew: Boolean = true,
)
