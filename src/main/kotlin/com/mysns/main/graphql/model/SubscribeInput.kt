package com.mysns.main.graphql.model

import com.mysns.main.graphql.data.NameEmphasis
import com.mysns.main.graphql.data.NameFont
import com.mysns.main.graphql.data.SubscriptionPlan
import com.mysns.main.graphql.data.ThemePreset

data class SubscribeInput(
    val plan: SubscriptionPlan,
    val theme: ThemePreset,
    val emphasis: NameEmphasis = NameEmphasis.NONE,
    val font: NameFont = NameFont.DEFAULT,
    val autoRenew: Boolean = true,
)
